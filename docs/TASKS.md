# GamePedia Android — Task Tracker

## Current slice: Authentication / Session foundation (branch `feat/auth-session-foundation`)

- [x] Backend auth contract extracted (GamePediaCoreServer `dev` merge `28a113e`, incl. atomic rotation fix `7c9f88f`; no OpenAPI auth section yet)
- [x] core/model auth types + failure taxonomy
- [x] core/storage: TokenStore/UserSessionStore + Keystore AES-GCM impl + in-memory fakes
- [x] core/network: auth DTOs, AuthApi/AuthedUserApi, failure mapper, interceptor + authenticator
- [x] core/auth: single-flight refresh coordinator, session generation/supersession, session StateFlow
- [x] feature/auth: minimal login/signup/Google-boundary/logout screen
- [x] 17-item deterministic test matrix + stress runs (docs/AUTH_TEST_MATRIX.md)
- [x] Documentation (AUTH_ARCHITECTURE, AUTH_CONTRACT, AUTH_TEST_MATRIX)
- [ ] Google OAuth client provisioning + Credential Manager implementation (blocked: R-01/R-02/R-10)
- [ ] Runtime login smoke against reachable backend (blocked: R-12)

## Previous slice: Trustworthy Search — COMPLETE (see VERIFICATION.md)

- [x] Phase 0 — Environment and safety inspection
- [x] Phase 1 — Git init, remote, `main`/`dev` branch policy
- [x] Phase 2 — Backend contract extraction (commit `8790a13`)
- [x] Phase 3 — Android project identity and scaffold (temp app ID, SDK 36/26)
- [x] Phase 4 — Module structure (app, core/{common,model,network,designsystem}, feature/search)
- [x] Phase 5 — Trustworthy Search implementation (debounce, latest-wins,
      cancellation, retry, clear, filters, states, a11y, privacy-safe diagnostics)
- [x] Unit + contract + HTTP-shape tests (fixtures from backend contract tests)
- [x] Verification: `build`, `test`, `lint` (see `VERIFICATION.md` for exact results)
- [x] Documentation set
- [x] Push `dev`

## Follow-ups for this slice

- [ ] Korean (and further) localization of `feature/search` strings
- [ ] Design pass to align with GamePedia visual direction once design evidence exists
- [ ] Production application ID decision + Play Console + signing (RISK R-01/R-02)
- [ ] Introduce Hilt when the third feature lands (DECISIONS D-0002)

## Deferred (documented, not implemented in this slice)

Authentication, Personalized Home, Game Detail, Steam linking, Library, Profile,
Privacy screens, Friends, Reviews, Comments, Discussions, Notifications, Push
tokens, Widgets, AI Search auth flow, Play Store release.

## Next recommended slice

Game Detail (`GET /games/{id}`) — natural continuation from Search results
tap-through. Requires a new backend contract gate (matrix + fixtures + commit hash)
before implementation.
