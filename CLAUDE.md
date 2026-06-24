# grimoire-extensions-api

The API contract shared by the Grimoire app and every content extension — the
model types (`Novel`, `Chapter`, `NovelPage`), `Source` interfaces, and the
HTTP/Jsoup base classes. Published to GitHub Packages as
`io.grimoire:extensions-api`.

## Package layout

Capability-based — a source mixes in only the interfaces it supports; there is
no `CatalogueSource` umbrella.

```
model/
  novel/    Novel, NovelStatus, Chapter, NovelPage, PageContent (sealed: Text/Image/Separator)
  filter/   Filter (sealed: Header/Separator/Text/CheckBox/TriState/Select/Group/Sort)
  lang/     Language (enum; code + displayName + nativeName; MULTI/UNKNOWN sentinels)
  pref/     SourcePreference, PrefValue (Str/Bool/Sensitive), ConfigValidationResult
source/
  Source            base: name, lang, getNovelDetails
  SourceInfo        @annotation read statically by the CI index generator
  SourceId          sourceIdFor(pkg) — identity from package name (no declared id)
  AdultContent      NONE / PARTIAL / FULL (surfaced in index.json)
  http/             HttpSource, ParsedHttpSource (transport only, no capability)
  web/              ChapterSource, ChapterListSource, PaginatedSource, PageListSource
  epub/             EpubSource (whole-book content axis)
  feature/          Popular/Latest/Search/Filter/Configurable/MultiLanguage/MultiHost/WebViewLogin
network/            OkHttp client, Cloudflare + cookie-jar internals
util/               richHtml / richDescription (Jsoup → constrained HTML)
```

Content is an axis: a web source declares a chapter list (`ChapterListSource`
**or** `PaginatedSource`) + `PageListSource`; an `EpubSource` delivers a whole
book instead. `MultiLanguageSource.availableLanguages()` is `suspend` and returns
`List<Language>`; `ConfigurableSource.setPreferences` takes `Map<String, PrefValue>`.

## ABI stability

The model types are a binary contract with already-installed extension APKs.
Changing a shared `data class` changes its generated constructor signature, so a
pre-built extension breaks at runtime with `NoSuchMethodError`.

- Model constructors carry `@JvmOverloads` — keep it on every model.
- Changes must be **append-only**: new fields last, always defaulted, never
  reordered/removed/retyped. Such additions are binary-compatible.
- A breaking change is a MAJOR version bump and forces every extension to be
  rebuilt.

## Versioning & releasing

The published version is computed in `api/build.gradle.kts` (`publishVersion`) —
it is not a hardcoded constant:

- No `API_RELEASE_TAG` env → `<base>-SNAPSHOT`. Every push to `main` publishes
  this mutable SNAPSHOT, for developing the app/extensions against unreleased
  changes.
- `API_RELEASE_TAG` set → the concrete `X.Y.Z`. The Publish workflow sets it
  from a pushed `vX.Y.Z` git tag.

The **git tag is the source of truth for a release**. It pins `X.Y.Z` to one
commit and is the deliberate publish gate; a hardcoded Gradle constant would
re-publish the same immutable coordinate on every merge to `main`.

### To cut a release

1. Push a `vX.Y.Z` tag on `main`:
   `git tag -a vX.Y.Z <main-sha> -m "Release X.Y.Z" && git push origin vX.Y.Z`
   This triggers the Publish workflow → immutable
   `io.grimoire:extensions-api:X.Y.Z` on GitHub Packages.
2. Bump the `-SNAPSHOT` base (`publishVersion` in `api/build.gradle.kts`) to the
   next version.
3. Pin consumers to the new concrete version:
   - `grimoire` app — `app/build.gradle.kts`
   - `grimoire-extensions` — `gradle/libs.versions.toml` (`extensionsApi`)

SemVer signals ABI impact: PATCH = fix, MINOR = additive / binary-compatible
(installed extensions keep working), MAJOR = breaking (extensions must rebuild).
