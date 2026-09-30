# xml-gen

`role compile` supports typed per-right RLS through
`objects[].restrictions[{"right":"Read","condition":"..."}]`. Restrictions are
emitted as `restrictionByCondition` under the exact granted right; malformed,
unknown, duplicate or ungranted bindings fail before any output is created.
Conditional `#Если/#ИначеЕсли/#Иначе/#КонецЕсли` directives are validated as an
ordered nested structure, including branch order and required `#Тогда`.

For an atomic same-name SKD `DataSetQuery` to `DataSetObject` migration with exact
direct-filter cleanup, use `xml-gen skd convert-dataset-type <SchemaPath> --json
<payload.json> [--dry-run]`. The command preserves non-target XML bytes, BOM, line
endings and file mode, and fails without mutation on selector/type/count/reference
mismatch. Query-to-Object conversion preserves the required existing `dataSource`
exactly and replaces only `query` with `objectName`; no default source is invented.

Java CLI для генерации, редактирования, инспекции и валидации XML-артефактов 1С из компактных JSON DSL и точечных команд.

Главная роль инструмента — дать AI-агентам и скриптам управляемый способ менять выгрузку 1С без ручной правки больших XML-файлов.

## Возможности

- Генерация конфигурации, EPF/ERF, объектов метаданных, форм, ролей, MXL, SKD, подсистем, шаблонов и справки.
- Точечные мутации существующих XML: формы, роли, SKD, Configuration.xml, подсистемы, CommandInterface, расширения, шаблоны.
- Инспекция и декомпиляция: `info` для основных типов, `form decompile`, `mxl decompile`, SKD query/fields/variant modes.
- Валидация XML собственными валидаторами `xml-gen`: structure/semantic checks, text/json output, autodetect по root element.
- Работа с расширениями CFE: `init`, `borrow`, `diff`, `patch-method`, `validate`.
- Guard-логика для конфигураций на поддержке поставщика: `support check|info`.
- Oracle-режимы для проверки поведения на каноническом корпусе XML и майнинга структурных правил.
- Byte-safe замена текста: сохраняет BOM и окончания строк, поддерживает dry-run/backup/validate.
- Форматы вывода Designer и частично EDT там, где это реализовано writers/layout.

## Требования

- JDK 17+
- Gradle wrapper из каталога `tools/xml-gen/`

## Сборка

```bash
cd tools/xml-gen
./gradlew build
```

Fat JAR создаётся в `build/libs/xml-gen-0.1.1-SNAPSHOT.jar`.
Архив собирается reproducibly: порядок ZIP entries фиксирован, file timestamps не
сохраняются. Поэтому две clean-сборки одинаковых исходников должны иметь одинаковый
byte SHA-256; built=installed gate сравнивает именно SHA файла, а не только содержимое
entries.

## Быстрый Старт

```bash
# Справка / версия
xml-gen --help
xml-gen --version

# Новая выгрузка конфигурации
xml-gen config init src/xml МояКонфигурация --compat Version8_3_24 --format-version 2.20

# Внешняя обработка / внешний отчёт
xml-gen epf init --format designer --name МояОбработка output/
xml-gen epf init --type report --with-skd --name МойОтчет output/

# Объект метаданных из JSON DSL с регистрацией в Configuration.xml
xml-gen meta compile catalog.json src/xml/

# Форма из JSON DSL или из метаобъекта
xml-gen form compile form.json Forms/Форма/Ext/Form.xml
xml-gen form compile --from-object --object Catalogs/Товары.xml Forms/Форма/Ext/Form.xml

# Валидация
xml-gen validate --type form Forms/Форма/Ext/Form.xml
xml-gen validate --output json --src-root src/xml src/xml/Configuration.xml
```

## Команды

### `config`

- `config init <outputDir> <name>` — создать `Configuration.xml`, `ConfigDumpInfo.xml`, язык.
- `config info <Configuration.xml> [--mode overview|brief|full]`.
- `config edit <Configuration.xml> --op <operation> --value <value>`:
  `modify-property`, `add-childObject`, `remove-childObject`, `add-defaultRole`,
  `remove-defaultRole`, `set-defaultRoles`.
- `config validate <Configuration.xml|configDir>`.

`config init` принимает `--synonym`, `--version`, `--vendor`, `--lang-name`, `--lang-code`,
`--compat <Version8_3_NN>`, `--format-version <2.NN>`.

### `meta`

- `meta compile <jsonPath> <configRoot>` — создать объект метаданных и зарегистрировать его в `Configuration.xml`.
- `meta edit <objectPath> --op <operation> --value <value>` — точечные операции.
- `meta edit <objectPath> --batch <file.json> [--dry-run]` — атомарный пакет мутаций;
  `--dry-run` выполняет тот же preflight и показывает фактическое число операций без записи XML.
- `meta edit <objectPath> --op normalize-runtime-attributes` — нормализация реквизитов.
- `meta info <Object.xml> [--mode brief|overview|full]`.
- `meta validate <Object.xml>`.
- `meta remove <configDir> <Type.Name> [--dry-run] [--keep-files] [--force]`.

`meta compile` берёт версию формата из `<configRoot>/Configuration.xml`, пишет объект и связанные файлы вроде
`Ext/Predefined.xml`, затем добавляет объект в `ChildObjects`.

