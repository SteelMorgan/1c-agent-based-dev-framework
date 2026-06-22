---
name: no-direct-db-access
description: Global prohibition on direct access to the DBMS. Agents work with data only through the 1C:Enterprise platform. Direct queries to the DBMS are allowed only for performance analysis and only in read-only mode.
alwaysApply: true
---

# Ban on Direct DBMS Access

A global rule for all agents and subagents.

## Context

The 1C:Enterprise platform is the sole legitimate data access layer. Direct access to the DBMS (PostgreSQL, MS SQL, etc.) bypasses business logic, compromises data integrity, and creates security risks.

## Rules

### PROHIBITED (without exceptions)

- Modifying data in the DBMS directly: `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE`, DDL operations
- Generating or suggesting SQL scripts to alter data
- Bypassing platform mechanisms (RLS, locks, subscriptions) through direct access

### PROHIBITED (without user approval)

- Reading data from the DBMS directly (`SELECT`) — only via 1С platform queries
- Connecting to the DBMS by any means (psql, sqlcmd, ODBC, etc.)

### ALLOWED (performance tasks only)

With **explicit** approval from the user **or** if the task is classified as a performance task:

- `SELECT` queries to the DBMS for execution plan analysis (`EXPLAIN ANALYZE`)
- Reading DBMS system views (`pg_stat_*`, `sys.dm_exec_*`)
- Analyzing locks, statistics, indexes via system views

Even in these cases — **read-only**, **no changes**.

## Behavior on Violation

1. DO NOT execute a query against the DBMS
2. Record the attempt in `{role}-context.md`
3. Propose a platform alternative (1С query, БСП mechanism, etc.)
4. If a platform alternative is impossible — `clarification_needed` → user

---
depends_on: []
---
