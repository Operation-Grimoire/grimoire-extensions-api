# Shared JVM runtime validation

Local Windows/JDK 21 checks completed 2026-11-08. The API source base was `dd6145178e8d421e2f7c76991d3c66580c858a72`.

- The corrected pre-extraction baseline passed 26 tests. One existing test put a block element inside `p`, which the HTML parser reparsed; the fixture now uses a valid section wrapper.
- Core passed 30 JVM tests, including host user-agent handling, cookie scope/sharing, challenge-interceptor retry, explicit platform setup and shared pool lifetime.
- Android release AAR assembly and lint passed (0 errors / 10 warnings).
- `tools/check_api_abi.py` passed against the base AAR and the app's cached `extensions-api:1.2.2` AAR: 65 public/protected classes retain JVM descriptors/hierarchy; no duplicate API classes or Android references in core; the wrapper POM exports the same core version.
- Grimoire compiled, passed 413 unit tests and assembled a debug APK against the local composite.
- The desktop consumer passed its 13-test suite through the core artifact. Live Royal Road returned 20 novels, 109 chapters for Mother of Learning and 172 content blocks for its first chapter.
- A dedicated Android instrumentation app loaded the actual packaged provider before `NetworkContext.init`, retained its client afterward and shared WebView login cookies with source HTTP. Its controlled encrypted-session flow passed all 12 checks / three Android phases on the warmed emulator.

The cached baseline AAR SHA-256 was `d58841d39e4b311316b04489ae96ffdf2be0b9700061972d0b28c0df877bd911`. This was local binary evidence, not a fresh package download. Binary checks cover JVM descriptors, not all Kotlin source or runtime behavior.

The local API check used:

```sh
./gradlew :core:test :api:assembleRelease :api:lintRelease \
  :core:generatePomFileForCorePublication :api:generatePomFileForReleasePublication --no-build-cache
```

`--no-build-cache` avoided a shared-cache lock held by the parallel app build; no caches were deleted. One emulator attempt failed in AndroidX application-factory startup before the tests; the warmed rerun completed. No app failures were suppressed.

Workflow YAML and publication order were checked locally. Remote CI/review, package publication, default consumer pins and real-source authenticated browser compatibility remain unexecuted. Main pushes publish a SNAPSHOT automatically, so coordinate the Inspector migration with the API layout change.

## Consumer migration follow-up

The Android challenge detector was extracted unchanged into core as
`Response.isSourceBrowserChallenge()`. Both Android and Inspector now call it;
only the host decides whether to solve or report a challenge. Two added detector
tests bring the core suite to 32 passing tests. Android release assembly/lint and
the 65-class ABI checks against both baselines passed again.

The local Inspector `shared-runtime` worktree consumes the core artifact without
compiling API source trees. Five deterministic migration tests and all 26 sources'
offline checks passed. Live Royal Road completed with zero errors and two
missing-thumbnail warnings. A null-context compatibility shim remains for
Android-specific GalaxyNovels source code; no browser is emulated.

The extensions `dual-runtime-artifacts` worktree builds Royal Road APK/JAR from
the same source and verifies matching identity/version metadata. Shared fixture
results matched across a packaged JVM JAR and an actual API 35 APK loaded through
DexClassLoader, with both hashes checked. Grimoire desktop tests also consumed
the external JAR. These are local consumer proofs; publication and coordinated
remote integration remain pending.
