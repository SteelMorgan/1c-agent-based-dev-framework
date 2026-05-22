# Валидация: form-validate и epf-validate

## form-validate — Валидатор формы

Проверяет Form.xml управляемой формы на структурные ошибки: уникальность ID, наличие companion-элементов, корректность ссылок DataPath и команд.

### Использование

```
/form-validate <FormPath>
```

### Параметры

| Параметр  | Обязательный | По умолчанию | Описание                    |
|-----------|:------------:|--------------|-----------------------------|
| FormPath  | да           | —            | Путь к файлу Form.xml       |
| MaxErrors | нет          | 30           | Остановиться после N ошибок |

### Команда

```bash
xmlgen validate --type form "<FormPath>"
```

С JSON-отчётом:
```bash
xmlgen validate --type form --output json "<FormPath>"
```

> Реализация: Java-CLI `xmlgen validate` (замена Python-скрипта). Коды ошибок — `FORM-001..008` (структура), `FORM-101..120` (семантика).

### Выполняемые проверки

| # | Проверка | Серьёзность |
|---|---|---|
| 1 | Корневой элемент `<Form>`, version="2.17" | ERROR / WARN |
| 2 | `<AutoCommandBar>` присутствует, id="-1" | ERROR |
| 3 | Уникальность ID элементов (отдельный пул) | ERROR |
| 4 | Уникальность ID реквизитов (отдельный пул) | ERROR |
| 5 | Уникальность ID команд (отдельный пул) | ERROR |
| 6 | Companion-элементы (ContextMenu, ExtendedTooltip, и др.) | ERROR |
| 7 | DataPath → ссылается на существующий реквизит (с учётом резолва) | ERROR |
| 8 | CommandName кнопок → ссылается на существующую команду | ERROR |
| 9 | События имеют непустые имена обработчиков | ERROR |
| 10 | Команды имеют Action (обработчик) | ERROR |
| 11 | Не более одного MainAttribute | ERROR |
| 12 | BaseForm: наличие и version (при расширении) | OK / WARN |
| 13 | callType значения: Before, After, Override | ERROR |
| 14 | ID расширения >= 1000000 для добавленных attrs/commands | WARN |
| 15 | callType без BaseForm — некорректная структура | WARN |

### Резолв сложных DataPath

Проверка #7 выполняет многоступенчатый резолв пути перед поиском реквизита.

#### 1. Числовые индексы и UUID — silent-skip

Платформа генерирует непрозрачные DataPath, которые не разрешимы из Form.xml:

| Форма | Пример | Действие |
|-------|--------|----------|
| Числовой индекс | `10`, `1000003` | Пропустить без ошибки |
| UUID-ссылка | `1/0:a917a122-f663-4c45-8de0-fd5104007de3` | Пропустить без ошибки |

Шаблон пропуска: `^\d+$` или `^\d+/\d+:[0-9a-fA-F-]+$`.

> Инженерная задача: реализовать в `xmlgen validate` до разбора сегментов.

#### 2. `~<Attr>.*` — текущая строка динамического списка

Префикс `~` является короткой записью «текущая строка элемента». Применяется в связке с `DynamicList`:

```
~Список.Ссылка  →  корневой реквизит: Список
```

Алгоритм: снять `~`, разбить по `.`, взять первый сегмент как имя реквизита. Остальные сегменты — поля самого объекта списка; валидатор не проверяет их существование (они разрешаются платформой в runtime).

#### 3. `Items.<Table>.CurrentData.*` — поле из текущей строки таблицы

Обращение к текущей строке элемента-таблицы формы через коллекцию `Items`:

```
Items.Список.CurrentData.Ссылка
```

Алгоритм резолва:
1. Если первый сегмент — `Items`: ожидаемая форма `Items.<TableName>.CurrentData.<Field>`.
2. Найти элемент формы с тегом `Table` и именем `<TableName>`.
   - Не найден → ERROR: `table element '<TableName>' not found`.
3. Прочитать `DataPath` найденной таблицы; снять `[N]` и `~`.
   - Таблица без `DataPath` (возможно в динамических формах) → принять молча, без ошибки.
4. Взять первый сегмент полученного пути как корневой реквизит и проверить его наличие в `attrMap`.

Другие формы `Items.*` (не `Items.<T>.CurrentData`) → WARN: `unknown Items.* shape`.

#### 4. Общий порядок резолва (сводка)

```
DataPath
  ├─ числовой / UUID  →  silent-skip
  ├─ начинается с '~'  →  снять '~', перейти к п.5
  ├─ начинается с 'Items.'  →  резолв через элемент-таблицу (п.3)
  └─ иначе
5. Снять индексы [N], взять первый сегмент → проверить в attrMap
```

