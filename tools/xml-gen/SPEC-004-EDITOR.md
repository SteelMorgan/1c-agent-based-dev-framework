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
