# GamePedia Android — Architecture

## Overview

Feature-oriented multi-module Gradle project (Kotlin DSL + version catalog). The
guiding rule is the smallest structure that supports testability, feature isolation,
network-contract ownership, and future expansion — not enterprise ceremony
(DECISIONS D-0002).

```
app                   Application shell: manifest, theme, AppContainer (manual DI),
                      MainActivity, Navigation Compose host
core/common           DispatcherProvider, TimeSource (JVM-only module)
core/model            Domain models + SearchFailure taxonomy + SearchOutcome (JVM-only)
core/network          Contract ownership: DTOs mirroring the backend OpenAPI,
                      Retrofit SearchApi, envelope decoding, failure mapping,
                      privacy-safe SearchDiagnostics, BuildConfig base URL
core/designsystem     GamePediaTheme (Material 3, light/dark, dynamic color),
                      shared state views (loading/empty/error)
feature/search        Trustworthy Search slice: repository + in-memory cache,
                      SearchViewModel state machine, Compose screen
```

Dependency direction: `app → feature → core`, `feature/search → core/*`,
`core/network → core/model + core/common`. Features never depend on features.

## Stack

Kotlin 2.2, Jetpack Compose (BOM 2025.06.01, Material 3), Coroutines/Flow,
ViewModel + SavedStateHandle, Navigation Compose, Retrofit 3 + OkHttp 4 +
kotlinx-serialization, Coil, JUnit4 + coroutines-test + Turbine + MockWebServer,
Compose UI testing.

## Build & environments

- `compileSdk 36`, `targetSdk 36`, `minSdk 26` (D-0004), AGP 8.11.1, Gradle 8.14.3.
- Application ID `com.hwb.gamepedia.android.dev` is **temporary** (D-0003).
- Environments via build types: debug → staging base URL, release → production
  base URL; both overridable with `-Pgamepedia.apiBaseUrl=...` (D-0005).
- Committed secrets: none. `local.properties`, keystores, and Firebase configs are
  gitignored; release builds are unsigned until Play Console ownership exists.

## State management pattern

Single `StateFlow<SearchUiState>` per screen ViewModel; UI is a pure function of
state. Suggestions and results have separate sub-states so the typeahead pipeline
can never clobber the results pane. Concurrency invariants (debounce, latest-wins,
cancellation) live in the ViewModel and are unit-tested with virtual time.

## Networking policy

- Public search endpoints: no `Authorization` header, ever (contract 8790a13).
- Timeouts: connect 10 s, read/write/call 15 s.
- No automatic HTTP retry (429 requires backoff); retries are user-initiated.
- No OkHttp logging interceptor in any build type. Diagnostics only through
  `SearchDiagnostics` (query length, result count, latency, cache hit/miss, stable
  error code — never the query, URL, headers, or bodies).
- Strict-ish decoding: non-lenient, no value coercion, nullability modeled exactly;
  unknown keys ignored for additive backend evolution. Shape violations surface as
  `MalformedResponse`.

## Error taxonomy

`SearchFailure` (core/model) is the single failure vocabulary, keyed by stable
backend `error.code` values plus transport categories. Each entry declares
retryability and a privacy-safe diagnostic category. UI copy is resolved per-type in
the feature layer (`userMessageRes`); backend `message` strings are never displayed.

## DI

Manual wiring in `AppContainer` (app module). Hilt is deferred until multiple
features make manual wiring costly (D-0002).

## Testing strategy

- core/network: contract fixture decoding (fixtures derived from backend contract
  tests), failure mapping, MockWebServer request-shape tests (path/params/no auth).
- feature/search: ViewModel behavior with `StandardTestDispatcher` virtual time;
  repository cache determinism/TTL/forceRefresh; diagnostics privacy regression.
- feature/search androidTest: Compose rendering per state + retry wiring.
