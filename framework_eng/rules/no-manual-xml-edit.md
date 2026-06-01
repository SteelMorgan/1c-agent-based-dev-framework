---
name: no-manual-xml-edit
description: Global ban on manual editing of 1C XML/MXL metadata. All operations go through the xmlgen CLI. For Claude Code, it is blocked automatically by the PreToolUse hook; for Codex and any other agent without PreToolUse, mandatory self-check via `block-direct-xml-edit.py --check` before every Edit/Write. Manual editing is allowed only if xmlgen explicitly does not support the operation, with mandatory logging.
alwaysApply: true
---

# Ban on Manual Editing of 1C XML and MXL

Global rule for all agents and subagents - regardless of IDE
(Claude Code, Codex, Cursor, Aider, Cline, Windsurf, and any others).

## TL;DR for the agent

1. **Do not edit directly** files of the following kinds:
   - `*.mxl` (any),
   - `*.xml` inside `**/Ext/`, `Configuration.xml`, or 1C root folders
     (`Catalogs/`, `Documents/`, `*Registers/`, `Roles/`, `Subsystems/`,
     `CommonModules/`, `ChartsOf*`, `Reports/`, `DataProcessors/`, `Enums/`,
     `Constants/`, `ExchangePlans/`, `Tasks/`, `BusinessProcesses/`,
     `HTTPServices/`, `WebServices/`, `EventSubscriptions/`, `ScheduledJobs/`,
     `DefinedTypes/`, `DocumentJournals/`, etc.).
2. Instead use `xml-gen <domain> <op>` — see skill
   `framework/skills/tool-usage/platform-data/xml-generation/SKILL.md`.
3. **If you are NOT in Claude Code** (that is, the PreToolUse hook does not protect you) -
   **you are required** before every `Edit`/`Write`/`apply_patch`/`sed` on a path with
   the `.xml` or `.mxl` extension to first call:
   ```bash
   python3 tools/hooks/block-direct-xml-edit.py --check "<path>" --tool Edit
   ```
   If exit code = 2 - stop, read stderr (there is a hint there for the
   required xml-gen command) and switch to xml-gen. If exit code = 0 -
   the path does not relate to 1C metadata, editing is allowed.

## Context

1C:Enterprise metadata stored in XML (`Form.xml`, `Rights.xml`, `Configuration.xml`, `*.xml` of catalogs / documents / registers / subscriptions / roles / plans / reports / processors / common modules and any other configuration objects) has a strict schema + non-obvious runtime dependencies. Direct editing through the Edit/Write tool regularly leads to non-canonical schema, which:

- passes `build_project` and LSP diagnostics,
- but breaks runtime UI / object behavior.

Precedent: OC-22444 F-01 — manual generation of `<ValueType><Type>...</Type></ValueType>` instead of canonical `<Type><v8:Type>...</v8:Type></Type>` + missing UI `<TableColumn>` elements. 6 iterations of Developer-Code were unable to stabilize the form.

The `xmlgen` tool (Java CLI) and its skill wrappers cover all typical operations:
- creation / editing of forms (attributes, UI elements, commands, events),
- access rights (Rights.xml),
- EPF, SKD, templates,
- byte-by-byte text replacement (`edit replace-text`) while preserving BOM/CRLF/LF,
- schema + structural + semantic validation rules.

## Rules

### PROHIBITED

- Using Edit / Write / sed / awk / any text tool for direct modification of 1C XML metadata.
- Creating 1C XML through template strings in Python/Bash/other scripts instead of `xmlgen`.
- Bypassing schema checks by copying XML blocks from other forms without running them through `xmlgen validate`.

### REQUIRED

- Any change to 1C metadata XML — through the `xmlgen` CLI and its skill wrappers:
  - `/form-edit`, `/form-info`, `/form-validate` — managed forms,
  - `xml-gen form add-attribute / add-element / add-command / remove-element / move-element`,
  - `xml-gen role add-object / add-right`,
  - `xml-gen epf add-attribute / add-tabular-section`,
  - `xml-gen skd add-parameter / add-field`,
  - `xml-gen config / subsystem / interface / meta / extension validate`,
  - `xml-gen edit replace-text` — for safe replacement of text blocks while preserving byte structure.
- After any 1C XML modification — `xml-gen validate` (of the corresponding type), exit code 0 or 2 (warnings).
- Before modification — `xml-gen validate` to capture the state (catches previous errors not related to the current edit).