Для прямых полей регистров (`dimensions`, `resources`, `attributes`) shorthand-флаги
`| indexing` / `| index` и object-form `"indexing": true|false|"DontIndex"|"Index"`
типизированы и компилируются в Designer `Indexing`. Для прямого `Attribute` также
поддерживается `IndexWithAdditionalOrder` / `| indexadditional`; для `Dimension` и
`Resource` это значение отклоняется до создания файлов. Если свойство не задано,
эмитится канонический default `DontIndex`. `meta validate` требует `Indexing` у каждого
прямого поля регистра и отклоняет неизвестные либо неприменимые enum-значения с кодом
`INVALID_FIELD_INDEXING`.

Те же typed-значения `indexing` поддерживаются реквизитами табличных
частей хранимых объектов: `Index`, `IndexWithAdditionalOrder` и default
`DontIndex`. Для `Report` / `DataProcessor` не-default indexing отклоняется до
создания файлов и изменения `Configuration.xml`. `meta validate` проверяет
наличие и exact enum у реквизитов ТЧ хранимых объектов.

Для object-form строкового поля доступно `"allowedLength": "Variable"|"Fixed"`;
пропущенное свойство даёт `Variable`. Неизвестное значение или применение
к нестроковому типу fail-closed с кодом `INVALID_STRING_ALLOWED_LENGTH`;
validator проверяет `<v8:StringQualifiers>/<v8:AllowedLength>` по той же матрице.

Для числового object-form реквизита `"nonneg": true` эмитит
`<v8:AllowedSign>Nonnegative</v8:AllowedSign>`, но `MinValue` остаётся каноническим
`<MinValue xsi:nil="true"/>`. Typed `MinValue=0` несовместим с Designer XDTO
`anyType`; `meta validate` отклоняет его с кодом `INVALID_ATTRIBUTE_MIN_VALUE`
как для прямых реквизитов, так и для реквизитов табличных частей.

Перед созданием файлов и изменением `Configuration.xml` команда проверяет имена прямых
`Attribute` / `Dimension` / `Resource` по стандартным полям конкретного типа объекта.
Сравнение регистронезависимое и поддерживает английские и русские платформенные имена:
например, `Recorder` / `Регистратор` запрещены у регистров, но `Регистратор` допустим у
справочника; `Дата` запрещена у документа, но допустима у независимого регистра сведений.
`meta validate` применяет ту же матрицу к существующему XML и сообщает object/path/name.
Стабильный код ошибки для автоматизированных проверок — `RESERVED_PLATFORM_FIELD_NAME`.
Реквизиты табличных частей имеют отдельное пространство имён и не смешиваются с прямыми
стандартными полями объекта.

Для регистров матрица учитывает свойства подтипа. `RecordType` / `ВидДвижения`
зарезервирован у balance-регистра накопления и у регистра бухгалтерии без
корреспонденции, но не у turnover/correspondence вариантов. Поля периодов действия и
базового периода регистра расчёта активируются соответственно `ActionPeriod` и
`BasePeriod`; `ExtDimension1..3` / `ExtDimensionType1..3` проверяются у регистра
бухгалтерии. Writer и validator используют одну матрицу и один порядок Designer.

Поддерживаемые семейства операций `meta edit`: `add`, `remove`, `modify` для реквизитов, табличных частей,
измерений, ресурсов, значений перечислений, предопределённых элементов, колонок, свойств и связанных секций.
В expanded JSON вложенное добавление реквизитов существующей табличной части задаётся как
`modify.tabularSections.<ИмяТЧ>.add`; поддерживаются shorthand-строки и object-form с `name`, `type`,
`synonym`, `fillChecking`, `allowedSign`, `after` или `before`. Повторное применение — byte-NO-OP.

Пример:

```bash
xml-gen meta edit src/xml/Catalogs/Договоры.xml --op add-predefined \
  --value "Лизинг|Договор лизинга;;Субаренда"
```

### `epf`

- `epf init --name <name> [--type processor|report] [--with-skd] <outputDir>`.
- `epf add-form --epf <name> --name <formName> [--default] <outputDir>`.
- `epf add-template --epf <name> --name <templateName> --type <type> <outputDir>`.
- `epf add-attribute <file> --name <name> --type <type>`.
- `epf add-tabular-section <file> --name <name>`.
- `epf bsp-init <epfPath> --kind <kind> [--target <Type.Name>]`.
- `epf bsp-add-command <epfPath> --id <id> --label <label> [--type <type>] [--form <FormName>]`.

### `form`

- `form compile <form.json> <Form.xml>`.
- `form compile --from-object [--object <Object.xml>] [--preset <name>] <Form.xml>`.

Form DSL принимает `type: "checkbox"` как канонический alias для
`CheckBoxField` в любой глубине, включая `Table.columns`; legacy `check` также
поддерживается. Lower-camel ключи form-level events (`onCreateAtServer`,
`onOpen` и т. п.) нормализуются в case-exact Designer enum (`OnCreateAtServer`, `OnOpen`).
Корневые `properties` эмитятся в XDTO sequence независимо от порядка ключей JSON;
объектно-зависимые свойства (`UsePostingMode`, `RepostOnWrite` и т. п.) размещаются после
`CommandSet`. `form validate` отклоняет неизвестные lower-case event names с кодом `FORM-129`
(регистр имён событий для платформы не значим, XG-126), а
нарушение порядка корневых XDTO-свойств — с кодом `FORM-130`.

