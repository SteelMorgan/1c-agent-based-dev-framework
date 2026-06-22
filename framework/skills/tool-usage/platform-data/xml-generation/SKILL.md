---
name: xml-generation
description: "MUST use WHEN нужно создать, изменить или валидировать любой XML метаданных 1С (формы, роли, объекты, MXL, СКД, EPF, расширения, конфигурация). Provides безопасную генерацию и точечную модификацию через CLI xml-gen, соблюдая правило no-manual-xml-edit."
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

`xml-gen` имеет две дополняющие рабочие поверхности:

- **JSON DSL surface** — `mxl/skd/form/role/meta compile` и доступные декомпиляторы, например `mxl decompile`. Используй, когда артефакт удобнее описать декларативно и скомпилировать в Designer XML.
- **Operational CLI surface** — публичные команды `epf init`, `epf add-template`, `form add-element`, `meta edit`, `template add`, `validate`. Используй, когда нужно создать или изменить существующее дерево метаданных явными CLI-действиями.
- **Support safety surface** — `support check/info` и встроенный guard мутаций. `xml-gen` читает `Ext/ParentConfigurations.bin` и блокирует прямую XML-правку объектов типовой конфигурации на поддержке поставщика.

Для сопровождения самого инструмента в `xml-gen` есть диагностические oracle-команды. В обычных задачах генерации/правки XML они не нужны; справочник по ним вынесен отдельно: [references/behavioral-oracles.md](references/behavioral-oracles.md).

Детали — в §2 и под-skills. Универсальные команды (validate, form/template/help add, edit replace-text) описаны в §3.

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

> Универсальные команды (`xml-gen form add`, `template add`, `help add`, `edit replace-text`, `validate`, `support check/info`) описаны в §3 ниже и не имеют отдельного под-skill-а.

## §3 Универсальные команды

Пять групп: **validate** (структурная/семантическая проверка любого XML), **support check/info** (проверка состояния поддержки поставщика), **form/template/help add** (добавление форм, макетов, справки к любому объекту метаданных), **edit replace-text** (побайтовая замена без нормализации line endings).

Когда применять: validate — перед и после каждой модификации; support check/info — перед осознанной правкой типовой конфигурации на поддержке или в hook-ах; form/template/help add — когда нужно зарегистрировать новый артефакт без пересборки; edit replace-text — при точечной правке XML с мультилайн в `<v8:content>` (тултипы, описания) или любой замене, где важно сохранить line endings.

→ [references/universal-commands.md](references/universal-commands.md)

## §3.1 Диагностика инструмента

Oracle-команды предназначены для сопровождения `xml-gen` и проверки поведения на канонических XML, а не для обычной генерации одного артефакта. Детали, режимы и тестовые матрицы см. в [references/behavioral-oracles.md](references/behavioral-oracles.md).

## §4 Сквозные принципы

1. **Формат только Designer** — `--format designer` (default). EDT не поддерживается.
2. **Encoding** — UTF-8 с BOM (`utf-8-sig`). Сохраняй BOM при правке.
3. **Line endings** — CRLF между тегами, bare LF в `<v8:content>`. Не используй Claude Code Edit — `xml-gen edit replace-text` (→ [references/universal-commands.md](references/universal-commands.md)).
4. **Idempotency** — `validate` перед и после модификации. При ошибке `<domain> edit` делает rollback автоматически.
5. **Batch operations** — JSON-формат для `form edit` / `meta edit` / `subsystem edit` принимает массивы операций; используй вместо повторных вызовов CLI.
6. **EPF layout** — корневой XML: `output/MyProcessor.xml`. Формы EPF: `output/MyProcessor/Forms/MainForm/Ext/Form.xml`.
7. **Oracle sandboxing** — `xml-gen oracle ...` читает канон и пишет сгенерированные XML только под `--out`; не указывай oracle output внутрь `src/xml`.
8. **Vendor support guard** — команды мутации внутри `xml-gen` проверяют `Ext/ParentConfigurations.bin`: `G=1` блокирует всю конфигурацию, `f1=0` блокирует объект, удаление требует `f1=2`. Расширения CFE не блокируются.

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
xml-gen extension init output_ext/ МоёРасширение --config-path output/
xml-gen extension borrow output_ext/ output/ "Catalog.Товары"
xml-gen extension diff output_ext/ output/
```

Детали — [extension-operations/SKILL.md](extension-operations/SKILL.md).

### Проверить состояние поддержки перед правкой

```bash
xml-gen support info "src/Catalogs/Номенклатура.xml"
xml-gen support check "src/Catalogs/Номенклатура.xml" --require editable
xml-gen support check "src/Catalogs/Номенклатура.xml" --require removed --output json
```

`support check` завершает команду ошибкой, если мутация запрещена. Для удаления объекта сначала нужен явный перевод объекта в состояние off-support внешним согласованным действием; `xml-gen` не снимает поддержку неявно.

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

## §7 Дополнительные слои защиты (для агентов без PreToolUse)

Для агентов без PreToolUse-протокола (Codex, Cursor, Aider, Cline и др.) рекомендуется настроить дополнительные слои защиты:

- **Git pre-commit hook** (`tools/hooks/pre-commit`) — расширить вызовом `--check` по всем staged `.xml`/`.mxl` файлам. Это поздняя сетка: не даёт пройти в репозиторий даже если агент проигнорировал правило:
  ```bash
  python3 tools/hooks/block-direct-xml-edit.py --check "<staged-file>" --tool Edit
  ```
  При exit code `2` — файл относится к 1С metadata, коммит прерывается.
- **CI на PR** — тот же `--check` по diff отлавливает любые попытки прямой правки на входе в `main`.

Тонкая настройка: списки `ONEC_ROOT_DIRS`, `EXCLUDE_SUBSTRINGS`, `EXCLUDE_BASENAMES` задаются константами в `tools/hooks/block-direct-xml-edit.py`. Дополняй их, если в проекте появляется новый паттерн 1С-конфигурации (например, нестандартное расположение) или новый ложноположительный случай (build XML с уникальным именем).

## §8 Workarounds

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
