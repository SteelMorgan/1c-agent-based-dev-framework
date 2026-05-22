# SPEC-004: XML Editor Commands

## Цель

Предоставить CLI-команды для **атомарных модификаций** существующих XML-файлов 1С (Form, EPF, Role, SKD) без ручного парсинга и StrReplace.
Каждая модификация автоматически проверяется валидатором (SPEC-003).

## Архитектура

```
CLI: xml-gen <type> <command> <args> <file>
                  │
            ┌─────┴─────┐
            │ Commands  │
            └─────┬─────┘
                  │
          ┌───────┴───────┐
          │  TypeEditor   │ (FormEditor, EpfEditor...)
          └───────┬───────┘
                  │
        1. XmlStructureReader.parse() → XmlDocument
        2. Modify XmlDocument tree (add/remove nodes)
        3. XmlDocumentWriter.write() → File
        4. Validator.validate() → OK/Rollback
```

## Компоненты

### 1. `XmlDocumentWriter`

Класс для записи `XmlDocument` обратно в файл.
- Сохраняет BOM (если был в исходном файле)
- Сохраняет XML declaration
- Сохраняет indentation (табуляция)
- Сохраняет порядок атрибутов (по возможности)
- Экранирует спецсимволы (`<`, `>`, `&`, `"`)

### 2. `IdAllocator`

Утилита для генерации уникальных ID в рамках документа.
- Читает все существующие `id="..."`
- Находит `max(id)`
- Выдаёт `max + 1`

### 3. Редакторы по типам

#### `FormEditor`

| Команда | Аргументы | Описание |
|---------|-----------|----------|
| `add-attribute` | `--name`, `--type`, `[--id]` | Добавить реквизит формы |
| `add-element` | `--type`, `--name`, `[--path]`, `[--parent]`, `[--after]` | Добавить UI-элемент |
| `add-command` | `--name`, `--action`, `--title` | Добавить команду формы |
| `remove-element` | `--name` | Удалить элемент по имени |
| `move-element` | `--name`, `--after`/`--before`/`--parent` | Переместить элемент |

#### `EpfEditor`

| Команда | Аргументы | Описание |
|---------|-----------|----------|
| `add-attribute` | `--name`, `--type`, `--synonym` | Добавить реквизит обработки |
| `add-tabular-section` | `--name`, `--synonym` | Добавить ТЧ (пока без колонок) |

#### `RoleEditor`

| Команда | Аргументы | Описание |
|---------|-----------|----------|
| `add-object` | `--name`, `--rights` (list/preset) | Добавить права на объект |
| `add-right` | `--object`, `--name`, `--value` | Добавить/изменить право |

#### `SkdEditor`

| Команда | Аргументы | Описание |
|---------|-----------|----------|
| `add-field` | `--dataset`, `--field`, `--path`, `--title` | Добавить поле в DataSet |
| `add-parameter` | `--name`, `--title`, `--type` | Добавить параметр |

## CLI Интерфейс

```bash
# Form
xml-gen form add-attribute --name IsFavorite --type boolean Form.xml
xml-gen form add-element --type CheckBoxField --name IsFavorite --path IsFavorite --parent Group1 Form.xml

# Role
xml-gen role add-object --name Catalog.Items --rights view Rights.xml
xml-gen role add-right --object Catalog.Items --name Insert --value true Rights.xml

# EPF
xml-gen epf add-attribute --name Employee --type CatalogRef.Employees --synonym "Сотрудник" epf.xml
```

## Авто-валидация и Rollback

1. Читаем файл в память (backup не нужен, так как держим в памяти).
2. Парсим в `XmlDocument`.
3. Модифицируем дерево в памяти.
4. Записываем во временный файл `file.tmp`.
5. Запускаем `Validator.validate(file.tmp)`.
6. Если OK → `mv file.tmp file`.
7. Если Fail → удаляем `file.tmp`, выводим ошибки, exit code 1.

## План разработки

1. **Phase 1: Core** (`XmlDocumentWriter`, `IdAllocator`, roundtrip tests)
2. **Phase 2: FormEditor** (самый востребованный)
3. **Phase 3: RoleEditor**
4. **Phase 4: SkdEditor**
5. **Phase 5: EpfEditor**
6. **Phase 6: CLI & Validation**

---

## Exit codes для edit-операций (TASK-155, 2026-05-22)

Контракт, действующий после патча TASK-155 (A2).

| Code | Meaning |
|------|---------|
| `0`  | Операция успешна, файл обновлён |
| `1`  | Бизнес/доменная ошибка (см. ниже) |
| `2`  | JVM/инфраструктурный сбой |

### Ситуации exit=1 (не JVM)

**Целевой объект не найден (`add-X` / `remove-Y` / `edit --op` на несуществующую сущность):**

```bash
xml-gen form remove-element --name НесуществующийЭлемент Form.xml
# → stderr: ERROR: Element 'НесуществующийЭлемент' not found
# → exit=1
```

**Дубликат (`add-X` с уже существующей сущностью):**

Следующие операции возвращают `exit=1` при попытке добавить дубликат:
- `form add-attribute` с тем же `--name`
- `form add-command` с тем же `--name`
- `epf add-form` с тем же именем формы
- `epf add-attribute` с тем же `--name`
- `epf add-tabular-section` с тем же `--name`
- `role add-object` с тем же именем объекта (используйте `role add-right` для добавления прав к существующему объекту)
- `role add-right` с уже существующим правом на тот же объект

```bash
xml-gen form add-attribute --name УжеЕсть --type string Form.xml
# → stderr: ERROR: Attribute 'УжеЕсть' already exists
# → exit=1
```

**Невалидный enum-значение:**

- `--right` в `role compile` / `role add-right` MUST быть из whitelist `RoleRight` (case-sensitive). Например, `"view"` (lowercase) → `exit=1`; корректно — `"View"`.
- `--type` в `epf add-attribute` обязателен; если отсутствует → `exit=1`.
- `--action` в `form add-command` обязателен; если отсутствует → `exit=1`.
- `--type` в `skd add-parameter` обязателен; если отсутствует → `exit=1`.

```bash
xml-gen role add-object --name Catalog.Товары --rights view Rights.xml
# → stderr: ERROR: Unknown RoleRight value: 'view' (expected one of: View, Read, Insert, ...)
# → exit=1
```

**Невалидный идентификатор 1С:**

- `--name` в `config init`, `epf init`, `extension init` MUST соответствовать regex `[A-Za-z_][A-Za-z0-9_]*`. Пробелы, кириллица и спецсимволы недопустимы.

```bash
xml-gen epf init --name "Bad Name!@#" output/
# → stderr: ERROR: Invalid 1C identifier: 'Bad Name!@#'. Must match [A-Za-z_][A-Za-z0-9_]*
# → exit=1
```

**JSON compile с отсутствующими обязательными полями:**

- `skd compile` с DSL без поля `dataSets` → `exit=1`
- `mxl compile` с DSL без хотя бы одного из `areas`/`columns`/`columnWidths`/`page` → `exit=1`

```bash
xml-gen skd compile empty.json Template.xml
# empty.json = {}
# → stderr: ERROR: dataSets field is required in SKD DSL
# → exit=1
```

**Rollback при ошибке валидации:**

После любой модификации файл автоматически валидируется. Если валидация вернула ERROR → изменения откатываются (файл остаётся в исходном состоянии) и выводятся ошибки. Временный `file.tmp` удаляется.

### Форма сообщения об ошибке

Все сообщения об ошибках записываются в `stderr` и начинаются с `ERROR: `. `stdout` в случае ошибки пуст.
