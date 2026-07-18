# Risk Register — GamePedia Android

| ID | Risk | Impact | Likelihood | Mitigation | Status |
|----|------|--------|------------|------------|--------|
| R-01 | Temporary application ID `com.hwb.gamepedia.android.dev` reaches a public build | Store identity locked to a dev ID; new listing required to fix | Medium | Documented as temporary in DECISIONS D-0003; release checklist gate | Open |
| R-02 | No Play Console ownership / signing configuration exists | Cannot release; late signing decisions can force ID churn | Certain (by design, deferred) | Track in release checklist; use Play App Signing when created | Open |
| R-03 | Backend contract drift after commit `8790a13` | Silent DTO mismatch, runtime decode failures | Medium | Strict decoding + contract fixtures from backend tests; record contract commit in API_CONTRACT_MATRIX and re-gate each slice | Open |
| R-04 | Search endpoints have no pagination; product may later expect infinite scroll | UI rework | Low | Contract documents limit-only behavior; UI treats list as complete | Accepted |
| R-05 | duckdns.org staging/production hosts may change | Broken environments | Low | Base URL injectable via Gradle property, single source in core/network | Open |
| R-06 | Raw query leakage through logs/analytics | Privacy policy violation, parity break with backend redaction | Low | No OkHttp body logging; privacy-safe diagnostics only (query length, result count, error code); dedicated unit test asserts no raw query in diagnostics | Mitigated |
| R-07 | Instrumentation tests may not run in CI/local (emulator availability) | UI regressions ship unverified | Medium | Pixel_8 AVD exists locally; VERIFICATION.md records exactly what ran | Open |
| R-08 | minSdk 26 chosen without production user data | Excludes some old devices or overly conservative | Low | Revisit before release (DECISIONS D-0004) | Open |
| R-09 | No DI framework; manual wiring may not scale past a few features | Refactor cost later | Low | Deliberate (D-0002); introduce Hilt when a second/third feature lands | Accepted |
| R-10 | Google Sign-In unimplemented: OAuth Web+Android client IDs and signing SHA-1 don't exist yet | Google login unusable; blocked on R-01/R-02 identity decisions | Certain (by design) | `GoogleIdTokenProvider` boundary isolates the gap; UI surfaces "not available"; config steps in AUTH_CONTRACT.md | Open |
| R-11 | Auth contract extracted from backend source (`dev` merge `28a113e`, atomic rotation fix `7c9f88f`), not from a published OpenAPI section | Silent drift if backend auth changes without a contract gate | Medium | Re-gate AUTH_CONTRACT.md + fixtures when backend publishes auth in cross-platform OpenAPI; codes pinned by mapper tests | Open |
| R-12 | Runtime login/refresh against a live backend unverified (staging/prod hosts unreachable from dev network on 2026-07-18) | Integration surprises (CORS/proxy/header quirks) despite MockWebServer coverage | Medium | VERIFICATION.md records exact gap; re-run emulator smoke when hosts reachable | Open |
| R-13 | Keystore key invalidation (OS restore, security settings change) silently logs users out | Unexpected re-login for affected users | Low | Deliberate degradation (D-0006); monitor once analytics exist | Accepted |
