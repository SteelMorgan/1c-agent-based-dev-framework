# SPEC-006 — `form compile --from-object`

Порт режима Shirokov `form-compile -FromObject` (см. `Nikolay-Shirokov/cc-1c-skills`, `.claude/skills/form-compile/scripts/form-compile.py`, апрельские коммиты 11–14.04.2026).

## 1. Область покрытия

Тип объекта берётся из XML-файла конфигурации (`<TypePlural>/<Name>/<Name>.xml`), назначение (`purpose`) выводится из OutputPath по русским маркерам в сегменте `Forms/<FormName>/…` (регистр игнорируется):

| Покрываемый тип (root элемент под `MetaDataObject`) | Секция пресета | Поддержанные purposes |
|---|---|---|
| `Catalog` | `catalog.*` | `item`, `folder`, `list`, `choice` |
| `Document` | `document.*` | `item`, `list`, `choice` |
| `DataProcessor` | `—` (только `default`) | `default` (форма-заготовка) |
| `Report` | `—` | `default` |
| `InformationRegister` | `informationRegister.*` | `record`, `list` |
| `AccumulationRegister` | `accumulationRegister.*` | `list` |
| `ChartOfCharacteristicTypes` | `chartOfCharacteristicTypes.*` (делегирует `catalog.*`, патчит типы, инжектит `ValueType`) | `item`, `folder`, `list`, `choice` |
| `ExchangePlan` | `exchangePlan.*` (делегирует `catalog.*`, патчит типы, инжектит `SentNo/ReceivedNo`) | `item`, `list`, `choice` |
| `ChartOfAccounts` | `chartOfAccounts.*` | `item`, `folder`, `list`, `choice` |

Определение `purpose` по имени папки формы (ищется в сегменте пути между `Forms/` и `/Ext/`):

| Маркер имени папки формы | Purpose |
|---|---|
| `ФормаСписка`, `ListForm`, `list` | `list` |
| `ФормаВыбора`, `ChoiceForm`, `choice` | `choice` |
| `ФормаГруппы`, `FolderForm`, `folder` | `folder` |
| `ФормаЗаписи`, `RecordForm`, `record` | `record` |
| иначе (включая `ФормаДокумента`, `ФормаЭлемента`, `ItemForm`) | `item` / `record` (см. матрицу типа: для `InformationRegister` без записи в имени — `list`) |

Матрица type → default purpose при неоднозначности указана в `FormFromObjectGenerator.resolveDefaultPurpose()`.

## 2. CLI

Команда `xml-gen form compile` расширяется двумя взаимоисключающими режимами:

```bash
# Режим 1 (существующий) — JSON DSL
xml-gen form compile [--format designer|edt] <Form.json> <Form.xml>

# Режим 2 (новый) — из метаданных объекта
xml-gen form compile --from-object [--preset <name>] [--preset-dir <path>] [--format designer|edt] <Form.xml>
```

Флаги:

| Флаг | Описание | Default |
|---|---|---|
| `--from-object` | Переключение в from-object режим | выкл |
| `--preset <name>` | Имя built-in пресета (без `.json`) | `erp-standard` |
| `--preset-dir <path>` | Явный корень для поиска project-level пресета (переопределяет авто-поиск вверх от OutputPath) | авто |
| `--object <path>` | Путь к XML-файлу объекта (если не указан — ищется подъёмом от OutputPath) | авто |

### Резолвинг пути к объекту

Из OutputPath вида `.../<TypePlural>/<ObjectName>/Forms/<FormName>/Ext/Form.xml` поднимаемся до сегмента `Forms/`, родитель этого сегмента — `<ObjectName>`, XML объекта лежит рядом: `<ObjectName>/<ObjectName>.xml` (EDT/Designer common layout).

### Резолвинг project-level пресета

От `OutputPath.getParent()` поднимаемся к корню ФС, в каждом каталоге проверяем `presets/skills/form/<preset>.json`. Первое найденное — применяется deep-merge поверх built-in. `--preset-dir` при наличии подменяет стартовую точку поиска.

