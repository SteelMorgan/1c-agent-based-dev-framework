# Scenario Techniques for Tech Log Analysis

A reference guide to applied diagnostic techniques based on technology journal entries.
The main process for working with the tech log is in `SKILL.md`.

---

## 1. Classifying a Tech Log Entry by Incident Type

| Incident type | Minimum set of tech log events | What to look for first |
|---------------|--------------------------------|-------------------------|
| **startup** | `EXCP`, `CONN` | Platform version, client mode, infobase type, port, source-set path |
| **HTTP** | `EXCP`, `CALL`, `SCALL` | Request path (`HTTPPath`), handler, service metadata |
| **web-client** | `EXCP`, `CALL` | Client URL, session ID, module/form name |
| **background job** | `EXCP`, `TLOCK`, `CALL` | Job name, parameters, user context, retry count |
| **auth/session** | `CONN`, `EXCP` | User name, role, authentication flag, session termination |
| **DBMS** | `DBMSSQL` / `DBPOSTGRS`, `SDBL` | Query text, `elapsed`, `table`/`index`, transaction ID |
| **lock/deadlock** | `TLOCK`, `TDEADLOCK`, `TTIMEOUT` | Lock holder/waiter, lock object, wait time |
| **long call** | `CALL`, `SCALL` | `Duration`, method/procedure name, call stack, transaction context |

---

## 2. Required Identifiers

When analyzing any tech log entry, record and keep in the working context:

| Identifier | Tech log field | Why it is needed |
|------------|----------------|-------------------|
| `timestamp + TZ` | `DateTime` | Basis for the timeline; without TZ, file correlation is unreliable |
| `infobase` | `IB` | Separating infobases in a cluster |
| `user` | `Usr` | Audit, link to the Event Log |
| `session` | `Session` | Chain of events for one session |
| `connection` | `Con` | Binding to the client-server channel |
| `process id` | `process` (tech log file name) | Identifying the worker process |
| `transaction id` | `Trans` | Transaction boundaries, rollback |
| `background job id` | `JobID` | Repeated starts, retry |
| `HTTP path` | `HTTPPath` | Routing of the HTTP request |
| `correlation id` | `CorrID` / `ClientID` | Correlation with the Event Log and across processes |

If an identifier is missing in the entry, state this explicitly in the output; do not substitute a guess.

---

## 3. Timeline and Time Source

**Rule:**

- When analyzing multiple tech log files, fix the time source and time zone of each file at the beginning of the work.
- If time zones differ, normalize everything to UTC before building the timeline.
- The first causal record (`EXCP`, start of `TLOCK`, first `DBMSSQL` exceeding the threshold) takes priority over subsequent consequences: rollback, retry, `TTIMEOUT`, session termination.
- Repeated records of the same type after the first error are a consequence, not a cause.
- If there are no records before the failure moment, explicitly request the preceding interval instead of interpreting an incomplete window.

---

## 4. Scenario: startup failure

**Goal:** determine the cause before suggesting code changes.

Verification order:

1. Platform version (from the tech log file/process name or `EXCP.descr`).
2. Client mode: file-based / client-server / web server.
3. Infobase type: file-based / SQL server; for SQL, the DBMS name and version.
4. Startup mode: cluster (cluster manager) or standalone.
5. Network parameters: cluster agent port, web server, IP binding.
6. Credentials: OS / 1C authentication; permissions for the infobase folder or DB.
7. Source-set path (configuration from the repository or files) - presence, accessibility, correctness.

Suggesting a code change is allowed only after items 1-7 have been confirmed or ruled out.

---

## 5. Scenario: HTTP / web-client

**Goal:** connect the incoming request to a specific module/procedure BEFORE interpreting the error message.

Procedure:

1. Extract `HTTPPath` from the tech log event (HTTP) or the form/session identifier (web-client).
2. Find the handler: HTTP service or web handler by path -> metadata object.
3. Navigate to the handler code (`navigate_symbol`).
4. Only after the context is established, interpret the text of `EXCP.descr` or `CALL.descr`.
5. Record in the output: `HTTPPath` -> service name -> module -> procedure/function.

---

## 6. Scenario: background job

**Goal:** collect the full job context before analyzing the error.

Record the following:

| Attribute | Source |
|-----------|--------|
| Job name | `JobID`, description in metadata |
| Schedule (if scheduled) | Configuration metadata |
| Start parameters | `EXCP.descr` / `CALL.descr` |
| User context | `Usr` in the tech log entry |
| Retry counter | number of `JobID` entries with the same name |
| Lock state | presence of `TLOCK`/`TTIMEOUT` with the same `Session`/`Trans` |

If the job ended with an error after several retries, the first run contains the primary cause; the later ones are consequences or masking.

---

## 7. DBMS clues as a single block

When `DBMSSQL` / `DBPOSTGRS` / `TLOCK` / `TDEADLOCK` events are present, keep all clues together:

```
query_text:       <full SQL query text>
lock_holder:      session=..., trans=..., process=...
lock_waiter:      session=..., trans=..., process=...
wait_event:       <type of wait>
table_index:      <table> / <index>
trans_boundary:   begin=..., end=... (or "не завершена")
elapsed_ms:       <duration in ms>
process_session:  process=..., session=..., connection=...
```

Never split this block apart: losing any field can lead to an incorrect interpretation of the cause of a lock or a slow query.

If an `SDBL` record is present near `DBMSSQL`, map them: `SDBL` contains the original query in 1C query language, `DBMSSQL` contains its SQL translation.

---

## 8. Output Template

Use for any tech log analysis result:

```
### Timeline
<only key events in chronological order; time source and TZ>

### Primary-cause hypothesis
<specific tech log record -> cause statement>

### Affected metadata / modules
<metadata object -> module -> procedure/function (or "not established")>

### Verification step
<specific action that confirms or disproves the hypothesis>

### Missing clues
<explicit list: what data is missing and which interval/event needs to be requested>
```

The "Missing clues" section is mandatory. If the tech log fragment does not allow a reliable conclusion, state this explicitly and request the preceding interval instead of guessing.
