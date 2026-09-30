# Initializing `v8project.yaml`

Use this reference when starting work with `v8-runner` in a 1С repository that does not yet have `v8project.yaml`. The goal is to ask the user **only the questions you cannot answer yourself by inspecting the repository**.

`v8-runner config init` already automatically detects:
- existing source sets (Designer and EDT) under the project root,
- the selected format when `--format=auto` (the default), based on what was found,
- the platform version, if it can be determined from the sources.

So in most projects the right command is simply:

```bash
v8-runner config init
```

After that, inspect the generated `v8project.yaml` and rerun with explicit flags only if auto-detection was wrong or the user has constraints that are not visible from the filesystem.

## Decision tree (do this BEFORE asking the user)

### 1. Check whether the project is already configured

If `v8project.yaml` exists — do **not** run `config init` without `--force`. Instead, inspect it and report what is configured. Using `--force` requires the user’s explicit consent because it will overwrite any local changes.

### 2. Determine the source format from the filesystem

Look at the project root and its immediate subdirectories.

| Filesystem signal | Likely format |
|---|---|
| `src/cf/Configuration.xml`, `src/cf/cfe/<ext>/Configuration.xml`, raw Designer XML tree | `DESIGNER` |
| `src/cf/.project`, `src/cf/Configuration/Configuration.mdo`, `*.mdo`, `DT-INF/` | `EDT` |
| Both kinds of trees side by side (mixed mono-repo) | `auto` (default) — let `config init` register both source sets |
| None of the above | the repository is not a 1С source tree — stop and ask the user what they expect to find there |

If the format is clearly one of the two, you may pass `--format=designer` or `--format=edt` for clarity, but you do not have to — `auto` will choose the same result.

### 3. Decide which builder backend to use

The default is `DESIGNER`. Switch to `IBCMD` only when:

- the project is `EDT`, and the command runs on a platform version where `ibcmd` is supported and faster (≥ 8.3.20); **and**
- there are no Designer-only features on the critical path (some legacy tasks still require the Configurator GUI).

Ask the user only if the choice is ambiguous and you cannot determine it from `tools.platform.version`. When in doubt, use `DESIGNER` — it is the safer baseline.

### 4. Decide which infobase connection to use

`--connection` is the connection string written to `tools.connection`. Three typical forms:

| Form | Example | When to use |
|---|---|---|
| File infobase managed by `v8-runner` | `--connection "File=build/ib"` | most local development cycles. The path is created on the first `v8-runner init`. Safe default. |
| File infobase that already exists on disk | `--connection "File=/abs/path/to/ib"` | the user has an existing baseline they want to point to |
| Server infobase | `--connection "Srvr=cluster:1541;Ref=ibname"` | central dev database, shared infobase, CI runner attached to a cluster |
|

For server connections there is no automatic creation (it requires DBA-level operations). The user must confirm that the database exists and that credentials are stored elsewhere (`v8project.local.yaml` keeps secrets out of git).

If the project README, `docker-compose.yml`, or `.env` already describes a connection, **use it** without asking again. If nothing is documented anywhere, ask the user **once** with the three options above and a default of `File=build/ib`.

### 5. Decide where to write the output

The default is `./v8project.yaml`. Override via `--output` only when the project is a subtree inside a larger repository and the user explicitly wants the config not at the root. Do not invent values for `--output`.

## When to ask the user (and how)

Ask only if at least one of these conditions is met:

1. **The repository looks ambiguous or empty** — no Designer/EDT signals and no other clues. Ask: «I do not see a Designer or EDT source tree under `<root>`. Where do the 1С sources live, or should I create a fresh empty config?»
2. **There is a hint of a server infobase, but no connection string is documented.** Ask: «This looks like a server project. What are the cluster:port and the infobase name? Or should I use a local `File=build/ib` by default?»
3. **Mixed Designer + EDT sources are detected** and the user clearly works with only one of them. Ask: «I detected both trees — Designer and EDT. Should I register both as separate source sets, or only one? Which one is primary?»
4. **`tools.platform.version` cannot be determined**, and the user has multiple versions installed. Ask once.

Phrase the questions in one pass, provide a default, and continue if the user wants to keep the defaults.

## Example interactions

### Typical project with a File infobase and Designer sources

```bash
v8-runner config init --connection "File=build/ib"
v8-runner init       # creates the file infobase
v8-runner build      # applies sources
```

No questions for the user.

### EDT project on platform 8.3.20+

```bash
v8-runner config init --format edt --builder IBCMD
v8-runner init
v8-runner build
```

No questions if the platform version is detected.

### Mixed Designer + EDT mono-repo

```bash
v8-runner config init       # registers both source sets via auto
```

Ask only if the user wants only one of them to be active.

### Server project

```bash
# After confirmation from the user:
v8-runner config init --connection "Srvr=10.0.0.10:1541;Ref=dssl_drive_ai"
# v8-runner init is NOT run — the database is managed externally
v8-runner build       # applies local sources to the existing infobase
```

Ask once about `Srvr=...;Ref=...` and the credentials policy (where they are stored). Server configuration usually lives outside git, through `v8project.local.yaml`.

## After `config init`

Always inspect the generated `v8project.yaml` before running mutating commands. Show the user:

- the selected format and builder (`format`, `builder`),
- the detected source sets (`source-sets[*].name`, `path`),
- the connection string,
- whether `tools.platform.version` is filled in,
- any warnings output by `config init` (often hints about missing tools or unusual layouts).

If something looks wrong, use explicit `--format`, `--builder`, or `--connection` instead of editing the YAML by hand — that keeps the workflow reproducible.