### REQUIRED for agents without a PreToolUse hook (Codex, Cursor, Aider, Cline, etc.)

If you are not in Claude Code - there is no automatic blocking, so **self-check is mandatory**.
Before every call to `Edit` / `Write` / `apply_patch` / `sed` / `awk` / any
text tool on a path with the `.xml` or `.mxl` extension:

1. Run the guard manually:
   ```bash
   python3 tools/hooks/block-direct-xml-edit.py --check "<path>" --tool Edit
   ```
2. If exit code = `2` - the path belongs to 1C metadata, **do not perform the edit**.
   Read stderr: it contains a hint about which `xml-gen` command to use for this
   file type. Switch to it.
3. If exit code = `0` - the path is not 1C metadata (for example, `pom.xml`, a test
   fixture in `/tests/`, documentation). You can edit it as usual.

The script is idempotent, has no side effects - this is a pure path detector. Run
it as many times as you want without concern.

Alternative pattern for shell scripts (batch processing of multiple files):
```bash
for f in $(git diff --name-only); do
  python3 tools/hooks/block-direct-xml-edit.py --check "$f" --tool Edit \
    || { echo "Stop: $f requires xml-gen"; exit 1; }
done
```

### ALLOWED (exception)

If `xmlgen` **explicitly does not support** the required operation:

1. The agent records in its `{role}-context.md`:
   ```
   [YYYY-MM-DD HH:MM] MANUAL_XML_EDIT:
     file: <full path>
     operation: <what exactly we are doing>
     reason: xmlgen lacks <capability>
     validation_method: <how we verified this is correct>
   ```
2. The agent notifies the orchestrator via an entry in `orchestrator-context.md`:
   ```
   [YYYY-MM-DD HH:MM] MANUAL_XML_EDIT_REPORTED: agent=<role>, file=<path>, reason=<...>
   ```
3. The orchestrator is required to:
   - record the fact for later expansion of `xmlgen` (a separate subtask to extend the tool),
   - if necessary, run `xml-gen validate` to catch side-effect schema bugs.

## What is done in code, not through xml-gen (do not express in XML)

> A separate branch from "hands off - only xml-gen". Some platform capabilities are not stored in metadata XML at all - they are **recommended** to be implemented programmatically when extending standard objects. xml-gen intentionally does not provide a generator for them. The absence of such a generator is a **design choice**, not a tool defect: do not file it as a missing capability and do not look for a manual XML workaround.

| Capability | How to do it | How NOT to do it |
|-------------|------------|---------------|
| Conditional formatting of forms and visibility-by-condition | Programmatically in the form module: `УсловноеОформление.Элементы.Добавить()` — sets `Оформление`, `Отбор`, `ОформляемыеПоля`. This is the recommended approach when extending standard objects | Write `<ConditionalAppearance>` into the form XML by hand or wait for a `form` DSL key for this |
| Filters / sorting / dynamic list parameters | Programmatically through `Список.КомпоновкаДанных.Отбор` / settings, or in your own dynamic list settings | Edit `<Filter>`/`<SettingsComposer>` of the list manually |
| Coloring/formatting MXL cells **by condition** when outputting | Programmatically when filling the tabular document: `Область.ТекстЦвет = …`, `Область.ЦветФона = …` on the filled area | Encode runtime conditional formatting of cells in `Template.xml` |

**Within xml-gen's responsibility zone (this is generated, the tool must be used):** static properties of elements, including static `Visible=false` (`form` DSL / `form edit`); **static** MXL cell styles - font/alignment/borders/wrapping/format (`mxl` DSL); conditional formatting of **reports (SKD)**, which legitimately lives in the data composition XML schema and is specified through the `skd` DSL - this is NOT the same as conditional formatting of a *form*, and is not moved into code.

**Why:** conditional formatting / list filters / formatting on output are runtime aspects tied to the object's element tree and data; expressing them in static XML is fragile (cross-cutting ids, schema passes validation but breaks in build/runtime - see the OC-22444 class above) and goes against the platform's recommended extension model. Implementing it in BSL keeps the form/layout XML minimal and the behavior testable.

## What is NOT 1C XML (the rule does not apply)

