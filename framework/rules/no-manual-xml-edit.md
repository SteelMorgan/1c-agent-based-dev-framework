---
name: no-manual-xml-edit
description: Глобальный запрет ручного редактирования 1С XML метаданных. Все операции — через xmlgen CLI и его skill-обёртки. Ручная правка допустима только если xmlgen явно не поддерживает операцию, с обязательным логированием.
alwaysApply: true
---

# Запрет ручной правки 1С XML

Глобальное правило для всех агентов и сабагентов.

## Контекст

Метаданные 1С:Предприятие, хранящиеся в XML (`Form.xml`, `Rights.xml`, `Configuration.xml`, `*.xml` справочников / документов / регистров / подписок / ролей / планов / отчётов / обработок / общих модулей и любых других объектов конфигурации), имеют строгую schema + неочевидные runtime-зависимости. Прямое редактирование через Edit/Write tool регулярно приводит к non-canonical schema, которая:

- проходит `build_project` и LSP-диагностику,
- но ломает runtime UI / поведение объекта.

Прецедент: OC-22444 F-01 — ручная генерация `<ValueType><Type>...</Type></ValueType>` вместо canonical `<Type><v8:Type>...</v8:Type></Type>` + отсутствие UI `<TableColumn>` элементов. 6 итераций Developer-Code не смогли стабилизировать форму.

Инструмент `xmlgen` (Java CLI) и его skill-обёртки покрывают все типовые операции:
- создание / редактирование форм (атрибуты, элементы UI, команды, события),
- права доступа (Rights.xml),
- EPF, SKD, шаблоны,
- byte-by-byte замена текста (`edit replace-text`) с сохранением BOM/CRLF/LF,
- валидация schema + structural + semantic правила.

## Правила

### ЗАПРЕЩЕНО

- Использование Edit / Write / sed / awk / любого текстового инструмента для прямой модификации 1С XML метаданных.
- Создание 1С XML через template string-ы в Python/Bash/других скриптах помимо `xmlgen`.
- Обход схемных проверок путём копирования блоков XML из других форм без прогонки через `xmlgen validate`.

### ОБЯЗАТЕЛЬНО

- Любое изменение 1С metadata XML — через `xmlgen` CLI и его skill-обёртки:
  - `/form-edit`, `/form-info`, `/form-validate` — управляемые формы,
  - `xml-gen form add-attribute / add-element / add-command / remove-element / move-element`,
  - `xml-gen role add-object / add-right`,
  - `xml-gen epf add-attribute / add-tabular-section`,
  - `xml-gen skd add-parameter / add-field`,
  - `xml-gen config / subsystem / interface / meta / extension validate`,
  - `xml-gen edit replace-text` — для безопасной замены текстовых блоков с сохранением байтовой структуры.
- После любой модификации 1С XML — `xml-gen validate` (соответствующего типа), exit code 0 или 2 (warnings).
- Перед модификацией — `xml-gen validate` для фиксации состояния (ловит предыдущие ошибки, не связанные с текущей правкой).

### ДОПУСТИМО (исключение)

Если `xmlgen` **явно не поддерживает** требуемую операцию:

1. Агент фиксирует в своём `{role}-context.md` запись:
   ```
   [YYYY-MM-DD HH:MM] MANUAL_XML_EDIT:
     file: <полный путь>
     operation: <что именно делаем>
     reason: xmlgen lacks <capability>
     validation_method: <как проверил что это корректно>
   ```
2. Агент уведомляет оркестратора через запись в `orchestrator-context.md`:
   ```
   [YYYY-MM-DD HH:MM] MANUAL_XML_EDIT_REPORTED: agent=<role>, file=<path>, reason=<...>
   ```
3. Оркестратор обязан:
   - зарегистрировать факт для последующего пополнения `xmlgen` (отдельная подзадача по расширению инструмента),
   - при необходимости запустить `xml-gen validate` для отлова побочных schema-bugs.

## Что НЕ является 1С XML (правило не применяется)

- `pom.xml`, `build.gradle.kts`, `settings.gradle.kts` — build descriptors.
- `.gitignore`, `.editorconfig`, CI/CD YAML/XML.
- Тестовые fixtures для инструментов (когда XML используется как test data, а не как реальная конфигурация 1С).
- Документация в XML-формате (если есть).

