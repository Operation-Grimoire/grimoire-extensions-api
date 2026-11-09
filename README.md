# grimoire-extensions-api

The API contract shared by the Grimoire reader app and its content extensions:
the model types (`Novel`, `Chapter`, `NovelPage`, ...), the `Source` interfaces,
and the HTTP/Jsoup base classes. Published to GitHub Packages as
`io.grimoire:extensions-api` for Android, with a shared JVM runtime in
`io.grimoire:extensions-core`.

## Shared JVM runtime

The `core` module owns the portable models, source interfaces, HTML helpers,
HTTP base classes and common network exceptions. It produces a Java 11-compatible
JAR without Android class references. The `api` module keeps the Android WebView
cookie jar, browser challenge handler and `NetworkContext`, and re-exports core
as a compile dependency. Android consumers keep the existing API coordinate.

Both artifacts use the same version from the root build. Publish core before
the Android wrapper. This change requires a release before consumers can resolve
the new coordinate from GitHub Packages; local composite builds require no
publication or global Maven-cache replacement.

JVM hosts install their browser/session integration before instantiating sources:

```kotlin
SourceNetwork.install(object : SourceHttpPlatform {
    override val cookieJar = hostCookieJar
    override val userAgent: String get() = hostBrowserUserAgent
    override val interceptors = listOf(hostChallengeInterceptor)
})
```

The host owns these collaborators for the process lifetime. Sources share one
HTTP client and connection pool; use `defaultOkHttpClient().newBuilder()` for
source-specific clients. Requests with an explicit User-Agent retain it.
Replacing an installed platform is rejected so existing sources cannot retain
different session owners accidentally.

Android discovers `AndroidSourceHttpPlatform` through the wrapper's ServiceLoader
resource using the host API classloader. This preserves source/client construction
before `NetworkContext.init(context)`; keep that initialization for browser-backed
challenge handling. Android's existing cookie/challenge behavior remains in the
adapter. A JVM host with no installed platform fails explicitly.

Build and test:

```sh
./gradlew :core:test :core:jar :api:assembleRelease :api:lintRelease
```

The CI binary check compares public/protected class hierarchies and member JVM
descriptors against the pull request base. It also checks duplicate classes,
Android references in core and the wrapper's core POM dependency. It is not a
replacement for runtime browser/device or Kotlin source-compatibility tests.

Tools that compile API source directories directly must migrate before adopting
this layout: portable sources now live in `core/src/main/kotlin`, while the
Android directory contains only platform adapters. Inspector migration is a
separate consumer gate before coordinating the API release.

## Source model

A source is **capability-based**: it implements a small base plus only the
feature interfaces it actually supports. There is no monolithic `CatalogueSource`
— the host detects each capability with an `is`/`as?` check and surfaces it
(browse chips, global search, migrate, settings).

- **`source.Source`** — base of every source: `name`, `lang: Language`, and
  `getNovelDetails`.
- **Transport bases** (`source.http`) — `HttpSource` (shared OkHttp client +
  `get`/`GET`/`resolveUrl`) and `ParsedHttpSource` (adds the Jsoup `asJsoup`
  helper). Carry no capability themselves.
- **Content axis** — either web (`source.web`: a chapter list via
  `ChapterListSource` **or** `PaginatedSource`, plus `PageListSource` for page
  content) or whole-book (`source.epub.EpubSource`).
- **Feature mixins** (`source.feature`) — `PopularSource`, `LatestSource`,
  `SearchSource`, `FilterSource`, `ConfigurableSource`, `MultiLanguageSource`,
  `MultiHostSource`, `WebViewLoginSource`.

```kotlin
@SourceInfo(name = "Foo", lang = Language.EN, baseUrl = "https://foo.com", versionCode = 1)
class Foo : ParsedHttpSource(), PopularSource, LatestSource, SearchSource,
    FilterSource, ChapterListSource, PageListSource { /* ... */ }
```

Packages: `model.{novel,filter,lang,pref}`, `source`, `source.{http,web,epub,feature}`,
`network` (transport internals), `util` (Jsoup `richHtml`/`richDescription`).
Identity is derived from the package name via `sourceIdFor()` — sources never
declare an id. `@SourceInfo` is read statically by the CI index generator.

## Versioning

Semantic versioning. The model types are a binary contract shared with
already-installed extension APKs, so the version communicates ABI impact:

- **PATCH** — fixes, no API surface change.
- **MINOR** — additive, binary-compatible changes (e.g. a new trailing field on
  a model). Installed extensions keep working. Model constructors carry
  `@JvmOverloads`, so additions must stay append-only — new fields last, always
  defaulted, never reordered/removed.
- **MAJOR** — breaking changes. Extensions must be rebuilt against the new API.

## Releasing

- Every push to `main` publishes `<next>-SNAPSHOT` (mutable) for developing the
  app and extensions against unreleased changes.
- A release is cut by pushing a `vX.Y.Z` tag — or running the **Publish to
  GitHub Packages** workflow with a `tag` input — which publishes the immutable
  `X.Y.Z` artifact.
- After cutting a release, bump the `-SNAPSHOT` base (`publishVersion` in
  root `build.gradle.kts`) to the next version.

Consumers — the `grimoire` app and the `grimoire-extensions` repo — should pin a
concrete `X.Y.Z` and upgrade deliberately.
