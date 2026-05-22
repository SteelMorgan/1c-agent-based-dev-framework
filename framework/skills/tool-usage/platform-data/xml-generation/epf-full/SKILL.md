---
name: epf-full
description: "Полный цикл работы с внешними обработками и отчётами 1С: создание EPF/ERF (init/add-form), управление макетами (template add/remove/add-help) для любых объектов метаданных, BSP-регистрация (СведенияОВнешнейОбработке + add-command). Используй при создании обработок, добавлении форм, шаблонов, справки, подключении к подсистеме «Дополнительные отчёты и обработки» БСП."
targets:
  - developer-code
  - architect
---

# EPF Full — Полный цикл внешних обработок

Workflow: `epf init → add-form → add-template → BSP-регистрация (BSL)`. Шаги 1–3 — через `xml-gen` CLI, BSP — прямое редактирование `ObjectModule.bsl`. Для макетов конфигурационных объектов — §4.

---

## §2 Быстрый индекс команд

| Задача | Команда |
|--------|---------|
| Создать обработку | `xml-gen epf init --name <Name> output/` |
| Создать внешний отчёт (ERF) | `xml-gen epf init --type report --name <Name> output/` |
| Добавить форму | `xml-gen epf add-form --epf <Name> --name <FormName> output/` |
| Добавить MXL-шаблон к EPF | `xml-gen epf add-template --epf <Name> --name <T> --type spreadsheet output/` |
| Добавить реквизит в обработку | `xml-gen epf add-attribute --name <N> --type <T> output/<Name>.xml` |
| Добавить ТЧ в обработку | `xml-gen epf add-tabular-section --name <N> output/<Name>.xml` |
| Зарегистрировать в БСП (печать) | Вставить `СведенияОВнешнейОбработке()` в `ObjectModule.bsl` — см. §5 |
| Добавить команду БСП | Вставить блок команды перед `Возврат` — см. §5 |
| Добавить макет к Справочнику/Документу | `xml-gen template add --object <Type.Name> --name <T> --type <TemplateType> src/` |
| Удалить макет | `xml-gen template remove --object <Type.Name> --name <T> src/` |
| Добавить встроенную справку | `xml-gen template add-help --object <Type.Name> src/` |

**Ключевые пути (Designer):**
- Корневой XML: `output/<Name>.xml`
- Модуль объекта: `output/<Name>/Ext/ObjectModule.bsl`
- Form.xml: `output/<Name>/Forms/<FormName>/Ext/Form.xml`
- Template: `output/<Name>/Templates/<TName>/Ext/Template.xml`

---

## §3 EPF Base — init, add-form, add-template

> **[references/epf-base.md](references/epf-base.md)**

- CLI принимает только **именованные аргументы** `--epf`, `--name`; `output_dir` — последний позиционный.
- `epf add-attribute` редактирует **корневой XML обработки** (`<Name>.xml`), а не Form.xml. Для формы — `form add-attribute`.

---

## §4 Templates — макеты для любых объектов метаданных

`template add / remove / add-help` — для Catalog, Document, Report, DataProcessor, InformationRegister, AccumulationRegister и др.

> **[references/templates.md](references/templates.md)**

- `--object` обязателен, формат `Type.Name` (пример: `Document.ЗаказКлиента`).
- Для СКД-отчётов при первом добавлении схемы — `--set-main-dcs`.
- Префикс `ПФ_` для SpreadsheetDocument применять автоматически.

**Типы макетов для `xml-gen epf add-template` (поддерживаются 4):**
| `--type` | Назначение |
|----------|-----------|
| `SpreadsheetDocument` | Печатная форма (MXL) |
| `HTMLDocument` | HTML-шаблон |
| `TextDocument` | Текстовый шаблон |
| `BinaryData` | Двоичные данные |

> **`DataCompositionSchema` в `epf add-template` НЕ поддерживается** — CLI отвечает `Failed to add template: Unknown template type: DataCompositionSchema`. Для добавления СКД-макета в EPF используй универсальную команду `xml-gen template add --object DataProcessor.<EpfName> --name <T> --type DataCompositionSchema <output_dir>` (она знает все 5 типов, включая `DataCompositionSchema`). См. также раздел §3 в [xml-generation/SKILL.md](../SKILL.md) и [references/universal-commands.md](../references/universal-commands.md).

После добавления MXL-макета — заполни содержимым через `xml-gen mxl compile invoice.json <путь к Template.xml>`.

---

## §5 EPF БСП — регистрация в «Дополнительные отчёты и обработки»

> **[references/epf-bsp.md](references/epf-bsp.md)**

- BSP-регистрация — **BSL-код** в `ObjectModule.bsl`, не CLI-команда.
- `СведенияОВнешнейОбработке()` — в область `#Область ПрограммныйИнтерфейс`.
- **Назначаемые виды** (ЗаполнениеОбъекта, Отчет, ПечатнаяФорма, СозданиеСвязанныхОбъектов) — требуют `Назначение.Добавить(...)`.
- **Глобальные виды** (ДополнительнаяОбработка, ДополнительныйОтчет) — без назначения.
- Дополнительные команды: `НСтр("ru = '...'")` для Представления (не `МетаданныеОбработки.Представление()`).
- Маппинг BspKind → ВидОбработки, BspCommandType → ТипКоманды — см. references/epf-bsp.md.

---

depends_on:
  - mxl-dsl
  - meta-operations
metadata:
  category: 1c-development
  version: "1.0"
---
