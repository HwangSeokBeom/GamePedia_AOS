# GamePedia Android (GamePedia_AOS)

Native Android client for GamePedia, built for cross-platform parity with iOS 2.0 and the GamePediaCoreServer backend.

## Status

Early development.

- **Trustworthy Search** — implemented (`GET /games/search`, `GET /games/suggestions`).
- **Authentication / session foundation** — implemented on `feat/auth-session-foundation`:
  email login, signup, Google-login boundary (SDK/OAuth config pending), logout,
  account-deletion boundary, Keystore-backed token storage, and a
  concurrency-safe single-flight refresh coordinator with session-generation
  supersession (parity with iOS 2.0's merged refresh-concurrency guarantees).
  See `docs/AUTH_ARCHITECTURE.md`, `docs/AUTH_CONTRACT.md`,
  `docs/AUTH_TEST_MATRIX.md`. Runtime login against a live backend and real
  Google Sign-In remain unverified (backend hosts unreachable from the dev
  network; OAuth client IDs not yet provisioned).

## Stack

- Kotlin, Jetpack Compose, Coroutines/Flow
- Gradle Kotlin DSL + Version Catalog
- Retrofit + OkHttp + kotlinx-serialization
- ViewModel + Navigation Compose
- JUnit + Compose UI testing

## Repository layout

```
app/                  Application shell, navigation
core/common/          Dispatchers, shared result types
core/model/           Domain models
core/network/         API contract DTOs, Retrofit services, error envelope
core/designsystem/    Theme, shared UI components
feature/search/       Trustworthy Search vertical slice
docs/                 Architecture, contracts, decisions, verification
```

## Branches

- `main` — protected stable baseline. No direct implementation work.
- `dev` — active development. Feature branches fork from `dev`.

## Backend contract

- Trustworthy Search: `GamePediaCoreServer` commit `8790a13`
  (`openapi/cross-platform.openapi.json`, `docs/TRUSTWORTHY_SEARCH_ANDROID_CONTRACT.md`).
- Authentication / Session: `GamePediaCoreServer` `dev` merge `28a113e`
  (includes atomic refresh-token rotation fix `7c9f88f`); see
  `docs/AUTH_CONTRACT.md`.

See `docs/API_CONTRACT_MATRIX.md`.

## Building

Requires JDK 17+, Android SDK with platform 36. No secrets, signing keys, or
`local.properties` are committed; Gradle resolves the SDK from your local setup.

```bash
./gradlew build
./gradlew test
./gradlew lint
```
