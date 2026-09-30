# Templates — макеты и встроенная справка объектов метаданных

Управление макетами и встроенной справкой объектов метаданных 1С (Справочник, Документ, Отчёт, Обработка и др.).

Реализовано в `xml-gen` (Java): команды `template add`, `template remove`, `template add-help` доступны из CLI.
Для внешних обработок/отчётов (EPF/ERF) — см. §1 основного SKILL.md, там есть `epf add-template`.

---

## Команда: template add

Добавляет макет указанного типа к объекту метаданных и регистрирует его в `ChildObjects` корневого XML.

### Синтаксис

```bash
xml-gen template add \
  --object <Type.ObjectName> \
  --name <TemplateName> \
  --type <TemplateType> \
  [--synonym <Synonym>] \
  [--src <SrcDir>] \
  [--set-main-dcs] \
  <configDir>
```

### Параметры

| Параметр | Обязательный | По умолчанию | Описание |
|----------|:------------:|--------------|----------|
| `--object` | да | — | Тип и имя объекта: `Catalog.Контрагенты`, `Document.ЗаказКлиента`, `Report.ОстаткиТоваров` и т.д. |
| `--name` | да | — | Имя создаваемого макета |
| `--type` | да | — | Тип макета (см. таблицу типов) |
| `--synonym` | нет | = `--name` | Синоним макета (отображаемое имя) |
| `--src` | нет | `src` | Каталог с исходниками внутри configDir |
| `--set-main-dcs` | нет | — | Принудительно установить `MainDataCompositionSchema` (только для Report) |
| `configDir` | да | — | Корневой каталог конфигурации (где лежит `Configuration.xml`) |

### Типы макетов

| Пользователь указывает | `--type` | Расширение файла | Содержимое |
|------------------------|----------|-----------------|------------|
| HTML, html-документ | `HTMLDocument` | `.html` | Пустой HTML |
| Text, текст, txt | `TextDocument` | `.txt` | Пустой файл |
| SpreadsheetDocument, MXL, табличный документ | `SpreadsheetDocument` | `.xml` | Минимальный SpreadsheetDocument |
| BinaryData, двоичные данные, bin | `BinaryData` | `.bin` | Пустой файл |
| DataCompositionSchema, СКД, схема компоновки | `DataCompositionSchema` | `.xml` | Минимальная DCS-схема |

### Поддерживаемые типы объектов

Catalog, Document, Report, DataProcessor, InformationRegister, AccumulationRegister, AccountingRegister, CalculationRegister, ChartOfCharacteristicTypes, ChartOfAccounts, ChartOfCalculationTypes, BusinessProcess, Task, ExchangePlan.

### Примеры

```bash
# Добавить MXL-макет (печатная форма) к документу
xml-gen template add \
  --object Document.ЗаказКлиента \
  --name ПФ_Счёт \
  --type SpreadsheetDocument \
  src/

# Добавить СКД к отчёту конфигурации и установить как основную схему
xml-gen template add \
  --object Report.ОстаткиТоваров \
  --name ОсновнаяСхема \
  --type DataCompositionSchema \
  --set-main-dcs \
  src/

# Добавить HTML-макет к справочнику
xml-gen template add \
  --object Catalog.Номенклатура \
  --name WebШаблон \
  --type HTMLDocument \
  src/
```

### Что создаётся

```
<SrcDir>/<ObjectType>/<ObjectName>/Templates/
├── <TemplateName>.xml              # Метаданные макета (UUID, синоним, тип)
└── <TemplateName>/
    └── Ext/
        └── Template.<ext>          # Содержимое макета
```

### Что модифицируется

- `<SrcDir>/<ObjectType>/<ObjectName>.xml` — добавляется `<Template>TemplateName</Template>` в конец `ChildObjects`
- Только для Report + `DataCompositionSchema`: заполняется `MainDataCompositionSchema` (если пуст или указан `--set-main-dcs`)

### Конвенция именования

Для макетов печатных форм (тип `SpreadsheetDocument`) применяй префикс `ПФ_`:

| Контекст | Формат имени | Пример |
|----------|-------------|--------|
| Печатная форма | `ПФ_<КраткоеИмя>` | `ПФ_Счёт`, `ПФ_М11`, `ПФ_СчётФактура` |
| Прочие макеты (загрузка, настройки, служебные) | Без префикса | `МакетЗагрузки`, `НастройкиПечати` |

Если пользователь назвал макет без префикса, но контекст — печатная форма, добавь `ПФ_` автоматически и сообщи об этом.

---

## Команда: template remove

