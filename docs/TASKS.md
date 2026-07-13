# GamePedia Android — Task Tracker

## Current slice: Trustworthy Search — COMPLETE (pending instrumentation evidence, see VERIFICATION.md)

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
