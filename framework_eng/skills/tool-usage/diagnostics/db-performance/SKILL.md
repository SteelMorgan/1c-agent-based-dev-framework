---
name: db-performance
description: "1С database and query performance diagnostics. Use when you need to diagnose a slow scenario, slow query, DBMS plan, locks, deadlock, TEMPDB/WAL, table sizes, or SCD on large data."
target_agents:
  - debugger
  - developer-code
  - architect
---

# DB Performance — database performance diagnostics

This skill works on **two levels at once**: the 1С platform (query, metadata, SCD) and the DBMS (plan, locks, waits, temp storage). A diagnosis without both levels is incomplete.

---

## When to use

| Symptom | First step |
|---------|-----------|
| Slow report / posting / exchange | Step 1: name the scenario |
| SQL in the tech log with a large `Duration` | Step 2: extract the query + metadata |
| Locks `TLOCK` / `TDEADLOCK` | Step 3: collect DBMS evidence |
| TEMPDB/WAL grows during a "read-only" scenario | Steps 3-4: red flags + causes |
| Table grew - the report became slower | Full algorithm (steps 1-5) |

This skill is the **lower evidence layer**. For rewriting query text or SCD, hand it off to `query-optimize`.

---

## Tools

| Task | Tool |
|--------|-----------|
| Search for a query in code | `rg` (ripgrep) over BSL text |
| Navigate to a symbol / procedure | `code-navigation` |
| Get SQL from the tech log | `tech-log-analysis` → `search_tech_log` with `name: DBMSSQL` / `DBPOSTGRS` |
| Test run / syntax check | `v8-runner` |
| Metadata information | `code-navigation` → register / catalog structure |

There is no direct access to `EXPLAIN ANALYZE`, `pg_stat_statements`, or `sys.dm_exec_query_stats` - instructions for obtaining them are passed to the user/administrator.

---

## Algorithm (5 steps)

### Step 1. Name the scenario

Precisely determine: what the user is doing / which background process / which exchange step / which report with which filters. Without a specific scenario, diagnosis is impossible.

Record: *name*, *expected time*, *actual time*, *conditions* (data volume, organization, period).

### Step 2. Extract the query + metadata

**Platform layer:**
- Find the query text in BSL: `rg "Запрос.Текст\s*=" --type-add "bsl:*.bsl" -t bsl`
- For SCD - find the `.xml` data composition schema via `code-navigation`
- Check metadata: register type (accumulation / information), periodicity, resources, dimensions, tabular sections
- Record: call context, transaction boundary, loop around the query, virtual table parameters

**Requirement:** the 1С query text must be paired with at least one DBMS artifact (step 3). Analysis of the query text alone without DBMS evidence does not provide a basis for proof.

### Step 3. Collect DBMS evidence

Evidence categories (at least one is required):

| Category | PostgreSQL | MS SQL Server | What it proves |
|-----------|-----------|---------------|----------------|
| **Query plan** | `EXPLAIN (ANALYZE, BUFFERS)` | `SET STATISTICS IO, TIME ON` + actual plan | Seq scan vs index scan, hash join cost, actual rows |
| **Locks / waits** | `pg_locks`, `pg_stat_activity` | `sys.dm_exec_requests`, `sys.dm_os_waiting_tasks` | Lock holder, waiter, lock type |
| **Temp storage** | WAL size, `pg_stat_bgwriter` | TEMPDB usage, VLF count | Hidden writes in a "read-only" scenario |
| **Table statistics** | `pg_stat_user_tables` | `sys.dm_db_index_usage_stats` | Seq scans vs index seeks, stale stats |
| **Tech log artifacts** | `DBPOSTGRS` events | `DBMSSQL` events | Duration, SQL text, context |

File-based infobase is a separate model: there is no DBMS plan, performance is determined by the structure of dbf files, locking is handled by the platform manager.

