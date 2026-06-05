---
name: extension-operations
description: "Use for создания расширений конфигурации (CFE), заимствования объектов, генерации перехватчиков методов и анализа состава расширения. Helps управлять CFE через xml-gen extension init/borrow/diff/validate."
---

# Extension Operations (CFE)

Работа с расширениями конфигурации 1С.

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно создать расширение | `extension init <output_dir> <Name> [--config-path <configPath>]` |
| Нужно заимствовать объект из конфигурации | `extension borrow <extPath> <configPath> "Type.Name"` |
| Нужно заимствовать форму | `extension borrow <extPath> <configPath> "Catalog.Name.Form.FormName"` |
| Нужно добавить реквизит и вывести его на форму типовой | `extension borrow ... "Type.Name.Form.X" --borrow-main-attribute form` |
| Нужно сгенерировать перехватчик метода | `extension patch-method <extPath> --module "Catalog.X.Form.Y" --method "ПриЗаписи" --type Before` |
| Нужно проанализировать расширение | `extension diff <extPath> <configPath>` |
| Нужно проверить расширение | `extension validate <extPath>` |

## Команды

### extension init

Создать расширение конфигурации.

```bash
xml-gen extension init <output_dir> <Name> [--config-path <configPath>] [--purpose Patch|Customization|AddOn] [--prefix <Prefix>]
```

**Параметры (позиционные):**
- `<output_dir>` — каталог, куда будет создано расширение (первый позиционный аргумент)
- `<Name>` — имя расширения (второй позиционный аргумент)

**Параметры (флаги):**
- `--config-path` — путь к базовой конфигурации (для чтения CompatibilityMode, DefaultLanguage и Language UUID). Без него — `[WARN] Language ExtendedConfigurationObject set to zeros`.
- `--purpose` — назначение (по умолчанию: Customization)
- `--prefix` — префикс имён (по умолчанию: из имени с суффиксом `_`)

**Антипаттерн (старый синтаксис):** `--name <Name>` и `--config <Path>` НЕ работают — CLI принимает имя и output как позиционные, а флаг для базовой конфигурации называется `--config-path`.

### extension borrow

Заимствование объекта из базовой конфигурации.

```bash
xml-gen extension borrow <extensionPath> <configPath> "<objectSpec>"
```

**Формат objectSpec:**
- `Catalog.Товары` — заимствовать объект
- `Catalog.Товары.Form.ФормаЭлемента` — заимствовать форму
- `Справочник.Товары` — русские синонимы поддерживаются
- `Catalog.Товары ;; Document.Заказ` — batch (разделитель `;;`)

### extension diff

Анализ расширения: состав, перехватчики, проверка переноса.

```bash
xml-gen extension diff <extensionPath> <configPath> [--mode A|B]
```

- **Mode A** (по умолчанию): список объектов [BORROWED]/[OWN], BSL-перехватчики, анализ форм.
- **Mode B**: поиск `&ИзменениеИКонтроль`, проверка `#Вставка`/`#КонецВставки`, сверка с базовой конфигурацией.

### extension borrow --borrow-main-attribute

Без флага заимствованная форма не имеет DataPath — `form-edit` упадёт при попытке вывести реквизит расширения.

```bash
# Реквизиты, уже выведенные на форме (рекомендуется)
xml-gen extension borrow <extPath> <configPath> "Catalog.Номенклатура.Form.ФормаЭлемента" --borrow-main-attribute form

# Все реквизиты и табличные части объекта
xml-gen extension borrow <extPath> <configPath> "Catalog.Номенклатура.Form.ФормаЭлемента" --borrow-main-attribute all
```

- `form` — только реквизиты, уже выведенные на форме (оптимально в большинстве случаев).
- `all` — все реквизиты объекта (нужно, если планируешь выводить реквизиты, которых на форме ещё нет).

Типовой сценарий: borrow с `--borrow-main-attribute form` → добавить реквизит → `form-edit`.

**Важно:** если объект уже заимствован — CLI добавляет недостающее, не перезаписывает.

### extension patch-method

Генерация перехватчика метода в модуле расширения.

```bash
xml-gen extension patch-method <extPath> \
  --module "Catalog.X.Form.Y" \
  --method "ПриЗаписи" \
  --type Before|After|Instead|ModificationAndControl \
  [--config <configPath>]
```

**Параметры:**
- `<extPath>` — путь к каталогу расширения
- `--module` — путь к модулю в формате `Тип.Имя.ТипМодуля` или `Тип.Имя.Form.ИмяФормы`
- `--method` — имя перехватываемого метода типовой конфигурации
- `--type` — тип перехвата (см. таблицу)
- `--config` — путь к базовой конфигурации (обязателен для `ModificationAndControl`)

**Типы перехвата:**

| `--type` | Декоратор BSL | Назначение |
|----------|--------------|------------|
| `Before` | `&Перед` | Код до вызова оригинального метода |
| `After` | `&После` | Код после вызова оригинального метода |
| `Instead` | `&Вместо` | Полная замена оригинального метода |
| `ModificationAndControl` | `&ИзменениеИКонтроль` | Копия тела оригинального метода с маркерами вставки |

**Маппинг ModulePath → BSL-файл:**

| ModulePath | Файл в расширении |
|------------|-------------------|
| `Catalog.X.ObjectModule` | `Catalogs/X/Ext/ObjectModule.bsl` |
| `Catalog.X.ManagerModule` | `Catalogs/X/Ext/ManagerModule.bsl` |
| `Catalog.X.Form.Y` | `Catalogs/X/Forms/Y/Ext/Form/Module.bsl` |
| `CommonModule.X` | `CommonModules/X/Ext/Module.bsl` |
| `Document.X.ObjectModule` | `Documents/X/Ext/ObjectModule.bsl` |
| `Document.X.Form.Y` | `Documents/X/Forms/Y/Ext/Form/Module.bsl` |
| `InformationRegister.X.RecordSetModule` | `InformationRegisters/X/Ext/RecordSetModule.bsl` |

Аналогично для Report, DataProcessor и других типов объектов.

**Имя процедуры-перехватчика** формируется с NamePrefix расширения: `Расш1_ПриЗаписи` (читается из `Configuration.xml` расширения).

**`ModificationAndControl`:** CLI читает тело оригинального метода из `--config` и вставляет его с маркерами `#Вставка`/`#КонецВставки`. Требует `--config`.

**При существующем файле:** дописывает процедуру в конец модуля, не перезаписывает.

### extension validate

Валидация расширения (9 проверок).

```bash
xml-gen extension validate <extensionPath>
```

**Проверки:** MetaDataObject/Configuration, InternalInfo (7 ClassId), Properties (ObjectBelonging, Name, Purpose, Prefix), enum-значения, ChildObjects, DefaultLanguage, файлы языков, каталоги объектов, заимствованные объекты (ObjectBelonging=Adopted + ExtendedConfigurationObject).

## Ключевые концепции CFE

- **ObjectBelonging:** `Adopted` — заимствованный объект; отсутствие атрибута — собственный.
- **ID-диапазоны:** Base 1–999999, Extension 1000000+.
- **BSL-перехватчики:** декораторы `&Перед`, `&После`, `&Вместо`, `&ИзменениеИКонтроль` над процедурой с префиксом расширения.
- **Маркеры переноса:** `#Вставка` / `#КонецВставки` (или `#Область <Префикс>_Вставка`).
- **Русские синонимы:** Справочник → Catalog, Документ → Document, РегистрСведений → InformationRegister, ОбщийМодуль → CommonModule и др. — поддерживаются во всех командах.

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