## Поведение при нарушении

1. Остановиться, не выполнять ручную правку.
2. Проверить наличие подходящей `xmlgen` команды: `xml-gen --help`, `xml-gen form --help`, SKILL.md соответствующего skill.
3. Если команда есть — использовать её.
4. Если команды нет — переключиться на процедуру «исключение» выше (логирование + уведомление оркестратора).
5. Если unclear — `clarification_needed` → оркестратор / пользователь.

## Enforcement: pre-tool-use хук

Правило усиливается автоматическим хуком `tools/hooks/block-direct-xml-edit.py`,
который определяет 1С metadata XML/MXL по структуре пути:

- `*.mxl` — всегда блокируется (двоичный формат).
- `*.xml` внутри `**/Ext/`, `Configuration.xml`, или любой из 1С root-папок
  (`Catalogs/`, `Documents/`, `InformationRegisters/`, `Roles/`, `Subsystems/`,
  `CommonModules/`, `ChartsOf*`, `*Registers/`, `Reports/`, `DataProcessors/`,
  `Enums/`, `Constants/`, `ExchangePlans/`, `Tasks/`, `BusinessProcesses/`,
  `HTTPServices/`, `WebServices/`, `EventSubscriptions/`, `ScheduledJobs/`,
  `DefinedTypes/`, `DocumentJournals/` и др.).
- Исключения (не блокируется): `pom.xml`, `*.gradle*`, CI-конфиги (`.github/`,
  `.gitlab/`), тестовые fixtures (`/test/`, `/tests/`, `/fixtures/`,
  `/__fixtures__/`, `/testdata/`, `/test-resources/`).

### Claude Code

Зарегистрирован в `.claude/settings.json` как PreToolUse hook для
`Edit|Write|MultiEdit|NotebookEdit`. При попытке прямой правки 1С XML/MXL хук
возвращает exit 2 — Claude Code отклоняет вызов tool и показывает модели stderr
с подсказкой по нужной команде xml-gen. Никаких ручных действий после клонирования
репо делать не нужно — хук активен с момента старта сессии Claude Code в этом
каталоге.

### Codex / прочие агенты без PreToolUse-протокола

Codex не имеет встроенного PreToolUse hook, поэтому enforcement в нём — текстовый
(через это правило в AGENTS.md). Тот же Python-скрипт можно вызывать вручную
или из shell-обёрток:

```bash
# CLI-режим: возвращает exit 2 если путь — 1С metadata XML/MXL
python3 tools/hooks/block-direct-xml-edit.py --check <path> --tool Edit

# Пример pre-action проверки в скрипте автоматизации
python3 tools/hooks/block-direct-xml-edit.py --check "$FILE" --tool Edit || {
    echo "Используй xml-gen вместо прямой правки"; exit 1;
}
```

Скрипт поддерживает оба режима:
- `stdin JSON` (Claude Code PreToolUse — payload с `tool_name`/`tool_input`).
- `--check <path> --tool <Name>` (любой другой агент или CI-проверка).

### Тонкая настройка / расширение списка путей

Списки `ONEC_ROOT_DIRS`, `EXCLUDE_SUBSTRINGS`, `EXCLUDE_BASENAMES` задаются
константами в `tools/hooks/block-direct-xml-edit.py`. Дополняй их, если в проекте
появляется новый паттерн 1С-конфигурации (например, нестандартное расположение)
или новый ложноположительный случай (build XML с уникальным именем).

## Связанные документы

- `framework/skills/tool-usage/platform-data/xml-generation/` — skill-обёртки xmlgen.
- `tools/xml-gen/README.md` + `SPEC-*.md` — спецификации xmlgen CLI.
- `tools/hooks/block-direct-xml-edit.py` — enforcement-хук этого правила.
- `.claude/settings.json` — регистрация хука для Claude Code.
- `framework/rules/protected-paths.md` — пересекается в части `exts/YAXUNIT/**` (protected) и других защищённых каталогов.

---
depends_on:
  - framework/rules/protected-paths.md
  - framework/rules/agent-context-protocol.md
---