**Missing evidence rule:** if a DBMS artifact cannot be obtained, record it explicitly: "DBMS evidence is absent, reason: <...>". Do not replace it with assumptions.

### Step 4. Separate the causes

Classify the cause by category:

| Category | Signs |
|-----------|---------|
| **Inefficient query** | Seq scan on a large table, missing filter in a virtual table, dot dereference without `ВЫРАЗИТЬ` |
| **Missing / harmful index** | Full table scan on a field without an index; or the index exists, but is not used because of the condition type |
| **Broad virtual table read** | `Остатки()` without period / dimension parameters |
| **Query-in-loop** | N queries for N rows: `Duration * N` in the tech log, repeated SQL with different parameters |
| **Lock contention** | `TLOCK` / `TDEADLOCK` in the tech log; blocking query in `pg_locks` / `sys.dm_exec_requests` |
| **DBMS maintenance** | Autovacuum, index rebuild, statistics are stale - the plan has degraded |
| **Data growth** | The query is correct, but the table volume has grown - the plan changed |

One cause per iteration. If there are multiple causes, start with the most likely one according to the evidence.

### Step 5. One measurable change + verification

- Propose **one** change: rewrite the query / add a virtual table parameter / add an index / move the query out of the loop
- For each change, the expected effect must be measurable: "Duration will drop from X to Y" / "Seq scan will be replaced by Index Seek"
- Verification: rerun the same scenario + compare DBMS evidence before and after
- Syntax check - through `v8-runner`

---

## Stop rules

1. **Do not recommend an index without** a specific predicate / JOIN / sort / group + an estimate of the write-cost tradeoff.
2. **Do not remove `РАЗРЕШЕННЫЕ`** and do not disable RLS/rights filters for the sake of performance without explicit security approval.
3. **Do not assert the cause at the DBMS level without DBMS evidence.** Record missing evidence.
4. **Do not generalize** a PostgreSQL-specific conclusion to MS SQL Server and vice versa.
5. **Do not propose several changes at once** - it is impossible to measure each contribution.

---

## DBMS evidence models

### PostgreSQL
- Main tool: `EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)`
- Problem patterns: Seq Scan on a table with > 100K rows, Hash Join with large `rows=`, high `shared hit` with low `actual rows`
- Temp files: `work_mem` overflows -> temp file in the plan -> WAL pressure
- Locks: `pg_stat_activity.wait_event_type = 'Lock'`

### MS SQL Server
- Main tool: Actual Execution Plan + `SET STATISTICS IO ON`
- Patterns: Table Scan / Clustered Index Scan instead of Seek, Key Lookup, implicit conversion due to types
- TEMPDB: spill to disk in Sort / Hash Match -> `tempdb.sys.dm_db_task_space_usage`
- Locks: `sys.dm_exec_requests.blocking_session_id`

### File-based infobase
- No DBMS plan
- Performance depends on the size of dbf files and platform indexes
- Locks are a platform mechanism, visible in the tech log (`TLOCK`)
- Recommendation: migrate to the client-server variant as volume grows

---

## Output (response format)

```
## Scenario and evidence
<Scenario: ...>
<DBMS evidence: plan / tech log / absent (reason)>

## Root cause (in descending order of likelihood)
1. <Category> — <fact from evidence>
2. ...

## Change
<One specific change: text / index / parameter>
<Expected measurable effect>

## Verification
<How to measure: command / scenario / tech log comparison>

## Residual risks
<Data volume / locks / DBMS-specific details>
```

---

## Red flags (immediate markers)

- The report filters by date / organization / tenant **after** joining large tables
- A virtual table is called without period / dimension parameters
- The same SQL in the tech log is repeated N times with different parameters (query-in-loop)
- Long locks coincide with large write operations: posting, exchange, background jobs
- Temp storage or the transaction log grows in a "read-only" scenario

---
depends_on:
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
  - framework/skills/bsl-practices/query-optimize/SKILL.md
---
