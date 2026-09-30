# Behavioral oracle xml-gen — справочник для сопровождения инструмента

Используй oracle-команды, когда нужно проверить поведение `xml-gen` на существующих канонических XML, а не просто сгенерировать один артефакт.

Oracle всегда пишет в sandbox и не должен перетирать `src/xml`.

```bash
# DSL round-trip: декомпилировать канон в JSON DSL, скомпилировать в отдельный sandbox и сравнить
xml-gen oracle mxl --source src/xml --out build/oracle --mode dsl

# CLI command reconstruction: построить и выполнить CommandPlan из публичных xml-gen команд
xml-gen oracle mxl --source src/xml --out build/oracle --mode cli

# Запустить оба независимых режима и отчитаться по ним отдельно
xml-gen oracle mxl --source src/xml --out build/oracle --mode both

# Широкий аудит _Демо по классам, где еще нет полного decompiler/CommandPlan oracle
xml-gen oracle demo --source src/xml --out build/oracle-demo --threads 8

# PredefinedData CLI-реконструкция через публичные meta-команды
xml-gen oracle predefined-data --source src/xml --out build/oracle-predefined-data

# ExchangePlanContent CLI-реконструкция через публичные meta-команды
xml-gen oracle exchange-plan-content --source src/xml --out build/oracle-exchange-plan-content
```

## MXL oracle

Два режима `oracle mxl` независимы:

- зеленый DSL round-trip не доказывает operational CLI command surface;
- зеленая CLI-реконструкция не доказывает побайтовый DSL round-trip.

Для `src/xml` MXL oracle по умолчанию берет `_Демо` pilot corpus; `--include-all` используй только для широкого аудита всех MXL `Template.xml`.

`mxl decompile` пишет редактируемую JSON-проекцию (`columns`, `areas`, `styles`, pictures и т.д.) плюс `losslessXmlBase64`. `mxl compile` сохраняет этот payload побайтово, чтобы неподдержанные Designer-секции не терялись при oracle round-trip.

Reports пишутся под выбранный `--out`: `oracle-report.json`, `coverage-matrix.json`, `xg-candidates.*`.

## PredefinedData oracle

`oracle predefined-data` — отдельный behavioral CLI oracle для `Ext/Predefined.xml`.

Он декомпилирует канон `PredefinedData` в JSON-дерево элементов, затем в sandbox выполняет только публичные команды:

- `config init`;
- `meta compile`;
- `meta edit --op add-predefined --value @items.json`;
- `validate --type xcf-body`.

Покрыты Catalog, ChartOfAccounts, ChartOfCalculationTypes, ChartOfCharacteristicTypes, включая пустой `<Code/>`, вложенные `ChildItems`, `Type`, `AccountType`, `AccountingFlags`, `ExtDimensionTypes`, `ActionPeriodIsBase`, `Displaced`.

## ExchangePlanContent oracle

`oracle exchange-plan-content` — отдельный behavioral CLI oracle для `ExchangePlans/<Name>/Ext/Content.xml`.

Он декомпилирует канон `ExchangePlanContent` в JSON-элементы, затем в sandbox выполняет только публичные команды:

- `config init`;
- `meta compile`;
- `meta edit --op add-exchange-content --value @items.json`;
- `validate --type xcf-body`.

Покрыты `Item/Metadata` и `Item/AutoRecord`.

## Demo oracle

`oracle demo` не заменяет полный behavioral decompiler.

Он параллельно запускает публичные `xml-gen validate --output json --level semantic` по `_Демо` XML и выполняет CLI registration checks для wrapper-артефактов дерева конфигурации:

- `Forms/<Name>.xml`;
- `Templates/<Name>.xml`;
- `Ext/Help.xml`.

Дополнительные проверки:

- Help-template registration smoke через `template add --type Help`;
- picture-body lossless oracle для `Ext/Picture.xml` с binary payload;
- синтетический `form-generation-edit` smoke через публичные команды формы: `form compile`, `form add-attribute`, `form add-command`, `form add-element`, `form move-element`, `validate`;
- локальный `EdtDerivedInvariantChecker`, основанный на EDT Xcore и `dt-project-checks`, без запуска EDT/v8-runner.

`form-generation-edit` проверяет generation invariants: `UserSettingsGroup` placeholders, table additions with `AdditionSource`, `ChildItems` placement, `AutoCommandBar`, `Popup`, `ButtonGroup`, button `Type=UsualButton`, `CommandName`.

XCF bodies без полной реконструкции остаются в `validation_only_no_decompiler`:

- `Ext/Content.xml`;
- `Ext/Predefined.xml`;
- `Ext/Aggregates.xml`;
- `Ext/Flowchart.xml` (`GraphicalSchema` / `http://v8.1c.ru/8.3/xcf/scheme`);
- `AppearanceTemplate`;
- help-template bodies.

Report классифицирует файлы по `artifactKind`, `capability`, `failureBucket` (`format_version`, `unknown_property`, `xDTO_read_error`, `bad_reference`, `undefined_type`, `missing_file`, `archive_or_directory_mode` и др.).

## EDT-derived invariants

`EdtDerivedInvariantChecker` работает локально по Designer XML и не вызывает EDT, `1cedtcli` или `v8-runner`.

Текущие проверки:

- form item `id`: отсутствует, `0`, дубликат, отрицательное значение как warning;
- form named elements: attributes / commands / parameters / items, с исключением для пустого `AutoCommandBar`;
- metadata reference integrity:
  - `Configuration/ChildObjects` -> наличие файлов объектов;
  - `CommonAttribute` metadata refs;
  - `ExchangePlanContent/Metadata` refs.

В `oracle demo` результат пишется в `details.edtDerivedInvariants`.
