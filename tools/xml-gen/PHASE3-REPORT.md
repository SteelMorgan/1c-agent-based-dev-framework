# Phase 3: Form Generation - Implementation Report

**Date:** 2026-02-12  
**Status:** ✅ Базовая реализация завершена (Designer format)

---

## Реализовано

### 1. JSON DSL для форм (`FormDsl.java`)

Структура данных для описания управляемых форм:

```java
- title: String                    // Заголовок формы
- properties: Map<String, Object>  // Свойства (autoTitle, windowOpeningMode и т.д.)
- excludedCommands: List<String>   // Исключённые стандартные команды
- events: Map<String, String>      // События формы
- elements: List<Map>              // UI-элементы (пока не реализовано)
- attributes: List<Attribute>      // Реквизиты формы
- parameters: List<Parameter>      // Параметры формы
- commands: List<Command>          // Команды формы
```

**Вложенные классы:**
- `Attribute` — реквизит формы (name, title, type, main, columns)
- `Column` — колонка коллекции (ValueTable/ValueTree)
- `Parameter` — параметр формы
- `Command` — команда формы (name, title, action, tooltip)

### 2. Генератор форм (`FormWriter.java`)

**Основной функционал:**
- Генерация `Form.xml` в формате Designer
- Поддержка всех секций формы:
  - Title, Properties
  - AutoCommandBar (обязательный элемент с id=-1)
  - Events
  - ChildItems (пока пустой)
  - Attributes (с поддержкой коллекций)
  - Commands

**Технические детали:**
- Без BOM для `Form.xml` (согласно спецификации 1С)
- Автоинкремент ID для реквизитов и команд
- Многоязычные строки (Title, ToolTip) с поддержкой русского языка
- Интеграция с `TypeResolver` для преобразования DSL типов

### 3. Расширение TypeResolver

Добавлена поддержка новых типов:
- `ExternalDataProcessorObject.Name` → `cfg:ExternalDataProcessorObject.Name`
- `ExternalReportObject.Name` → `cfg:ExternalReportObject.Name`
- `ValueTable` / `ValueTree` (case-insensitive) → `v8:ValueTable` / `v8:ValueTree`

### 4. CLI команда `form compile`

```bash
java -jar xml-gen.jar form compile <input.json> <output.xml> [--format designer|edt]
```

**Пример:**
```bash
java -jar xml-gen.jar form compile test-form.json Form.xml
```

### 5. Тесты (`FormWriterTest.java`)

7 тестов, все проходят:
1. ✅ `testMinimalForm` — минимальная форма (только title)
2. ✅ `testFormWithAttributes` — форма с реквизитами
3. ✅ `testFormWithCommands` — форма с командами
4. ✅ `testFormWithEvents` — форма с событиями
5. ✅ `testFormWithValueTable` — форма с коллекцией (ValueTable)
6. ✅ `testCompleteForm` — полная форма (все секции)
7. ✅ `testJsonDslRoundtrip` — JSON DSL → XML roundtrip

### 6. Исправления

**Проблема 1:** TypeResolver не поддерживал типы `ExternalDataProcessorObject.*` и `ValueTable`.  
**Решение:** Добавлены паттерны для этих типов в `TypeResolver.resolve()`.

**Проблема 2:** `XmlWriter.createWriter()` падал с NPE, если `outputPath.getParent()` возвращал null.  
**Решение:** Добавлена проверка `if (parent != null)` перед `Files.createDirectories()`.

---

## Ограничения текущей реализации

### 1. UI-элементы (ChildItems) не реализованы

Секция `<ChildItems>` пока пустая. Не поддерживаются:
- InputField, Button, Table, Group и другие элементы
- Автогенерация ExtendedTooltip, ContextMenu
- Привязка элементов к реквизитам (DataPath)
- Иерархия элементов (Parent-Child)

**Причина:** Это самая сложная часть формы, требует отдельной реализации.

### 2. Параметры формы не используются

Класс `FormDsl.Parameter` определён, но не обрабатывается в `FormWriter`.

### 3. Исключённые команды не обрабатываются

Поле `excludedCommands` в DSL игнорируется.

### 4. EDT формат не реализован

`FormWriter.create()` для EDT выбрасывает `UnsupportedOperationException`.

### 5. Нет валидации DSL

Не проверяется корректность:
- Уникальность имён реквизитов/команд
- Обязательность полей
- Корректность типов

---

## Пример использования

### JSON DSL:

```json
{
  "title": "Тестовая форма",
  "properties": {
    "autoTitle": false,
    "windowOpeningMode": "LockOwnerWindow"
  },
  "events": {
    "OnCreateAtServer": "ПриСозданииНаСервере"
  },
  "attributes": [
    {
      "name": "Объект",
      "title": "Объект",
      "type": "ExternalDataProcessorObject.ТестоваяОбработка",
      "main": true
    },
    {
      "name": "Параметр1",
      "title": "Параметр 1",
      "type": "string(100)"
    }
  ],
  "commands": [
    {
      "name": "Выполнить",
      "title": "Выполнить",
      "action": "Выполнить",
      "tooltip": "Выполнить обработку"
    }
  ]
}
```

### Генерация:

```bash
java -jar xml-gen.jar form compile test-form.json Form.xml
```

### Результат:

Корректный `Form.xml` с:
- Заголовком и свойствами
- Событиями
- Реквизитами (с правильными типами и ID)
- Командами
- Без BOM (как требует 1С)

---

## Следующие шаги (Phase 3 продолжение)

### Приоритет 1: UI-элементы (Top-15)

Реализовать генерацию основных элементов формы:
1. InputField (поле ввода)
2. Button (кнопка)
3. Table (таблица)
4. Group (группа)
5. Label (надпись)
6. CheckBox (флажок)
7. RadioButton (переключатель)
8. CommandBar (панель команд)
9. Pages (страницы)
10. Decoration (декорация)
11. Calendar (календарь)
12. Chart (диаграмма)
13. Gantt (диаграмма Ганта)
14. PictureField (поле картинки)
15. HTMLField (HTML-поле)

**Для каждого элемента:**
- Автогенерация `ExtendedTooltip`
- Автогенерация `ContextMenu` (где применимо)
- Поддержка `DataPath` для привязки к реквизитам
- Поддержка событий элемента

### Приоритет 2: Параметры формы

Реализовать обработку `FormDsl.Parameter` в `FormWriter`.

### Приоритет 3: Исключённые команды

Обработка `excludedCommands` в DSL.

### Приоритет 4: EDT формат

Реализовать `FormWriter` для EDT (аналогично Designer).

### Приоритет 5: Валидация

Добавить проверки корректности DSL перед генерацией.

---

## Статистика

- **Файлов создано:** 3 (FormDsl.java, FormWriter.java, FormWriterTest.java)
- **Файлов изменено:** 3 (Commands.java, TypeResolver.java, XmlWriter.java)
- **Строк кода:** ~600
- **Тестов:** 7 (все проходят)
- **Время разработки:** ~1 час

---

## Заключение

**Phase 3 (базовая часть) завершена успешно.**

Реализован минимально работающий генератор форм:
- ✅ JSON DSL для описания форм
- ✅ Генерация Form.xml (Designer format)
- ✅ Поддержка реквизитов, команд, событий, свойств
- ✅ CLI команда `form compile`
- ✅ Полное покрытие тестами

**Основное ограничение:** UI-элементы (ChildItems) не реализованы. Это требует отдельной итерации разработки, так как элементы формы — самая сложная и объёмная часть спецификации 1С.

Текущая реализация позволяет генерировать "скелет" формы с корректной структурой метаданных, что уже полезно для автоматизации создания форм.
