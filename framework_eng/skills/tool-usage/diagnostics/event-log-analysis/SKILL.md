---
name: event-log-analysis
description: "Analysis of the 1C registration log (Event Log). The skill teaches the agent to search for errors, events, and user actions in the registration log (RJ) via ClickHouse."
uses_capabilities:
  - search_event_log
  - logc_get_actual_log_timestamp
---

# Registration Log Analysis (Event Log)

The RJ is a read-only source. Do not include raw entries in the output without masking personal data. By default, `mode: "minimal"`.

For DBMS queries, locks, and platform exceptions, use `tech-log-analysis`.

---

## When to Use

| Trigger | Action |
|---------|----------|
| The user reports an error | First the latest `Error`, then a narrow time window |
| An error for a specific period | `search_event_log` with `from`/`to` |
| Auditing user actions | `search_event_log` with a user filter |
| Checking object changes | `search_event_log` with a metadata filter |
| Database state after tests | `search_event_log` with `from: test_start_time`, `limit: 20` |
| Vanessa run failed | First the latest `Error`, if empty — without a level filter |

---

## Search Algorithm

1. Get `cluster_guid` and `infobase_guid` from `cluster_map.yaml`.
2. Determine the strategy (see the cascade below).
3. Run `search_event_log` with the required filters.
4. Analyze the `records`: `event_time`, `event_presentation`, `comment`, `metadata_presentation`.
5. When a metadata object is mentioned, use `navigate_symbol` to jump to the code.

### Filtering Cascade

1. **First** — the latest records with `level: "Error"` (without `from`/`to` when the exact time is unknown).
2. **If empty** — repeat without the level filter.
3. **If the time is known** — narrow `from`/`to` to ±15 minutes.
4. **If the RJ gives no answer** — move to `tech-log-analysis`.

Timezone drift: local time and ClickHouse may differ; therefore, step 1 without a time filter is more reliable.

```
search_event_log(
  cluster_guid: "...",
  infobase_guid: "...",
  from: "2025-02-11T13:00:00Z",
  to:   "2025-02-11T14:00:00Z",
  level: "Error",
  mode: "minimal",
  limit: 100
)
```

### Interpreting Results (After Tests / Vanessa)

| Situation | Conclusion |
|-----------|-------|
| No records | The database did not start / the RJ is unavailable |
| There are events, but `Error` is absent | The database is alive and the tests are running |
| The last event is a session end without errors | The tests completed successfully |
| There is an `Error` in `event_presentation` / `comment` | Analyze `comment` and `metadata_presentation` |
| `Security warning` | Move to visual diagnostics via GUI/screenshot |

---

## Linking an RJ Entry with Code

After obtaining `event_presentation` and `metadata_presentation`:

1. If `metadata_presentation` contains the name of a metadata object, call `navigate_symbol` to jump to the code.
2. If `comment` mentions an error text or a procedure name, search the codebase for the comment text.
3. In the report, specify the **concrete module and procedure** (not only the metadata object name). Format: `ОбщийМодуль.ОбработкаОшибок → ЗафиксироватьОшибку()`.

---

## Correlation ID and Session

If the `comment` field contains a session identifier, an HTTP request identifier, or a correlation id:

1. Extract the value and preserve it in the response context.
2. Explicitly include in the output: `session=...`, `corrId=...` or `httpReq=...`.
3. If further investigation is needed, pass these identifiers to `tech-log-analysis` as filters (`search_tech_log` with `session`/`corrId`).

The goal is to preserve correlation keys for the next step without mixing RJ and Tech Log analysis in a single query.

---

## Security

- Mask full names, tax IDs, and contact details in the output.
- By default, `mode: "minimal"`.
- Narrow `from`/`to` to the minimum necessary period.
- Do not request more than 1000 records during the initial search.

---

## Capabilities

| Capability | Purpose |
|------------|------------|
| `search_event_log` | Search the registration log (via ClickHouse) |
| `navigate_symbol` | Jump to code by metadata from an RJ entry |

---

## Common Mistakes

| Error | Workaround |
|--------|---------------|
| GUIDs are not from `cluster_map.yaml` | Read the config; if it is missing, ask the user |
| ClickHouse is unavailable | Record the reason; suggest an alternative via the Tech Log |
| Time range is too wide — timeout | Narrow the window to 15–30 minutes; reduce `limit` |
| No errors in the RJ | Cascade: no level filter → narrow the window → Tech Log |

---
depends_on: []
---
