---
name: no-manual-xml-edit
description: If you are editing 1C XML/MXL, apply the xml-generation skill. Manual editing is forbidden; for agents without a PreToolUse hook, self-check via block-direct-xml-edit.py is mandatory.
alwaysApply: true
---

# Ban on manual editing of 1C XML and MXL

> **Trigger:** any attempt to use Edit/Write/sed/awk/Bash on a `*.mxl` or `*.xml` file in 1C directories. When triggered, apply the `xml-generation` skill (`framework/skills/tool-usage/platform-data/xml-generation/SKILL.md`).

## FORBIDDEN (no exceptions)

- Edit / Write / sed / awk / any text tool for direct modification of 1C XML/MXL metadata.
- Create 1C XML via template strings in Python/Bash/other scripts instead of `xmlgen`.
- Bypass schema checks by copying XML blocks from other forms without running them through `xmlgen validate`.

**Why:** 1C metadata has a strict schema + non-obvious runtime dependencies. Direct editing regularly produces a non-canonical schema that passes build and LSP, but breaks runtime UI. Precedent: OC-22444 F-01 — 6 iterations of Developer-Code were unable to stabilize the form.

## What counts as 1C XML (the rule applies)

- `*.mxl` (any).
- `*.xml` inside `**/Ext/`, `Configuration.xml`, or 1C root folders: `Catalogs/`, `Documents/`, `*Registers/`, `Roles/`, `Subsystems/`, `CommonModules/`, `ChartsOf*`, `Reports/`, `DataProcessors/`, `Enums/`, `Constants/`, `ExchangePlans/`, `Tasks/`, `BusinessProcesses/`, `HTTPServices/`, `WebServices/`, `EventSubscriptions/`, `ScheduledJobs/`, `DefinedTypes/`, `DocumentJournals/` and so on.

## What is NOT 1C XML (the rule does not apply)

- `pom.xml`, `build.gradle.kts`, `settings.gradle.kts` — build descriptors.
- `.gitignore`, `.editorconfig`, CI/CD YAML/XML.
- Test fixtures for tools (XML as test data, not 1C configuration).
- Documentation in XML format.

## Self-check for agents without a PreToolUse hook (Codex, Cursor, Aider, Cline, etc.)

In Claude Code the hook blocks automatically. In all other environments — **self-check is mandatory** before every Edit/Write/apply_patch/sed on a path with the `.xml` or `.mxl` extension:

```bash
python3 tools/hooks/block-direct-xml-edit.py --check "<path>" --tool Edit
```

- Exit code `2` → the path refers to 1C metadata, do not make the edit. Read stderr — it contains a hint about the required xml-gen command.
- Exit code `0` → not 1C metadata, editing is allowed.

## What is done in code, not through xml-gen

Some platform capabilities are not stored in XML — they are recommended to be implemented programmatically. The absence of a generator in xml-gen for them is a **design**, not a defect.

| Capability | How to do it | How NOT to do it |
|-------------|------------|---------------|
| Conditional form appearance and visibility-by-condition | Programmatically in the form module: `УсловноеОформление.Элементы.Добавить()` | Write `<ConditionalAppearance>` into the form XML by hand |
| Filters / sorting / dynamic list parameters | Programmatically through `Список.КомпоновкаДанных.Отбор` | Manually edit `<Filter>`/`<SettingsComposer>` |
| Coloring/formatting MXL cells **by condition** when outputting | Programmatically when filling: `Область.ЦветФона = …` | Encode conditional formatting in `Template.xml` |

> In xml-gen's responsibility zone (the tool must be used): static properties of form elements, static MXL cell styles, conditional formatting of **reports (СКД)** through the `skd` DSL.

## Exception (xmlgen does not support the operation)

Record it in `{role}-context.md`:
```
[YYYY-MM-DD HH:MM] MANUAL_XML_EDIT:
  file: <полный путь>
  operation: <что именно делаем>
  reason: xmlgen lacks <capability>
  validation_method: <как проверил что это корректно>
```
Notify the orchestrator via `orchestrator-context.md`: `MANUAL_XML_EDIT_REPORTED: agent=<role>, file=<path>, reason=<...>`

---
depends_on:
  - framework/skills/tool-usage/platform-data/xml-generation/SKILL.md
  - framework/rules/protected-paths.md
---
