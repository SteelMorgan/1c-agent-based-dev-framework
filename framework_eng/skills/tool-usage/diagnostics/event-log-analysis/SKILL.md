---
name: event-log-analysis
description: Analysis of the 1C Event Log. The skill teaches the agent to look for errors, events, and user actions in the Event Log via ClickHouse.
---

# Event log analysis (Event Log)

The Event Log is a read-only source. Do not include raw entries in the output without masking personal data. The default mode is `mode: "minimal"`.

For database queries, locks, and platform exceptions — use `tech-log-analysis`.

---

## When to apply

| Trigger | Action |
|---------|--------|
| User reports an error | Start with the latest `Error` entries, then narrow the time window |
| Error within a specific period | `search_event_log` with `from`/`to` |
| Audit of user actions | `search_event_log` with a filter by user |
| Reviewing changes to an object | `search_event_log` with a filter by metadata |
| Assessing the database state after tests | `search_event_log` with `from: test_start_time`, `limit: 20` |
| Vanessa run failed | Start with the latest `Error` entries; if none, repeat without a level filter |

---

## Search algorithm

1. Retrieve `cluster_guid` and `infobase_guid` from `cluster_map.yaml`.
2. Determine the strategy (see the cascade below).
3. Run `search_event_log` with the required filters.
4. Analyze the `records`: `event_time`, `event_presentation`, `comment`, `metadata_presentation`.
5. When a metadata object is mentioned, use `navigate_symbol` to jump to the code.

### Filtering cascade

1. **First** — the latest entries with `level: "Error"` (no `from`/`to` when the exact time is unknown).
2. **If empty** — repeat without the level filter.
3. **If the time is known** — narrow `from`/`to` to ±15 minutes.
4. **If the Event Log does not yield results** — move to `tech-log-analysis`.

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

### Interpreting results (after tests / Vanessa)

| Situation | Takeaway |
|-----------|----------|
| No records | The database did not start or the Event Log is unavailable |
| There are events but no `Error` | The database is alive and the tests are still running |
| The last entry is a session end without errors | The tests completed successfully |
| There is an `Error` in `event_presentation` or `comment` | Analyze the `comment` and `metadata_presentation` |
| `Security warning` | Switch to visual diagnostics via GUI/screenshot |

---

## Safety

- Mask personal names, INNs, and contact details in the output.
- Keep `mode: "minimal"` by default.
- Narrow `from`/`to` to the smallest necessary period.
- Do not request more than 1000 entries during the initial search.

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `search_event_log` | Search the Event Log (via ClickHouse) |
| `navigate_symbol` | Jump to metadata code referenced in an Event Log entry |

---

## Common mistakes

| Mistake | Workaround |
|---------|------------|
| GUIDs not taken from `cluster_map.yaml` | Read the config; if it is missing, ask the user |
| ClickHouse is unavailable | Record the reason and suggest an alternative via the Tech Log |
| Time window too wide — timeout | Narrow the window to 15–30 minutes; reduce the `limit` |
| No errors in the Event Log | Cascade: remove the level filter → narrow the window → switch to the Tech Log |

---
depends_on: []
---
