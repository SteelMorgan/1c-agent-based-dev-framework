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

> Часть возможностей платформы (условное оформление форм, отборы динамических списков, раскраска ячеек MXL по условию) не хранится в XML и реализуется программно — это дизайн, не дефект xml-gen; не искать ручной XML-обход. Перечень и образцы — в навыке `xml-generation`.

## Исключение (xmlgen не поддерживает операцию)

Если xml-gen **явно** не поддерживает нужную операцию — зафиксировать факт ручной правки (`MANUAL_XML_EDIT:` с файлом, операцией, причиной, методом валидации) в `{role}-context.md` и уведомить оркестратора (`MANUAL_XML_EDIT_REPORTED:`) в `orchestrator-context.md`. Формат записи — в навыке `xml-generation`.

---
depends_on:
  - framework/skills/tool-usage/platform-data/xml-generation/SKILL.md
  - framework/rules/protected-paths/SKILL.md
---
