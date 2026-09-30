# SPEC-cfe-cli-extension: Расширение CLI xml-gen для работы с CFE

**Статус:** Draft  
**Дата:** 2026-05-21  
**Адресат:** разработчик инструмента `xml-gen` (Java/CLI)  
**Источники:** навыки Широкова `cfe-borrow` и `cfe-patch-method` (https://github.com/Nikolay-Shirokov/cc-1c-skills)

---

## 1. Контекст

Два поведения, реализованных в PowerShell-скриптах Широкова, нужно добавить в `xml-gen` как нативные под-команды / флаги. Это даёт кросс-платформенность (Linux/macOS) и интеграцию с существующими командами `extension borrow` и `extension diff`.

---

## 2. Фича A — `--borrow-main-attribute` для `extension borrow`

### 2.1 Проблема

При заимствовании формы через `extension borrow <ext> <cfg> "Catalog.X.Form.Y"` форма копируется из базовой конфигурации, но **без DataPath** на основной объект формы. Последующий вызов `form-edit` не может привязать новый реквизит расширения к форме, поскольку форма не знает о своём основном реквизите.

### 2.2 Ожидаемое CLI

```
xml-gen extension borrow <extensionPath> <configPath> "<objectSpec>" --borrow-main-attribute <mode>
```

| Параметр | Тип | Обязательность | Значения |
|----------|-----|----------------|----------|
| `--borrow-main-attribute` | enum | Опциональный | `form` \| `all` |

Флаг применим **только** когда `<objectSpec>` указывает на форму (`Type.Name.Form.X`). При указании на объект без формы — ошибка с внятным сообщением.

### 2.3 Режим `form`

- Прочитать `Form.xml` из базовой конфигурации по пути `<objectType>s/<objectName>/Forms/<formName>/Ext/Form.xml`
- Извлечь все элементы формы (`Items`), у которых задан `DataPath`
- Собрать уникальный список реквизитов объекта, на которые ссылаются эти `DataPath`
- Для каждого реквизита: скопировать его XML-описание из метаданных объекта базовой конфигурации в XML объекта расширения (создать при необходимости `<Attributes>` / `<TabularSections>`)
- Выставить `ObjectBelonging=Adopted` для заимствованного объекта (если не установлен)

### 2.4 Режим `all`

- Скопировать **все** реквизиты (`Attributes`) и табличные части (`TabularSections`) из XML объекта базовой конфигурации в XML объекта расширения
- Логика та же, что для `form`, но без фильтрации по DataPath формы

### 2.5 Защита существующих данных

- Если объект уже заимствован и содержит реквизиты — **не перезаписывать** существующие, только добавлять отсутствующие (сравнивать по `Name`)
- Если форма уже заимствована — обновить только XML объекта (не трогать `Form.xml` и `Module.bsl`)

### 2.6 Edge cases

| Ситуация | Ожидаемое поведение |
|----------|---------------------|
| `objectSpec` не содержит `.Form.` | Ошибка: `--borrow-main-attribute requires a form object spec` |
| Форма не найдена в базовой конфигурации | Ошибка с путём, который ищется |
| Объект не заимствован (нет XML в расширении) | Автоматически заимствовать родительский объект перед добавлением реквизитов |
| Реквизит уже есть в расширении | Пропустить, не перезаписывать |
| `form` режим, форма без единого DataPath | Предупреждение, завершить успехом (нечего заимствовать) |

---

## 3. Фича B — `extension patch-method` (новая под-команда)

### 3.1 Назначение

Автогенерация процедуры-перехватчика в BSL-модуле расширения. Создаёт файл модуля или дописывает в существующий.

### 3.2 Ожидаемое CLI

```
xml-gen extension patch-method <extensionPath> \
  --module "<modulePath>" \
  --method "<methodName>" \
  --type <interceptorType> \
  [--config <configPath>] \
  [--context <bslContext>] \
  [--function]
```

| Параметр | Тип | Обязательность | Описание |
|----------|-----|----------------|----------|
| `<extensionPath>` | path | Обязательный | Путь к каталогу расширения |
| `--module` | string | Обязательный | Путь к модулю (см. формат ниже) |
| `--method` | string | Обязательный | Имя метода типовой конфигурации |
| `--type` | enum | Обязательный | `Before` \| `After` \| `Instead` \| `ModificationAndControl` |
| `--config` | path | Обязательный для `ModificationAndControl` | Путь к базовой конфигурации |
| `--context` | string | Опциональный | BSL-директива контекста (по умолчанию: `НаСервере`) |
| `--function` | flag | Опциональный | Генерировать функцию вместо процедуры (добавить `Возврат Неопределено`) |

### 3.3 Формат `--module` и маппинг в файловую систему

| `--module` | BSL-файл в расширении |
|------------|----------------------|
| `Catalog.X.ObjectModule` | `Catalogs/X/Ext/ObjectModule.bsl` |
| `Catalog.X.ManagerModule` | `Catalogs/X/Ext/ManagerModule.bsl` |
| `Catalog.X.Form.Y` | `Catalogs/X/Forms/Y/Ext/Form/Module.bsl` |
| `CommonModule.X` | `CommonModules/X/Ext/Module.bsl` |
| `Document.X.ObjectModule` | `Documents/X/Ext/ObjectModule.bsl` |
| `Document.X.Form.Y` | `Documents/X/Forms/Y/Ext/Form/Module.bsl` |
| `InformationRegister.X.RecordSetModule` | `InformationRegisters/X/Ext/RecordSetModule.bsl` |
| `Report.X.ObjectModule` | `Reports/X/Ext/ObjectModule.bsl` |
| `Report.X.Form.Y` | `Reports/X/Forms/Y/Ext/Form/Module.bsl` |
| `DataProcessor.X.ObjectModule` | `DataProcessors/X/Ext/ObjectModule.bsl` |
| `DataProcessor.X.Form.Y` | `DataProcessors/X/Forms/Y/Ext/Form/Module.bsl` |

Маппинг аналогичен существующей логике `extension borrow` (тот же метаданный маппер).

### 3.4 Имя процедуры-перехватчика

Читать `NamePrefix` из `<extensionPath>/Configuration.xml` (XML-элемент `//Configuration/NamePrefix`). Имя процедуры: `<NamePrefix>_<MethodName>`.

Пример: NamePrefix=`Расш1`, метод=`ПриЗаписи` → `Расш1_ПриЗаписи`.

### 3.5 Генерируемый BSL-код

**Before:**
```bsl
&<Context>
&Перед("<MethodName>")
Процедура <NamePrefix>_<MethodName>()
	// TODO: код перед вызовом оригинального метода
КонецПроцедуры
```

**After:**
```bsl
&<Context>
&После("<MethodName>")
Процедура <NamePrefix>_<MethodName>()
	// TODO: код после вызова оригинального метода
КонецПроцедуры
```

**Instead:**
```bsl
&<Context>
&Вместо("<MethodName>")
Процедура <NamePrefix>_<MethodName>()
	// TODO: замена оригинального метода
КонецПроцедуры
```

**ModificationAndControl** (требует `--config`):
```bsl
&<Context>
&ИзменениеИКонтроль("<MethodName>")
Процедура <NamePrefix>_<MethodName>()
	// --- Тело оригинального метода из базовой конфигурации ---
	// <скопированный код>
	// ... в точках изменения: маркеры #Вставка / #КонецВставки
КонецПроцедуры
```

Для `ModificationAndControl`: прочитать тело оригинального метода из BSL-файла базовой конфигурации, используя тот же маппинг `--module` → путь к файлу (но относительно `<configPath>`). Вставить тело как есть. Маркеры `#Вставка` / `#КонецВставки` — ответственность разработчика (не генерировать автоматически, только добавить комментарий-подсказку).

Для `--function`: заменить `Процедура` / `КонецПроцедуры` на `Функция` / `КонецФункции` и добавить `Возврат Неопределено;` перед `КонецФункции`.

### 3.6 Поведение при существующем файле

- Если файл BSL уже существует — **дописать** процедуру в конец (не перезаписывать)
- Перед добавлением проверить: если процедура с таким именем уже есть в файле — вывести предупреждение и не добавлять дубликат
- Если файл не существует — создать (включая промежуточные каталоги)

### 3.7 Edge cases

| Ситуация | Ожидаемое поведение |
|----------|---------------------|
| `--type ModificationAndControl` без `--config` | Ошибка: `--config is required for ModificationAndControl` |
| Метод не найден в базовой конфигурации (`ModificationAndControl`) | Ошибка с указанием пути поиска |
| NamePrefix не найден в `Configuration.xml` расширения | Ошибка: `NamePrefix not found in extension Configuration.xml` |
| Объект не заимствован (нет каталога в расширении) | Предупреждение, но файл создать (разработчик мог создать вручную) |
| Процедура с тем же именем уже есть в BSL-файле | Предупреждение, пропустить добавление |
| `--module` в неизвестном формате | Ошибка с примером корректного формата |

---

## 4. Связанные команды и порядок применения

### Сценарий: добавить реквизит расширения и вывести на форму типовой

```bash
# 1. Заимствовать форму с реквизитами (DataPath)
xml-gen extension borrow src cfg "Catalog.Контрагенты.Form.ФормаЭлемента" --borrow-main-attribute form

# 2. Добавить реквизит в объект расширения (xml-gen meta-edit или вручную)
xml-gen meta-edit src "Catalog.Контрагенты" --add-attribute "МойРеквизит" --type String

# 3. Вывести реквизит на форму
xml-gen form-edit src "Catalog.Контрагенты.Form.ФормаЭлемента" --add-input "МойРеквизит"
```

### Сценарий: перехватить метод объекта

```bash
# 1. Заимствовать объект (если ещё не заимствован)
xml-gen extension borrow src cfg "Catalog.Контрагенты"

# 2. Сгенерировать перехватчик
xml-gen extension patch-method src \
  --module "Catalog.Контрагенты.ObjectModule" \
  --method "ПриЗаписи" \
  --type Before

# 3. Для глубокой интеграции — ИзменениеИКонтроль
xml-gen extension patch-method src \
  --module "Catalog.Контрагенты.ObjectModule" \
  --method "ПриЗаписи" \
  --type ModificationAndControl \
  --config /path/to/cfg
```

---

## 5. Ссылки

- Оригинальная реализация `--borrow-main-attribute`: https://github.com/Nikolay-Shirokov/cc-1c-skills/tree/main/.claude/skills/cfe-borrow
- Оригинальная реализация перехватчиков: https://github.com/Nikolay-Shirokov/cc-1c-skills/tree/main/.claude/skills/cfe-patch-method
- Skill-документация (агентская): `framework/skills/tool-usage/platform-data/xml-generation/extension-operations/SKILL.md`
- Существующий анализ разрыва между Широковым и xml-gen: `docs/specs-and-analisys/xml-gen-delta-vs-shirokov-2026-03-05.md`
- Общий план расширения xml-gen: `docs/specs-and-analisys/xml-gen-expansion-plan-2026-03-09.md`
