# API Contract Matrix

## Slices

| Slice | Contract source | Matrix |
|---|---|---|
| Trustworthy Search | `openapi/cross-platform.openapi.json` @ `8790a13` | below |
| Authentication / Session | backend source @ `dev` merge `28a113e` (atomic rotation fix `7c9f88f`; no OpenAPI auth section yet — R-11) | `docs/AUTH_CONTRACT.md` |

# Trustworthy Search

**Backend source of truth:** `GamePediaCoreServer` commit `8790a13`
(`feat(search): trustworthy search contract slice — OpenAPI 3.1, HTTP contract
tests, log redaction`), files `openapi/cross-platform.openapi.json` and
`docs/TRUSTWORTHY_SEARCH_ANDROID_CONTRACT.md`.

> Note: the task brief referenced `GamePediaCoreServer-dev/`; locally the repository
> is checked out at `GamePediaCoreServer/` on branch `dev` with `8790a13` as HEAD.
> Content verified identical to the referenced commit.

## Endpoints

| Endpoint | Auth | Limits | Android binding |
|---|---|---|---|
| `GET /games/search?q&limit` | Public — no Authorization header sent | `q` 1–100 chars trimmed; `limit` 1–30, default 20 | `SearchApi.searchGames` → `SuccessEnvelopeDto<GameSearchDataDto>` |
| `GET /games/suggestions?q&limit` | Public — no Authorization header sent | `q` 1–100 chars trimmed; `limit` 1–8, default 6 | `SearchApi.getSuggestions` → `SuccessEnvelopeDto<GameSuggestionsDataDto>` |

Pagination: **none**. Limit-only, no cursor/offset/total. The client treats every
list as complete and requests at most `limit=30`. `meta.resultCount` equals the
returned list length.

Out-of-range limits are a 400, not a clamp → limits are compile-time constants in
`SearchApi.Companion`, always within range.

## DTO mapping

| Backend field | Backend type | Android DTO | Android domain |
|---|---|---|---|
| `data.query` | string | `GameSearchDataDto.query` | `SearchResults.query` |
| `data.games[]` | GameListItem[] | `games: List<GameListItemDto>` | **canonical binding** → `SearchResults.games` |
| `data.results[]` | GameListItem[] | decoded for strictness | **ignored** (iOS 2.0 alias) |
| `data.suggestions[]` | GameSuggestionItem[] | `List<GameSuggestionItemDto>` | `SearchResults.suggestions` |
| `data.meta` | SearchMeta | `SearchMetaDto` | `SearchMeta` |
| `GameListItem.id` | int | `Int` | `Game.id` |
| `.name/.summary/.coverUrl` | string\|null | `String?` | same (no defaults masking nulls) |
| `.genres/.platforms` | string[] | `List<String>` | same (empty allowed) |
| `.rating/.aggregatedRating/.totalRating` | number\|null | `Double?` | same |
| `.releaseDate` | int(unix s)\|null | `Long?` | `Game.releaseDateEpochSeconds` |
| `GameSuggestionItem` | {id,name,coverUrl,rating} | `GameSuggestionItemDto` | `GameSuggestion` |
| `SearchMeta.*` | strings + int | `SearchMetaDto` | `SearchMeta` |

## Error mapping

Envelope: `{"success":false,"error":{"code","message","details"?}}` — branch on
`code` only; `message` is sanitized/localized and unstable.

| HTTP | Backend code | `SearchFailure` | Retryable | User copy key |
|---|---|---|---|---|
| 400 | `INVALID_SEARCH_QUERY` | `InvalidQuery` | no | `search_error_invalid_query` (normally prevented client-side) |
| 400 | `VALIDATION_ERROR` | `InvalidQuery` | no | `search_error_invalid_query` |
| 400 | `INVALID_GAMES_LIMIT` | `InvalidLimit` | no | `search_error_unexpected` (client bug; code kept for QA) |
| 429 | `IGDB_RATE_LIMITED` | `RateLimited` | manual only | `search_error_rate_limited` |
| 502 | `IGDB_UPSTREAM_ERROR` | `ProviderUnavailable` | yes | `search_error_provider_unavailable` |
| 502 | `TWITCH_AUTH_UNAVAILABLE` | `ProviderUnavailable` | yes | `search_error_provider_unavailable` |
| 500 | `IGDB_NOT_CONFIGURED` | `ServerError` | no | `search_error_server` |
| 500 | `INTERNAL_SERVER_ERROR` | `ServerError` | no | `search_error_server` |
| — | (timeout) | `Timeout` | yes | `search_error_timeout` |
| — | (I/O failure) | `NetworkUnavailable` | yes | `search_error_offline` |
| — | (decode failure) | `MalformedResponse` | yes | `search_error_malformed` |
| other | unknown code | `Unexpected(code)` | no | `search_error_unexpected` |

Unparseable error bodies fall back to HTTP-status semantics (429→RateLimited,
502→ProviderUnavailable, 500→ServerError). Empty `games`/`suggestions` with 200 is
the Empty state, never an error. 401 is not possible on these endpoints.

## Caching (per backend contract)

Server caches ~10 min per normalized candidate set; responses carry no cache
headers. Client: in-memory LRU only (32 entries), key `trimmedLowercasedQuery|limit`,
TTL 10 min, never persisted; retry passes `forceRefresh`. No offline search.

## Privacy (parity with backend log redaction)

Never logged: raw query, URLs containing `q`, auth material, emails, Steam IDs,
provider payloads, credentials. Allowed: query length, result count, request
category, latency, cache hit/miss, stable error code. Enforced by a regression test
(`DefaultSearchRepositoryTest.diagnostics never contain the raw query…`).

## Contract fixtures

`core/network/src/test/resources/fixtures/*.json` are derived from
`test/games-search-contract.test.js` shapes/values at `8790a13` (game 1001
"Contract Quest", rating 88.5, releaseDate 1700000000, Korean sanitized error
messages, `details: [{field, message}]`).

## Re-gating rule

Any backend change to these endpoints requires updating this matrix, the fixtures,
and the recorded commit hash before Android code changes.
