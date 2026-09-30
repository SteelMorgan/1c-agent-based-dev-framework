---
name: onec-server-maintenance-hooks
description: "1C server maintenance webhooks: container restart and external component cache cleanup"
---

# 1C server maintenance webhooks

> Use this skill when you need to manage the stand's maintenance HTTP webhooks for 1C: restarting the 1C server container and clearing the cache of external components. This is a tool-usage skill for infrastructure operations, not a skill for infobase updates.

## When to use

| Situation | Action |
|----------|----------|
| Need to restart the 1C server after an update, cache cleanup, or stuck processes | `POST /restart/onec-server` |
| Need to clear the unpacked cache of a specific 1C external component | `POST /external-components/cache/clear` with `component` |
| Need to understand whether a restart will be required after clearing the EK cache | First call cache clear with `dry_run=true` and check `restart_required` |
| Need to change the webhook service itself | First do read-only diagnostics, then get explicit user confirmation |

## General safety rules

- Take the token from the project secret adopted on the stand; do not print the token in logs or in the response.
- Do not use direct `systemctl`, `docker restart`, `rm` over SSH if a controlled webhook exists.
- Perform destructive SSH actions on the 1C server only after explicit user confirmation.
- To clear the cache, pass the name or a narrow mask of the component, not a file path.
- Do not call clearing with a broad mask like `*`: the hook must work on a specific EK.

## Healthcheck

```bash
curl -sS http://onec-infra:8765/health
```

The expected response contains `status: "ok"` and a list of available endpoints.

## Restarting the 1C container

```bash
curl -sS -X POST \
  -H "Authorization: Bearer $(cat /workspaces/work/secrets/onec_restart_token)" \
  http://onec-infra:8765/restart/onec-server
```

Expect HTTP 200 and JSON:

```json
{"status": "restarted", "container": "onec-server"}
```

After restarting, check container readiness:

```bash
ssh -i "/workspaces/work/repos/1C Framework/1c-log-checker/.ssh/onec-infra/id_ed25519" \
  sandbox@192.168.250.2 \
  'sudo -n docker inspect -f "{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}" onec-server'
```

Ready state: `running healthy`.

## Clearing the external component cache

Always do `dry_run=true` first:

```bash
curl -sS -X POST \
  -H "Authorization: Bearer $(cat /workspaces/work/secrets/onec_restart_token)" \
  -H "Content-Type: application/json" \
  --data '{"container":"onec-server","component":"WebTransportAddIn","dry_run":true}' \
  http://onec-infra:8765/external-components/cache/clear
```

Response fields:

| Field | Meaning |
|------|-------|
| `matches` | Cache files found that will be deleted when `dry_run=false` |
| `deleted` | Files deleted during the real run |
| `loaded` | Component mappings in live processes via `/proc/*/maps` |
| `restart_required` | `true` if the component is already loaded by a process and deleting the file is not enough |

Actual cleanup:

```bash
curl -sS -X POST \
  -H "Authorization: Bearer $(cat /workspaces/work/secrets/onec_restart_token)" \
  -H "Content-Type: application/json" \
  --data '{"container":"onec-server","component":"WebTransportAddIn","dry_run":false}' \
  http://onec-infra:8765/external-components/cache/clear
```

## Whether 1C needs to be restarted after cleanup

- If `restart_required=false`, the cache file is not loaded into a live process; you can repeat the check or test without restart.
- If `restart_required=true`, the component is already loaded into a process (`addnhost`, `rphost`, etc.). Deleting the file from disk will not unload the old code from memory. You need to call `/restart/onec-server`, then wait for `running healthy`.

## Checking the result after cleanup

Repeat `dry_run=true`. For a successfully cleaned component, the expected result is:

```json
{
  "matches": [],
  "loaded": [],
  "restart_required": false
}
```

If after restart the component appears again as the old version, then 1C unpacked the old payload again from the template or extension. In this case, check the version and contents of the delivered VK in the configuration, rather than cleaning the cache again.

## Where the hook lives on the test environment

Usually on server `onec-infra`:

- service: `onec-restart.service`;
- script: `/opt/onec-restart/restart_svc.py`;
- access for diagnostics may be described in the project environment or in the log-checker documentation.

You can modify the remote hook only as an infrastructure change: first read-only diagnostics, then explicit user confirmation, after the change — syntax check, backup of the old file, and restart only the webhook service, not the 1C server.

---
depends_on: []
---