### Вывод

```
=== Validation: ФормаДокумента ===

[OK]    Root element: Form version=2.17
[OK]    AutoCommandBar: name='ФормаКоманднаяПанель', id=-1
[OK]    Unique element IDs: 96 elements
[OK]    Unique attribute IDs: 38 entries
[OK]    Unique command IDs: 5 entries
[OK]    Companion elements: 86 elements checked
[OK]    DataPath references: 53 paths checked
[OK]    Command references: 2 buttons checked
[OK]    Event handlers: 41 events checked
[OK]    Command actions: 5 commands checked
[OK]    MainAttribute: 1 main attribute

---
Total: 96 elements, 38 attributes, 5 commands
All checks passed.
```

Код возврата: 0 = все проверки пройдены, 1 = есть ошибки.

Проверки 12–15 активируются автоматически при обнаружении `<BaseForm>`.

Использовать после `/form-compile`, `/form-edit`, ручного редактирования Form.xml — для выявления структурных ошибок до сборки EPF.

---

## epf-validate — Валидатор внешней обработки (EPF/ERF)

Проверяет структурную корректность XML-исходников внешней обработки: корневую структуру, InternalInfo, свойства, ChildObjects, реквизиты, табличные части, уникальность имён, наличие файлов форм и макетов.

Скрипт также работает для внешних отчётов (ERF) — автоопределение по типу элемента.

### Использование

```
/epf-validate <ObjectPath>
```

### Параметры

| Параметр   | Обязательный | По умолчанию | Описание                                      |
|------------|:------------:|--------------|-------------------------------------------------|
| ObjectPath | да           | —            | Путь к корневому XML или каталогу обработки     |
| MaxErrors  | нет          | 30           | Остановиться после N ошибок                     |
| OutFile    | нет          | —            | Записать результат в файл (UTF-8 BOM)           |

`ObjectPath` авторезолв: если указана директория — ищет `<dirName>/<dirName>.xml`.

### Команда

```bash
xmlgen validate --type epf "<ObjectPath>"
```

С JSON-отчётом:
```bash
xmlgen validate --type epf --output json "<ObjectPath>"
```

> Реализация: Java-CLI `xmlgen validate` (замена Python-скрипта). Коды ошибок — `EPF-001..006` (структура), `EPF-007..010` (семантика: дубли, идентификаторы, Form.xml, GUID).

### Выполняемые проверки

| #  | Проверка                                              | Серьёзность  |
|----|-------------------------------------------------------|--------------|
| 1  | Root structure: MetaDataObject/ExternalDataProcessor   | ERROR        |
| 2  | InternalInfo: ClassId, ContainedObject, GeneratedType  | ERROR / WARN |
| 3  | Properties: Name (identifier), Synonym                 | ERROR / WARN |
| 4  | ChildObjects: допустимые типы, порядок                 | ERROR / WARN |
| 5  | Cross-references: DefaultForm → Form, AuxiliaryForm    | ERROR / WARN |
| 6  | Attributes: UUID, Name, Type                           | ERROR        |
| 7  | TabularSections: UUID, Name, GeneratedType, Attributes | ERROR / WARN |
| 8  | Уникальность имён (Attribute, TS, Form, Template, Command) | ERROR   |
| 9  | Файлы: формы (.xml + Ext/Form.xml), макеты            | ERROR        |
| 10 | Дескрипторы форм: корневая структура, uuid, Name, FormType | ERROR / WARN |

### Вывод

```
=== Validation: EPF.МояОбработка ===

[OK]    1. Root structure: MetaDataObject/ExternalDataProcessor, version 2.17
[OK]    2. InternalInfo: ClassId correct, 1 GeneratedType
[OK]    3. Properties: Name="МояОбработка", Synonym present, DefaultForm set
[OK]    4. ChildObjects: Attribute(3), TabularSection(1), Form(1)
[OK]    5. Cross-references: DefaultForm valid
[OK]    6. Attributes: 3 checked (UUID, Name, Type)
[OK]    7. TabularSections: 1 sections, 5 inner attributes
[OK]    8. Name uniqueness: 6 names, all unique
[OK]    9. File existence: 3 files verified
[OK]    10. Form descriptors: 1 checked

=== Result: 0 errors, 0 warnings ===
```

Код возврата: 0 = все проверки пройдены, 1 = есть ошибки.

### Когда использовать

- После `/epf-init`, добавления формы/макета, ручного редактирования XML — выявить структурные ошибки до сборки.
- При отладке сборки — найти причину ошибки Designer.
