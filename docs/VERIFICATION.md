# Verification — Trustworthy Search slice

Date: 2026-07-13. Machine: macOS (Darwin 25.4.0), JDK 17.0.19 (Homebrew), Android
SDK platform 36.1, Gradle 8.14.3 (wrapper), AGP 8.11.1.

## Commands run and results

| Command | Result |
|---|---|
| `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL (debug APK produced) |
| `./gradlew build` (assemble debug+release, unit tests, lint) | ✅ BUILD SUCCESSFUL |
| `./gradlew test` | ✅ 74 test executions (37 unique × debug/release variants), 0 failures |
| `./gradlew lint` | ✅ BUILD SUCCESSFUL, no lint errors |
| `./gradlew connectedDebugAndroidTest` (Pixel_8 AVD, emulator-5554) | ✅ 6/6 passed, 0 failed |

Release compilation verified **without** signing credentials (unsigned release
artifacts; signing deferred per RISK R-02).

## Test inventory vs. required coverage

| # | Required behavior | Test |
|---|---|---|
| 1 | Query debounce | `SearchViewModelTest.suggestions are debounced…` |
| 2 | Latest query wins | `…an older search response never replaces a newer query` |
| 3 | Superseded request cancellation | `…submitting a new query cancels the in-flight search` |
| 4 | Search success decoding | `SearchContractDecodingTest.search success fixture decodes…` + `SearchApiHttpContractTest` |
| 5 | Suggestions success decoding | `…suggestions success fixture decodes…` + HTTP-shape test |
| 6 | Empty-result state | `…zero games renders Empty state not Error` + decode fixture |
| 7 | Invalid query mapping | `SearchFailureMapperTest.INVALID_SEARCH_QUERY…` + ViewModel over-long test |
| 8 | Invalid limit mapping | `SearchFailureMapperTest.INVALID_GAMES_LIMIT…` |
| 9 | Provider error mapping | mapper tests (upstream/Twitch/rate-limit/500s) + ViewModel retryable-error test |
| 10 | Retry behavior | `…retry reruns the accepted query with forceRefresh…` |
| 11 | Clear cancellation | `…clear cancels in-flight search and suggestions…` |
| 12 | Filter change without request | `…genre filter changes never trigger a backend request` |
| 13 | Raw query absent from diagnostics | `DefaultSearchRepositoryTest.diagnostics never contain the raw query…` |
| 14 | Loading state rendering | `SearchStateRenderingTest.loadingState_…` + ViewModel loading test |
| 15 | Result state rendering | `SearchStateRenderingTest.successState_rendersResultList` |
| 16 | Empty state rendering | `SearchStateRenderingTest.emptyState_…` |
| 17 | Error state rendering | `SearchStateRenderingTest.errorState_…` + non-retryable variant |

Extra: cache determinism/TTL/forceRefresh, no-Authorization-header assertions,
malformed-response mapping, state restoration, suggestion-tap submit, idle render.

## Instrumentation evidence

`./gradlew connectedDebugAndroidTest` executed on the local `Pixel_8` AVD
(emulator-5554, Android 16 / API 36 image):

```
Starting 6 tests on Pixel_8(AVD) - 16
Pixel_8(AVD) - 16 Tests 6/6 completed. (0 skipped) (0 failed)
BUILD SUCCESSFUL
```

XML results: `tests="6" failures="0" errors="0"`.

## Manual runtime smoke test (emulator)

- Debug APK installed and launched: Search screen renders (idle state, field,
  headings) with no crashes in logcat.
- Typing `zelda` → "Loading suggestions…" appears after the debounce (never an
  empty-result render).
- IME Search action → distinct "Searching…" loading state with spinner and clear
  button.
- Live backend check: both `staging-gamepedia-api.duckdns.org` and
  `gamepedia-api.duckdns.org` were unreachable from this network at verification
  time (host-level `curl` timeouts, HTTP 000). The app's 15 s call timeout fired
  and rendered the **retryable Timeout error state** with correct localized copy
  and a Retry button — the error path verified end-to-end against real
  infrastructure conditions.
- Live success path against a real backend therefore **could not be verified
  today**; it is covered by MockWebServer HTTP-shape tests and Compose
  instrumentation rendering tests. Re-run the smoke test when the staging host is
  reachable (or against a local backend via
  `-Pgamepedia.apiBaseUrl=http://10.0.2.2:3001`).

## Sensitive log scan

- No `HttpLoggingInterceptor` / `logging-interceptor` anywhere in sources or catalog.
- Only `Log` call: `SearchDiagnostics` default sink (fields: category, outcome,
  q_len, result_count/error code, latency, cache hit/miss).
- No secrets/keys/tokens in tracked files; `local.properties`, keystores, Firebase
  configs gitignored and absent from git status.

## Not verified

- Runtime behavior against the real staging backend (no live-network test in CI
  scope for this slice; MockWebServer covers the HTTP shape).
- Play-signed release artifact (no signing exists yet — intentional).
