# Architecture & Project Decisions

Format: newest first. Each decision records context, choice, and consequences.

---

## D-0005 — API base URLs per environment (2026-07-13)

**Context:** The backend OpenAPI intentionally publishes no real hosts; the iOS client
(`GamePedia/Application/APIEnvironment.swift`) defines local `http://127.0.0.1:3001`,
staging `https://staging-gamepedia-api.duckdns.org`, production
`https://gamepedia-api.duckdns.org`.

**Decision:** Base URL is injected per build type via `BuildConfig` and can be
overridden with the Gradle property `gamepedia.apiBaseUrl`. Debug defaults to the
staging host; release defaults to the production host. These hosts are already
compiled into the shipped iOS client and are not secrets. No other credentials are
committed.

**Consequences:** Local backend testing uses
`./gradlew ... -Pgamepedia.apiBaseUrl=http://10.0.2.2:3001` (emulator loopback).

## D-0004 — SDK levels (2026-07-13)

**Context:** Locally installed: platform `android-36.1`, build-tools 36.0.0/36.1.0/37.0.0.
SDK versions must come from the actual toolchain, not memory.

**Decision:** `compileSdk = 36`, `targetSdk = 36` (latest stable installed).
`minSdk = 26` (Android 8.0): covers the overwhelming majority of active devices,
gives `java.time` without desugaring, and matches Compose support comfortably.

**Consequences:** Revisit `minSdk` with real user data before release.

## D-0003 — Temporary application ID (2026-07-13)

**Context:** No approved Android application ID exists. GamePediaDocs
`environment-overview.md` lists Android identity as `[Placeholder]`. iOS uses
`com.hwb.GamePedia.*`.

**Decision:** Use explicitly temporary `com.hwb.gamepedia.android.dev` as
`applicationId`, kept configurable in `app/build.gradle.kts`. The Kotlin namespace
`com.hwb.gamepedia` is independent of the store-facing application ID.

**Consequences (before any public release):**
- A production application ID must be approved by the project owner.
- Play Console ownership and app signing (Play App Signing recommended) must be
  established under that final ID — an application ID cannot change after the first
  Play Store upload.
- Migration is a rename in one Gradle field while the temporary ID has never been
  published; after publishing it would require a new store listing.

## D-0002 — Multi-module feature-oriented structure (2026-07-13)

**Context:** First slice must stay small but future slices (Home, Detail, Library)
are known, and network contract ownership matters for parity work.

**Decision:** `app` + `core/common` + `core/model` + `core/network` +
`core/designsystem` + `feature/search`. No DI framework yet (manual wiring in a
small AppContainer); no Room; no paging. Interfaces only where a test seam is needed
(`SearchApi` over Retrofit).

**Consequences:** Adding a feature = one new module depending on `core/*`.
DI framework (Hilt) is deferred until a second feature makes wiring painful.

## D-0001 — Repository and branch policy (2026-07-13)

**Context:** Fresh empty repository `HwangSeokBeom/GamePedia_AOS`; remote verified
empty via `git ls-remote` before first push.

**Decision:** `main` is the protected stable baseline; all implementation happens on
`dev` (or feature branches from `dev`). No force pushes, no history rewrites, no
direct commits to `main`. The initial `main` commit contains only the repository
baseline (README, docs, .gitignore) — no Android code.

**Consequences:** `dev` → `main` merges happen only at verified milestones and were
explicitly out of scope for the initial slice.
