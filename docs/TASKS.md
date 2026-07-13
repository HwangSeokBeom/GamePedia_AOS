# GamePedia Android — Task Tracker

## Current slice: Trustworthy Search

- [x] Phase 0 — Environment and safety inspection
- [x] Phase 1 — Git init, remote, `main`/`dev` branch policy
- [ ] Phase 2 — Backend contract extraction (commit `8790a13`)
- [ ] Phase 3 — Android project identity and scaffold
- [ ] Phase 4 — Module structure
- [ ] Phase 5 — Trustworthy Search implementation
- [ ] Testing (17 required areas + contract fixtures)
- [ ] Verification (`build`, `test`, `lint`, instrumentation if runtime available)
- [ ] Documentation set
- [ ] Push `dev`

## Deferred (documented, not implemented in this slice)

Authentication, Personalized Home, Game Detail, Steam linking, Library, Profile,
Privacy screens, Friends, Reviews, Comments, Discussions, Notifications, Push
tokens, Widgets, AI Search auth flow, Play Store release.

## Next recommended slice

Game Detail (`GET /games/{id}`) — natural continuation from Search results tap-through.
Requires a new backend contract gate before implementation.