BUG-013 reject-only domain применяется одной таблицей до сериализации DSL и при
`validate --type form`: `WindowOpeningMode` принимает только `LockOwnerWindow` /
`LockWholeInterface`, `AutoSaveDataInSettings` — `Use`, `SaveDataInSettings` — `UseList`,
`UsePostingMode` — `Auto`. Designer defaults `Modeless`, `DontUse`, `Postings` кодируются
отсутствием property/tag; явный default или alias даёт `FORM-131`. `ExcludedCommand`
Имена четырёх governed DSL properties принимаются только в exact lowerCamel; любой
case-variant (например, `WindowopeningMode`) отклоняется до output. XML validator аналогично
отклоняет case-variant тега вместо exact Designer UpperCamel.
проверяется по main attribute: `DocumentObject` разрешает
`Copy, Write, Post, UndoPosting, SetDeletionMark, Delete`, document-backed `DynamicList` —
`Create, Copy, Delete, SetDeletionMark, Post, UndoPosting`; unknown/context-invalid command
даёт `FORM-132`. Writer hard-fail до создания output и никогда не нормализует/не удаляет
значения молча.
- `form decompile <Form.xml> [output.json]`.
- `form info <Form.xml> [--limit N] [--offset N]`.
- `form add <objectXml> <formName> [--synonym <syn>] [--default]`.
- `form remove <objectXml> <formName>`.
- `form edit <Form.xml> --json <spec.json>` — JSON-спецификация мутаций с rollback для формы и BSL stub.
- `form add-attribute`, `form add-element`, `form add-command`, `form remove-element`, `form move-element`.

Мутации форм используют diff-gate: существующие ошибки формы не блокируют точечную правку, но новые ошибки после правки блокируются.

### `role`

- `role compile <role.json> <outputDir>`.
- `role info <Rights.xml> [--show-denied]`.
- `role add-object <Rights.xml> --name <Object> --rights Read,View`.
- `role add-right <Rights.xml> --object <Object> --name <Right> --value true|false`.

### `mxl`

- `mxl compile <mxl.json> <Template.xml>`.
- `mxl decompile <Template.xml> [output.json]`.
- `mxl info <Template.xml> [--with-text]`.

### `skd`

- `skd compile <skd.json> <Template.xml> [--include-base <dir>]`.
- `skd info <Template.xml> [--mode overview|query|fields|variant|...] [--raw] [--outfile <file>]`.
<!--++agent TASK-204 [11.07.2026 00:01:00]-->
- `skd replace-from-file <target Template.xml> --source <source.xml> --expect-target-sha <sha256> --state-dir <external-dir> [--backup <path>] [--dry-run]` — diagnostics-only atomic full-artifact replacement.
- `skd restore-from-manifest <manifest.json> --state-dir <same-external-dir>` — exact restore активной транзакции.
<!--++agent TASK-204-->
- `skd add-parameter`, `skd add-field`.
- `skd edit <SchemaPath> <operation> "<value>" [--dataSet <name>] [--variant <name>] [--no-selection]`.
- `skd import-templates <SchemaPath> --json <payload.json> [--dry-run]` — точечный upsert DSL-областей либо exact-import donor AreaTemplate и привязок.
- `skd patch-template-parts <SchemaPath> --json <payload.json> [--dry-run]` — atomic/lossless batch точных expression и group binding без сериализации AreaTemplate.
- `skd upsert-structure <SchemaPath> --json <payload.json>` — lossless upsert именованных групп структуры варианта.
<!--++agent TASK-174 [11.07.2026 21:57:04]-->
- `skd upsert-dataset <SchemaPath> --json <payload.json> [--dry-run]` — atomic/lossless upsert целых `DataSetQuery`/`DataSetObject` из exact XML или donor СКД.
<!--++agent TASK-174-->
- `skd remove-components <SchemaPath> --json <payload.json> [--dry-run]` — atomic/lossless удаление точных structure/binding/link/root-dataset/root-dataset-field целей.
- `skd rename-dataset-field <SchemaPath> --json <payload.json> [--dry-run]` — atomic/lossless rename direct-пары `dataPath`/`field` выбранного root dataset.
//++agent TASK-174 [10.07.2026 22:10:00]
- `skd upsert-dataset-link <SchemaPath> --json <payload.json>` — atomic/lossless upsert отношений наборов.
//--agent TASK-174

`skd edit` поддерживает операции `add-field`, `modify-field`, `remove-field`, `set-field-role`,
`add-parameter`, `modify-parameter`, `remove-parameter`, `rename-parameter`, `reorder-parameters`,
`add-total`, `remove-total`, `modify-structure`, `set-query`, `patch-query`,
`clear-conditionalAppearance`. No-op операции не переписывают файл.

