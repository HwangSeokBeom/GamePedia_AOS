# GamePedia Android (GamePedia_AOS)

Native Android client for GamePedia, built for cross-platform parity with iOS 2.0 and the GamePediaCoreServer backend.

## Status

Early development. The first vertical slice is **Trustworthy Search** (`GET /games/search`, `GET /games/suggestions`).

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

Source of truth: `GamePediaCoreServer` commit `8790a13`
(`openapi/cross-platform.openapi.json`, `docs/TRUSTWORTHY_SEARCH_ANDROID_CONTRACT.md`).
See `docs/API_CONTRACT_MATRIX.md`.

## Building

Requires JDK 17+, Android SDK with platform 36. No secrets, signing keys, or
`local.properties` are committed; Gradle resolves the SDK from your local setup.

```bash
./gradlew build
./gradlew test
./gradlew lint
```
