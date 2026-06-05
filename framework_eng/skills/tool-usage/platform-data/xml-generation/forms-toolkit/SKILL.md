---
name: forms-toolkit
description: "Use for analyzing form structure, adding elements, validating, and mapping Title→Name for Vanessa scenarios. Helps work with Form.xml and EPF/ERF through xml-gen form-info/form-edit/form-validate/form-element-mapping/epf-validate."
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

# forms-toolkit — Working with forms and EPF/ERF

## §1 Form workflow lifecycle

```
form-info → form-edit → form-validate → form-info
epf-validate — for EPF/ERF
form-element-mapping — Title→Name mapping for Vanessa scenarios
```

## §2 When to use

| Trigger | Operation | Reference |
|---------|----------|-----------|
| Understand form structure | `form-info` | [references/info.md](references/info.md) |
| Add a field / attribute / command | `form-edit` | [references/edit.md](references/edit.md) |
| Check Form.xml after changes | `form-validate` | [references/validate.md](references/validate.md) |
| Writing Vanessa steps (Title→Name) | `form-element-mapping` | [references/element-mapping.md](references/element-mapping.md) |
| Validate EPF / ERF | `epf-validate` | [references/validate.md](references/validate.md) (EPF section) |

## §3 Short operation index

| Operation | Command | Key parameters |
|----------|---------|-------------------|
| `form-info` | `xml-gen form info "<FormPath>"` | `--limit N`, `--offset N` |
| `form-edit` | `xml-gen form edit "<FormPath>" --json "<JsonPath>"` | JSON: elements / attributes / commands |
| `form-validate` | `xml-gen validate --type form "<FormPath>"` | `--output json` |
| `epf-validate` | `xml-gen validate --type epf "<ObjectPath>"` | `--output json` |
| `form-element-mapping` | grep on Form.xml / Module.bsl (algorithm) | 4 search steps |

## §4 Quick example

```bash
# 1. Examine the structure
xml-gen form info "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"

# 2. Apply changes (spec.json with elements/attributes)
xml-gen form edit "src/.../Form.xml" --json "spec.json"

# 3. Verify the result
xml-gen validate --type form "src/.../Form.xml"

# EPF validation
xml-gen validate --type epf "src/МояОбработка/"

# Find the programmatic name by the title (for Vanessa)
grep -B5 "Контрагент" path/to/Form.xml | grep "<Name>"
```

---

Details for each operation:
- [references/info.md](references/info.md) — detailed form-info output, pagination, type abbreviations
- [references/edit.md](references/edit.md) — JSON format, element types, attribute type system, events
- [references/validate.md](references/validate.md) — checklist for form-validate and epf-validate, error codes, DataPath resolution
- [references/element-mapping.md](references/element-mapping.md) — Title→Name algorithm (4 steps), pitfalls, value format