<!--++agent TASK-204 [11.07.2026 00:01:00]-->
`skd replace-from-file` предназначена только для контролируемой диагностики и recovery,
когда требуется временно подменить существующий SKD целым проверенным артефактом. Это не
обычный редактор: команда не объединяет схемы и не сериализует source заново. До записи она
проверяет SHA текущей цели, XML root/namespace и полный semantic SKD validator для target/source.
`--expect-target-sha` обязателен и защищает от stale overwrite. По умолчанию операция backup-free:
она не создаёт backup/manifest и допускает следующий replace с актуальным SHA. Для durable restore
нужно явно передать `--backup <external-path>` вне Designer metadata tree; корень определяется
по ближайшему `Configuration.xml` вверх от target (для standalone-дерева — каталог target), а
существующий parent backup канонизируется с разрешением symlink. Существующий backup никогда не
перезаписывается. Между preflight и созданием файла остаётся обычное filesystem TOCTOU-окно при
враждебном переименовании уже канонизированного внешнего каталога; штатная работа предполагает,
что вызывающий контролирует внешний backup-каталог.
Backup содержит точные исходные байты, а source переносится byte-for-byte через temp в каталоге
цели и atomic move. Обязательный `--state-dir` должен быть заранее созданным внешним каталогом,
не внутри Designer metadata tree; symlink и `..` отклоняются, POSIX mode принудительно `0700`.
Canonical target path хешируется SHA-256: `<key>.lock`, `<key>.manifest.json` и restored archives
живут только в state-dir. Поэтому один target разделяет стабильный lock inode между процессами,
а разные targets изолированы. Lock остаётся пустым mode `0600`. Непосредственно перед commit target
SHA перечитывается под lock. На POSIX сохраняются mode и, по возможности,
owner/group; на non-POSIX portable mode-гарантии нет. File и parent directory синхронизируются там,
где filesystem поддерживает directory fsync. В выводе фиксируются old/new/backup SHA-256 и manifest.

До activation во внешнем state-dir создаётся `<key>.manifest.json` со state `PREPARED`,
target/source/backup, original/replacement SHA, mode и timestamps. После activation state становится
`SWAPPED`. Любой новый replace при активном manifest отклоняется. Восстановление выполняется только
отдельной командой, которая проверяет current replacement SHA и original backup SHA, атомарно возвращает
original, фиксирует `RESTORED` и архивирует manifest:

```bash
xml-gen skd replace-from-file Reports/X/Templates/Main/Ext/Template.xml \
  --source /tmp/baseline.xml --expect-target-sha "$CURRENT_SHA" \
  --backup /tmp/current-exact.xml --state-dir /tmp/xg84-state

xml-gen skd restore-from-manifest \
  /tmp/xg84-state/<target-key>.manifest.json --state-dir /tmp/xg84-state
```

После recovery SHA цели и POSIX mode должны точно совпасть с исходными. Если процесс оборвался после
activation, manifest остаётся в `PREPARED`/`SWAPPED` и restore остаётся однозначным. Ошибка restore
сохраняет manifest и backup для повторной recovery-попытки. Legacy sibling lock/manifest/archive
в metadata tree диагностируются и блокируют операцию до внешней миграции/удаления после проверки.
`--dry-run` выполняет весь preflight, но не создаёт backup/manifest или запись цели; lock во внешнем
state-dir может быть создан. В target dir temp существует только под lock и удаляется до возврата.
Platform Designer build/load остаётся отдельной
обязательной проверкой между swap и restore: локальный validator не заменяет XDTO платформы.
<!--++agent TASK-204-->

`skd import-templates` принимает подмножество canonical templates DSL и не пересобирает
datasets, параметры, варианты настроек и соседние области:

```json
{
  "templates": [{
    "name": "ДетальнаяСтрока",
    "rows": [["{Сумма}"]],
    "parameters": [{"name": "Сумма", "expression": "Сумма"}]
  }],
  "groupTemplates": [{
    "groupName": "Детали",
    "templateType": "Header",
    "template": "ДетальнаяСтрока"
  }]
}
```

Область обновляется по `name`, привязка — по сочетанию `groupName`/`groupField` и
`templateType`. Допустимые типы: `Header`, `OverallHeader`, `GroupHeader`, `Footer`,
`OverallFooter`. Каждая привязка обязана ссылаться на область, уже существующую в СКД
или присутствующую в том же payload. Повтор одинакового payload является NO-OP. Запись выполняется атомарно
с сохранением UTF-8 BOM, Designer CRLF и bare LF внутри `v8:content`.

Для существующей сложной AreaTemplate, которую нельзя lossless выразить сокращённым
DSL, тот же atomic batch принимает exact selector. `sourceFile` разрешается относительно
payload JSON, `sourceName` выбирает ровно один root `template`, а в перенесённом subtree
меняется только текст direct `name`:

```json
{
  "ifAbsent": "fail",
  "exactTemplates": [{
    "sourceFile": "donor/Template.xml",
    "sourceName": "Макет7",
    "targetName": "МакетДеталиОпераций"
  }],
  "groupTemplates": [{
    "groupName": "ГруппировкаДеталиОпераций",
    "templateType": "Header",
    "template": "МакетДеталиОпераций"
  }]
}
```

`ifAbsent=fail|noop` управляет отсутствующим source selector; неоднозначный source,
duplicate target и занятое отличающимся subtree target-имя всегда являются ошибкой.
Повтор exact-import идентичного subtree — byte-NO-OP. Donor framing (indent и EOL)
обязан совпадать с target: команда не нормализует exact subtree скрытым образом.

