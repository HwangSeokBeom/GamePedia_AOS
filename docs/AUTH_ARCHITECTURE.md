# Authentication & Session Architecture

Parity target: the concurrency guarantees of the merged iOS 2.0 implementation
(GamePedia PR #14, `fix/auth-refresh-concurrency-ios`), re-expressed with Kotlin
coroutines instead of Combine.

## Module layout

```
core/model      AuthUser, AuthTokens/AuthSession (redacted toString), AuthFailure,
                AuthOutcome, SessionState
core/storage    TokenStore + UserSessionStore interfaces;
                SecureSessionStorage (AndroidKeyStore AES-256-GCM over private
                SharedPreferences, synchronous whole-record commits);
                InMemory* deterministic implementations for tests
core/network    AuthApi (plain client) / AuthedUserApi (authenticated client),
                auth DTOs, AuthFailureMapper, AuthDiagnostics,
                SessionTokenGateway + AuthorizationInterceptor + SessionAuthenticator
core/auth       AuthRepository + DefaultAuthRepository (single-flight refresh
                coordinator, session generation, session StateFlow, gateway impl)
feature/auth    Minimal login/signup/Google-boundary/logout screen + AuthViewModel
app             Wiring: plain vs authenticated client, SecureSessionStorage,
                navigation (Search top-bar account action → auth route)
```

Dependency direction is unchanged: `app → feature → core`; `core/auth →
core/{network,storage,model,common}`; features never depend on features. The
Search feature exposes an optional `onAccountClick` hook (default null) so it has
no dependency on the auth feature.

## Client topology (recursive-refresh safety)

- **Plain client**: public search + every credential `/auth` endpoint (signup,
  login, google, refresh, logout). Never carries Authorization; has no
  authenticator. A 401 from `/auth/refresh` therefore *cannot* re-enter the
  refresh path — non-recursion is structural, and a test asserts it.
- **Authenticated client**: plain client + `AuthorizationInterceptor` (attaches
  `Bearer`) + `SessionAuthenticator` (401 → single-flight refresh → retry at most
  once for a given token state). Used by `AuthedUserApi` (`GET/DELETE /auth/me`)
  and future authenticated slices.

## Refresh coordinator state model

One flight slot + a monotonically increasing session **generation**, both guarded
by a plain lock (`synchronized`); the lock is never held across network I/O or
`await`.

A `RefreshFlight` captures at creation: the generation, the stored refresh token
(read exactly once per flight), a `CompletableDeferred` result (always completed,
never cancelled — so a waiter's `CancellationException` is unambiguous), and a
`slotCleared` signal.

Transitions:

| Event | Effect |
|---|---|
| First caller | Creates the flight (creator pre-registered as waiter #1), launches provider job in the repository scope |
| Concurrent caller | Joins as one more waiter while the flight is joinable |
| Waiter cancelled | Count decrements; shared work survives for remaining waiters |
| LAST waiter cancelled | Flight abandoned, provider job cancelled; store untouched |
| Provider result | Under lock: identity + generation check → commit (atomic pair persist) or, for auth-rejection failures only, generation++ and store clear; transport failures leave the session intact. Flight detached exactly once |
| Login/signup/Google adoption | generation++, in-flight flight's completion claimed (late results inert), waiters resolved `Superseded`, provider job cancelled, new session persisted |
| Logout/account deletion | Same supersession, store cleared |
| Slot release | Only when the flight's job has fully completed (`invokeOnCompletion`); new callers finding a dying flight wait on `slotCleared` then re-enter |

Consequences:

- **One rotating token, one wire submission**: a replacement flight cannot start
  until the superseded/abandoned flight's request has actually finished or been
  cancelled, and it re-reads the (possibly rotated) stored token.
- **Commit-on-race**: if the response has arrived when the last waiter cancels,
  the rotation still commits — the server already revoked the old refresh token,
  so discarding the response would strand the client. Tests pin this behavior.
- **Late results are inert**: generation/identity checks make a stale success
  unable to overwrite, and a stale failure unable to clear, a newer session.

## Session state

`AuthRepository.sessionState: StateFlow<SessionState>` (Unauthenticated /
Authenticated(user), never tokens). Restoration happens in the repository
constructor: only a consistent tokens+user pair restores; partial records are
cleared. The app navigation reads this via `AuthViewModel`.

## What is deliberately NOT here

- No Hilt/DI framework (D-0002 still stands), no UseCase layer, no RxJava.
- No Apple login on Android (no product contract requires it).
- No proactive token-expiry timers — refresh is 401-driven plus explicit calls.
- No Google Sign-In SDK dependency yet: `GoogleIdTokenProvider.Unavailable`
  stands in until OAuth client configuration exists (see AUTH_CONTRACT.md).
