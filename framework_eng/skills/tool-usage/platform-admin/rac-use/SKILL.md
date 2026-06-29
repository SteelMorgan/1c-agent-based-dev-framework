---
name: rac-use
description: "RAC administration: sessions, locks, 1С infobases"
---

# RAC — cluster administration utility for 1С

## When to use

| Trigger | Action |
|---------|----------|
| Need to check/kill sessions in a database | `session list` / `session terminate` |
| Need to disconnect connections | `connection list` / `connection disconnect` |
| Need to block logins to a database | `infobase update --sessions-deny=on` |
| Need to forbid scheduled jobs | `infobase update --scheduled-jobs-deny=on` |
| Need to view locks | `lock list` |
| Need information about the cluster/databases | `cluster list` / `infobase summary list` |

---

## Connection

**Binary:** `/opt/1cv8/current/rac` (always the current version).

**Connection data:** `<project_root>/configs/yaxunit-runner.yml`, `app.connection` section — server, database, login, password.

**Cluster agent address:** default is `localhost:1545`. If the server differs, specify it explicitly as the last argument: `rac <command> <host>:<port>`.

---

## First step — get the cluster UUID

All commands require `--cluster=<uuid>`. Get it first:

```bash
/opt/1cv8/current/rac cluster list
```

The output contains `cluster : <uuid>` — remember it and use it later.

---

## Main scenarios

### View database sessions

```bash
# Find the database UUID
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> summary list

# List database sessions
/opt/1cv8/current/rac session --cluster=<cluster_uuid> list --infobase=<infobase_uuid>
```

### Force session termination

```bash
/opt/1cv8/current/rac session --cluster=<cluster_uuid> terminate \
  --session=<session_uuid> \
  --error-message="The session was terminated by the agent to perform the task"
```

### Block logins to the database

```bash
# Enable blocking
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> update \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --sessions-deny=on \
  --denied-message="The database is blocked for maintenance" \
  --permission-code="secret123"

# Remove blocking
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> update \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --sessions-deny=off
```

### Manage scheduled jobs

```bash
# Forbid
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> update \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --scheduled-jobs-deny=on

# Allow
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> update \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --scheduled-jobs-deny=off
```

### View locks

```bash
/opt/1cv8/current/rac lock --cluster=<cluster_uuid> list --infobase=<infobase_uuid>
```

### View and disconnect connections

```bash
# List database connections
/opt/1cv8/current/rac connection --cluster=<cluster_uuid> list --infobase=<infobase_uuid>

# Disconnect a specific connection
/opt/1cv8/current/rac connection --cluster=<cluster_uuid> disconnect \
  --process=<process_uuid> --connection=<connection_uuid>
```

---

## All RAC modes

| Mode | Purpose |
|-------|-----------|
| `cluster` | Clusters: list, create, delete, administrators |
| `infobase` | Infobases: create, update, delete, session/scheduled job blocking |
| `session` | Sessions: list, information, forced termination |
| `connection` | Connections: list, disconnect |
| `lock` | Locks: view |
| `process` | Worker processes |
| `server` | Worker servers |
| `manager` | Cluster managers |
| `agent` | Cluster agent |
| `service` | Manager services |
| `rule` | Assignment requirements |
| `profile` | Security profiles |
| `counter` | Resource consumption counters |
| `limit` | Resource consumption limits |

Help for any mode: `rac help <mode>`.

---

## Typical errors

| Error | Cause | Solution |
|--------|---------|---------|
| `Cluster agent unavailable` | `ragent` is not running or the address is incorrect | Check `localhost:1545` or specify the correct address |
| `Invalid cluster identifier` | The UUID was copied incorrectly | Run `cluster list` again |
| `Insufficient permissions` | Cluster administrator credentials are required | Add `--cluster-user` / `--cluster-pwd` |
| `Infobase not found` | Incorrect database UUID | Check via `infobase summary list` |

---
depends_on: []
requires:
  - tools
---