<!--++agent TASK-174 [11.07.2026 11:00:00]-->
`skd patch-template-parts` предназначена для более узкой правки уже существующих ручных
AreaTemplate. Команда не передаёт rows/cells/appearance writer-у: у параметра меняется только
текст direct `dcsat:expression`, а при его отсутствии добавляется только этот direct child.
Template и parameter выбираются по точным именам; неоднозначность всегда является ошибкой.

```json
{
  "ifAbsent": "fail",
  "parameterExpressions": [
    {"template": "Макет1", "parameter": "Итог", "expression": "НовыйИтог"}
  ],
  "groupBindings": [
    {
      "action": "replace",
      "groupName": "СтараяГруппа",
      "templateType": "Header",
      "template": "Макет1",
      "newGroupName": "НоваяГруппа",
      "newTemplateType": "Header",
      "newTemplate": "Макет1"
    },
    {"action": "remove", "groupName": "ЛишняяГруппа", "templateType": "Header", "template": "Макет2"},
    {"action": "upsert", "groupName": "Детали", "templateType": "Header", "template": "МакетДетали"}
  ]
}
```

Selector binding — точная тройка `groupName/templateType/template`. Для `replace`
`newGroupName` можно опустить (тогда имя группы сохраняется), но `newTemplateType` и
`newTemplate` обязательны. `remove` и `upsert` разрешаются как один конечный набор, поэтому
dependency-safe замена binding не проходит через промежуточную коллизию. Финальный preflight
требует ровно один AreaTemplate и ровно одну именованную settings structure group для каждой
привязки; одинаковые `groupName/templateType` отклоняются как collision. Допустимые типы:
`Header`, `OverallHeader`, `GroupHeader`, `Footer`, `OverallFooter`.

Весь JSON применяется атомарно. `ifAbsent: "noop"` пропускает отсутствующие expression/remove
selectors; default `fail` останавливает batch без записи. Повтор уже применённого `replace` и
`upsert` возвращает byte-NO-OP. `--dry-run` выполняет тот же preflight. Запись через sibling temp
и atomic move сохраняет BOM, CRLF/LF и mode исходного файла.
<!--++agent TASK-174-->

<!--++agent TASK-174 [12.07.2026 00:28:00]-->
`skd upsert-structure` адресует существующие variant/dataset по имени и рекурсивно
добавляет именованные top-level `StructureItemGroup` либо lossless обновляет exact-one
существующую именованную группу на любой глубине, не меняя её parent/path. Если identity
встречается более одного раза, операция fail-closed без записи. Поля
`groupItems` и `order[].field` обязаны существовать в указанном dataset. Direct `selection`
может дополнительно содержать уникальные поля datasets, достижимых от указанного dataset по
цепочке `dataSetLink`; отсутствующее, неоднозначное, несвязанное или повторённое поле отклоняется
до мутации;
`details` обозначает платформенный `GroupItemAuto` (детальные записи). Имена всех
элементов payload уникальны. При update меняются только явно переданные `groupItems`,
`order`, `selection`, `outputParameters` и именованные `children`; filter/
conditionalAppearance и прочие неадресованные узлы сохраняются byte-exact.

Для управляемых коллекций действует tri-state контракт: отсутствующее поле или `null`
сохраняет существующий узел; явный `selection: []`, `groupItems: []`, `order: []`
или `outputParameters: []`
удаляет только соответствующий direct-узел группы; непустой список заменяет его целиком.
Элементы `order` имеют форму `{"field":"Дата","direction":"Asc"}`; допустимы только
`Asc` и `Desc`, повтор одного поля отклоняется. `children: []` не удаляет существующие
дочерние группы: непустой `children` рекурсивно upsert-ит только именованные группы.

`outputParameters` адресуется только на уже существующей exact-one группе: отсутствующая группа,
duplicate identity, повтор direct-блока или параметра отклоняют весь batch. Каждый элемент имеет
`parameter`, scalar `value`, `valueType` (QName либо тип пространства `dcsset` без префикса) и
опциональный `use`. Значения `use: true` и omitted канонически не сериализуются; `false` пишется явно.

Опциональный `topLevelOrder` задаёт **полный** порядок всех direct named structure items
выбранного варианта, включая chart/table и группы, добавляемые тем же payload. Политика
fail-closed: отсутствующее, повторённое, безымянное или не перечисленное top-level identity
останавливает весь batch без записи. При перестановке каждый structure subtree переносится
как исходный byte span; неперечисленные settings-узлы остаются на своих местах.

```json
{
  "variant": "Основной_1",
  "dataSet": "ДвиженияКапитала",
  "items": [{
    "kind": "group",
    "name": "ШапкаТаблицыОпераций",
    "outputParameters": [{
      "parameter": "ВариантИспользованияГруппировки",
      "valueType": "DataCompositionGroupUseVariant",
      "value": "AdditionalInformation",
      "use": true
    }]
  }, {
    "kind": "group", "name": "ГруппировкаВидДвиженияКапитала",
    "children": [{
      "kind": "group",
      "name": "ГруппировкаДеталиДвиженийКапитала",
      "order": [
        {"field": "КапиталДата", "direction": "Asc"},
        {"field": "ДокументВводаВывода", "direction": "Asc"}
      ]
    }]
  }],
  "topLevelOrder": [
    "ИнформацияОПортфеле", "ШапкаТаблицыМонет", "ГруппировкаМонета",
    "ШапкаТаблицыОпераций", "ГруппировкаИнструменты",
    "ГруппировкаВидДвиженияКапитала", "Диаграмма"
  ]
}
```

