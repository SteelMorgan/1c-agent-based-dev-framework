---
name: forms-toolkit
description: "A complete toolkit for working with 1C managed forms and external processors/reports (EPF/ERF) through the xmlgen CLI: structure analysis, adding elements, validation, and element mapping by visible text. Combines form-info, form-edit, form-validate, form-element-mapping, epf-validate."
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

## §1 Form work lifecycle

```
form-info → form-edit → form-validate → form-info
epf-validate — for EPF/ERF
form-element-mapping — Title→Name mapping for Vanessa scenarios
```

## §2 When to use

| Trigger | Operation | Reference |
|---------|----------|-----------|
| Understand the form structure | `form-info` | [references/info.md](references/info.md) |
| Add a field / attribute / command | `form-edit` | [references/edit.md](references/edit.md) |
| Check Form.xml after changes | `form-validate` | [references/validate.md](references/validate.md) |
| Write Vanessa steps (Title→Name) | `form-element-mapping` | [references/element-mapping.md](references/element-mapping.md) |
| Validate EPF / ERF | `epf-validate` | [references/validate.md](references/validate.md) (EPF section) |

## §3 Quick operation index

| Operation | Command | Key parameters |
|----------|---------|-------------------|
| `form-info` | `xmlgen form info "<FormPath>"` | `--limit N`, `--offset N` |
| `form-edit` | `xmlgen form edit "<FormPath>" --json "<JsonPath>"` | JSON: elements / attributes / commands |
| `form-validate` | `xmlgen validate --type form "<FormPath>"` | `--output json` |
| `epf-validate` | `xmlgen validate --type epf "<ObjectPath>"` | `--output json` |
| `form-element-mapping` | grep in Form.xml / Module.bsl (algorithm) | 4 search steps |

## §4 Quick example

```bash
# 1. Изучить структуру
xmlgen form info "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"

# 2. Применить изменения (spec.json с elements/attributes)
xmlgen form edit "src/.../Form.xml" --json "spec.json"

# 3. Проверить результат
xmlgen validate --type form "src/.../Form.xml"

# Валидация EPF
xmlgen validate --type epf "src/МояОбработка/"

# Найти программное имя по заголовку (для Vanessa)
grep -B5 "Контрагент" path/to/Form.xml | grep "<Name>"
```

---

Details for each operation:
- [references/info.md](references/info.md) — detailed form-info output, pagination, type abbreviations
- [references/edit.md](references/edit.md) — JSON format, element types, attribute type system, events
- [references/validate.md](references/validate.md) — form-validate and epf-validate checklists, error codes, DataPath resolution
- [references/element-mapping.md](references/element-mapping.md) — Title→Name algorithm (4 steps), pitfalls, value format
