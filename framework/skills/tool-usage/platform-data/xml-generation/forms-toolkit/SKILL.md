---
name: forms-toolkit
description: "Use for анализа структуры форм, добавления элементов, валидации и маппинга Title→Name для Vanessa-сценариев. Helps работать с Form.xml и EPF/ERF через xml-gen form-info/form-edit/form-validate/form-element-mapping/epf-validate."
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

# forms-toolkit — Работа с формами и EPF/ERF

## §1 Жизненный цикл работы с формой

```
form-info → form-edit → form-validate → form-info
epf-validate — для EPF/ERF
form-element-mapping — маппинг Title→Name для Vanessa-сценариев
```

## §2 Когда применять

| Триггер | Операция | Reference |
|---------|----------|-----------|
| Понять структуру формы | `form-info` | [references/info.md](references/info.md) |
| Добавить поле / реквизит / команду | `form-edit` | [references/edit.md](references/edit.md) |
| Проверить Form.xml после изменений | `form-validate` | [references/validate.md](references/validate.md) |
| Написание Vanessa-шагов (Title→Name) | `form-element-mapping` | [references/element-mapping.md](references/element-mapping.md) |
| Валидация EPF / ERF | `epf-validate` | [references/validate.md](references/validate.md) (раздел EPF) |

## §3 Краткий индекс операций

| Операция | Команда | Ключевые параметры |
|----------|---------|-------------------|
| `form-info` | `xml-gen form info "<FormPath>"` | `--limit N`, `--offset N` |
| `form-edit` | `xml-gen form edit "<FormPath>" --json "<JsonPath>"` | JSON: elements / attributes / commands |
| `form-validate` | `xml-gen validate --type form "<FormPath>"` | `--output json` |
| `epf-validate` | `xml-gen validate --type epf "<ObjectPath>"` | `--output json` |
| `form-element-mapping` | grep по Form.xml / Module.bsl (алгоритм) | 4 шага поиска |

## §4 Быстрый пример

```bash
# 1. Изучить структуру
xml-gen form info "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"

# 2. Применить изменения (spec.json с elements/attributes)
xml-gen form edit "src/.../Form.xml" --json "spec.json"

# 3. Проверить результат
xml-gen validate --type form "src/.../Form.xml"

# Валидация EPF
xml-gen validate --type epf "src/МояОбработка/"

# Найти программное имя по заголовку (для Vanessa)
grep -B5 "Контрагент" path/to/Form.xml | grep "<Name>"
```

---

Подробности по каждой операции:
- [references/info.md](references/info.md) — детальный вывод form-info, пагинация, сокращения типов
- [references/edit.md](references/edit.md) — JSON-формат, типы элементов, система типов реквизитов, события
- [references/validate.md](references/validate.md) — чеклист проверок form-validate и epf-validate, коды ошибок, резолв DataPath
- [references/element-mapping.md](references/element-mapping.md) — алгоритм Title→Name (4 шага), ловушки, формат значений
