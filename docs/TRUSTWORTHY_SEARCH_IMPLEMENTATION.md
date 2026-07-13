# Trustworthy Search — Android Implementation

Backend contract: `GamePediaCoreServer` `8790a13` (see `API_CONTRACT_MATRIX.md`).

## Slice scope

Search input → suggestions (typeahead) → full results, with loading/empty/error
states, retry, clear, and local genre filtering. No pagination (contract has none),
no persistence, no auth. Everything else GamePedia is deferred (see `TASKS.md`).

## Flow

```
TextField ──onQueryChange──▶ SearchViewModel
   │   debounce 300 ms (one job: window + request; keystroke/clear cancels both)
   │       └──▶ repository.suggestions(q) ──▶ SuggestionsUiState
   │
   ├─IME Search / suggestion tap──▶ submitSearch(q)
   │       cancels previous search job, ++requestId
   │       └──▶ repository.search(q [, forceRefresh]) ──▶ ResultsUiState
   │
   ├─Retry──▶ submitSearch(acceptedQuery, forceRefresh = true)
   └─Clear──▶ cancel both jobs, ++requestId, reset state
```

## Concurrency invariants (unit-tested)

1. **Debounce is not emptiness** — during the 300 ms window the previous
   suggestions state is kept; nothing renders as "no results".
2. **Loading is not emptiness** — `ResultsUiState.Loading` is a distinct surface.
3. **Latest query wins** — a new submit cancels the in-flight job *and* a
   monotonically increasing request id drops any response that races past
   cancellation. An older response can never overwrite a newer query's state.
4. **Clear cancels everything** — both pipelines cancelled synchronously; the id
   bump invalidates already-in-flight responses.
5. **Local filters are free** — genre chips derive from loaded results and only
   change `selectedGenreId`; filtering happens in `SearchUiState.visibleGames`.
6. **Retry targets the accepted query** — the last *submitted* query, not the live
   field text, with `forceRefresh = true` to bypass the cache.
7. **Distinct state ownership** — `suggestions` and `results` are separate fields;
   a typeahead failure hides suggestions and never touches results.

## Query validation (client-side, per contract)

- Trimmed empty → no request.
- Trimmed length > 100 → inline `search_query_too_long` error; no request; submit
  blocked. Prevents `INVALID_SEARCH_QUERY` round-trips.
- Limits are constants (search 20/30 max, suggestions 6/8 max) → prevents
  `INVALID_GAMES_LIMIT` by construction.

## Cache

In-memory LRU (32 entries/category), key `trimmedLowercasedQuery|limit`, TTL 10
minutes, injected `TimeSource` for determinism. Suggestions and search cached
independently. Never persisted; retry refreshes. Raw queries exist only as map keys
and are never logged.

## UI / accessibility

- Material 3, light + dark, dynamic color on Android 12+; font-scale-friendly
  (no fixed text sizes; `sp` via typography).
- TalkBack: field content description, clear/retry ≥48 dp targets, suggestion rows
  ≥48 dp, headings marked, loading announced politely, errors assertively
  (liveRegion), rating read as "Rating N out of 100", covers described per game.
- Keyboard: single line, `ImeAction.Search` submits.
- Back: dismisses suggestions first (`BackHandler`), then default behavior.
- Stable LazyColumn keys: `game.id`; filter chips keyed by stable genre id.
- State restoration: query + accepted query in `SavedStateHandle`; the accepted
  query re-runs after process death.
- All copy in `res/values/strings.xml` (localization-ready; Korean translation is
  a follow-up).

## Privacy

See `API_CONTRACT_MATRIX.md` § Privacy. No OkHttp logging interceptor exists in the
dependency graph; `SearchDiagnostics` is the only logging surface and a regression
test asserts raw queries never appear in emitted lines.

## Known parity gaps / follow-ups

- iOS visual direction not replicated pixel-for-pixel (no reliable design evidence
  in this repo); functional + accessibility parity prioritized.
- Korean localization pending (backend messages are Korean; Android copy is English
  base with localizable keys).
- Suggestion loading indicator is text-only; design pass pending.
- Next slice recommendation: Game Detail (`GET /games/{id}`) behind a new contract gate.
