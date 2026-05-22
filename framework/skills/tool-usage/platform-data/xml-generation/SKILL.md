---
name: xml-generation
description: "Единый toolkit для генерации, редактирования и валидации XML метаданных 1С через CLI xml-gen. Покрывает 11 доменов (EPF, Form, MXL, SKD, Role, Config, Subsystem, Interface, Meta 23 типа, Extension/CFE) + универсальные операции (validate, edit replace-text, form/template/help add). ~45 CLI-операций в формате Designer. Применяй при создании конфигураций и внешних обработок, добавлении объектов метаданных, форм, ролей, отчётов, печатных форм, расширений; а также при валидации и точечной модификации существующих XML."
argument-hint: <domain> <operation> [<args>]
allowed-tools:
  - Bash
  - Read
  - Write
  - Glob
metadata:
  category: 1c-development
  version: "2.0"
---

# xml-generation — Toolkit для работы с XML метаданных 1С

Единый CLI `xml-gen` покрывает весь цикл работы с XML 1С: генерация из JSON DSL, точечная модификация существующих файлов, валидация. Этот SKILL.md — **роутер**: содержит обзор, индекс под-областей и сквозные принципы. За детальной спецификацией по каждому домену — переходи в соответствующий под-skill (`<имя>/SKILL.md`).

## §1 Обзор CLI xml-gen

Установка: `python tools/install.py --install-xml-gen` (требует JDK 17+).

`xml-gen` покрывает 4 типа операций — compile / edit / init / validate — детали в §2 и под-skills. Универсальные команды (validate, form/template/help add, edit replace-text) описаны в §3.

**Не используй** когда: нужен формат EDT (только Designer), нужны DataSetUnion/CalculatedFields в СКД (workaround: вычисления в запросах).

## §2 Индекс под-областей

| Под-область | Что делает | Когда применять | Reference |
|-------------|------------|-----------------|-----------|
| `forms-toolkit` | info / edit / validate / element-mapping / epf-validate — операционный цикл работы с управляемыми формами и EPF | анализ структуры формы, добавление полей, валидация, маппинг Title→Name для Vanessa | [forms-toolkit/SKILL.md](forms-toolkit/SKILL.md) |
| `form-dsl` | компиляция формы из JSON DSL (`form compile`, в т.ч. `--from-object`) | создать форму с нуля или сгенерировать по объекту | [form-dsl/SKILL.md](form-dsl/SKILL.md) |
| `skd-dsl` | компиляция СКД из JSON (`skd compile`) | создать схему компоновки с нуля | [skd-dsl/SKILL.md](skd-dsl/SKILL.md) |
| `skd-edit` | patch-операции по существующей СКД (`skd add-parameter`, `skd add-field`) | точечная правка Schema.xml | [skd-edit/SKILL.md](skd-edit/SKILL.md) |
| `mxl-dsl` | макеты MXL / SpreadsheetDocument (`mxl compile`) | печатные формы, шаблоны | [mxl-dsl/SKILL.md](mxl-dsl/SKILL.md) |
| `role-dsl` | компиляция ролей (`role compile`, `role add-object`, `role add-right`) | создать/изменить роль | [role-dsl/SKILL.md](role-dsl/SKILL.md) |
| `config-operations` | работа с корнем конфигурации (`config init/info/edit/validate`) | инициализация новой CF, навигация по корню | [config-operations/SKILL.md](config-operations/SKILL.md) |
| `meta-operations` | 23 типа объектов метаданных (`meta compile/info/edit`) | Catalogs / Documents / InformationRegisters / Enums и др. | [meta-operations/SKILL.md](meta-operations/SKILL.md) |
| `subsystem-interface` | подсистемы и командные интерфейсы (`subsystem compile/edit`, `interface edit/validate`) | организация интерфейса конфигурации | [subsystem-interface/SKILL.md](subsystem-interface/SKILL.md) |
| `epf-full` | внешние обработки и отчёты (`epf init/add-form/add-template/bsp-init`) | создание EPF / ERF с нуля, включая БСП-варианты | [epf-full/SKILL.md](epf-full/SKILL.md) |
| `extension-operations` | расширения конфигурации / CFE (`extension init/borrow/diff`) | создать CFE, заимствовать объекты, сравнить расширение с основой | [extension-operations/SKILL.md](extension-operations/SKILL.md) |

> Универсальные команды (`xml-gen form add`, `template add`, `help add`, `edit replace-text`, `validate`) описаны в §3 ниже и не имеют отдельного под-skill-а.

## §3 Универсальные команды

