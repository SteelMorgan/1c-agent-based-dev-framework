---
name: epf-full
description: "xml-gen EPF/ERF: внешние отчёты и обработки"
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
| Создать внешний СКД-отчёт (ERF) | `xml-gen epf init --type report --name <Name> --with-skd output/` |
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
- Для внешнего СКД-отчёта предпочтителен `epf init --type report --with-skd`: сгенерированный ERF должен содержать пустой `DefaultForm`, `MainDataCompositionSchema` и DCS-макет. По умолчанию он **не должен** создавать форму.
- Пустой `DefaultForm` и отсутствие `Forms/` для отчётов — штатный вариант как для внешних ERF, так и для встроенных конфигурационных отчётов. Платформа автоматически использует стандартную форму отчёта. Собственную форму добавляй только при явной необходимости кастомизировать форму или перехватить клиентские события формы.
- `epf add-form` — отдельная явная операция. Если форму явно добавляют во внешний отчёт, первая форма становится `DefaultForm`, а её главный реквизит должен быть `Отчет`, а не `Объект`.
- После генерации ERF выполни `xml-gen validate --type epf output/<Name>.xml` и проверь корневой XML на триаду `DefaultForm`/`MainDataCompositionSchema`/`Template` перед сборкой бинарного `.erf`. Для обычного СКД-отчёта не требуй `<Form>`.
- В корневом XML внешний объект и его contained object должны иметь разные идентификаторы: `ExternalReport/@uuid` или `ExternalDataProcessor/@uuid` не равен `InternalInfo/ContainedObject/ObjectId`. `xml-gen validate --type epf` должен диагностировать равенство как `EPF-018`, но после валидации всё равно выполни Designer package/load/open-проверку собранного EPF/ERF.
- Если генератор нарушает этот инвариант, останови сборку зависимого объекта и примени процесс исправления дефекта из `xml-generation` §4.1. Не меняй один из идентификаторов вручную в сгенерированном XML: исправление должно пройти regression test, полный build инструмента и повторный Designer/live-check исходного объекта.

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

Для внешних отчётов `epf init --type report --with-skd` создаёт основной `DataCompositionSchema` и связывает его как `MainDataCompositionSchema`. Для уже существующего ERF поддерживается `epf add-template --type DataCompositionSchema`, и он должен заполнять `ExternalReport.<Name>.Template.<TemplateName>`.

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
