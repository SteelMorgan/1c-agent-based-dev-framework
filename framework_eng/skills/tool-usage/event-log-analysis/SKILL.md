---
name: event-log-analysis
description: Analysis of the 1С Event Log. The skill trains the agent to search for errors, events, and user actions in the Event Log (ЖР) via ClickHouse.
---

# Event Log Analysis (Event Log)

## Purpose

The skill trains the agent to **search for and analyze events in the 1С Event Log** (ЖР). The Event Log records all significant platform events: errors, user actions, data changes, session start/stop.

**Principle:** The Event Log is a read-only source. Raw entries must not be included in output without masking personal data. Use `mode: "minimal"` by default.

For performance and technical event analysis (DB queries, locks, exceptions) use the separate skill [`tech-log-analysis`](../tech-log-analysis/SKILL.md).

---

## When to apply

| Trigger | Action |
|---------|--------|
| The user reports an error | `search_event_log` with `level: "Error"` and a narrow time window |
| Need to find an error for a specific period | `search_event_log` with `from`/`to` |
| Audit of user actions | `search_event_log` filtered by user |
| Verify changes to a specific object | `search_event_log` filtered by metadata |
| Tests are running — need to know whether the database is operational | `search_event_log` with `from: test_start_time`, `limit: 20` |

---

## Usage scenarios

### Scenario 1: Find errors for a period

**Steps:**

1. Get `cluster_guid` and `infobase_guid` from `cluster_map.yaml`.
2. Define the period: `from`/`to` in ISO 8601 format.
3. `search_event_log` with `level: "Error"`.
4. Analyze `records`: `event_time`, `event_presentation`, `comment`, `metadata_presentation`.
5. When a metadata object is mentioned — use `navigate_symbol` to open the code.

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

### Scenario 2: User action audit

**Steps:**

1. Retrieve GUIDs from `cluster_map.yaml`.
2. `search_event_log` with filters for the user and/or event (for example `_$Data$.Write`).
3. Analyze the sequence of events to identify suspicious or anomalous actions.

### Scenario 3: Assess database status after running tests

**Goal:** determine whether the database is working after tests start — are events flowing, are there errors.

**Steps:**

1. **Before running tests** — record the current time (`test_start_time` in ISO 8601).
2. After the run — `search_event_log` with `from: test_start_time`, `limit: 20`, no level filter.
3. Evaluate the output:
   - **No events at all** → the database did not start or the Event Log is unreachable.
   - **There are events, no `Error`** → the database is alive and tests are running.
   - **`Error` entries exist** → proceed to step 4.
4. If errors are present — rerun with `level: "Error"` for focused analysis of the root causes.

```
# Step 2: general picture (last 20 events since start)
search_event_log(
  cluster_guid: "...",
  infobase_guid: "...",
  from: "2025-03-01T10:00:00Z",   # test_start_time
  mode: "minimal",
  limit: 20
)

# Step 4: errors only (if needed)
search_event_log(
  cluster_guid: "...",
  infobase_guid: "...",
  from: "2025-03-01T10:00:00Z",
  level: "Error",
  mode: "minimal",
  limit: 20
)
```

**Interpretation of the last 20 records:**

| Picture | Conclusion |
|---------|------------|
| No records | Database did not start / Event Log unavailable |
| Events exist, `Error` absent | Database is alive, tests are running |
| Last event is a session termination without errors | Tests finished successfully |
| `Error` in `event_presentation` / `comment` | Tests failed — analyze `comment` and `metadata_presentation` |

### Scenario 4: Error "at 15:00 yesterday"

**Steps:**

1. Narrow the window to ±15 minutes around the reported time.
2. `search_event_log` with `level: "Error"` or without a level filter.
3. If there are no errors — expand the window or check the tech log (ТЖ) via `tech-log-analysis`.

---

## Security and personal data

| Rule | Description |
|------|-------------|
| **Masking** | The Event Log contains users’ full names, tax numbers, and contact details. Do not include raw entries in output without masking. |
| **mode: minimal** | `mode: "minimal"` by default — less data, less risk. |
| **Narrow window** | Shrink `from`/`to` to the shortest necessary period. |
| **limit** | Do not request more than 1000 records at once during initial exploration. |

---

## Capabilities

| Capability | Purpose |
|------------|---------|
| `search_event_log` | Search the Event Log (via ClickHouse) |
| `navigate_symbol` | Jump to code from metadata referenced in a log entry |

---

## Common mistakes

| Mistake | Workaround |
|---------|------------|
| GUIDs not from `cluster_map.yaml` | Read the config file; if absent, request it from the user |
| ClickHouse is unavailable | Record the cause; suggest an alternative via the tech log (ТЖ) |
| Range too wide — timeout | Narrow the window to 15–30 minutes; decrease `limit` |
| No errors in the Event Log | The Event Log only records platform events; technical issues (DB, locks) are in the tech log (ТЖ) |

---
depends_on: []
---