Четыре группы: **validate** (структурная/семантическая проверка любого XML), **form/template/help add** (добавление форм, макетов, справки к любому объекту метаданных), **edit replace-text** (побайтовая замена без нормализации line endings).

Когда применять: validate — перед и после каждой модификации; form/template/help add — когда нужно зарегистрировать новый артефакт без пересборки; edit replace-text — при точечной правке XML с мультилайн в `<v8:content>` (тултипы, описания) или любой замене, где важно сохранить line endings.

→ [references/universal-commands.md](references/universal-commands.md)

## §4 Сквозные принципы

1. **Формат только Designer** — `--format designer` (default). EDT не поддерживается.
2. **Encoding** — UTF-8 с BOM (`utf-8-sig`). Сохраняй BOM при правке.
3. **Line endings** — CRLF между тегами, bare LF в `<v8:content>`. Не используй Claude Code Edit — `xml-gen edit replace-text` (→ [references/universal-commands.md](references/universal-commands.md)).
4. **Idempotency** — `validate` перед и после модификации. При ошибке `<domain> edit` делает rollback автоматически.
5. **Batch operations** — JSON-формат для `form edit` / `meta edit` / `subsystem edit` принимает массивы операций; используй вместо повторных вызовов CLI.
6. **EPF layout** — корневой XML: `output/MyProcessor.xml`. Формы EPF: `output/MyProcessor/Forms/MainForm/Ext/Form.xml`.

## §5 Быстрые примеры (entry-level workflows)

### Создать внешнюю обработку с формой

```bash
xml-gen epf init --name MyProcessor output/
xml-gen epf add-form --epf MyProcessor --name MainForm output/
xml-gen validate --type epf output/MyProcessor
```

Детали — [epf-full/SKILL.md](epf-full/SKILL.md).

### Добавить поле на существующую форму

```bash
# 1. Изучить структуру
xml-gen form info "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"

# 2. Добавить элемент с привязкой к реквизиту
xml-gen form add-element --type InputField --name Склад --path Объект.Склад \
  --parent ГруппаШапка --after Контрагент \
  "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"

# 3. Проверить
xml-gen validate --type form "src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"
```

Детали — [forms-toolkit/SKILL.md](forms-toolkit/SKILL.md) (info/edit/validate) и [form-dsl/SKILL.md](form-dsl/SKILL.md) (compile с нуля).

### Скомпилировать СКД из JSON

```bash
xml-gen skd compile schema.json Template.xml
xml-gen validate --type skd Template.xml
```

Детали — [skd-dsl/SKILL.md](skd-dsl/SKILL.md). Для точечной правки готовой Schema.xml — [skd-edit/SKILL.md](skd-edit/SKILL.md).

### Создать расширение и заимствовать объект

```bash
xml-gen extension init --name МоёРасширение --config output/ output_ext/
xml-gen extension borrow output_ext/ output/ "Catalog.Товары"
xml-gen extension diff output_ext/ output/
```

Детали — [extension-operations/SKILL.md](extension-operations/SKILL.md).

## §6 Антипаттерны (правильно / неправильно)

```bash
# Неправильно: role compile с файлом на выход
xml-gen role compile role.json Roles/МояРоль.xml

# Правильно: output_dir → Roles/<Name>/Ext/Rights.xml
xml-gen role compile role.json output/
```

```bash
# Неправильно: form add-element без --path
xml-gen form add-element --type InputField --name Наименование Form.xml

# Правильно: --path связывает элемент с реквизитом
xml-gen form add-element --type InputField --name Наименование --path Наименование Form.xml
```

```bash
# Неправильно: role add-object с "view"
xml-gen role add-object --name Catalog.Номенклатура --rights view Rights.xml

# Правильно: права через запятую, регистр из enum RoleRight
xml-gen role add-object --name Catalog.Номенклатура --rights Read,View Rights.xml
```

## §7 Workarounds

| Проблема | Решение |
|----------|---------|
| `Parent element not found` (form) | Проверь точное имя родителя в Form.xml — регистр важен |
| `Object already exists` (role) | `role add-right` вместо `add-object` |
| `DataSet not found` (skd) | Проверь имя набора данных в Schema.xml |
| Edit tool ломает line endings | Используй `xml-gen edit replace-text` |
| Нужен DataSetUnion / CalculatedFields в СКД | Workaround: вычисления в запросах |
| Нужен формат EDT | Не поддерживается, только Designer |

---
depends_on:
  - framework/skills/tool-usage/platform-data/xml-generation/forms-toolkit/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/form-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/skd-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/skd-edit/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/mxl-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/role-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/config-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/meta-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/subsystem-interface/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/epf-full/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/extension-operations/SKILL.md
---
