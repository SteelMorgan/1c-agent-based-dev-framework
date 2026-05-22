---
name: no-manual-xml-edit
description: Global prohibition on manual editing of 1C XML/MXL metadata. All operations are through the xmlgen CLI. For Claude Code it is blocked automatically by a PreToolUse hook; for Codex and any other agent without PreToolUse, mandatory self-check via `block-direct-xml-edit.py --check` before every Edit/Write. Manual editing is allowed only if xmlgen explicitly does not support the operation, with mandatory logging.
alwaysApply: true
---

# Prohibition on manual editing of 1C XML and MXL

Global rule for all agents and subagents - regardless of IDE
(Claude Code, Codex, Cursor, Aider, Cline, Windsurf, and any others).

## TL;DR for the agent

1. **Do not edit** files of the following types directly:
   - `*.mxl` (any),
   - `*.xml` inside `**/Ext/`, `Configuration.xml`, or 1C root folders
     (`Catalogs/`, `Documents/`, `*Registers/`, `Roles/`, `Subsystems/`,
     `CommonModules/`, `ChartsOf*`, `Reports/`, `DataProcessors/`, `Enums/`,
     `Constants/`, `ExchangePlans/`, `Tasks/`, `BusinessProcesses/`,
     `HTTPServices/`, `WebServices/`, `EventSubscriptions/`, `ScheduledJobs/`,
     `DefinedTypes/`, `DocumentJournals/`, etc.).
2. Instead, use `xml-gen <domain> <op>` - see the skill
   `framework/skills/tool-usage/platform-data/xml-generation/SKILL.md`.
3. **If you are NOT in Claude Code** (that is, the PreToolUse hook does not protect you) -
   you are **required** before every `Edit`/`Write`/`apply_patch`/`sed` on a path with
   the `.xml` or `.mxl` extension to first call:
   ```bash
   python3 tools/hooks/block-direct-xml-edit.py --check "<path>" --tool Edit
   ```
   If exit code = 2 - stop, read stderr (it contains a hint about the
   required xml-gen command) and switch to xml-gen. If exit code = 0 -
   the path is not 1C metadata, editing is allowed.

## Context

1C:Enterprise metadata stored in XML (`Form.xml`, `Rights.xml`, `Configuration.xml`, `*.xml` for catalogs / documents / registers / subscriptions / roles / plans / reports / processors / common modules and any other configuration objects) has a strict schema + non-obvious runtime dependencies. Direct editing through the Edit/Write tool regularly leads to a non-canonical schema that:

- passes `build_project` and LSP diagnostics,
- but breaks runtime UI / object behavior.

Precedent: OC-22444 F-01 - manual generation of `<ValueType><Type>...</Type></ValueType>` instead of the canonical `<Type><v8:Type>...</v8:Type></Type>` + missing UI `<TableColumn>` elements. 6 Developer-Code iterations failed to stabilize the form.

The `xmlgen` tool (Java CLI) and its skill wrappers cover all typical operations:
- creating / editing forms (attributes, UI elements, commands, events),
- access rights (`Rights.xml`),
- EPF, SKD, templates,
- byte-by-byte text replacement (`edit replace-text`) while preserving BOM/CRLF/LF,
- schema + structural + semantic rule validation.

## Rules

### PROHIBITED

- Using Edit / Write / sed / awk / any text tool to directly modify 1C XML metadata.
- Creating 1C XML through template strings in Python/Bash/other scripts instead of `xmlgen`.
- Bypassing schema checks by copying XML blocks from other forms without running `xmlgen validate`.

### REQUIRED

- Any change to 1C metadata XML - through the `xmlgen` CLI and its skill wrappers:
  - `/form-edit`, `/form-info`, `/form-validate` - managed forms,
  - `xml-gen form add-attribute / add-element / add-command / remove-element / move-element`,
  - `xml-gen role add-object / add-right`,
  - `xml-gen epf add-attribute / add-tabular-section`,
  - `xml-gen skd add-parameter / add-field`,
  - `xml-gen config / subsystem / interface / meta / extension validate`,
  - `xml-gen edit replace-text` - for safe replacement of text blocks while preserving byte structure.
- After any modification of 1C XML - `xml-gen validate` (appropriate type), exit code 0 or 2 (warnings).
- Before modification - `xml-gen validate` to capture the current state (catches previous errors unrelated to the current edit).

### REQUIRED for agents without the PreToolUse hook (Codex, Cursor, Aider, Cline, etc.)

If you are not in Claude Code - there is no automatic blocking, so self-check is mandatory.
Before every call to `Edit` / `Write` / `apply_patch` / `sed` / `awk` / any
text tool on a path with the `.xml` or `.mxl` extension:

1. Run the guard manually:
   ```bash
   python3 tools/hooks/block-direct-xml-edit.py --check "<path>" --tool Edit
   ```
2. If exit code = `2` - the path belongs to 1C metadata, do **not** perform the edit.
   Read stderr: it contains a hint about which `xml-gen` command to use for this
   file type. Switch to it.
3. If exit code = `0` - the path is not 1C metadata (for example, `pom.xml`, a test
   fixture in `/tests/`, documentation). You may edit it as usual.

The script is idempotent, with no side effects - it is a pure path detector. Run it
as many times as you like without fear.

An alternative pattern for shell scripts (batch processing of multiple files):
```bash
for f in $(git diff --name-only); do
  python3 tools/hooks/block-direct-xml-edit.py --check "$f" --tool Edit \
    || { echo "Stop: $f requires xml-gen"; exit 1; }
done
```

