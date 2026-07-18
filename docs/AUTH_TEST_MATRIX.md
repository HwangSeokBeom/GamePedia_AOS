# Auth Test Matrix

All tests are deterministic: virtual time (`runTest` + explicit
`runCurrent`/`advanceUntilIdle`), gated fakes (`CompletableDeferred` per provider
call), explicit arrival channels, and hard barriers (latches that fail loudly on
timeout). No sleeps, no polling loops, no silent "eventually" helpers. Assertions
always include final session/store state, not just call counts.

| # | Required behavior | Test |
|---|---|---|
| 1 | Two concurrent callers share one refresh | `AuthRefreshConcurrencyTest.two concurrent callers share one refresh request and one result` |
| 2 | Sequential refresh uses the rotated token | `…sequential refresh submits the rotated refresh token` |
| 3 | One waiter cancellation keeps the survivor's refresh | `…cancelling one waiter keeps the shared refresh alive for the survivor` |
| 4 | Last waiter cancellation cancels provider work | `…cancelling the last waiter cancels the provider request and leaves the session untouched` |
| 5 | Delayed subscriber receives the shared result | `…a waiter joining mid-flight receives the same shared result without a second request` |
| 6 | Login supersedes an older refresh success | `AuthSupersessionTest.late refresh success cannot overwrite a newer login session` |
| 7 | Login supersedes an older refresh failure | `…late refresh failure cannot clear a newer login session` |
| 8 | Signup supersedes an older refresh | `…signup supersedes an in-flight refresh` |
| 9 | Google login supersedes an older refresh | `…google login supersedes an in-flight refresh` |
| 10 | Logout prevents late refresh persistence | `…refresh completing after logout cannot restore credentials` |
| 11 | Account deletion prevents late refresh persistence | `…refresh completing after account deletion cannot restore credentials` |
| 12 | Late failure cannot clear a newer session | `…refresh failure resolving after a newer login leaves that session authenticated` |
| 13 | Cancellation/completion race leaves the coordinator reusable | `AuthRefreshConcurrencyTest.response arriving as the last waiter cancels…` + `…cancellation before the response…` + `AuthRaceStressTest.completion racing last-waiter cancellation…` (real threads, ×50) |
| 14 | Refresh failure detaches the flight | `…network failure detaches the flight and preserves the stored session` + `…token-revoked failure clears the session…` |
| 15 | Credential logging privacy | `AuthPrivacyTest` (diagnostics capture across the full lifecycle + toString redaction) |
| 16 | OkHttp 401 fan-out triggers one refresh | `SessionAuthenticatorHttpTest.two concurrent 401s share one refresh and both requests succeed after rotation` |
| 17 | Refresh endpoint never recursively refreshes | `…a 401 from the refresh endpoint fails once and never recurses` |
| + | Retry at most once per token state | `…a request that still 401s after one rotation is not retried again` |
| + | No overlapping rotating-token submission | `AuthSupersessionTest.replacement refresh never overlaps the superseded flight's request` + stress invariant |
| + | 8-way fan-out shares one flight (real threads, ×50) | `AuthRaceStressTest.concurrent waiter fan-out always shares exactly one flight` |
| + | Wire contract: envelopes, zod null-vs-omitted, code mapping | `AuthContractDecodingTest`, `AuthFailureMapperTest` (core/network) |

## Stress policy

`AuthRaceStressTest` runs each race scenario 50× per test invocation with real
dispatchers and `CyclicBarrier`-aligned threads, asserting interleaving-independent
invariants (consistent token pair, coordinator reusability, single wire
submission). The full auth test classes are additionally executed ≥20× via Gradle
rerun during verification (see VERIFICATION.md).
