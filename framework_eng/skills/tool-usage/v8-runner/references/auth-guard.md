# Auth Guard — secure access to the infobase

Apply these rules **before** any `v8-runner` operation that accesses the infobase (`build`, `init`, `syntax`, `test`, `dump`, `launch`). This applies equally to file and server infobases.

## Hard stops for licensing

If the output of any command contains at least one of the patterns below, immediately stop all operations and ask the user to resolve the licensing issue in the environment. Do not try to fix it, work around it, or rerun it:

**Russian patterns:**
- `лиценз` (any word containing this root)
- `Не обнаружена лицензия`
- `Лицензия не найдена`
- `программная лицензия`

**Latin patterns:**
- `license` / `License` / `LICENSE`
- `HASP`
- `nethasp`
- `LM` (only in the context of platform licensing messages)
- `License not found`
- `No license`

**Rule:** on a match with any pattern, hard stop with a message to the user. Do not make repeated attempts, do not suggest alternative credentials, and do not change startup parameters.

## Two-candidate rule

If the user did not specify login/password explicitly (neither in command arguments, nor in `v8project.local.yaml`, nor in environment variables):

1. Try user **`Администратор`** with an empty password.
2. If that does not work (authorization error, not licensing), try **`Admin`** with an empty password.
3. If that does not work, **stop** and ask the user which account to connect with.

Do not try other names. Do not create users. Do not reset the password.

## Three-way error classification

After a connection attempt, classify the result:

| Class | Sign | Action |
|-------|------|--------|
| **license** | Any pattern from the "Hard stops" section | Hard stop, inform the user |
| **auth** | Authorization error (invalid user / password) | Next candidate or question to the user |
| **path** | Infobase unavailable, path is wrong, infobase not found | Stop, report the specific error, do not try to fix it |

Do not mix classes: a path error is not a credentials problem.

## Credentials handling

**Priority order (from highest to lowest):**

1. CLI parameters of the `v8-runner` command (`--user`, `--password`, or equivalents, if supported)
2. Environment variables (if supported by the current command)
3. The `connection` field in `v8project.local.yaml`

**Where to store credentials:**

- Write the login/password **only** to `v8project.local.yaml` — this file is local to the machine and does not go into the repository.
- **Never** write credentials to `v8project.yaml` — it is versioned in Git.
- If you still have to add them temporarily to `v8project.yaml`, warn the user explicitly and do not do it silently.

**Logs:**

- Never print the password in messages, logs, or the final response — not even partially.
- Mask the password when reproducing the command for the user: `--password ***`.

## Example structure of `v8project.local.yaml`

```yaml
connection:
  user: Администратор
  password: ""        # empty string — explicit empty password
```

For a server infobase, the same applies: credentials go in `v8project.local.yaml`, and the connection path goes in `v8project.yaml`.

## What NOT to do

- Do not pass `--config v8project.local.yaml` as an argument — the file is picked up automatically when it is present next to `v8project.yaml`.
- Do not use the `V8TR_CONFIG` variable — it is not part of our configuration model.
- Do not rerun a failed operation with the same credentials more than once without changing the candidate or getting an explicit user request.
