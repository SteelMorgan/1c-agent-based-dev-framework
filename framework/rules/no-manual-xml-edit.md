---
name: no-manual-xml-edit
description: Глобальный запрет ручного редактирования 1С XML/MXL метаданных. Все операции — через xmlgen CLI. Для Claude Code блокируется автоматически PreToolUse-хуком; для Codex и любого другого агента без PreToolUse — обязательная самопроверка через `block-direct-xml-edit.py --check` перед каждым Edit/Write. Ручная правка допустима только если xmlgen явно не поддерживает операцию, с обязательным логированием.
alwaysApply: true
---

# Запрет ручной правки 1С XML и MXL

Глобальное правило для всех агентов и сабагентов — независимо от IDE
(Claude Code, Codex, Cursor, Aider, Cline, Windsurf, и любых других).

## TL;DR для агента

1. **Не редактируй** напрямую файлы вида:
   - `*.mxl` (любой),
   - `*.xml` внутри `**/Ext/`, `Configuration.xml`, или 1С root-папок
     (`Catalogs/`, `Documents/`, `*Registers/`, `Roles/`, `Subsystems/`,
     `CommonModules/`, `ChartsOf*`, `Reports/`, `DataProcessors/`, `Enums/`,
     `Constants/`, `ExchangePlans/`, `Tasks/`, `BusinessProcesses/`,
     `HTTPServices/`, `WebServices/`, `EventSubscriptions/`, `ScheduledJobs/`,
     `DefinedTypes/`, `DocumentJournals/`, и т.п.).
2. Вместо этого используй `xml-gen <domain> <op>` — см. skill
   `framework/skills/tool-usage/platform-data/xml-generation/SKILL.md`.
3. **Если ты НЕ в Claude Code** (то есть PreToolUse-хук тебя не защищает) —
   **обязан** перед каждым `Edit`/`Write`/`apply_patch`/`sed` по пути с
   расширением `.xml` или `.mxl` сначала вызвать:
   ```bash
   python3 tools/hooks/block-direct-xml-edit.py --check "<path>" --tool Edit
   ```
   Если exit code = 2 — остановиться, прочитать stderr (там подсказка по
   нужной команде xml-gen) и переключиться на xml-gen. Если exit code = 0 —
   путь не относится к 1С metadata, правка разрешена.

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

### ОБЯЗАТЕЛЬНО для агентов без PreToolUse-хука (Codex, Cursor, Aider, Cline и др.)

Если ты не в Claude Code — автоматического блокирования нет, поэтому **самопроверка обязательна**.
Перед каждым вызовом `Edit` / `Write` / `apply_patch` / `sed` / `awk` / любого
текстового tool на путь с расширением `.xml` или `.mxl`:

1. Запусти guard вручную:
   ```bash
   python3 tools/hooks/block-direct-xml-edit.py --check "<path>" --tool Edit
   ```
2. Если exit code = `2` — путь относится к 1С metadata, **правку не выполнять**.
   Прочитай stderr: там подсказка, какую команду `xml-gen` использовать для этого
   типа файла. Переключайся на неё.
3. Если exit code = `0` — путь не 1С metadata (например, `pom.xml`, тестовая
   fixture в `/tests/`, документация). Можно править как обычно.

Скрипт идемпотентен, без побочных эффектов — это чистый детектор пути. Запускай
без опасений сколько угодно раз.

Альтернативный pattern для shell-скриптов (батч-обработка нескольких файлов):
```bash
for f in $(git diff --name-only); do
  python3 tools/hooks/block-direct-xml-edit.py --check "$f" --tool Edit \
    || { echo "Stop: $f требует xml-gen"; exit 1; }
done
```

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

## Что делается кодом, а НЕ через xml-gen (НЕ выражать в XML)

> Отдельная ветка от «руками нельзя — только xml-gen». Часть возможностей платформы вообще не хранится в метаданных-XML — их **рекомендуется** реализовывать программно при доработке типовых объектов. Для них xml-gen намеренно не даёт генератора. Отсутствие такого генератора — это **дизайн**, а не дефект инструмента: не заводить как недостающую возможность и не искать ручной обход через XML.

| Возможность | Как делать | Как НЕ делать |
|-------------|------------|---------------|
| Условное оформление форм и видимость-по-условию | Программно в модуле формы: `УсловноеОформление.Элементы.Добавить()` — задаёт `Оформление`, `Отбор`, `ОформляемыеПоля`. Это рекомендуемый путь при доработке типовых объектов | Писать `<ConditionalAppearance>` в XML формы руками или ждать DSL-ключ `form` для этого |
| Отборы / сортировка / параметры динамических списков | Программно через `Список.КомпоновкаДанных.Отбор` / настройки, либо в собственных настройках динамического списка | Править `<Filter>`/`<SettingsComposer>` списка вручную |
| Раскраска/оформление ячеек MXL **по условию** при выводе | Программно при заполнении таб. документа: `Область.ТекстЦвет = …`, `Область.ЦветФона = …` на заполненной области | Кодировать рантайм-условное оформление ячеек в `Template.xml` |

**В зоне ответственности xml-gen (это генерируется, инструмент использовать нужно):** статические свойства элементов, включая статическую `Visible=false` (`form` DSL / `form edit`); **статические** стили ячеек MXL — шрифт/выравнивание/границы/перенос/формат (`mxl` DSL); условное оформление **отчётов (СКД)**, которое легитимно живёт в XML-схеме компоновки данных и задаётся через `skd` DSL — это НЕ то же самое, что условное оформление *формы*, и в код НЕ выносится.

**Почему:** условное оформление / отборы списков / раскраска при выводе — это рантайм-аспекты, завязанные на дерево элементов объекта и данные; выражать их статическим XML хрупко (сквозные id, схема проходит валидацию, но ломается на сборке/в рантайме — ср. класс OC-22444 выше) и идёт вразрез с рекомендуемой платформой моделью доработки. Реализация в BSL держит XML формы/макета минимальным, а поведение — тестируемым.

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

### Codex / Cursor / Aider / Cline / прочие агенты без PreToolUse-протокола

В Codex и аналогах нет встроенного PreToolUse-протокола — внешний скрипт не
может перехватить tool-вызов **до** выполнения. Поэтому защита делается
**на стороне самой модели**: агент обязан вызывать `--check` руками перед
каждой правкой XML/MXL (правило выше «ОБЯЗАТЕЛЬНО для агентов без
PreToolUse-хука»).

Скрипт `block-direct-xml-edit.py` поддерживает два режима — один и тот же
бинарь работает и для Claude Code (stdin JSON), и для всего остального
(`--check`):

| Режим | Когда | Как вызывается |
|-------|-------|----------------|
| stdin JSON | Claude Code PreToolUse — настроено в `.claude/settings.json`, агенту делать ничего не нужно | автоматически, до каждого Edit/Write |
| `--check` | Codex/Cursor/Aider/Cline/CI/shell-скрипт | агент вызывает руками перед Edit; exit 2 = block |

Дополнительные слои защиты для agent-ов без PreToolUse (рекомендуется
оркестратору проекта):
- **Git pre-commit hook** (`tools/hooks/pre-commit`) можно расширить вызовом
  `--check` по всем staged `.xml`/`.mxl` — поздняя сетка, не даёт пройти
  в репозиторий даже если агент проигнорировал правило.
- **CI на PR** — тот же `--check` по diff отлавливает любые попытки на
  входе в `main`.

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
