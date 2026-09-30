---
name: forms-toolkit
description: "xml-gen forms: info, edit, validate, mapping"
argument-hint: <operation> <FormPath> [<JsonPath>]
allowed-tools:
  - Bash
  - Read
  - Write
  - Glob
metadata:
  category: tool-usage
depends_on:
  - framework/skills/tool-usage/platform-data/xml-generation/SKILL.md
---

# forms-toolkit — Working with Forms and EPF/ERF

## §1 Form Work Lifecycle

```
form-info → form-edit → form-validate → [preview + validate_form] → form-info
form-decompile → form-compile — only for scaffolding a new form based on an example
epf-validate — for EPF/ERF
form-element-mapping — mapping Title→Name for Vanessa scenarios
```

## §2 When to Apply

| Trigger | Operation | Reference |
|---------|----------|-----------|
| Understand the form structure | `form-info` | [references/info.md](references/info.md) |
| Get a JSON draft of a new form based on an example | `form-decompile` | draft, not lossless |
| Add a field / attribute / command | `form-edit` | [references/edit.md](references/edit.md) |
| Check Form.xml after changes | `form-validate` | [references/validate.md](references/validate.md) |
| Writing Vanessa steps (Title→Name) | `form-element-mapping` | [references/element-mapping.md](references/element-mapping.md) |
| Visually evaluate a form after compile/edit (without building) | MCP `open_preview` + `capture_preview` | [../references/form-viewer-mcp.md](../references/form-viewer-mcp.md) |
| Second opinion on the form structure | MCP `validate_form` (alongside `form-validate`, not instead of it) | [../references/form-viewer-mcp.md](../references/form-viewer-mcp.md) |
| EPF / ERF validation | `epf-validate` | [references/validate.md](references/validate.md) (EPF section) |

## §3 Operations Quick Index

| Operation | Command | Key parameters |
|----------|---------|-------------------|
| `form-info` | `xml-gen form info "<FormPath>"` | `--limit N`, `--offset N` |
| `form-decompile` | `xml-gen form decompile "<FormPath>" [out.json]` | scaffold JSON for `form compile` |
| `form-edit` | `xml-gen form edit "<FormPath>" --json "<JsonPath>"` | JSON: elements / attributes / commands |
| `form-validate` | `xml-gen validate --type form "<FormPath>"` | `--output json` |
| `epf-validate` | `xml-gen validate --type epf "<ObjectPath>"` | `--output json` |
| `form-element-mapping` | grep in Form.xml / Module.bsl (algorithm) | 4 search steps |

## §4 Quick Example

```bash
# 1. Study the structure
xml-gen form info "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"

# 2. Apply changes (spec.json with elements/attributes)
xml-gen form edit "src/.../Form.xml" --json "spec.json"

# 3. Check the result
xml-gen validate --type form "src/.../Form.xml"

# Scaffold a new form based on an example
xml-gen form decompile "src/.../Form.xml" draft-form.json

# 4. (if MCP 1c-form-viewer is connected) visual self-check and a second opinion:
#    open_preview {path: "<absolute path>/Ext/Form.xml", audience: "agent"} -> capture_preview -> validate_form
#    View only: edit the form only with xml-gen

# EPF validation
xml-gen validate --type epf "src/МояОбработка/"

# Find the programmatic name by title (for Vanessa)
grep -B5 "Контрагент" path/to/Form.xml | grep "<Name>"
```

---

Details for each operation:
- [references/info.md](references/info.md) — detailed form-info output, pagination, type abbreviations
- [references/edit.md](references/edit.md) — JSON format, element types, attribute type system, events
- [references/validate.md](references/validate.md) — form-validate and epf-validate checklists, error codes, DataPath resolution
- [references/element-mapping.md](references/element-mapping.md) — Title→Name algorithm (4 steps), pitfalls, value format
