---
name: no-manual-xml-edit
description: Global prohibition on manual editing of 1C XML metadata. All operations go through the xmlgen CLI and its skill wrappers. Manual editing is allowed only if xmlgen explicitly does not support the operation, with mandatory logging.
alwaysApply: true
---

# Prohibition on Manual 1C XML Editing

Global rule for all agents and subagents.

## Context

1C:Enterprise metadata stored in XML (`Form.xml`, `Rights.xml`, `Configuration.xml`, `*.xml` for catalogs / documents / registers / subscriptions / roles / charts / reports / processing objects / common modules and any other configuration objects) has a strict schema plus non-obvious runtime dependencies. Direct editing through the Edit/Write tool regularly leads to a non-canonical schema that:

- passes `build_project` and LSP diagnostics,
- but breaks the runtime UI / object behavior.

Precedent: OC-22444 F-01 - manual generation of `<ValueType><Type>...</Type></ValueType>` instead of the canonical `<Type><v8:Type>...</v8:Type></Type>` + missing UI `<TableColumn>` elements. 6 iterations of Developer-Code were not able to stabilize the form.

The `xmlgen` tool (Java CLI) and its skill wrappers cover all typical operations:
- creating / editing forms (attributes, UI elements, commands, events),
- access rights (`Rights.xml`),
- EPF, SKD, templates,
- byte-by-byte text replacement (`edit replace-text`) while preserving BOM/CRLF/LF,
- schema + structural + semantic validation rules.

## Rules

### PROHIBITED

- Using Edit / Write / sed / awk / any text tool to directly modify 1C XML metadata.
- Creating 1C XML through template strings in Python/Bash/other scripts instead of `xmlgen`.
- Bypassing schema checks by copying XML blocks from other forms without running them through `xmlgen validate`.

### REQUIRED

- Any change to 1C metadata XML must go through the `xmlgen` CLI and its skill wrappers:
  - `/form-edit`, `/form-info`, `/form-validate` - managed forms,
  - `xml-gen form add-attribute / add-element / add-command / remove-element / move-element`,
  - `xml-gen role add-object / add-right`,
  - `xml-gen epf add-attribute / add-tabular-section`,
  - `xml-gen skd add-parameter / add-field`,
  - `xml-gen config / subsystem / interface / meta / extension validate`,
  - `xml-gen edit replace-text` - for safe replacement of text blocks while preserving the byte structure.
- After any modification of 1C XML - `xml-gen validate` (corresponding type), exit code 0 or 2 (warnings).
- Before modification - `xml-gen validate` to capture the current state (catches previous errors unrelated to the current edit).

### ALLOWED (exception)

If `xmlgen` **explicitly does not support** the required operation:

1. The agent records an entry in its `{role}-context.md`:
   ```
   [YYYY-MM-DD HH:MM] MANUAL_XML_EDIT:
     file: <full path>
     operation: <what exactly we are doing>
     reason: xmlgen lacks <capability>
     validation_method: <how we verified that this is correct>
   ```
2. The agent notifies the orchestrator through an entry in `orchestrator-context.md`:
   ```
   [YYYY-MM-DD HH:MM] MANUAL_XML_EDIT_REPORTED: agent=<role>, file=<path>, reason=<...>
   ```
3. The orchestrator must:
   - register the fact for later expansion of `xmlgen` (a separate subtask to extend the tool),
   - if needed, run `xml-gen validate` to catch side-effect schema bugs.

## What is NOT 1C XML (the rule does not apply)

- `pom.xml`, `build.gradle.kts`, `settings.gradle.kts` - build descriptors.
- `.gitignore`, `.editorconfig`, CI/CD YAML/XML.
- Test fixtures for tools (when XML is used as test data rather than as a real 1C configuration).
- Documentation in XML format (if any).

## Behavior on Violation

1. Stop, do not perform manual editing.
2. Check whether a suitable `xmlgen` command exists: `xml-gen --help`, `xml-gen form --help`, SKILL.md of the relevant skill.
3. If the command exists - use it.
4. If the command does not exist - switch to the exception procedure above (logging + notifying the orchestrator).
5. If unclear - `clarification_needed` -> orchestrator / user.

## Related Documents

- `framework/skills/tool-usage/platform-data/xml-generation/` - xmlgen skill wrappers.
- `tools/xml-gen/README.md` + `SPEC-*.md` - xmlgen CLI specifications.
- `framework/rules/protected-paths.md` - overlaps in `exts/YAXUNIT/**` (protected) and other protected directories.

---
depends_on:
  - framework/rules/protected-paths.md
  - framework/rules/agent-context-protocol.md
---