### Резолвинг purpose

1. Берём сегмент пути `Forms/<FormName>/...` — вытаскиваем `<FormName>`.
2. Сопоставляем с таблицей выше.
3. Если не распознано — используем `resolveDefaultPurpose(type)`:
   - `Catalog`, `Document`, `ChartOfCharacteristicTypes`, `ExchangePlan`, `ChartOfAccounts` → `item`
   - `InformationRegister` → `record`
   - `AccumulationRegister` → `list`
   - `DataProcessor`, `Report` → `default`

## 3. Схема пресета

Root JSON object. Ключи верхнего уровня — `<type>.<purpose>` секции.

```json
{
  "name": "erp-standard",
  "description": "...",
  "document.item": { ... },
  "document.list": { ... },
  "catalog.item": { ... },
  ...
}
```

### Ключи секций (форма объекта — item/record/folder)

| Ключ | Назначение | Значения |
|---|---|---|
| `basedOn` | Наследовать поля из другой секции (рекурсивный deep merge) | имя секции |
| `header.position` | Где шапка | `insidePage` \| `abovePages` |
| `header.layout` | Колонки шапки | `1col` \| `2col` |
| `header.distribute` | Распределение атрибутов в 2col | `even` \| `left` \| `right` |
| `header.dateTitle` | Заголовок поля Date (Document) | строка |
| `header.right` | Явное включение атрибутов в правую колонку | `string[]` |
| `footer.fields` | Атрибуты, выносимые в подвал | `string[]` |
| `footer.position` | Позиция подвала | `insidePage` \| `belowPages` \| `none` |
| `tabularSections.container` | Куда складывать ТЧ | `pages` \| `inline` \| `single-no-pages` |
| `tabularSections.exclude` | Скрыть ТЧ | `string[]` |
| `tabularSections.lineNumber` | Колонка LineNumber в ТЧ | `boolean` |
| `additional.position` | Блок доп. атрибутов | `page` \| `below` \| `none` |
| `additional.layout` | Колонки доп. блока | `1col` \| `2col` |
| `additional.left` / `additional.right` | Явные списки | `string[]` |
| `additional.bspGroup` | Создавать `ГруппаДополнительныеРеквизиты` для BSP | `boolean` |
| `codeDescription.layout` | Code + Description | `horizontal` \| `vertical` |
| `codeDescription.order` | Порядок | `descriptionFirst` \| `codeFirst` |
| `parent.title` | Заголовок поля Parent | строка |
| `parent.position` | Позиция Parent | `beforeCodeDescription` \| `afterCodeDescription` \| `inHeader` |
| `owner.readOnly` | Owner readonly | `boolean` |
| `owner.position` | Позиция Owner | `first` |
| `fieldDefaults.ref.choiceButton` | Кнопка выбора для Ref | `boolean` |
| `fieldDefaults.boolean.element` | Тип элемента для Boolean | `check` |
| `commandBar` | Командная панель формы | `auto` \| `none` |
| `properties` | Свойства Form | Map<String,Object> |

### Ключи секций (список/choice)

| Ключ | Значения |
|---|---|
| `basedOn` | имя секции |
| `columns` | `"all"` \| `string[]` |
| `columnType` | `labelField` \| `input` |
| `hiddenRef` | `boolean` (скрытая колонка Ref с `UserVisible=false`) |
| `tableCommandBar` | `auto` \| `none` |
| `commandBar` | `auto` \| `none` |
| `choiceMode` | `boolean` (для choice-форм) |
| `properties` | Map<String,Object> |

## 4. Deep merge

