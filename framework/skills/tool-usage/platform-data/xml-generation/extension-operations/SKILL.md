---
name: extension-operations
description: Операции с расширениями конфигурации 1С (CFE) — init, borrow, diff, validate. Используй при создании расширений, заимствовании объектов, анализе состава и перехватчиков.
---

# Extension Operations (CFE)

Работа с расширениями конфигурации 1С.

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно создать расширение | `extension init --name <Name> --config <configPath> <output_dir>` |
| Нужно заимствовать объект из конфигурации | `extension borrow <extPath> <configPath> "Type.Name"` |
| Нужно заимствовать форму | `extension borrow <extPath> <configPath> "Catalog.Name.Form.FormName"` |
| Нужно проанализировать расширение | `extension diff <extPath> <configPath>` |
| Нужно проверить расширение | `extension validate <extPath>` |

## Команды

### extension init

Создать расширение конфигурации.

```bash
xml-gen extension init --name <Name> --config <configPath> [--purpose Patch|Customization|AddOn] [--prefix <Prefix>] <output_dir>
```

**Параметры:**
- `--name` — имя расширения
- `--config` — путь к базовой конфигурации (для чтения CompatibilityMode и DefaultLanguage)
- `--purpose` — назначение (по умолчанию: Customization)
- `--prefix` — префикс имён (по умолчанию: из имени)

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

**Что происходит при заимствовании:**
1. Читает UUID объекта из базовой конфигурации
2. Генерирует XML с ObjectBelonging=Adopted
3. Создаёт ExtendedConfigurationObject ссылку
4. Регистрирует в ChildObjects расширения (каноничный порядок)
5. При заимствовании формы: копирует Form.xml как BaseForm, создаёт Module.bsl

### extension diff

Анализ расширения: состав, перехватчики, проверка переноса.

```bash
xml-gen extension diff <extensionPath> <configPath> [--mode A|B]
```

**Mode A (обзор):**
- Список всех объектов: [BORROWED] / [OWN]
- BSL-перехватчики (&Перед, &После, &ИзменениеИКонтроль, &Вместо)
- Анализ форм (заимствованные vs собственные)

**Mode B (проверка переноса):**
- Поиск `&ИзменениеИКонтроль` декораторов
- Проверка `#Вставка` / `#КонецВставки` блоков
- Сверка с модулями базовой конфигурации

### extension validate

Валидация расширения (9 проверок).

```bash
xml-gen extension validate <extensionPath>
```

**Проверки:** MetaDataObject/Configuration, InternalInfo (7 ClassId), Properties (ObjectBelonging, Name, Purpose, Prefix), enum-значения, ChildObjects, DefaultLanguage, файлы языков, каталоги объектов, заимствованные объекты (ObjectBelonging=Adopted + ExtendedConfigurationObject).

## Ключевые концепции CFE

### ObjectBelonging
- `Adopted` — заимствованный объект (копия из базовой конфигурации)
- `Own` (отсутствует) — собственный объект расширения

### ID-диапазоны
- Base elements: 1–999999
- Extension elements: 1000000+

### BSL-перехватчики
```bsl
&Перед("ПриСозданииНаСервере")
Процедура ДССЛ_ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    // Код перехвата
КонецПроцедуры
```

### Маркеры переноса
```bsl
#Область ДССЛ_Вставка  // или #Вставка
    // Собственный код
#КонецОбласти
```

## Русские синонимы типов

Справочник → Catalog, Документ → Document, РегистрСведений → InformationRegister, ОбщийМодуль → CommonModule и др. (25 маппингов).

## См. также

- [config-operations](../config-operations/) — работа с базовой конфигурацией
- [meta-operations](../meta-operations/) — объекты метаданных

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---