`--dry-run` выполняет тот же полный preflight и semantic validation через временный файл,
но не заменяет schema. Повтор уже применённого payload возвращает byte-NO-OP; BOM,
CRLF/LF и mode сохраняются.
<!--++agent TASK-174-->

`skd remove-components` сначала разрешает весь batch, затем удаляет только найденные byte spans.
Selector `dataSetFields` адресует только прямое объявление поля выбранного корневого набора и не
обходит settings/resources/links/templates рекурсивно:

```json
{
  "dataSetFields": [{
    "dataSet": "ДвиженияКапитала",
    "type": "DataSetObject",
    "dataPath": "Аккаунт"
  }]
}
```

`dataSet` и `dataPath` обязательны, `type` — необязательный fail-closed guard. Exact-one preflight
отклоняет неоднозначное поле, несовпадающий тип и отсутствующую цель при `ifAbsent: "fail"`;
`ifAbsent: "noop"` разрешает повторный byte-NO-OP.
Structure item адресуется точным `variant` и рекурсивным `path`; каждый сегмент — direct child.
Binding адресуется полной идентичностью `groupName` и/или `groupField`, `templateType` и `template`.
Mapping `dataSetLink` адресуется совместимой с upsert identity: `sourceDataSet`,
`destinationDataSet`, `sourceExpression` и optional `parameter`. Optional
`destinationExpression` усиливает селектор до строгого совпадения; без него несколько подходящих
mapping отклоняются как неоднозначные.
Root `dataSet` адресуется точным `name`; optional `type` (`DataSetQuery`, `DataSetObject`,
`DataSetUnion`) служит fail-closed guard. Перед записью команда проверяет уже спланированный
результат всего batch и отклоняет удаление, если имя dataset остаётся в link, settings,
template, resource/expression или другом XML-узле. Поэтому связанные компоненты можно удалить
в том же batch, но случайное создание висячей ссылки невозможно.
AreaTemplate команда не удаляет. Неоднозначная цель и удаление последней top-level структуры варианта
всегда отклоняются. По умолчанию отсутствующая цель — ошибка; `"ifAbsent":"noop"` явно разрешает
повтор как byte-NO-OP. `--dry-run` выполняет тот же preflight и не записывает файл.

`skd rename-dataset-field` меняет только текст двух direct child-узлов точного
`DataSetFieldField`; settings, resources, links, templates и sibling declarations не обходятся
и не обновляются. `oldField` можно опустить, тогда он равен `oldDataPath`. Политика ссылок всегда
явная `referencePolicy: "preserve"` (и является default): cascade update должен выполняться
отдельными доменными операциями.

```json
{
  "ifAbsent": "fail",
  "referencePolicy": "preserve",
  "fields": [{
    "dataSet": "ДвиженияКапитала",
    "type": "DataSetObject",
    "oldDataPath": "Аккаунт",
    "oldField": "Аккаунт",
    "dataPath": "КапиталАккаунт",
    "field": "КапиталАккаунт"
  }]
}
```

До формирования результата команда проверяет exact-one dataset/direct field, optional dataset
type, old physical field, уникальность source/target selectors и collision нового `dataPath`.
Весь batch атомарен. Если old-пара отсутствует, но exact new-пара уже существует, повтор является
byte-NO-OP; иначе поведение задаёт `ifAbsent: "fail"|"noop"`. `--dry-run` выполняет preflight без
записи. Запись сохраняет UTF-8 BOM, line endings и mode через temp + semantic validation + atomic move.

```json
{
  "ifAbsent": "fail",
  "structureItems": [{"variant":"Основной", "path":["ПоОрганизации","Детали"]}],
  "groupTemplates": [{
    "groupName":"Детали", "templateType":"Header", "template":"ДетальнаяСтрока"
  }],
  "dataSetLinks": [{
    "sourceDataSet":"ОсновнойНабор", "destinationDataSet":"Детали",
    "sourceExpression":"Организация", "destinationExpression":"Организация"
  }],
  "dataSets": [{"name":"Детали", "type":"DataSetObject"}]
}
```

```json
{
  "variant": "Основной",
  "dataSet": "Продажи",
  "items": [{
    "kind": "group", "name": "ПоОрганизации", "groupItems": ["Организация"],
    "children": [{
      "kind": "group", "name": "ДеталиПродаж", "groupItems": ["details"],
      "selection": ["Дата", "Сумма"]
    }]
  }]
}
```

Повтор идентичного payload — NO-OP без перезаписи. Неизвестный variant/dataset,
тип элемента, поле или duplicate identity отклоняются до создания результата;
запись выполняется через temp, validation и atomic move с сохранением BOM/line endings.

<!--++agent TASK-174 [11.07.2026 21:57:04]-->
`skd upsert-dataset` переносит целый root `dataSet` как exact byte span, не
сериализуя соседние datasets, settings и ручные AreaTemplates. Источник можно
задать общим `sourceFile` (полная donor `DataCompositionSchema` или файл с одним
root `dataSet`) либо полем `xml` конкретного элемента:

