---
name: no-manual-xml-edit
description: Правишь 1С XML/MXL → применить навык xml-generation. Ручная правка запрещена; для агентов без PreToolUse-хука обязателен self-check через block-direct-xml-edit.py.
alwaysApply: true
---

# Запрет ручной правки 1С XML и MXL

> **Триггер:** любая попытка Edit/Write/sed/awk/Bash по файлу `*.mxl` или `*.xml` в 1С-каталогах. При срабатывании — применить навык `xml-generation` (`framework/skills/tool-usage/platform-data/xml-generation/SKILL.md`).

## ЗАПРЕЩЕНО (без исключений)

- Edit / Write / sed / awk / любой текстовый инструмент для прямой модификации 1С XML/MXL метаданных.
- Создание 1С XML через template string-ы в Python/Bash/других скриптах помимо `xmlgen`.
- Обход схемных проверок путём копирования блоков XML из других форм без прогонки через `xmlgen validate`.

**Почему:** метаданные 1С имеют строгую схему + неочевидные runtime-зависимости. Прямая правка регулярно даёт non-canonical schema, которая проходит сборку и LSP, но ломает runtime UI. Прецедент: OC-22444 F-01 — 6 итераций Developer-Code не смогли стабилизировать форму.

## Что является 1С XML (правило применяется)

- `*.mxl` (любой).
- `*.xml` внутри `**/Ext/`, `Configuration.xml`, или 1С root-папок: `Catalogs/`, `Documents/`, `*Registers/`, `Roles/`, `Subsystems/`, `CommonModules/`, `ChartsOf*`, `Reports/`, `DataProcessors/`, `Enums/`, `Constants/`, `ExchangePlans/`, `Tasks/`, `BusinessProcesses/`, `HTTPServices/`, `WebServices/`, `EventSubscriptions/`, `ScheduledJobs/`, `DefinedTypes/`, `DocumentJournals/` и т.п.

## Что НЕ является 1С XML (правило не применяется)

- `pom.xml`, `build.gradle.kts`, `settings.gradle.kts` — build descriptors.
- `.gitignore`, `.editorconfig`, CI/CD YAML/XML.
- Тестовые fixtures для инструментов (XML как test data, не конфигурация 1С).
- Документация в XML-формате.

## Self-check для агентов без PreToolUse-хука (Codex, Cursor, Aider, Cline и др.)

В Claude Code хук блокирует автоматически. Во всех остальных средах — **самопроверка обязательна** перед каждым Edit/Write/apply_patch/sed по пути с расширением `.xml` или `.mxl`:

```bash
python3 tools/hooks/block-direct-xml-edit.py --check "<path>" --tool Edit
```

- Exit code `2` → путь относится к 1С metadata, правку не выполнять. Читать stderr — там подсказка по нужной команде xml-gen.
- Exit code `0` → не 1С metadata, правка разрешена.

## Что делается кодом, а НЕ через xml-gen

Часть возможностей платформы не хранится в XML — их рекомендуется реализовывать программно. Отсутствие генератора в xml-gen для них — это **дизайн**, не дефект.

| Возможность | Как делать | Как НЕ делать |
|-------------|------------|---------------|
| Условное оформление форм и видимость-по-условию | Программно в модуле формы: `УсловноеОформление.Элементы.Добавить()` | Писать `<ConditionalAppearance>` в XML формы |
| Отборы / сортировка / параметры динамических списков | Программно через `Список.КомпоновкаДанных.Отбор` | Править `<Filter>`/`<SettingsComposer>` вручную |
| Раскраска/оформление ячеек MXL **по условию** при выводе | Программно при заполнении: `Область.ЦветФона = …` | Кодировать условное оформление в `Template.xml` |

> В зоне xml-gen (инструмент использовать нужно): статические свойства элементов формы, статические стили ячеек MXL, условное оформление **отчётов (СКД)** через `skd` DSL.

## Исключение (xmlgen не поддерживает операцию)

Зафиксировать в `{role}-context.md`:
```
[YYYY-MM-DD HH:MM] MANUAL_XML_EDIT:
  file: <полный путь>
  operation: <что именно делаем>
  reason: xmlgen lacks <capability>
  validation_method: <как проверил что это корректно>
```
Уведомить оркестратора через `orchestrator-context.md`: `MANUAL_XML_EDIT_REPORTED: agent=<role>, file=<path>, reason=<...>`

---
depends_on:
  - framework/skills/tool-usage/platform-data/xml-generation/SKILL.md
  - framework/rules/protected-paths.md
---
