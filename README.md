# grimoire-extensions-api

The API contract shared by the Grimoire reader app and its content extensions:
the model types (`Novel`, `Chapter`, `NovelPage`, ...), the `Source` interfaces,
and the HTTP/Jsoup base classes. Published to GitHub Packages as
`io.grimoire:extensions-api`.

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
