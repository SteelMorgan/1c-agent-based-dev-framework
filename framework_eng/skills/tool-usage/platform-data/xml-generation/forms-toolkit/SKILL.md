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

# forms-toolkit — Working with forms and EPF/ERF

## §1 Form lifecycle

```
form-info → form-edit → form-validate → form-info
form-decompile → form-compile — только для scaffold новой формы по образцу
epf-validate — для EPF/ERF
form-element-mapping — маппинг Title→Name для Vanessa-сценариев
```

## §2 When to use

| Trigger | Operation | Reference |
|---------|----------|-----------|
| Understand form structure | `form-info` | [references/info.md](references/info.md) |
| Get a JSON draft of a new form by example | `form-decompile` | draft, not lossless |
| Add a field / attribute / command | `form-edit` | [references/edit.md](references/edit.md) |
| Validate Form.xml after changes | `form-validate` | [references/validate.md](references/validate.md) |
| Writing Vanessa steps (Title→Name) | `form-element-mapping` | [references/element-mapping.md](references/element-mapping.md) |
| EPF / ERF validation | `epf-validate` | [references/validate.md](references/validate.md) (EPF section) |

## §3 Quick operation index

| Operation | Command | Key parameters |
|----------|---------|-------------------|
| `form-info` | `xml-gen form info "<FormPath>"` | `--limit N`, `--offset N` |
| `form-decompile` | `xml-gen form decompile "<FormPath>" [out.json]` | scaffold JSON for `form compile` |
| `form-edit` | `xml-gen form edit "<FormPath>" --json "<JsonPath>"` | JSON: elements / attributes / commands |
| `form-validate` | `xml-gen validate --type form "<FormPath>"` | `--output json` |
| `epf-validate` | `xml-gen validate --type epf "<ObjectPath>"` | `--output json` |
| `form-element-mapping` | grep on Form.xml / Module.bsl (algorithm) | 4 search steps |

## §4 Quick example

```bash
# 1. Изучить структуру
xml-gen form info "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"

# 2. Применить изменения (spec.json с elements/attributes)
xml-gen form edit "src/.../Form.xml" --json "spec.json"

# 3. Проверить результат
xml-gen validate --type form "src/.../Form.xml"

# Scaffold новой формы по образцу
xml-gen form decompile "src/.../Form.xml" draft-form.json

# Валидация EPF
xml-gen validate --type epf "src/МояОбработка/"

# Найти программное имя по заголовку (для Vanessa)
grep -B5 "Контрагент" path/to/Form.xml | grep "<Name>"
```

---

Details for each operation:
- [references/info.md](references/info.md) — detailed form-info output, pagination, type abbreviations
- [references/edit.md](references/edit.md) — JSON format, element types, requisite type system, events
- [references/validate.md](references/validate.md) — checklist of form-validate and epf-validate checks, error codes, DataPath resolution
- [references/element-mapping.md](references/element-mapping.md) — Title→Name algorithm (4 steps), pitfalls, value format
