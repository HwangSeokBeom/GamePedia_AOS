# Auth Contract — GamePediaCoreServer

Source of truth: GamePediaCoreServer `dev` merge commit `28a113e` (auth
baseline; includes focused atomic refresh-token fix `7c9f88f`) — extracted from
`src/routes/auth.routes.js`, `src/validators/auth.validator.js`,
`src/services/auth.service.js`, `src/middlewares/auth.middleware.js`,
`src/modules/user/user.mapper.js`. Response DTO shapes and endpoints are
unchanged from the earlier `8790a13` extraction. **The cross-platform OpenAPI
does not yet cover auth**; when the backend publishes an auth section, re-gate
this document against it (tracked in RISK_REGISTER R-11).

## Endpoints used by Android

| Endpoint | Auth | Request body | Success |
|---|---|---|---|
| `POST /auth/signup` | none | `{email, password, nickname, profileImageUrl?, deviceName?}` | **201** `{user, tokens}` |
| `POST /auth/login` | none | `{email, password, deviceName?}` | 200 `{user, tokens}` |
| `POST /auth/google` | none | `{idToken, deviceName?}` | 200 `{user, tokens}` |
| `POST /auth/refresh` | none | `{refreshToken, deviceName?}` | 200 `{user, tokens}` |
| `POST /auth/logout` | none | `{refreshToken}` | 200 `{loggedOut:true}` |
| `GET /auth/me` | Bearer | — | 200 `{user}` |
| `DELETE /auth/me` | Bearer | — | 200 `{deleted:true, deletedAt}` |

Not implemented on Android in this slice: `POST /auth/forgot-password`,
`POST /auth/reset-password`, `POST /auth/apple` (Apple login is iOS-only by
product decision).

All bodies use the shared envelope (`{success:true,data}` /
`{success:false,error:{code,message,details?}}`). Branch on `error.code` only.

Validation constraints (zod): email ≤320 trimmed; password 8–72; nickname 2–30
trimmed; deviceName 1–100 optional. **Optional fields reject explicit `null`** —
the Android DTOs omit absent optionals (encodeDefaults=false), verified by test.

`user` shape (`mapUserToDto`): `id` (uuid string), `email`, `nickname`,
`profileImageUrl` (nullable, absolutized server-side), `status`
(`ACTIVE|INACTIVE|SUSPENDED`), `createdAt`/`updatedAt` (ISO-8601 strings).

## Refresh-token rotation contract (server behavior)

- Access and refresh tokens are JWTs; the refresh token is stored server-side as
  a SHA-256 hash with expiry and revocation state.
- `POST /auth/refresh` **atomically revokes the submitted token and issues a new
  pair** — implemented as a PostgreSQL compare-and-swap rotation (focused fix
  `7c9f88f`). A concurrent refresh request that loses the server rotation race
  receives **HTTP 401 with `error.code` `TOKEN_REVOKED`** (mapped to
  SessionExpired below; Android branches on the stable `error.code`, never on
  the localized `error.message`). Consequences for the client:
  - the pair must be persisted atomically (TokenStore.save contract);
  - a completed rotation must not be discarded (the old token is already dead);
  - the same rotating token must never be in flight twice — hence the
    single-flight coordinator.
- Client responsibility: single-flight, supersession, atomic persist, deciding
  session end only on definitive rejection. Server responsibility: rotation
  atomicity, revocation, hashing, account-status enforcement.

## Error codes (stable) and Android mapping

| HTTP | code | AuthFailure | Ends session on refresh? |
|---|---|---|---|
| 401 | `INVALID_CREDENTIALS` | InvalidCredentials | n/a (login only) |
| 409 | `EMAIL_ALREADY_IN_USE` | EmailAlreadyInUse | n/a |
| 409 | `NICKNAME_ALREADY_EXISTS` | NicknameAlreadyExists | n/a |
| 400 | `VALIDATION_ERROR` | ValidationFailed | no |
| 401 | `UNAUTHORIZED` / `TOKEN_EXPIRED` / `TOKEN_REVOKED` | SessionExpired | **yes** |
| 404 | `ACCOUNT_NOT_FOUND` | AccountUnavailable | **yes** |
| 403 | `ACCOUNT_INACTIVE` / `ACCOUNT_SUSPENDED` | AccountUnavailable | **yes** |
| 400 | `GOOGLE_ID_TOKEN_REQUIRED` | ValidationFailed | no |
| 400 | `GOOGLE_EMAIL_REQUIRED` | GoogleAccountRejected | no |
| 401 | `GOOGLE_EMAIL_NOT_VERIFIED` | GoogleAccountRejected | no |
| 409 | `SOCIAL_ACCOUNT_CONFLICT` / `GOOGLE_ACCOUNT_LINK_CONFLICT` | SocialConflict | no |
| 500 | `INTERNAL_SERVER_ERROR` | ServerError | no |
| — | timeout / IO / decode failure | Timeout / NetworkUnavailable / MalformedResponse | **never** |

Logout semantics: server marks the submitted refresh token revoked
(`updateMany`, idempotent, succeeds even for unknown tokens). Android clears the
local session first, then revokes best-effort. Account deletion: `DELETE
/auth/me` removes the user and all refresh tokens; Android invalidates the local
session after server success.

## Google configuration still required (outside source control)

- Google Cloud OAuth **Web client ID** matching the backend's ID-token audience
  (`google-auth.service.js` verification), plus an **Android client** registered
  with `applicationId` and signing SHA-1. No client secret belongs in the app.
- Add a Credential Manager implementation of `GoogleIdTokenProvider` once those
  exist. Until then `GoogleIdTokenProvider.Unavailable` surfaces "not available
  in this build".
- Blocked by the unresolved production applicationId / signing decision
  (RISK_REGISTER R-01/R-02).

## Intentionally unimplemented (this slice)

Password reset flows, Apple login, proactive refresh scheduling, multi-account,
push-token registration on login/logout parity with iOS, profile editing.