```json
{
  "mode": "upsert",
  "ifAbsent": "fail",
  "sourceFile": "../donor/Template.xml",
  "dataSets": [
    {"name": "ИсторияОпераций", "type": "DataSetQuery"}
  ]
}
```

`name` и `type` (`DataSetQuery`/`DataSetObject`) обязательны и должны exact
совпадать у selector и source. `mode: "upsert"` вставляет отсутствующий набор или
заменяет существующий того же типа; одноимённый набор другого типа является
collision. `mode: "replace"` требует существующую цель, а `ifAbsent: "noop"`
разрешает её отсутствие как byte-NO-OP. Item-level `sourceFile` переопределяет
общий, но у каждого item допустим ровно один источник: `xml` или `sourceFile`.

До записи разрешается весь batch: проверяются duplicate identities, exact-one
source/target, тип, обязательные `query/dataSource` или `objectName`, наличие
root dataSource и все оставшиеся `dataSetLink`/field references. Framing exact
фрагмента обязан совпадать с indent/EOL целевого Designer XML — команда не
нормализует источник скрытно. Повтор exact payload не переписывает файл;
`--dry-run` выполняет тот же preflight. Temp + semantic validation + atomic move
сохраняют BOM, CRLF/LF, mode и все неадресованные byte spans.
<!--++agent TASK-174-->

//++agent TASK-174 [10.07.2026 22:10:00]
`skd upsert-dataset-link` адресует mapping по `source` + `destination` +
`sourceExpression` + optional `parameter`. Каждый mapping сериализуется отдельным
canonical sibling `dataSetLink`; отсутствующие в payload sibling mappings сохраняются.
`destinationExpression` и `parameterListAllowed` являются изменяемыми значениями mapping:

```json
{
  "links": [{
    "source": "ОсновнойНабор",
    "destination": "Детали",
    "mappings": [
      {"sourceExpression": "Организация", "destinationExpression": "Организация"},
      {"sourceExpression": "Склад", "destinationExpression": "Склад",
       "parameter": "ПараметрСвязи", "parameterListAllowed": false}
    ]
  }]
}
```

Наборы и поля проверяются до мутации; self-link, пустые/повторные mapping identities и повторная
endpoint identity в `links[]` отклоняются. Соседние datasets, mappings, links, templates, bindings и settings остаются
байтово неизменными. Повтор идентичного payload — NO-OP без перезаписи.
//--agent TASK-174

### `subsystem` и `interface`

- `subsystem compile <jsonPath> <outputDir> [--parent <Subsystem.xml>] [--no-stubs]`.
- `subsystem info <Subsystem.xml> [--mode brief|overview|full|tree|ci]`.
- `subsystem edit <Subsystem.xml> --op add-content|remove-content|add-child|remove-child|set-property --value <value>`.
- `subsystem validate <Subsystem.xml|subsystemDir>`.
- `interface edit <CommandInterface.xml> --op hide|show|place|set-order|set-subsystem-order|set-group-order --value <value>`.
- `interface validate <CommandInterface.xml>`.

`config edit` и `interface edit` сначала валидируют preview и не пишут файл, если правка добавляет новые ошибки.

### `template` и `help`

- `template add --object Type.Name --name T --type TemplateType [--synonym S] [--src dir] [--set-main-dcs] <configDir>`.
- `template remove --object Type.Name --name T [--src dir] <configDir>`.
- `template add-help --object Type.Name [--lang ru] [--src dir] <configDir>`.
- Legacy-форма: `template add|remove <objectXml> <templateName>`.
- Для корня конфигурации: `template add <Configuration.xml> --name T --type binary --input <payload>`
  создаёт `CommonTemplates/T.xml` + byte-exact `CommonTemplates/T/Ext/Template.bin` и регистрирует
  `CommonTemplate.T`; `template remove <Configuration.xml> --name T` удаляет их идемпотентно.
- `help add <objectXml> [--lang ru]`.

### `extension`

- `extension init <outputDir> <name> [--synonym S] [--prefix P] [--purpose P] [--compat V] [--version V] [--vendor V] [--config-path path] [--no-role]`.
- `extension validate <extensionPath>`.
- `extension borrow <extensionPath> <configPath> <objectSpec> [--borrow-main-attribute form|all]`.
- `extension diff <extensionPath> <configPath> [--mode A|B]`.
- `extension patch-method <extensionPath> --module <path> --method <name> --type Before|After|Instead|ModificationAndControl [--config <baseConfig>] [--context <ctx>] [--function]`.

### `validate`

Общая команда:

```bash
xml-gen validate [--type <type>] [--format designer|edt] [--level structure|semantic] \
  [--output text|json] [--src-root <path>] <file> [files...]
```

Поддерживаемые типы: `form`, `role`, `skd`, `mxl`, `epf`/`erf`, `meta`, `config`,
`extension`, `subsystem`, `interface`, `client-interface`, `template`, `xcf-body`,
`platform-xsd`. Если `--type` не указан, тип определяется по root element.

Важно: это собственные валидаторы `xml-gen`, а не XSD/JAXB-валидация платформенных схем.
`platform-xsd` — легкий слой подсказок по фактам, перенесенным из XSD-delta; он не заменяет
полную проверку платформенной XSD.

### `support`

- `support info <path> [--require editable|removed] [--output text|json]`.
- `support check <path> [--require editable|removed] [--output text|json]`.

