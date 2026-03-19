---
name: rac-use
description: 1C cluster server administration via RAC utility — view/terminate sessions, manage locks, connections, infobases, and other cluster objects.
---

# RAC — 1C Cluster Administration Utility

## When to Use

| Trigger | Action |
|---------|--------|
| Need to check/kill sessions in a database | `session list` / `session terminate` |
| Need to disconnect connections | `connection list` / `connection disconnect` |
| Need to block database login | `infobase update --sessions-deny=on` |
| Need to disable scheduled jobs | `infobase update --scheduled-jobs-deny=on` |
| Need to view locks | `lock list` |
| Need cluster/database information | `cluster list` / `infobase summary list` |

---

## Connection

**Binary:** `/opt/1cv8/current/rac` (always the current version).

**Connection details:** `<project_root>/configs/yaxunit-runner.yml`, section `app.connection` — server, database, login, password.

**Cluster agent address:** defaults to `localhost:1545`. If the server differs — specify explicitly as the last argument: `rac <command> <host>:<port>`.

---

## First Step — Get the Cluster UUID

All commands require `--cluster=<uuid>`. Get it first:

```bash
/opt/1cv8/current/rac cluster list
```

The output contains `cluster : <uuid>` — save it and use in subsequent commands.

---

## Common Scenarios

### View Database Sessions

```bash
# Find the infobase UUID
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> summary list

# List sessions for the infobase
/opt/1cv8/current/rac session --cluster=<cluster_uuid> list --infobase=<infobase_uuid>
```

### Force-Terminate a Session

```bash
/opt/1cv8/current/rac session --cluster=<cluster_uuid> terminate \
  --session=<session_uuid> \
  --error-message="Session terminated by agent for task execution"
```

### Block Database Login

```bash
# Enable session lock
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> update \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --sessions-deny=on \
  --denied-message="Database locked for maintenance" \
  --permission-code="secret123"

# Disable session lock
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> update \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --sessions-deny=off
```

### Manage Scheduled Jobs

```bash
# Disable
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> update \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --scheduled-jobs-deny=on

# Enable
/opt/1cv8/current/rac infobase --cluster=<cluster_uuid> update \
  --infobase=<infobase_uuid> \
  --infobase-user=<user> --infobase-pwd=<pwd> \
  --scheduled-jobs-deny=off
```

### View Locks

```bash
/opt/1cv8/current/rac lock --cluster=<cluster_uuid> list --infobase=<infobase_uuid>
```

### View and Disconnect Connections

```bash
# List connections for the infobase
/opt/1cv8/current/rac connection --cluster=<cluster_uuid> list --infobase=<infobase_uuid>

# Disconnect a specific connection
/opt/1cv8/current/rac connection --cluster=<cluster_uuid> disconnect \
  --process=<process_uuid> --connection=<connection_uuid>
```

---

## All RAC Modes

| Mode | Purpose |
|------|---------|
| `cluster` | Clusters: list, create, delete, administrators |
| `infobase` | Infobases: create, update, delete, session/scheduled job locks |
| `session` | Sessions: list, info, force terminate |
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

## Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| `Cluster agent unavailable` | `ragent` not running or wrong address | Check `localhost:1545` or specify the correct address |
| `Invalid cluster identifier` | UUID copied incorrectly | Repeat `cluster list` |
| `Insufficient permissions` | Cluster admin credentials required | Add `--cluster-user` / `--cluster-pwd` |
| `Infobase not found` | Wrong infobase UUID | Check via `infobase summary list` |

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-run/SKILL.md
requires:
  - tools
---
