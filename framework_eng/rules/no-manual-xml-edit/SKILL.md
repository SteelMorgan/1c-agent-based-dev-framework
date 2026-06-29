---
name: no-manual-xml-edit
description: "When editing 1C XML/MXL, use xml-generation"
alwaysApply: true
---

# Prohibition on manual editing of 1C XML and MXL

> **Trigger:** any attempt to use Edit/Write/sed/awk/Bash on a `*.mxl` or `*.xml` file in 1C directories. When triggered, apply the `xml-generation` skill (`framework/skills/tool-usage/platform-data/xml-generation/SKILL.md`).

## PROHIBITED (without exceptions)

- Edit / Write / sed / awk / any text tool for direct modification of 1C XML/MXL metadata.
- Creating 1C XML through template strings in Python/Bash/other scripts apart from `xmlgen`.
- Bypassing schema checks by copying XML blocks from other forms without running `xmlgen validate`.

**Why:** 1C metadata has a strict schema plus non-obvious runtime dependencies. Direct editing regularly produces a non-canonical schema that passes build and LSP, but breaks runtime UI. Precedent: OC-22444 F-01 - 6 iterations of Developer-Code could not stabilize the form.

## What counts as 1C XML (rule applies)

- `*.mxl` (any).
- `*.xml` inside `**/Ext/`, `Configuration.xml`, or 1C root folders: `Catalogs/`, `Documents/`, `*Registers/`, `Roles/`, `Subsystems/`, `CommonModules/`, `ChartsOf*`, `Reports/`, `DataProcessors/`, `Enums/`, `Constants/`, `ExchangePlans/`, `Tasks/`, `BusinessProcesses/`, `HTTPServices/`, `WebServices/`, `EventSubscriptions/`, `ScheduledJobs/`, `DefinedTypes/`, `DocumentJournals/` and so on.

## What is NOT 1C XML (rule does not apply)

- `pom.xml`, `build.gradle.kts`, `settings.gradle.kts` - build descriptors.
- `.gitignore`, `.editorconfig`, CI/CD YAML/XML.
- Test fixtures for tools (XML as test data, not 1C configuration).
- Documentation in XML format.

## Self-check for agents without a PreToolUse hook (Codex, Cursor, Aider, Cline, etc.)

In Claude Code, the hook blocks automatically. In all other environments, **self-check is mandatory** before every Edit/Write/apply_patch/sed on a path with the `.xml` or `.mxl` extension:

```bash
python3 tools/hooks/block-direct-xml-edit.py --check "<path>" --tool Edit
```

- Exit code `2` -> the path belongs to 1C metadata, do not make the edit. Read stderr - it contains a hint about the required xml-gen command.
- Exit code `0` -> not 1C metadata, editing is allowed.

> Some platform capabilities (conditional form formatting, dynamic list filters, MXL cell coloring by condition) are not stored in XML and are implemented programmatically - this is design, not an xml-gen defect; do not look for a manual XML workaround. The list and examples are in the `xml-generation` skill.

## Exception (xmlgen does not support the operation)

If xml-gen **explicitly** does not support the required operation, record the fact of manual editing (`MANUAL_XML_EDIT:` with the file, operation, reason, validation method) in `{role}-context.md` and notify the orchestrator (`MANUAL_XML_EDIT_REPORTED:`) in `orchestrator-context.md`. The recording format is in the `xml-generation` skill.

---
depends_on:
  - framework/skills/tool-usage/platform-data/xml-generation/SKILL.md
  - framework/rules/protected-paths/SKILL.md
---