Порядок (каждый шаг deep-merge'ится поверх предыдущего):

1. **Hardcoded defaults** — константный Map<String, Section> в `FormPresetLoader.defaults()`.
2. **Built-in preset** — `/presets/form/<name>.json` из classpath (resource).
3. **Project-level preset** — первый найденный вверх от OutputPath файл `presets/skills/form/<name>.json`.

**Алгоритм deep merge** (реализация: `FormPresetMerger.merge`):

- Если оба значения — Map → рекурсивно мерджить по ключам, overlay побеждает.
- Иначе overlay побеждает (скаляр, список → замена целиком).

**Resolve `basedOn`:** после всех merge проходим по секциям; если `basedOn` присутствует → deep-merge-им базу под текущую секцию, `basedOn` удаляем. Глубина наследования не ограничена (но без циклов — циклы игнорируем).

## 5. Генераторы

Вся генерация собирает Java-модель `FormDsl` (через билдер `DslBuilder`, см. §7) и затем отдаёт её существующему `FormWriter`. Это экономит сотни строк — не дублируется XML-эмиттер.

### `catalog.item`
- Шапка (`ГруппаШапка`, vertical):
  - Owner (если `meta.Owners != []`) с `readOnly=true`
  - Code+Description: если `codeDescription.layout=horizontal` и есть Code → горизонтальная `ГруппаКодНаименование` (порядок из `order`), иначе два поля последовательно
  - Parent (если `Hierarchical`) — до или после Code/Description согласно `parent.position`
  - Кастомные атрибуты (кроме попавших в `footer.fields`) — каждый через `fieldDefaults`
- ТЧ: по каждой не-исключённой `TabularSection` → `table` + `labelField LineNumber` (если `tabularSections.lineNumber`) + колонки
- Footer: поля из `footer.fields`
- BSP group: `ГруппаДополнительныеРеквизиты` (если `additional.bspGroup`)
- Properties: merge `p.properties` + `useForFoldersAndItems=Items` (если `HierarchyType==HierarchyFoldersAndItems`)
- Attributes: единственный `Объект` типа `CatalogObject.<Name>` с `main=true`

### `catalog.folder`
- Code (если Length>0), Description, Parent
- Properties: `useForFoldersAndItems=Folders` всегда; `windowOpeningMode=LockOwnerWindow` по умолчанию
- Attributes: `Объект` типа `CatalogObject.<Name>`

### `catalog.list`
- Колонки: Description → Code (если есть) → атрибуты → скрытая `Ссылка` (если `hiddenRef`)
- `table` с `commandBarLocation=None`, `tableAutofill=false`, `rowPictureDataPath=Список.DefaultPicture`
- Если `Hierarchical` → `initialTreeView=ExpandTopLevel`, `enableStartDrag/Drag=true`
- Attributes: `Список` (DynamicList, mainTable=`Catalog.<Name>`)

### `catalog.choice`
- Делегирует в `catalog.list` + `choiceMode=true` на таблице + `windowOpeningMode=LockOwnerWindow`

### `document.item`
- Парсинг preset: `header`, `footer`, `additional`, `tabularSections`, `ts_exclude`
- Классификация атрибутов (claim): footer → header.right → additional.left/right → unclaimed
- Распределение unclaimed по `header.distribute`
- `ГруппаНомерДата` (horizontal, внутри left) — Number (width=9, autoMaxWidth=false) + Date (title из `dateTitle`)
- Шапка: 1col или 2col (`ГруппаШапкаЛево` + `ГруппаШапкаПраво`)
- Тело: если нет visible ТЧ → плоская форма; иначе Pages (`ГруппаОсновное` + по странице на каждую ТЧ + `ГруппаДополнительно` если `additional.position==page`)
- Footer: `insidePage` (внутри `ГруппаОсновное`) или `belowPages` (после Pages)
- BSP group inside additional page или как отдельный `ГруппаДополнительныеРеквизиты`
- Attributes: `Объект` типа `DocumentObject.<Name>`

### `document.list`
- Колонки: Номер, Дата → атрибуты → hidden Ссылка
- DynamicList на `Document.<Name>`

### `document.choice`
- Делегирует в `document.list` + `windowOpeningMode=LockOwnerWindow`

### `informationRegister.record`
- Period (если Periodicity != Nonperiodical) → Dimensions → Resources → Attributes
- Все через `fieldDefaults`; ValueStorage скипаются
- Attributes: `Запись` типа `InformationRegisterRecordManager.<Name>`, `main=true`, `savedData=true`

### `informationRegister.list`
- Period (если periodic) → Recorder + LineNumber (если `WriteMode==RecorderSubordinate`) → Dimensions → Resources → Attributes
- Для Resources/Attributes с типом Boolean колонка создаётся как `check`, иначе `labelField`
- DynamicList на `InformationRegister.<Name>`

### `accumulationRegister.list`
- Всегда Period + Recorder + LineNumber
- Далее Dimensions, Resources (Boolean → check), Attributes
- DynamicList на `AccumulationRegister.<Name>`

### `chartOfCharacteristicTypes.*`
Делегирует в `catalog.*`, пост-патч:
- `CatalogObject.<Name>` → `ChartOfCharacteristicTypesObject.<Name>`
- `Catalog.<Name>` (в DynamicList.mainTable) → `ChartOfCharacteristicTypes.<Name>`
- Для `item`: инжект `ТипЗначения` (input, path=`Объект.ValueType`) сразу после `Наименование` или `ГруппаКодНаименование`

### `exchangePlan.*`
Делегирует в `catalog.*`, пост-патч:
- `CatalogObject.<Name>` → `ExchangePlanObject.<Name>`
- mainTable: `ExchangePlan.<Name>`
- Для `item`: инжект `НомерОтправленного` и `НомерПринятого` (readOnly) после наименования

### `chartOfAccounts.*`
Полноценная отдельная реализация (не делегирует!):
- `item`: Code (+ Parent если hierarchical) → Description → `Забалансовый` (check на `OffBalance`) → `ГруппаПризнакиУчета` из `AccountingFlags` (checkboxes) → таблица `ВидыСубконто` (ExtDimensionType + TurnoversOnly + ExtDimensionAccountingFlags) → Attributes → ТЧ
- `folder`: Code, Description, Parent (если hierarchical); `useForFoldersAndItems=Folders`
- `list`/`choice`: делегируют в `catalog.*` и патчат `ChartOfAccounts.<Name>`
- Attributes для item/folder: `Объект` типа `ChartOfAccountsObject.<Name>`, `savedData=true`

### `dataProcessor.default` / `report.default`
Минимальная форма: `Объект` типа `DataProcessorObject.<Name>` (или `ReportObject.<Name>`), `main=true`; `title=Synonym` + свойство `autoTitle=false`. Нет элементов — чистая заготовка.

## 6. Инварианты / guardrails

- **Skip ValueStorage**: `isDisplayableType(t)` возвращает `false`, если в строке типа есть `ValueStorage`, `v8:ValueStorage` или `ХранилищеЗначения`. Такие атрибуты не попадают ни в шапку, ни в ТЧ, ни в колонки списков.
- **Throw on invalid runtime types**: если атрибут/resource/dimension/TS-колонка имеет тип из `KNOWN_INVALID_TYPES` (`FormDataStructure`, `FormDataCollection`, `FormDataTree`, …), генератор кидает `FromObjectException` с именем атрибута и подсказкой (см. список в `FormFromObjectGenerator.INVALID_TYPES`).
- **Number/Date в списках документов** — всегда присутствуют (по XML `StandardAttribute.Number/Date`, не по реквизитам).
- **UserVisible для скрытой Ref**: добавляется свойство `userVisible=false` на DSL-колонке → FormWriter эмитит `<UserVisible><xr:Common>false</xr:Common></UserVisible>`.
- **Guard — на входе**: guardrails живут на этапе парсинга метаданных объекта (`ObjectMetaReader`), ДО построения DSL — не на уровне `FormDsl`, т.к. в DSL такие строки невалидны даже синтаксически. Это соответствует поведению Shirokov-скрипта.

## 7. План реализации (Java)

### Новые классы

| Класс | Пакет | Назначение |
|---|---|---|
| `FormPreset` | `io.github.onec.xmlgen.form.preset` | POJO корневого пресета (Map<String, Section>) |
| `FormPresetSection` | `io.github.onec.xmlgen.form.preset` | Секция (Map<String,Object> внутри) |
| `FormPresetLoader` | `io.github.onec.xmlgen.form.preset` | hardcoded defaults + built-in resource + project-level файл |
| `FormPresetMerger` | `io.github.onec.xmlgen.form.preset` | deep-merge + resolve-basedOn |
| `ObjectMetaReader` | `io.github.onec.xmlgen.form.fromobject` | Парсинг XML объекта → `ObjectMeta` |
| `ObjectMeta` | `io.github.onec.xmlgen.form.fromobject` | POJO с полями Type, Name, Synonym, Attributes, TabularSections, Dimensions, Resources, CodeLength, DescriptionLength, Hierarchical, HierarchyType, Owners, Periodicity, WriteMode, AccountingFlags, ExtDimensionAccountingFlags, MaxExtDimensionCount |
| `FromObjectException` | `io.github.onec.xmlgen.form.fromobject` | Ошибки guard и парсинга |
| `PurposeResolver` | `io.github.onec.xmlgen.form.fromobject` | OutputPath → purpose |
| `FormFromObjectGenerator` | `io.github.onec.xmlgen.form.fromobject` | Главная точка входа — метод `generate(objectPath, outputPath, presetName, presetDir) → FormDsl` |
| `DslBuilder` | `io.github.onec.xmlgen.form.fromobject` | Fluent-билдер для элементов DSL (group/input/table/page/pages/labelField/check) — возвращает `Map<String,Object>` согласованные с существующим `FormWriter` |
| `CatalogFormGenerator` | `io.github.onec.xmlgen.form.fromobject.generator` | item/folder/list/choice |
| `DocumentFormGenerator` | `.generator` | item/list/choice |
| `InformationRegisterFormGenerator` | `.generator` | record/list |
| `AccumulationRegisterFormGenerator` | `.generator` | list |
| `ChartOfCharacteristicTypesFormGenerator` | `.generator` | делегат к Catalog + патч |
| `ExchangePlanFormGenerator` | `.generator` | делегат к Catalog + патч |
| `ChartOfAccountsFormGenerator` | `.generator` | item/folder/list/choice |
| `DataProcessorFormGenerator` | `.generator` | default-заглушка |

### Новый resource

`tools/xml-gen/src/main/resources/presets/form/erp-standard.json` — 1:1 портирован из Shirokov.

### Изменения в существующих классах

- `io.github.onec.xmlgen.cli.Commands.formCompile` — новый парсинг: `--from-object`, `--preset`, `--preset-dir`, `--object`.

### Тесты

1. `FormPresetMergerTest` — deep merge + basedOn.
2. `PurposeResolverTest` — разбор путей `.../Catalogs/Контрагенты/Forms/ФормаСписка/Ext/Form.xml` и др.
3. `ObjectMetaReaderTest` — парсинг Catalog/Document/InformationRegister из фикстур.
4. `FromObjectCatalogItemTest` — end-to-end: фикстура Catalog XML + OutputPath → `Form.xml` валиден (`FormValidator` зелёный), содержит ожидаемые элементы.
5. `FromObjectValueStorageTest` — ValueStorage атрибут скипается.
6. `FromObjectInvalidTypeTest` — FormDataStructure в атрибуте бросает `FromObjectException` в `ObjectMetaReader` (guard на входе, до сборки DSL).

## 8. Known limitations этого порта

- В первой версии `meta.Owners/HierarchyType/AccountingFlags` парсятся, но фичи `header.right`, `additional.left/right` реализованы только частично (на путь из Shirokov Python-реализации — как в скрипте версии 13.04).
- Sync-skill навыка `form-dsl` в `framework/` отдельным патчем упоминает поддержку `--from-object` (см. CLAUDE.md, после изменения файла `framework/skills/.../form-dsl/SKILL.md` запускается `python3 tools/sync-skill.py ...`).
- Purposes без явного маркера для `Catalog` (редкий путь без `ФормаЭлемента/List/Choice/Folder`) → по умолчанию `item`. Пользователь должен именовать папку формы по стандарту.