- `pom.xml`, `build.gradle.kts`, `settings.gradle.kts` — build descriptors.
- `.gitignore`, `.editorconfig`, CI/CD YAML/XML.
- Test fixtures for tools (when XML is used as test data, not as a real 1C configuration).
- Documentation in XML format (if any).

## Behavior on violation

1. Stop, do not perform the manual edit.
2. Check for a suitable `xmlgen` command: `xml-gen --help`, `xml-gen form --help`, the SKILL.md of the corresponding skill.
3. If the command exists — use it.
4. If the command does not exist — switch to the "exception" procedure above (logging + notifying the orchestrator).
5. If unclear — `clarification_needed` → orchestrator / user.

## Enforcement: pre-tool-use hook

The rule is reinforced by the automatic hook `tools/hooks/block-direct-xml-edit.py`,
which identifies 1C metadata XML/MXL by path structure:

- `*.mxl` — always blocked (binary format).
- `*.xml` inside `**/Ext/`, `Configuration.xml`, or any of the 1C root folders
  (`Catalogs/`, `Documents/`, `InformationRegisters/`, `Roles/`, `Subsystems/`,
  `CommonModules/`, `ChartsOf*`, `*Registers/`, `Reports/`, `DataProcessors/`,
  `Enums/`, `Constants/`, `ExchangePlans/`, `Tasks/`, `BusinessProcesses/`,
  `HTTPServices/`, `WebServices/`, `EventSubscriptions/`, `ScheduledJobs/`,
  `DefinedTypes/`, `DocumentJournals/` etc.).
- Exceptions (not blocked): `pom.xml`, `*.gradle*`, CI configs (`.github/`,
  `.gitlab/`), test fixtures (`/test/`, `/tests/`, `/fixtures/`,
  `/__fixtures__/`, `/testdata/`, `/test-resources/`).

### Claude Code

Registered in `.claude/settings.json` as a PreToolUse hook for
`Edit|Write|MultiEdit|NotebookEdit`. When attempting direct editing of 1C XML/MXL,
the hook returns exit 2 - Claude Code rejects the tool call and shows the model stderr
with a hint about the required xml-gen command. No manual action after cloning the
repo is needed - the hook is active from the moment a Claude Code session starts in this directory.

### Codex / Cursor / Aider / Cline / other agents without a PreToolUse protocol

Codex and similar tools do not have a built-in PreToolUse protocol - an external
script cannot intercept the tool call **before** execution. Therefore protection is
done **on the model side**: the agent must call `--check` manually before
every XML/MXL edit (the rule above "REQUIRED for agents without a
PreToolUse hook").

The script `block-direct-xml-edit.py` supports two modes - the same binary works
for Claude Code (stdin JSON) and for everything else
(`--check`):

| Mode | When | How it is called |
|-------|-------|----------------|
| stdin JSON | Claude Code PreToolUse - configured in `.claude/settings.json`, nothing needs to be done by the agent | automatically, before every Edit/Write |
| `--check` | Codex/Cursor/Aider/Cline/CI/shell script | agent calls it manually before Edit; exit 2 = block |

Additional protection layers for agents without PreToolUse (recommended for the
project orchestrator):
- **Git pre-commit hook** (`tools/hooks/pre-commit`) can be extended with a call to
  `--check` for all staged `.xml`/`.mxl` files - a late safety net, it does not allow
  bad changes into the repository even if the agent ignored the rule.
- **CI on PR** - the same `--check` on the diff catches any attempts at the
  entry into `main`.

### Fine-tuning / expanding the path list

The `ONEC_ROOT_DIRS`, `EXCLUDE_SUBSTRINGS`, `EXCLUDE_BASENAMES` lists are
defined as constants in `tools/hooks/block-direct-xml-edit.py`. Extend them if the project
gets a new 1C configuration pattern (for example, a non-standard location)
or a new false positive case (build XML with a unique name).

## Related documents

- `framework/skills/tool-usage/platform-data/xml-generation/` — xmlgen skill wrappers.
- `tools/xml-gen/README.md` + `SPEC-*.md` — xmlgen CLI specifications.
- `tools/hooks/block-direct-xml-edit.py` — enforcement hook for this rule.
- `.claude/settings.json` — hook registration for Claude Code.
- `framework/rules/protected-paths.md` — intersects in the `exts/YAXUNIT/**` (protected) part and other protected directories.

---
depends_on:
  - framework/rules/protected-paths.md
  - framework/rules/agent-context-protocol.md
---
