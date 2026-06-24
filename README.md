# grimoire-extensions-api

The API contract shared by the Grimoire reader app and its content extensions:
the model types (`Novel`, `Chapter`, `NovelPage`, ...), the `Source` interfaces,
and the HTTP/Jsoup base classes. Published to GitHub Packages as
`io.grimoire:extensions-api`.

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
  `api/build.gradle.kts`) to the next version.

Consumers — the `grimoire` app and the `grimoire-extensions` repo — should pin a
concrete `X.Y.Z` and upgrade deliberately.