### ALLOWED (exception)

If `xmlgen` **explicitly does not support** the required operation:

1. The agent records the following in its `{role}-context.md`:
   ```
   [YYYY-MM-DD HH:MM] MANUAL_XML_EDIT:
     file: <full path>
     operation: <what exactly is being done>
     reason: xmlgen lacks <capability>
     validation_method: <how you verified that this is correct>
   ```
2. The agent notifies the orchestrator by writing to `orchestrator-context.md`:
   ```
   [YYYY-MM-DD HH:MM] MANUAL_XML_EDIT_REPORTED: agent=<role>, file=<path>, reason=<...>
   ```
3. The orchestrator is required to:
   - register the fact for future `xmlgen` expansion (a separate subtask to extend the tool),
   - if necessary, run `xml-gen validate` to catch side-effect schema bugs.

## What is NOT 1C XML (the rule does not apply)

- `pom.xml`, `build.gradle.kts`, `settings.gradle.kts` - build descriptors.
- `.gitignore`, `.editorconfig`, CI/CD YAML/XML.
- Test fixtures for tools (when XML is used as test data, not as real 1C configuration).
- XML documentation (if any).

## Behavior on violation

1. Stop, do not perform manual editing.
2. Check for an appropriate `xmlgen` command: `xml-gen --help`, `xml-gen form --help`, the corresponding skill's `SKILL.md`.
3. If the command exists - use it.
4. If the command does not exist - switch to the "exception" procedure above (logging + notifying the orchestrator).
5. If unclear - `clarification_needed` -> orchestrator / user.

## Enforcement: pre-tool-use hook

The rule is enforced by the automatic hook `tools/hooks/block-direct-xml-edit.py`,
which determines 1C metadata XML/MXL by path structure:

- `*.mxl` - always blocked (binary format).
- `*.xml` inside `**/Ext/`, `Configuration.xml`, or any of the 1C root folders
  (`Catalogs/`, `Documents/`, `InformationRegisters/`, `Roles/`, `Subsystems/`,
  `CommonModules/`, `ChartsOf*`, `*Registers/`, `Reports/`, `DataProcessors/`,
  `Enums/`, `Constants/`, `ExchangePlans/`, `Tasks/`, `BusinessProcesses/`,
  `HTTPServices/`, `WebServices/`, `EventSubscriptions/`, `ScheduledJobs/`,
  `DefinedTypes/`, `DocumentJournals/`, etc.).
- Exceptions (not blocked): `pom.xml`, `*.gradle*`, CI configs (`.github/`,
  `.gitlab/`), test fixtures (`/test/`, `/tests/`, `/fixtures/`,
  `/__fixtures__/`, `/testdata/`, `/test-resources/`).

### Claude Code

Registered in `.claude/settings.json` as a PreToolUse hook for
`Edit|Write|MultiEdit|NotebookEdit`. On an attempt to directly edit 1C XML/MXL the
hook returns exit 2 - Claude Code rejects the tool call and shows the model stderr
with a hint about the required xml-gen command. No manual actions after cloning the
repo are needed - the hook is active from the moment the Claude Code session starts
in this directory.

### Codex / Cursor / Aider / Cline / other agents without the PreToolUse protocol

Codex and similar tools do not have a built-in PreToolUse protocol - an external
script cannot intercept the tool call **before** execution. Therefore protection is
done **on the model's side**: the agent must call `--check` manually before every
xml/MXL edit (the rule above "REQUIRED for agents without the PreToolUse hook").

The `block-direct-xml-edit.py` script supports two modes - the same
binary works both for Claude Code (stdin JSON) and for everything else
(`--check`):

| Mode | When | How it is called |
|------|------|-------------------|
| stdin JSON | Claude Code PreToolUse - configured in `.claude/settings.json`, the agent does not need to do anything | automatically, before each Edit/Write |
| `--check` | Codex/Cursor/Aider/Cline/CI/shell script | the agent calls it manually before Edit; exit 2 = block |

Additional protection layers for agents without PreToolUse (recommended for the
project orchestrator):
- **Git pre-commit hook** (`tools/hooks/pre-commit`) can be extended to call
  `--check` on all staged `.xml`/`.mxl` files - a later safety net that does not let
  anything into the repository even if the agent ignored the rule.
- **PR CI** - the same `--check` on the diff catches any attempts at the
  `main` entry point.

### Fine-tuning / extending the path list

The `ONEC_ROOT_DIRS`, `EXCLUDE_SUBSTRINGS`, `EXCLUDE_BASENAMES` lists are defined
as constants in `tools/hooks/block-direct-xml-edit.py`. Extend them if the project
introduces a new 1C configuration pattern (for example, a nonstandard location)
or a new false positive case (build XML with a unique name).

## Related documents

- `framework/skills/tool-usage/platform-data/xml-generation/` - xmlgen skill wrappers.
- `tools/xml-gen/README.md` + `SPEC-*.md` - xmlgen CLI specifications.
- `tools/hooks/block-direct-xml-edit.py` - enforcement hook for this rule.
- `.claude/settings.json` - hook registration for Claude Code.
- `framework/rules/protected-paths.md` - overlaps in the `exts/YAXUNIT/**` part (protected) and other protected directories.

---
depends_on:
  - framework/rules/protected-paths.md
  - framework/rules/agent-context-protocol.md
---