Удаляет макет и очищает его регистрацию в `ChildObjects` корневого XML.

### Синтаксис

```bash
xml-gen template remove \
  --object <Type.ObjectName> \
  --name <TemplateName> \
  [--src <SrcDir>] \
  <configDir>
```

### Параметры

| Параметр | Обязательный | По умолчанию | Описание |
|----------|:------------:|--------------|----------|
| `--object` | да | — | Тип и имя объекта: `Catalog.Контрагенты` и т.д. |
| `--name` | да | — | Имя удаляемого макета |
| `--src` | нет | `src` | Каталог с исходниками |
| `configDir` | да | — | Корневой каталог конфигурации |

### Пример

```bash
xml-gen template remove \
  --object Document.ЗаказКлиента \
  --name ПФ_Счёт \
  src/
```

### Что удаляется

```
<SrcDir>/<ObjectType>/<ObjectName>/Templates/<TemplateName>.xml   # Метаданные макета
<SrcDir>/<ObjectType>/<ObjectName>/Templates/<TemplateName>/      # Каталог содержимого (рекурсивно)
```

### Что модифицируется

- `<SrcDir>/<ObjectType>/<ObjectName>.xml` — убирается `<Template>TemplateName</Template>` из `ChildObjects`
- Если удалённый макет был указан в `MainDataCompositionSchema` — значение очищается

---

## Команда: template add-help

Добавляет встроенную справку к объекту метаданных: файл-дескриптор `Help.xml` и HTML-страницу.

### Синтаксис

```bash
xml-gen template add-help \
  --object <Type.ObjectName> \
  [--lang <lang>] \
  [--src <SrcDir>] \
  <configDir>
```

### Параметры

| Параметр | Обязательный | По умолчанию | Описание |
|----------|:------------:|--------------|----------|
| `--object` | да | — | Тип и имя объекта: `Catalog.Контрагенты` и т.д. |
| `--lang` | нет | `ru` | Код языка справки |
| `--src` | нет | `src` | Каталог с исходниками |
| `configDir` | да | — | Корневой каталог конфигурации |

### Пример

```bash
xml-gen template add-help \
  --object Catalog.Контрагенты \
  src/
```

### Что создаётся

```
<SrcDir>/<ObjectType>/<ObjectName>/Ext/
├── Help.xml                        # Дескриптор справки (список языков)
└── Help/
    └── ru.html                     # HTML-страница справки
```

### Что модифицируется

- Если у объекта есть формы — в `Forms/<FormName>.xml` добавляется `<IncludeHelpInContents>false</IncludeHelpInContents>` (если отсутствует). Это включает кнопку справки в `AutoCommandBar` формы.
- Справка **не регистрируется** в `ChildObjects` — достаточно наличия файлов.

### После создания

Отредактируй вручную `Ext/Help/ru.html` — наполни содержимым:
```html
<h1>Контрагенты</h1>
<h2>Назначение</h2>
<p>Справочник хранит информацию о контрагентах...</p>
```
Используй стандартные теги: `<h1>...<h4>`, `<p>`, `<ul>`, `<table>`.

---

## Интеграция с другими командами

После добавления MXL-макета — заполни его содержимым через `mxl compile`:

```bash
# 1. Добавить макет
xml-gen template add \
  --object Document.ЗаказКлиента \
  --name ПФ_Счёт \
  --type SpreadsheetDocument \
  src/

# 2. Заполнить содержимое через MXL DSL
xml-gen mxl compile invoice.json \
  src/Documents/ЗаказКлиента/Templates/ПФ_Счёт/Ext/Template.xml
```

Для получения списка существующих макетов используй:

```bash
xml-gen meta info src/Documents/ЗаказКлиента
```

---

## Правильно / Неправильно

```bash
# Неправильно — не указан тип объекта (только имя)
xml-gen template add --object ЗаказКлиента --name ПФ_Счёт --type SpreadsheetDocument src/

# Правильно — полный путь Type.Name
xml-gen template add --object Document.ЗаказКлиента --name ПФ_Счёт --type SpreadsheetDocument src/
```

```bash
# Неправильно — для Report без --set-main-dcs при уже существующей MainDataCompositionSchema
xml-gen template add --object Report.Продажи --name НоваяСхема --type DataCompositionSchema src/
# Результат: MainDataCompositionSchema не перезапишется (сохранится старое значение)

# Правильно — явно указать флаг для перезаписи
xml-gen template add --object Report.Продажи --name НоваяСхема --type DataCompositionSchema --set-main-dcs src/
```
