---
name: db-performance
description: "Diagnostics of slow queries, locks, and DBMS execution plans"
target_agents:
  - debugger
  - developer-code
  - architect
---

# DB Performance — database performance diagnostics

The skill works at **two levels simultaneously**: the 1С platform (query, metadata, SCD) and the DBMS (plan, locks, waits, temp storage). A diagnosis without both levels is incomplete.

---

## When to use

| Symptom | First step |
|---------|-----------|
| Slow report / posting / exchange | Step 1: name the scenario |
| SQL in the tech log with a large `Duration` | Step 2: extract the query + metadata |
| Locks `TLOCK` / `TDEADLOCK` | Step 3: collect DBMS evidence |
| TEMPDB/WAL grows during a "read-only" scenario | Steps 3-4: red flags + causes |
| The table grew and the report became slower | Full algorithm (steps 1-5) |

This skill is the **lower evidence layer**. For rewriting the query text or SCD, pass it to `query-optimize`.

---

## Tools

| Task | Tool |
|--------|-----------|
| Find the query in code | `rg` (ripgrep) over BSL text |
| Navigate to a symbol / procedure | `code-navigation` |
| Get SQL from the tech log | `tech-log-analysis` → `search_tech_log` with `name: DBMSSQL` / `DBPOSTGRS` |
| Run a test / syntax check | `v8-runner` |
| Metadata information | `code-navigation` → register / catalog structure |

There is no direct access to `EXPLAIN ANALYZE`, `pg_stat_statements`, `sys.dm_exec_query_stats` - instructions for obtaining them are passed to the user/administrator.

---

## Algorithm (5 steps)

### Step 1. Name the scenario

Precisely determine: what the user does / which background process / which exchange step / which report with which filters. Without a concrete scenario, diagnosis is impossible.

Record: *name*, *expected time*, *actual time*, *conditions* (data volume, company, period).

### Step 2. Extract the query + metadata

**Platform layer:**
- Find the query text in BSL: `rg "Запрос.Текст\s*=" --type-add "bsl:*.bsl" -t bsl`
- For SCD - find the `.xml` data composition schema through `code-navigation`
- Check metadata: register type (accumulation / information), periodicity, resources, dimensions, tabular sections
- Record: call context, transaction boundary, loop around the query, virtual table parameters

**Requirement:** the 1C query text must **always be paired** with at least one DBMS artifact (step 3). Analysis of the query text alone, without DBMS evidence, does not provide an evidence base.

### Step 3. Collect DBMS evidence

Evidence categories (at least one is required):

| Category | PostgreSQL | MS SQL Server | What it proves |
|-----------|-----------|---------------|----------------|
| **Query plan** | `EXPLAIN (ANALYZE, BUFFERS)` | `SET STATISTICS IO, TIME ON` + actual plan | Seq scan vs index scan, hash join cost, actual rows |
| **Locks / waits** | `pg_locks`, `pg_stat_activity` | `sys.dm_exec_requests`, `sys.dm_os_waiting_tasks` | Lock holder, waiter, lock type |
| **Temp storage** | WAL size, `pg_stat_bgwriter` | TEMPDB usage, VLF count | Hidden writes during a "read-only" scenario |
| **Table statistics** | `pg_stat_user_tables` | `sys.dm_db_index_usage_stats` | Seq scans vs index seeks, stale stats |
| **Tech log artifacts** | `DBPOSTGRS` events | `DBMSSQL` events | Duration, SQL text, context |

File-based infobase is a separate model: there is no DBMS plan, and performance is determined by the structure of dbf files and platform locks.

**Missing evidence rule:** if a DBMS artifact cannot be obtained, record it explicitly: "DBMS evidence is absent, reason: <...>". Do not replace it with assumptions.

### Step 4. Separate the causes

Classify the cause by category:

| Category | Signs |
|-----------|---------|
| **Inefficient query** | Seq scan on a large table, no filter in a virtual table, dot dereference without `ВЫРАЗИТЬ` |
| **Missing / harmful index** | Full table scan on a field without an index; or an index exists but is not used because of the condition type |
| **Wide read of a virtual table** | `Остатки()` without period / dimension parameters |
| **Query-in-loop** | N queries for N rows: `Duration * N` in the tech log, repeated SQL with different parameters |
| **Lock contention** | `TLOCK` / `TDEADLOCK` in the tech log; blocking query in `pg_locks` / `sys.dm_exec_requests` |
| **DBMS maintenance** | Autovacuum, index rebuild, stale statistics - the plan degraded |
| **Data growth** | The query is correct, but the table volume grew - the plan changed |

One cause per iteration. If there are multiple causes, start with the most likely one based on evidence.

### Step 5. One measurable change + verification

- Propose **one** change: rewrite the query / add a virtual table parameter / add an index / move the query out of the loop
- For each change, the expected effect must be measurable: "Duration will drop from X to Y" / "Seq scan will be replaced by Index Seek"
- Verification: rerun the same scenario + compare DBMS evidence before and after
- Syntax check - through `v8-runner`

---

## Stop rules

1. **Do not recommend an index without** a specific predicate / JOIN / sort / grouping + an estimate of the write-cost tradeoff.
2. **Do not remove `РАЗРЕШЕННЫЕ`** and do not disable RLS/permission filters for performance without explicit security approval.
3. **Do not claim a DBMS-level cause without DBMS evidence.** Record missing evidence.
4. **Do not generalize** a PostgreSQL-specific conclusion to MS SQL Server and vice versa.
5. **Do not propose multiple changes at once** - it is impossible to measure each one's contribution.

---

## Evidence models by DBMS

### PostgreSQL
- Main tool: `EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)`
- Problem patterns: Seq Scan on a table > 100K rows, Hash Join with large `rows=`, high `shared hit` with low `actual rows`
- Temp files: `work_mem` overflow → temp file in the plan → WAL pressure
- Locks: `pg_stat_activity.wait_event_type = 'Lock'`

### MS SQL Server
- Main tool: Actual Execution Plan + `SET STATISTICS IO ON`
- Patterns: Table Scan / Clustered Index Scan instead of Seek, Key Lookup, implicit conversion due to types
- TEMPDB: spill to disk in Sort / Hash Match → `tempdb.sys.dm_db_task_space_usage`
- Locks: `sys.dm_exec_requests.blocking_session_id`

### File-based infobase
- No DBMS plan
- Performance depends on the size of dbf files and platform indexes
- Locks are a platform mechanism, visible in the tech log (`TLOCK`)
- Recommendation: migrate to the client-server variant as the volume grows

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
<Data volume / locks / DBMS-specific behavior>
```

---

## Red flags (immediate indicators)

- The report filters by date / company / tenant **after** joining large tables
- A virtual table is called without period / dimension parameters
- The same SQL in the tech log repeats N times with different parameters (query-in-loop)
- Long locks coincide with large writes: posting, exchange, background jobs
- Temp storage or transaction log grows in a "read-only" scenario

---
depends_on:
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/v8-runner/SKILL.md
  - framework/skills/bsl-practices/query-optimize/SKILL.md
---