Команда проверяет состояние поддержки поставщика и используется как guard перед мутациями.

### `edit`

```bash
xml-gen edit replace-text <file> --old "old" --new "new" [--all] [--dry-run] \
  [--backup] [--validate] [--encoding utf-8-sig|utf-8]
```

Можно указывать несколько пар `--old/--new`. Алиасы: `--search/--replace`.

### `oracle`

- `oracle mxl --source <src> --out <dir> [--mode dsl|cli|both] [--include-all]`.
- `oracle demo --source <src> --out <dir> [--threads N] [--include-mxl]`.
- `oracle predefined-data --source <src> --out <dir>`.
- `oracle exchange-plan-content --source <src> --out <dir>`.
- `oracle mine-rules --source <src> --out <dir> [--min-support N] [--digest-limit N] [--disposition rules.json]`.

Oracle-режимы нужны для регрессий на реальных выгрузках: сравнение канонических XML, классификация расхождений,
поиск структурных правил и пробелов покрытия.

### XSD coverage delta

В `scripts/xsd_coverage_delta.py` есть отдельный audit-helper для сравнения платформенных XSD из
`namespace-forest` с тем, что реально видно в исходниках `xml-gen`.

```bash
tools/xml-gen/scripts/xsd_coverage_delta.py \
  --forest /path/to/namespace-forest \
  --xmlgen-root tools/xml-gen \
  --version latest \
  --out-dir tools/xml-gen/build/xsd-coverage-delta
```

Опционально можно добавить произвольный корпус XML-выгрузок, чтобы отделить теоретические пробелы XSD
от форматов, которые реально встречаются в проектах:

```bash
tools/xml-gen/scripts/xsd_coverage_delta.py \
  --forest /path/to/namespace-forest \
  --xmlgen-root tools/xml-gen \
  --version latest \
  --corpus-root /path/to/src/xml \
  --out-dir tools/xml-gen/build/xsd-coverage-delta
```

Отчет показывает namespace delta, глобальные элементы/типы/enum-ы и required-атрибуты, которые не найдены
в литералах, валидаторах, writer-ах и oracle-коде `xml-gen`. При `--corpus-root` добавляется corpus delta:
что из XSD отсутствует в `xml-gen`, но наблюдается в реальных XML-файлах.

Выявленные XSD/corpus gaps перенесены в валидаторный слой:

- `client-interface` валидирует `Ext/ClientApplicationInterface.xml`.
- `platform-xsd` распознает XSD-only namespaces и CMI root `section`/`group`/`command`,
  включая required-атрибуты, найденные в `namespace-forest`.

## Архитектура

```text
JSON DSL / CLI operation
  -> DSL models / command parser
  -> model helpers / editors / writers
  -> XML files + directory layout
  -> validators / oracle reports
```

Основные пакеты:

- `cli/` — entry point и диспетчер команд.
- `dsl/` — Jackson POJO для JSON DSL.
- `writer/` — генерация XML и scaffold файлов.
- `editor/` — точечные мутации существующих XML и byte-safe операции.
- `validator/` — DOM-парсер, типовые валидаторы, text/json reporters.
- `info/` — инспекция и декомпиляция XML в краткие/полные представления.
- `form/` — JSON-мутации форм, генерация формы по объекту, presets.
- `oracle/` — поведенческие проверки на каноническом корпусе.
- `support/` — guard для объектов на поддержке поставщика.
- `model/` — резолвинг типов, путей метаданных, UUID/ID, служебные модели.
- `format/` — Designer/EDT layout.

## Тесты

```bash
cd tools/xml-gen
./gradlew test
```

В проекте 115 test-файлов: CLI-contract тесты, writer/editor/validator тесты, info/decompile тесты,
oracle тесты, support guard и round-trip проверки. Все тесты используют временные каталоги и не должны
оставлять следы в рабочем дереве.

## Зависимости

- `io.github.1c-syntax:mdclasses:0.17.4` — enum-ы и модели 1С.
- `io.github.1c-syntax:bsl-common-library:0.9.2` — типы и квалификаторы 1С.
- `jackson-databind` — JSON DSL.
- `lombok` — boilerplate в моделях.
- `junit5`, `assertj` — тесты.

## Exit Codes

| Code | Meaning | When |
|------|---------|------|
| `0` | Success | Операция выполнена без ошибок. |
| `1` | Business/domain error | Невалидный ввод, дубль, неизвестное право, отсутствующий объект, ошибка валидации. |
| `2` | JVM/infrastructure failure | Сбой JVM, OOM, отсутствующий jar и т.п. |

По умолчанию ошибки печатаются как `ERROR: <message>`. Для stack trace используйте `--debug`
в любом месте командной строки или `XML_GEN_DEBUG=1`.

## Ограничения

- `xml-gen` не генерирует JAXB-модели из XSD и не выполняет XSD-валидацию платформенных схем.
- Часть XML всё ещё создаётся/редактируется writer-ами и текстовыми splice-операциями; риск снижается
  локальными валидаторами, preview/diff-gate, rollback и oracle-тестами, но это не эквивалент полной
  проверки платформой 1С.
- EDT поддерживается неравномерно: перед использованием проверяйте конкретную команду и тесты.

## Лицензия

LGPL-3.0.
