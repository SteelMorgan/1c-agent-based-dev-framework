# Explore-04 — Аудит framework/skills/tool-usage/ + framework/skills/onescript/

> Дата: 2026-07-02. Метод: полное чтение всех 126 файлов (SKILL.md, references/*, скрипты, yaml/json — бегло) шестью параллельными агентами + ведущим агентом (onescript/, README.md). Суммарный объём: 24 358 строк (включая steps.json 5 581 и два ревью-скрипта ~2 100).

## Сводка

- **Файлов:** 126; **строк:** 24 358.
- **type:** проектно-специфичное 46, 1c-доменное 46, общепрограммистское 25, ассеты/лицензии/бинарники 9.
- **usefulness:** ядро процесса ~42, полезен ~72, сомнителен 6, кандидат на удаление/архив 0 (формально; см. кандидатов ниже).
- **verbosity:** low ~43, med ~50, high ~29.
- **probe_facts:** ~400 (396 с полем `expected_in_weights`; «не подлежит пробе» — только 8 записей: бинарные ассеты и голые конфиги).

### Кандидаты на удаление/архив/консолидацию (по совокупности признаков)

1. `v8-runner/agents/openai.yaml` — мёртвый артефакт: 4 строки метаданных, ссылок на него не найдено (сомнителен).
2. `browser-ui/visual-check/SKILL.md` — 21 строка, чистый маршрутизатор без собственного контента (сомнителен).
3. `diagnostics/agent-debug/references/learned-patterns.md` — один неподтверждённый паттерн со статусом candidate (сомнителен).
4. `xml-generation/references/behavioral-oracles.md` — внутренняя QA-инфраструктура CLI `xml-gen`, не нужна в рабочем цикле агента (сомнителен).
5. `onescript/autumn/reference.md` — список модулей экосистемы без потребителей в репо; аннотации дублируют autumn/SKILL.md (сомнителен).
6. `onescript/winow/SKILL.md` — веб-сервер на OneScript, потребителей в репо не найдено (сомнителен).
7. `cross-provider-review/scripts/{claude_opus_review,codex_review}.py` — ~700+ строк структурного дубля; кандидат на общий модуль.
8. Дубликаты LICENSE.txt (playwright / playwright-interactive / screenshot — идентичный Apache 2.0 с незаполненным копирайтом).

### Топ водянистых (verbosity=high, крупные)

- `tool-usage/README.md` (454) — числа навыков и % покрытия дублируют registry.yaml/mcp-inventory и требуют ручной синхронизации.
- `v8-runner/*` — WS-режим описан трижды (SKILL.md, project-workflows.md, testing.md); инцидент idle-handler (DRIVE 2026-05-11) — трижды; command-selection.md vs project-workflows.md — один набор команд в двух файлах.
- `diagnostics/runtime-investigation/SKILL.md` (340) и `bug-reporting/SKILL.md` (303) — ядро процесса, но редакторски раздуты.
- `web-test-1c/regress.md` (351) + recording.md (227) — перекрытие по API-примерам.
- skd-dsl/SKILL.md ↔ skd-edit/references/{fields,parameters}.md — повтор доменного описания ролей полей/флагов параметров; forms-toolkit: edit.md ↔ info.md — почти идентичные таблицы «вид элемента ↔ XML-тег».
- rac-use ↔ subsystem-update — дубли RAC-команд блокировок/сеансов; query-syntax-cheatsheet ↔ platform-data-core/SKILL.md — дубли примеров NULL/дат/строк.
- xml-generation/SKILL.md — дублирует собственные references/universal-commands.md и quick-примеры extension-operations.

Далее — полные пофайловые отчёты по частям A–G.

---

# ЧАСТЬ A-browser-ui

# Аудит: framework/skills/tool-usage/browser-ui/

Базовый путь для всех относительных путей ниже: `framework/skills/tool-usage/browser-ui/`

Всего обработано файлов: 33. Суммарно строк (по `wc -l`, включая бинарные/текстовые ассеты): **3592**.

Обновление (по указанию оркестратора): `probe_facts` заполнены для ВСЕХ файлов, включая проектно-специфичные. Для проектно-специфичных файлов факты сформулированы как вопрос через ближайшее публичное знание, с оценкой `expected_in_weights` (нет|частично|да) — знает ли базовая LLM это без чтения файла. `не подлежит пробе` оставлено только для чистых бинарных ассетов.

---

### gui-control/SKILL.md
- `lines`: 130
- `purpose`: Экстренная разблокировка зависших X11-окон/диалогов 1С (Xlib + fake_input) при завершении тестового прогона.
- `type`: проектно-специфичное (жёстко завязано на связку ЖР/`event-log-analysis`, VA MCP, `va-visual-check` этого фреймворка; не самостоятельный публичный инструмент)
- `probe_facts`:
  - "Как программно прочитать заголовок/класс окна X11 через `python-xlib` (`get_wm_name`, `get_wm_class`, `root.query_tree()`)?" — секция "1. Детектировать диалог ошибки"; expected_in_weights: да (стандартный публичный API python-xlib).
  - "Как симулировать нажатие клавиши в X11 через расширение XTEST (`Xlib.ext.xtest.fake_input`, `keysym_to_keycode`)?" — секция "2. Закрыть диалог и завершить базу"; expected_in_weights: частично (XTEST существует и известен, но конкретная связка `fake_input`+`keysym_to_keycode` менее хрестоматийна).
  - "Нужно ли устанавливать `DISPLAY=:99` до импорта Xlib/PIL при работе с Xvfb в контейнере?" — секция "Настройка окружения"/"Типичные ошибки"; expected_in_weights: да (общеизвестная практика Xvfb).
  - Специфика сверх весов: привязка последовательности Enter→Escape→Enter именно к диалогам 1С, требование сверяться с ЖР (`event-log-analysis`) и VA MCP capability check перед действием — собственная политика фреймворка, не публичное знание.
- `verbosity`: med (таблицы триггеров и capabilities несколько избыточны относительно простого алгоритма из 3 шагов)
- `usefulness`: полезен — реальный fallback-алгоритм для CI/Xvfb-агента с конкретным кодом (Xlib keycodes, query_tree), не просто декларация.
- `duplicate_of`: нет

### img-grid/SKILL.md
- `lines`: 93
- `purpose`: Наложение нумерованной сетки на скриншот печатной формы MXL для reverse-engineering пропорций колонок.
- `type`: проектно-специфичное (обёртка над `tools/img-grid/grid.py` этого репозитория)
- `probe_facts`:
  - "Как нарисовать сетку линий с подписями на изображении через Pillow (`PIL.ImageDraw.line`, поля/margin, сохранение как RGB PNG)?" — секция "Что делает скрипт"; expected_in_weights: частично (общая техника рисования сеток в Pillow известна, но конкретная реализация с полями 20px/24px и разной яркостью каждой 5-й/10-й линии — нет).
  - "Существует ли CLI-флаг `--cell-size`, который автоматически определяет число колонок/строк по размеру ячейки в пикселях, и как он соотносится с явным `--cols`/`--rows`?" — секция "Параметры"; expected_in_weights: нет (это специфичный интерфейс конкретного скрипта `tools/img-grid/grid.py`).
  - "Что такое макет MXL-документа 1С (печатная форма SpreadsheetDocument) и как задаются пропорции колонок через `columnWidths`?" — секция "Записать пропорции"; expected_in_weights: частично (формат MXL/SpreadsheetDocument известен в 1С-домене, но конкретный JSON-контракт `columnWidths` — собственный формат фреймворка).
- `verbosity`: low-med — пример с формой М-11 и разбором границ немного длинноват, но иллюстративен.
- `usefulness`: полезен — узкая, но конкретная задача (обратный инжиниринг MXL-макета) без альтернативного покрытия другим навыком.
- `duplicate_of`: нет

### playwright/agents/openai.yaml
- `lines`: 6
- `purpose`: Манифест интерфейса Codex-агента для skill "playwright" (имя, иконки, дефолтный промпт).
- `type`: общепрограммистское (стандартный формат манифеста скилла Codex/OpenAI, не 1С-специфика)
- `probe_facts`:
  - "Существует ли у Codex/OpenAI CLI формат `skill`-манифеста с полями `interface.display_name`/`short_description`/`icon_small`/`icon_large`/`default_prompt`?" — секция целиком; expected_in_weights: частично (концепция агентских skill-манифестов известна в общем, но именно эта точная схема полей Codex — нишевое знание).
  - "Указывают ли поля `icon_small`/`icon_large` на относительные пути внутри той же директории скилла (`./assets/...`)?" — секция целиком; expected_in_weights: да (обычная конвенция относительных путей в манифестах).
- `verbosity`: low
- `usefulness`: полезен — минимальный интеграционный файл, требуется форматом Codex skills.
- `duplicate_of`: структурно идентичен `playwright-interactive/agents/openai.yaml` и `screenshot/agents/openai.yaml` (тот же шаблон полей `interface.*`), различаются только значения.

### playwright/assets/playwright.png
- `lines`: бинарный ассет, 1730 байт визуального ресурса, не подлежит пробе.

### playwright/assets/playwright-small.svg
- `lines`: бинарный/визуальный ассет, 828 байт, не подлежит пробе.

### playwright/LICENSE.txt
- `lines`: 201
- `purpose`: Лицензия стороннего инструмента (Apache License 2.0, Microsoft Corporation) — покрывает материалы, заимствованные из microsoft/playwright-cli.
- `type`: лицензия стороннего инструмента (юридический текст)
- `probe_facts`:
  - "Требует ли Apache License 2.0 сохранять во всех производных работах уведомление об изменениях внесённых в файлы (§4b) и копию самой лицензии (§4a)?" — разделы 4(a)/4(b); expected_in_weights: да (стандартное и широко известное условие Apache 2.0).
  - "Прекращается ли патентная лицензия по Apache 2.0 автоматически для стороны, подавшей патентный иск против участников (§3)?" — раздел 3 "Grant of Patent License"; expected_in_weights: да (известное патентное условие Apache 2.0, отличающее её от MIT/BSD).
- `verbosity`: low (юридический шаблон, без специфики фреймворка)
- `usefulness`: полезен как формальное требование распространения (Apache 2.0 обязывает включать копию лицензии), не как рабочая инструкция.
- `duplicate_of`: побайтово идентичен `playwright-interactive/LICENSE.txt`; тот же текст (иной copyright-плейсхолдер) в `screenshot/LICENSE.txt`.

### playwright/NOTICE.txt
- `lines`: 14
- `purpose`: Атрибуция: материал происходит из `microsoft/playwright-cli` (`skills/playwright-cli/SKILL.md`), лицензия Apache 2.0, с пометкой о модификациях (добавлен wrapper-скрипт и локальные reference-файлы).
- `type`: проектно-специфичное по содержанию атрибуции (какой именно материал и как модифицирован — факт про конкретный fork/адаптацию этого фреймворка), хотя сам факт существования апстрима — публичный
- `probe_facts`:
  - "Существует ли публичный репозиторий `microsoft/playwright-cli`, распространяющий Playwright-скиллы под Apache 2.0?" — секция целиком; expected_in_weights: частично (Microsoft/Playwright хорошо известны, но существование именно этого репозитория и его путь `skills/playwright-cli/SKILL.md` — нишевая деталь).
  - "Что именно было изменено локальным форком относительно апстрима (добавлен ли wrapper-скрипт и локальные reference-guides, или это переименование/структурная адаптация)?" — секция "Modifications"; expected_in_weights: нет (это факт только о данном репозитории, не выводится из общих знаний об апстриме).
- `verbosity`: low
- `usefulness`: полезен — юридически необходимая атрибуция при заимствовании стороннего материала.
- `duplicate_of`: содержательно пересекается с `playwright-interactive/NOTICE.txt`, который прямо ссылается на этот файл ("The local `playwright` skill attributes those assets...").

### playwright/references/cli.md
- `lines`: 116
- `purpose`: Полный перечень команд обёрточного `pwcli` (клики, клавиатура, мышь, вкладки, DevTools, сессии) для CLI Playwright.
- `type`: общепрограммистское
- `probe_facts`:
  - "Существует ли npm-пакет `@playwright/cli`, дающий интерактивный CLI поверх Playwright с командами вроде `snapshot`/`click`/`type`/`screenshot`?" — предисловие + весь файл; expected_in_weights: частично (Playwright как библиотека широко известен; отдельный официальный `@playwright/cli` пакет — более новая и нишевая часть экосистемы, знание не гарантировано).
  - "Даёт ли команда `snapshot` в таком CLI стабильные ссылки на элементы (`e3`, `e7`, ...), которые затем используются в `click`/`fill`/`hover`?" — секция "Core"; expected_in_weights: частично (сама идея accessibility-snapshot с element refs соответствует публичному Playwright MCP/agent-tooling паттерну, но точный синтаксис `eN` — деталь конкретной реализации).
  - "Поддерживает ли такой CLI именованные изолированные сессии через флаг `--session`/переменную окружения (`PLAYWRIGHT_CLI_SESSION`)?" — секция "Sessions"; expected_in_weights: нет (специфичная деталь данной обёртки, не общее свойство Playwright).
- `verbosity`: low — почти чистый справочник команд, минимум прозы.
- `usefulness`: ядро процесса — это единственный полный CLI-референс для playwright-обёртки, на который ссылается SKILL.md.
- `duplicate_of`: нет прямого дублирования, но пересекается по набору команд с `playwright-interactive/references/snippets.md` (тот работает через `js_repl`/JS API вместо CLI-команд — иной интерфейс к тому же Playwright).

### playwright/references/workflows.md
- `lines`: 95
- `purpose`: Типовые workflow-рецепты `pwcli` (форма, извлечение данных, отладка/tracing, сессии, конфиг-файл, troubleshooting).
- `type`: общепрограммистское
- `probe_facts`:
  - "Читает ли инструмент по умолчанию файл конфигурации `playwright-cli.json` из текущей директории, с возможностью указать другой через `--config`?" — секция "Configuration file"; expected_in_weights: нет (специфика конкретного `@playwright/cli`, не документировано в общих знаниях о Playwright).
  - "Поддерживает ли конфиг вложенные ключи `browser.launchOptions.headless` и `browser.contextOptions.viewport`?" — тот же раздел; expected_in_weights: частично (структура `launchOptions`/`contextOptions` совпадает с обычным Playwright Node.js API `browser.newContext(...)`, это действительно стандартные термины Playwright).
  - "Является ли повторный вызов snapshot стандартным способом восстановления после ошибки протухшего element-ref в agent-driven Playwright автоматизации?" — секция "Troubleshooting"; expected_in_weights: да (общий паттерн в agent/MCP-стиле автоматизации браузера).
- `verbosity`: low-med
- `usefulness`: полезен — конкретные готовые рецепты (форма, tracing, sessions), развивающие `cli.md`.
- `duplicate_of`: пересекается с `cli.md` (общий core-loop `open`→`snapshot`→`click`→`snapshot` повторяется в обоих) и с `playwright/SKILL.md` (раздел "Recommended patterns" почти дословно повторяет "Form fill and submit" из этого файла).

### playwright/scripts/playwright_cli.sh
- `lines`: 25
- `purpose`: Bash-обёртка, запускающая `npx --yes --package @playwright/cli playwright-cli` с опциональной подстановкой `--session` из `PLAYWRIGHT_CLI_SESSION`.
- `type`: общепрограммистское
- `probe_facts`:
  - "Является ли `npx --yes --package <pkg> <bin>` стандартным способом одноразового запуска npm-бинаря без глобальной установки?" — весь скрипт; expected_in_weights: да (широко известный паттерн npx).
  - "Проверяет ли скрипт наличие уже переданного флага `--session`/`--session=*` в аргументах перед тем как подставить `PLAYWRIGHT_CLI_SESSION`, чтобы избежать дублирования флага?" — блок `has_session_flag`; expected_in_weights: нет (это деталь реализации конкретного wrapper-скрипта).
  - "Использует ли скрипт `set -euo pipefail` и явную проверку `command -v npx` перед запуском?" — начало файла; expected_in_weights: да (стандартная защитная практика bash-скриптов).
- `verbosity`: low
- `usefulness`: полезен — тонкая, корректно написанная обёртка (использует `set -euo pipefail`, проверку `npx`).
- `duplicate_of`: нет

### playwright/SKILL.md
- `lines`: 93
- `purpose`: Точка входа skill'а: CLI-first автоматизация браузера через `pwcli`, включая раздел "1C Boundary" (Playwright — не основной маршрут для 1С UI, приоритет `va-visual-check`).
- `type`: общепрограммистское (основной контент — Playwright CLI), с одной проектно-специфичной вставкой ("1C Boundary", ссылка на `va-visual-check`)
- `probe_facts`:
  - "Является ли последовательность `open → snapshot → interact по refs → re-snapshot` стандартной рекомендуемой практикой для агентной автоматизации браузера через Playwright/аналогичные accessibility-snapshot инструменты?" — секция "Core workflow"; expected_in_weights: да (общий паттерн, используемый и в Playwright MCP, и в аналогичных инструментах).
  - "Рекомендуется ли предпочитать явные команды (`click`, `fill`) вместо `eval`/произвольного JS-кода при агентной браузерной автоматизации, чтобы действия были прозрачны и воспроизводимы?" — секция "Guardrails"; expected_in_weights: частично (общая идея есть в лучших практиках агентных инструментов, конкретная формулировка — авторская).
  - "Существует ли для 1С:Предприятие профильный процесс визуальной проверки форм через Vanessa Automation/TestClient (`va-visual-check`), которому Playwright уступает приоритет?" — секция "1C Boundary"; expected_in_weights: нет (это внутреннее правило маршрутизации данного фреймворка, не публичное знание об экосистеме 1С или Playwright).
- `verbosity`: med — раздел "Recommended patterns" дублирует workflows.md.
- `usefulness`: ядро процесса — единственная входная точка для использования playwright-cli в агенте.
- `duplicate_of`: пересекается с `playwright-interactive/SKILL.md` почти дословно в разделе "1C Boundary" (структура фразы идентична, только формулировки чуть отличаются); раздел "Recommended patterns" дублирует `references/workflows.md`.

### playwright-interactive/agents/openai.yaml
- `lines`: 6
- `purpose`: Манифест интерфейса Codex-агента для skill "playwright-interactive" (Electron/persistent QA).
- `type`: общепрограммистское
- `probe_facts`:
  - "Использует ли манифест ту же схему полей `interface.display_name/short_description/icon_small/icon_large/default_prompt`, что и другие Codex-скиллы?" — секция целиком; expected_in_weights: частично (общая идея манифеста — да, точная схема — нет).
  - "Указывают ли `icon_small`/`icon_large` на переиспользуемые (не собственные) файлы `./assets/playwright-small.svg`/`./assets/playwright.png`?" — секция целиком; expected_in_weights: нет (это факт конкретно про данный репозиторий/переиспользование ассетов).
- `verbosity`: low
- `usefulness`: полезен — обязательный манифест по формату Codex skills.
- `duplicate_of`: тот же шаблон, что `playwright/agents/openai.yaml` и `screenshot/agents/openai.yaml`.

### playwright-interactive/assets/playwright.png
- `lines`: бинарный ассет, 1785 байт визуального ресурса, не подлежит пробе.

### playwright-interactive/assets/playwright-small.svg
- `lines`: бинарный/визуальный ассет, 843 байта, не подлежит пробе.

### playwright-interactive/LICENSE.txt
- `lines`: 201
- `purpose`: Лицензия стороннего инструмента (Apache License 2.0, Microsoft Corporation), идентична `playwright/LICENSE.txt`.
- `type`: лицензия стороннего инструмента (юридический текст)
- `probe_facts`:
  - "Обязывает ли Apache 2.0 распространителя предоставлять получателям копию самой лицензии вместе с производной работой (§4a)?" — раздел 4(a); expected_in_weights: да.
  - "Разрешает ли Apache 2.0 добавлять собственные условия лицензирования к модификациям, не отменяя условий для оригинальной части (§4, последний абзац)?" — раздел 4, финальный абзац; expected_in_weights: да (известное свойство Apache 2.0 как permissive-лицензии).
- `verbosity`: low
- `usefulness`: полезен как формальное требование распространения.
- `duplicate_of`: побайтово идентичен `playwright/LICENSE.txt`.

### playwright-interactive/NOTICE.txt
- `lines`: 13
- `purpose`: Атрибуция: скилл переиспользует иконки Playwright из `playwright`-скилла, с пометкой о модификациях (репакет ассетов под `js_repl`-фокусированный скилл, новые инструкции по persistent-отладке).
- `type`: проектно-специфичное по содержанию (факт о конкретном переиспользовании ассетов внутри этого репозитория)
- `probe_facts`:
  - "Переиспользует ли данный скилл иконки из соседнего скилла `playwright` вместо создания собственных уникальных ассетов?" — секция целиком; expected_in_weights: нет (это внутрирепозиторный факт, не выводимый из общих знаний).
  - "Написаны ли инструкции по persistent browser-debugging заново для этого скилла, а не скопированы из `playwright`-скилла?" — раздел "Modifications"; expected_in_weights: нет (специфика конкретной адаптации).
- `verbosity`: low
- `usefulness`: полезен — юридически необходимая атрибуция переиспользованных ассетов.
- `duplicate_of`: явно ссылается на `playwright/NOTICE.txt` и переиспользует его ассеты — прямое пересечение по origin/лицензии.

### playwright-interactive/references/snippets.md
- `lines`: 344
- `purpose`: Готовые JS-сниппеты для копипаста в `js_repl`-сессии: bootstrap, web/mobile/native-window контексты, Electron-сессия и рестарт, скриншот-хелперы (CSS-нормализация), проверка viewport fit, cleanup.
- `type`: общепрограммистское
- `probe_facts`:
  - "Запускается ли headless-браузер Chromium через `playwright`-пакет вызовом `chromium.launch({ headless: false })`, а Electron-приложение — через `_electron.launch({ args: [...] })`?" — секция "Bootstrap"; expected_in_weights: да (это стандартный, широко документированный публичный API Playwright).
  - "Задаётся ли мобильная эмуляция в Playwright через `browser.newContext({ viewport, isMobile: true, hasTouch: true })`?" — секция "Mobile Web Context"; expected_in_weights: да (стандартные, документированные опции Playwright `BrowserContext`).
  - "Можно ли получить скриншот окна Electron из main-процесса через `BrowserWindow.getAllWindows()[0].capturePage()` и `NativeImage.resize(...)`?" — секция "Electron CSS normalization"; expected_in_weights: частично (`capturePage`/`NativeImage` — реальный публичный Electron API, но именно такая комбинация для CSS-нормализации через resize — специфический приём этого файла).
  - "Является ли специфичный для Codex вызов `codex.emitImage({ bytes, mimeType })` частью публичного/документированного Codex API?" — секция "Shared emit/click helpers"; expected_in_weights: нет (специфичный внутренний API среды Codex/`js_repl`, не общедоступное знание).
- `verbosity`: high — много почти идентичных блоков (web/mobile/native-window контексты differ только парой строк), могло бы быть компактнее через параметризацию.
- `usefulness`: полезен — это фактическое "тело" persistent-сессии, на которое многократно ссылается `SKILL.md` (единственное место с реальным кодом).
- `duplicate_of`: нет прямого дублирования с другими файлами списка, но сильно самоповторяющийся внутри себя (desktop/mobile/native-window блоки почти идентичны).

### playwright-interactive/SKILL.md
- `lines`: 162
- `purpose`: Инструкция по persistent Playwright-сессии в `js_repl` для итеративной отладки web/Electron UI: режимы сессий, reload/relaunch, скриншоты, viewport-проверки, чеклисты QA/signoff, типичные ошибки.
- `type`: общепрограммистское (основной контент), с проектной вставкой "1C Boundary"
- `probe_facts`:
  - "Включается ли feature `js_repl` в Codex CLI через `~/.codex/config.toml` (`[features] js_repl = true`) или флаг `--enable js_repl`?" — секция "Preconditions"; expected_in_weights: нет (специфичная конфигурация конкретного CLI-инструмента Codex).
  - "Требует ли установка браузерного движка команды `npx playwright install chromium`?" — секция "One-time setup"; expected_in_weights: да (это стандартная, широко известная команда Playwright).
  - "Уничтожает ли `js_repl_reset` живые хендлы Playwright/Electron, требуя пересоздания сессии?" — секция "Preconditions"; expected_in_weights: нет (внутренняя особенность среды выполнения Codex `js_repl`, не общее знание).
- `verbosity`: high — очень подробные чеклисты (Functional QA, Visual QA, Signoff) занимают большую часть файла относительно фактической механики.
- `usefulness`: ядро процесса для Electron/web QA-сценариев в интерактивном режиме — детальные чеклисты реально операционализируемы, не декоративны.
- `duplicate_of`: раздел "1C Boundary" почти зеркален `playwright/SKILL.md`; общий контур (bootstrap→interact→re-snapshot/reload→signoff) концептуально пересекается с `playwright/references/workflows.md`, но целевой домен другой (persistent REPL vs одноразовые CLI-вызовы).

### screenshot/agents/openai.yaml
- `lines`: 6
- `purpose`: Манифест интерфейса Codex-агента для skill "screenshot" (захват экрана).
- `type`: общепрограммистское
- `probe_facts`:
  - "Соответствует ли структура полей той же схеме `interface.*`, что в других Codex-скиллах данного репозитория?" — секция целиком; expected_in_weights: частично.
  - "Указывают ли иконки на `./assets/screenshot-small.svg` и `./assets/screenshot.png` относительно каталога скилла?" — секция целиком; expected_in_weights: да (обычная конвенция относительных путей).
- `verbosity`: low
- `usefulness`: полезен — обязательный манифест.
- `duplicate_of`: тот же шаблон, что и оба `.../agents/openai.yaml` из playwright-скиллов.

### screenshot/assets/screenshot.png
- `lines`: бинарный ассет, 860 байт визуального ресурса, не подлежит пробе.

### screenshot/assets/screenshot-small.svg
- `lines`: бинарный/визуальный ассет, 1019 байт, не подлежит пробе.

### screenshot/LICENSE.txt
- `lines`: 201
- `purpose`: Шаблон лицензии Apache License 2.0 (генерическая версия с плейсхолдером `Copyright [yyyy] [name of copyright owner]`, не заполнена конкретным правообладателем — в отличие от playwright-лицензий, где явно указан Microsoft Corporation).
- `type`: лицензия стороннего/шаблонного характера
- `probe_facts`:
  - "Является ли `Copyright [yyyy] [name of copyright owner]` стандартным незаполненным плейсхолдером из официального Apache 2.0 приложения (Appendix), а не индивидуальной атрибуцией?" — раздел "APPENDIX"; expected_in_weights: да (это буквально официальный текст-образец из самой лицензии Apache 2.0).
  - "Требует ли Apache 2.0 явно распространять текст NOTICE-файла вместе с производной работой, если он был у оригинала (§4d)?" — раздел 4(d); expected_in_weights: да.
- `verbosity`: low
- `usefulness`: формально необходим при распространении заимствованного материала под Apache 2.0, но незаполненный плейсхолдер копирайта наводит на вопрос, не забыли ли указать реального правообладателя (см. сквозное наблюдение №3).
- `duplicate_of`: тот же текст Apache License 2.0, что в `playwright/LICENSE.txt` и `playwright-interactive/LICENSE.txt`, но с незаполненным copyright-плейсхолдером.

### screenshot/scripts/ensure_macos_permissions.sh
- `lines`: 54
- `purpose`: Проверяет/запрашивает разрешение macOS Screen Recording перед захватом скриншота (обёртка над `macos_permissions.swift`).
- `type`: общепрограммистское
- `probe_facts`:
  - "Существует ли на macOS системное разрешение 'Screen Recording' (Privacy & Security), без которого приложение не может делать скриншоты других окон/экрана?" — весь скрипт; expected_in_weights: да (широко известный факт про macOS начиная с Catalina).
  - "Может ли повторный программный запрос разрешения не показать системный диалог, а сразу открыть System Settings, если пользователь уже отклонял запрос ранее?" — итоговый блок с инструкцией; expected_in_weights: частично (общее поведение известно macOS-разработчикам, но не всем).
  - "Специфично ли для данного скрипта то, что в песочнице (`CODEX_SANDBOX` установлен) проверка разрешений намеренно блокируется с `exit 3`?" — блок проверки `CODEX_SANDBOX`; expected_in_weights: нет (внутреннее поведение конкретно этой обвязки Codex).
- `verbosity`: low
- `usefulness`: полезен — необходимый преflight-шаг конкретно для macOS permission-модели, реально решает типовую проблему первого запуска.
- `duplicate_of`: логически парный к `macos_permissions.swift` (сам скрипт лишь human-friendly обёртка над его JSON-выводом), не дублирует по коду.

### screenshot/scripts/macos_display_info.swift
- `lines`: 22
- `purpose`: Печатает JSON со списком индексов подключённых дисплеев (`NSScreen.screens`).
- `type`: общепрограммистское
- `probe_facts`:
  - "Даёт ли `NSScreen.screens` (AppKit) список подключённых экранов, а `.count` — их число?" — весь файл; expected_in_weights: да (стандартный публичный AppKit API).
  - "Использует ли скрипт `JSONEncoder` с `.sortedKeys` для детерминированного вывода JSON?" — блок `encoder.outputFormatting`; expected_in_weights: частично (это публичный API Foundation, но конкретное решение сортировать ключи — деталь реализации).
- `verbosity`: low
- `usefulness`: полезен — маленький, но необходимый building block для multi-display capture на macOS (`take_screenshot.py` вызывает его через `macos_display_indexes()`).
- `duplicate_of`: нет — уникальная роль (displays) в отличие от `macos_window_info.swift` (windows) и `macos_permissions.swift` (permissions).

### screenshot/scripts/macos_permissions.swift
- `lines`: 40
- `purpose`: Проверяет/запрашивает разрешение Screen Recording через `CGPreflightScreenCaptureAccess`/`CGRequestScreenCaptureAccess`, печатает JSON-статус.
- `type`: общепрограммистское
- `probe_facts`:
  - "Существуют ли публичные функции CoreGraphics `CGPreflightScreenCaptureAccess()` (проверка без запроса) и `CGRequestScreenCaptureAccess()` (с показом системного диалога), доступные с macOS 10.15+?" — весь файл; expected_in_weights: да (документированный публичный Apple API).
  - "Обрабатывает ли скрипт передаваемый аргумент `--request` командной строки, чтобы решить, запрашивать ли доступ активно или только проверить статус?" — начало файла (`shouldRequest`); expected_in_weights: частично (сама идея разделения check/request распространена, конкретный флаг — деталь реализации).
- `verbosity`: low
- `usefulness`: полезен — ядро permission-проверки, вызывается и из `.sh`, и напрямую из `take_screenshot.py`.
- `duplicate_of`: нет прямого; логически связан с `ensure_macos_permissions.sh` (тот — его CLI-обёртка с текстовыми подсказками).

### screenshot/scripts/macos_window_info.swift
- `lines`: 126
- `purpose`: Ищет и ранжирует окна macOS через `CGWindowListCopyWindowInfo` по имени приложения/заголовку/frontmost, печатает JSON (выбранное окно + опциональный полный список).
- `type`: общепрограммистское
- `probe_facts`:
  - "Существует ли публичный CoreGraphics API `CGWindowListCopyWindowInfo(options, kCGNullWindowID)`, возвращающий массив словарей с метаданными видимых окон (владелец, имя, слой, границы)?" — секция сбора окон; expected_in_weights: да (документированный, широко используемый публичный API для перечисления окон на macOS).
  - "Является ли фильтрация 'on-screen only, exclude desktop elements' (`[.optionOnScreenOnly, .excludeDesktopElements]`) стандартным набором опций для получения списка реально видимых пользовательских окон?" — та же секция; expected_in_weights: частично (опции публичные и документированы, но их типовая комбинация для такой задачи — уже прикладное знание).
  - "Специфично ли для данного скрипта правило ранжирования (сначала normal-layer окна, затем по убыванию площади) при выборе 'наиболее вероятного' целевого окна приложения?" — функция `rank`; expected_in_weights: нет (эвристика конкретной реализации, не публичный API).
- `verbosity`: low-med — самый длинный из swift-скриптов, но пропорционально функциональности (много CLI-флагов: `--app`, `--window-name`, `--frontmost`, `--list`).
- `usefulness`: полезен — реализует нетривиальную логику выбора нужного окна для скриншота конкретного приложения на macOS.
- `duplicate_of`: нет — уникальная роль (windows) в отличие от display/permissions скриптов.

### screenshot/scripts/take_screenshot.ps1
- `lines`: 163
- `purpose`: PowerShell-скрипт захвата скриншота на Windows (весь экран/регион/активное окно/по хендлу) через `System.Drawing`/`user32.dll` P/Invoke.
- `type`: общепрограммистское
- `probe_facts`:
  - "Является ли `[user32.dll]::GetForegroundWindow()` + `GetWindowRect(...)` через P/Invoke стандартным способом получить хендл и границы активного окна Windows из .NET/PowerShell?" — блок `Add-Type` с `NativeMethods`; expected_in_weights: да (широко известная и часто демонстрируемая техника).
  - "Является ли `System.Drawing.Graphics.CopyFromScreen(source, target, size)` стандартным способом сделать скриншот произвольной области экрана в .NET?" — конец файла; expected_in_weights: да (классический, документированный .NET Framework приём).
  - "Поддерживает ли `System.Drawing.Imaging.ImageFormat` перечисление форматов Png/Jpeg/Bmp при сохранении `Bitmap.Save(...)`?" — блок `$imageFormat`; expected_in_weights: да (стандартный публичный .NET API).
- `verbosity`: low-med — есть некоторая ручная работа с путями (`Resolve-OutputPath`), но пропорционально задаче.
- `usefulness`: полезен — единственный Windows-путь захвата скриншота в этом скилле (Python-скрипт explicitно отказывается от Windows и отсылает сюда).
- `duplicate_of`: функциональный аналог `take_screenshot.py` для другой ОС (Windows vs macOS/Linux) — не дублирование кода, а ветвление по платформе с примерно эквивалентным набором опций (`--region`/`-Region`, `--active-window`/`-ActiveWindow`, `--window-id`/`-WindowHandle`).

### screenshot/scripts/take_screenshot.py
- `lines`: 585
- `purpose`: Кроссплатформенный (macOS/Linux, с явным отказом на Windows) Python-скрипт захвата скриншота: разрешения macOS, поиск окон/дисплеев через swift-хелперы, множественные Linux-бэкенды (scrot/gnome-screenshot/ImageMagick), тестовый режим с фейковыми путями и PNG.
- `type`: общепрограммистское
- `probe_facts`:
  - "Является ли предпочтение `scrot` → `gnome-screenshot` → ImageMagick `import` типичным порядком fallback для скриншотов на Linux в средах без гарантированного специфичного desktop environment?" — функция `capture_linux`; expected_in_weights: да (это общеизвестный набор Linux screenshot-утилит и обычный порядок деградации).
  - "Является ли команда macOS `screencapture -x -t<format>` с флагами `-i` (интерактив), `-D<display>`, `-l<window_id>`, `-R<x,y,w,h>` стандартным встроенным инструментом захвата экрана в macOS?" — функция `capture_macos`; expected_in_weights: да (widely known встроенная macOS-утилита с задокументированными флагами).
  - "Читается ли дефолтная директория сохранения скриншотов на macOS через `defaults read com.apple.screencapture location`?" — функция `mac_default_dir`; expected_in_weights: частично (факт про существование этого defaults-ключа известен продвинутым macOS-пользователям/разработчикам, но не тривиален).
  - "Специфичен ли для данного скрипта набор переменных окружения тестового режима (`CODEX_SCREENSHOT_TEST_MODE`, `_TEST_PLATFORM`, `_TEST_WINDOWS`, `_TEST_DISPLAYS`), позволяющий детерминированно тестировать без реального захвата экрана?" — начало файла; expected_in_weights: нет (это внутренний тестовый механизм именно этого скрипта).
- `verbosity`: med — размер оправдан числом веток (3 ОС/несколько инструментов/тестовый режим), но некоторые проверки взаимоисключающих флагов (`main()`, ~15 строк `raise SystemExit`) можно было бы свернуть в таблицу правил.
- `usefulness`: ядро процесса — самый содержательный исполняемый файл во всём скилле "screenshot", остальные скрипты (swift, sh) вызываются им как хелперы.
- `duplicate_of`: функциональный (не буквальный) аналог `take_screenshot.ps1` для Windows-ветки; сам явно делегирует Windows в `.ps1` (`raise SystemExit("Windows support lives in scripts/take_screenshot.ps1...")`).

### screenshot/SKILL.md
- `lines`: 101
- `purpose`: Точка входа skill'а "screenshot": правила выбора пути сохранения, macOS preflight, кроссплатформенные опции Python-хелпера, приоритет Linux-инструментов, PowerShell-хелпер для Windows, прямые OS-команды, обработка ошибок.
- `type`: общепрограммистское (основной контент), с проектными вставками про `va-visual-check`/1С-fallback
- `probe_facts`:
  - "Является ли `ffmpeg -f x11grab -video_size WxH -i :99 -frames:v 1 out.png` рабочим способом сделать один кадр-скриншот с виртуального X11-дисплея (Xvfb) в контейнере?" — секция "Linux tool selection"; expected_in_weights: да (известный и часто используемый приём для headless Linux/CI).
  - "Показывает ли `xdpyinfo | grep dimensions` разрешение текущего X11-дисплея?" — та же секция; expected_in_weights: да (стандартная X11-утилита и общеизвестный способ узнать разрешение).
  - "Ограничены ли флаги `--app`/`--window-name`/`--list-windows` только macOS (в отличие от `--active-window`/`--window-id` на Linux)?" — примечание после таблицы опций; expected_in_weights: нет (это ограничение конкретной реализации `take_screenshot.py`, а не общее свойство ОС).
- `verbosity`: med — таблицы опций для Python и PowerShell почти дублируют друг друга по структуре (что оправдано, т.к. описывают разные интерфейсы).
- `usefulness`: ядро процесса — единственная точка входа, агрегирующая все скрипты скилла в понятные правила выбора пути и инструмента.
- `duplicate_of`: раздел "Прямые OS-снимки" частично дублирует функциональность, уже покрытую `take_screenshot.py`/`.ps1` (даёт сырые команды `screencapture`/`scrot`/`ffmpeg` как альтернативу скриптам).

### visual-check/SKILL.md
- `lines`: 21
- `purpose`: Deprecated-заглушка/редирект: указывает, что визуальная проверка 1С-форм перенесена в `va-visual-check` (+ `form-visual-requirements` для оценки качества формы).
- `type`: проектно-специфичное (чистая внутренняя маршрутизация фреймворка, нет самостоятельного контента)
- `probe_facts`:
  - "Существует ли инструмент 'Vanessa Automation' — открытая BDD/Gherkin-платформа для функционального тестирования 1С:Предприятие через TestClient?" — упоминание "Vanessa/TestClient + VA MCP"; expected_in_weights: частично (Vanessa Automation — реальный известный в 1С-сообществе инструмент, но глубина знания модели о нём ограничена и специфична для домена 1С).
  - "Является ли headless-запуск через X11/Xvfb типовым способом гонять GUI-based тесты 1С (толстый/тонкий клиент) в CI без физического дисплея?" — упоминание "Linux headless X11/Xvfb рецепт"; expected_in_weights: частично (общая идея Xvfb для headless GUI широко известна, но привязка именно к 1С-клиенту — нет).
  - "Является ли данный файл просто редиректом на другой навык (`va-visual-check`), без описания самой техники визуальной проверки?" — весь файл; expected_in_weights: нет (это факт о структуре именно этого репозитория/фреймворка).
- `verbosity`: low
- `usefulness`: сомнителен — весь файл это переадресация; полезен только пока остаются старые ссылки на `visual-check`, иначе кандидат на архивацию после чистки всех входящих ссылок.
- `duplicate_of`: по сути указывает на `va-visual-check`/`form-visual-requirements` (файлы вне текущего списка) — не хранит уникальной информации сам по себе.

### web-test-1c/recording.md
- `lines`: 227
- `purpose`: Сравнение двух путей видеозаписи для 1С (Vanessa Automation vs Playwright/`web-test-1c`), полный API записи Playwright-пути (`startRecording`/`stopRecording`/`showCaption`/`addNarration`/оверлеи), конфигурация TTS, troubleshooting.
- `type`: проектно-специфичное (API `startRecording`/`showCaption`/`.v8-project.json`/`webtest.config.mjs` — собственный инструмент этого фреймворка поверх Playwright, не публичный 1С-инструмент)
- `probe_facts`:
  - "Умеет ли Vanessa Automation из коробки записывать видео прогона Gherkin-сценария и автоматически генерировать субтитры из текстов шагов?" — раздел "Путь 1: Запись через Vanessa Automation"; expected_in_weights: частично (Vanessa Automation как инструмент известен в 1С-сообществе, но детальная фича автогенерации субтитров из шагов — узкоспециализированное знание, вряд ли широко известное).
  - "Требуется ли для наложения TTS-озвучки на записанное видео внешний инструмент кодирования (ffmpeg) и/или сервис синтеза речи (например, Microsoft Edge TTS без API-ключа)?" — раздел "Путь 2", "Предварительные требования"; expected_in_weights: частично (сама идея TTS-через-Edge и необходимость ffmpeg для муксинга видео/аудио — общеизвестные технические факты по отдельности, но их конкретная связка в этом инструменте — нет).
  - "Является ли `showCaption(text)` + последующая пауза на расчётное время озвучки (~70 мс/символ) типовым паттерном синхронизации субтитров с TTS в видео-автоматизации?" — раздел "Smart TTS wait"; expected_in_weights: нет (это специфическая эвристика именно данного инструмента, не общепринятый стандарт).
- `verbosity`: med — таблицы параметров подробны, но соответствуют реальному API; раздел про Vanessa-профиль короче и по делу.
- `usefulness`: полезен — реальный конкретный API с примером полного сценария (заголовок→шаги→озвучка), не абстрактная декларация.
- `duplicate_of`: нет прямого; пересекается по теме "запись видео" с упоминаниями в `web-test-1c/SKILL.md` (раздел "Запись видео и субтитры" там — сжатый анонс, здесь — полное раскрытие).

### web-test-1c/regress.md
- `lines`: 351
- `purpose`: Полное описание регресс-движка для 1С поверх Playwright: структура сьютов/тестов, контракт `ctx`, `assert.*`, `webtest.config.mjs`, `_hooks.mjs`, CLI-запуск, готовые паттерны (СКД-отчёт, multi-user, параметризация, баг-репродукция), severity, антипаттерны, разбор падений.
- `type`: проектно-специфичное (собственный test-runner `tools/web-test`/`run.mjs` этого фреймворка, конкретный API `navigateSection`/`fillFields`/`readTable` и т.д. — не публичный инструмент 1С-экосистемы)
- `probe_facts`:
  - "Является ли структура тест-файла с именованными экспортами (`export const name`, `export default async function(ctx) {...}`, `setup`/`teardown`) распространённым паттерном в JS/TS custom test runner'ах (аналогично Jest/AVA по духу, но не идентично)?" — раздел "Анатомия тест-файла"; expected_in_weights: частично (общая идея модульных тест-файлов с метаданными через экспорты знакома по JS-экосистеме, но точный контракт — нет).
  - "Является ли Система Компоновки Данных (СКД) стандартным механизмом построения отчётов в 1С:Предприятие, а `readSpreadsheet()`/сброс 'стандартных настроек' — типовым паттерном работы с уже сформированным отчётом?" — раздел 'СКД-отчёт'; expected_in_weights: частично (СКД — известный в 1С-домене термин, но конкретный workflow сброса пользовательских настроек и его связь с API `readSpreadsheet` — нет).
  - "Существует ли для этого движка формальный контракт multi-user тестов (`export const contexts = [...]`) для сценариев с несколькими параллельными пользовательскими сессиями 1С, при этом отмеченный как ещё не перенесённый в текущий runtime?" — раздел "ctx.testInfo" / "Multi-user процесс"; expected_in_weights: нет (это факт о текущем статусе конкретной реализации инструмента).
  - "Считается ли `sleep`/фиксированный `wait()` вместо ожидания конкретного состояния типичным антипаттерном автоматизированного UI-тестирования (flaky tests)?" — раздел "Антипаттерны"; expected_in_weights: да (это общеизвестный, широко описанный антипаттерн в тестировании UI, не специфичный для 1С).
- `verbosity`: med — самый длинный файл в скилле, но почти всё содержимое — конкретные примеры кода/конфигов, а не общие рассуждения; раздел "Антипаттерны" компактен и по делу.
- `usefulness`: ядро процесса — это фактическая спецификация регресс-тестирования 1С-решений в фреймворке, явно разграничивает когда `test`, а когда `run`/`exec`.
- `duplicate_of`: явно отмечает несоответствие текущей реализации: multi-user `contexts` описаны как "целевой контракт", но "пока не включён в runtime `tools/web-test`" — то же предупреждение почти дословно повторено в `web-test-1c/SKILL.md` (раздел "Регресс-движок", "Ограничение текущего runtime").

### web-test-1c/SKILL.md
- `lines`: 163
- `purpose`: Точка входа skill'а "web-test-1c": семантический слой поверх Playwright для DOM веб-клиента 1С — граница применения относительно `va-visual-check`, установка, режимы (`run`/`start`/`test`/`exec`/`shot`/`stop`), полный перечень API навигации/чтения/действий, особенности (headed mode, Ctrl+V, fuzzy matching), горячие клавиши 1С, ссылки на регресс и запись видео.
- `type`: проектно-специфичное (собственный API/CLI `tools/web-test`, хотя упоминает подлинные факты платформы 1С)
- `probe_facts`:
  - "Существуют ли в 1С:Предприятие для управляемых форм стандартные горячие клавиши: `F4` — открыть форму выбора для ссылочного поля, `Shift+F4` — очистить значение ссылочного поля, `F8` — создать новый элемент из ссылочного поля?" — секция "Горячие клавиши 1С"; expected_in_weights: частично (это подлинные платформенные хоткеи 1С, известные в 1С-сообществе; общая LLM может знать часть из них с разной степенью уверенности, но не гарантированно все и не гарантированно точно).
  - "Требует ли веб-клиент 1С:Предприятие эмуляции 'доверенных' (trusted) браузерных событий при вводе текста (например, через `Ctrl+V`/буфер обмена), потому что программный `page.fill()` Playwright им игнорируется?" — раздел "Важные особенности"; expected_in_weights: нет (это узкая, нетривиальная деталь именно о специфике DOM веб-клиента 1С, маловероятно присутствующая в общих знаниях модели).
  - "Совпадает ли расширение файлов внешних обработок/отчётов 1С (`.epf`/`.erf`) с общеизвестной 1С-номенклатурой 'внешняя обработка'/'внешний отчёт', открываемых через диалог с предупреждением безопасности?" — API `openFile(path)`; expected_in_weights: частично (расширения EPF/ERF и связанное предупреждение безопасности — известный факт в 1С-домене, но не всегда точно воспроизводимый моделью без домена).
  - "Является ли навигация по метаданным через `Shift+F11` (открытие объекта по ссылке/URL) стандартной платформенной возможностью 1С:Предприятие, а не изобретением данного инструмента?" — API `navigateLink(url)`; expected_in_weights: нет (специфический, малоизвестный за пределами 1С-разработчиков хоткей платформы).
- `verbosity`: med — таблицы API (Навигация/Чтение/Действия/Утилиты) местами дают только сигнатуру без примера, из-за чего для незнакомого с API агента потребуется ещё `regress.md`/`recording.md`.
- `usefulness`: ядро процесса — центральный референс для всего скилла, все остальные файлы (`recording.md`, `regress.md`) явно ссылаются на него как на базовый API.
- `duplicate_of`: раздел "Запись видео и субтитры" — сжатое резюме `recording.md` (намеренное, не мусорное дублирование — SKILL.md как индекс); раздел "Регресс-движок" аналогично резюмирует `regress.md`, включая почти дословное предупреждение про текущее ограничение runtime (multi-user contexts).

---

## Сквозные наблюдения (не по одному файлу)

1. **playwright vs playwright-interactive** — два разных интерфейса к одному и тому же Playwright: `playwright` — CLI-обёртка (`pwcli`, одноразовые команды), `playwright-interactive` — persistent JS REPL (Electron + web, живые хендлы между итерациями). Дублирование сосредоточено в: (а) идентичном тексте LICENSE.txt (Apache 2.0, Microsoft Corporation в обоих), (б) NOTICE.txt playwright-interactive явно объявляет переиспользование ассетов playwright, (в) практически дословном разделе "1C Boundary" в обоих SKILL.md, (г) одинаковой структуре `agents/openai.yaml`. По существу это не избыточность, а осознанное разделение по use-case (одноразовая автоматизация vs долгоживущая отладочная сессия), но лицензионные/атрибуционные файлы и "1C Boundary"-абзац могли бы быть вынесены в общий shared-файл, если бы формат Codex skills это позволял.

2. **screenshot/scripts — ветвление по ОС, не дублирование кода**: `take_screenshot.py` (macOS + Linux, кроссплатформенный оркестратор) и `take_screenshot.ps1` (Windows, отдельная реализация на PowerShell/.NET) реализуют одну и ту же концептуальную возможность (регион/активное окно/по id/полный экран) на разных платформах несовместимыми технологиями — это нормальное и ожидаемое разделение, а не избыточность для устранения. Внутри macOS-ветки `macos_permissions.swift` (права), `macos_display_info.swift` (дисплеи) и `macos_window_info.swift` (окна) — три независимых узкоспециализированных хелпера, вызываемых из `take_screenshot.py` через общую функцию `swift_json()`; `ensure_macos_permissions.sh` — человекочитаемая CLI-обёртка над `macos_permissions.swift` для preflight-сценария до основного захвата. Дублирования по существу нет, но координация между `.py` и `.sh` (оба независимо реализуют вызов `macos_permissions.swift` и проверку `CODEX_SANDBOX`) — потенциальное место рассинхронизации при будущих правках одного без другого.

3. **Лицензионные файлы**: `playwright/LICENSE.txt` и `playwright-interactive/LICENSE.txt` — побайтово идентичный Apache 2.0 текст с указанием `Copyright (c) Microsoft Corporation`. `screenshot/LICENSE.txt` — тот же текст лицензии, но с незаполненным плейсхолдером `Copyright [yyyy] [name of copyright owner]` — то есть это, по всей видимости, шаблонная версия лицензии без явной привязки к конкретному upstream-репозиторию (в отличие от `screenshot/agents/openai.yaml`/скриптов, у которых нет NOTICE.txt с указанием источника, как это сделано в playwright-скиллах). Стоит проверить, не забыли ли для screenshot-скилла заполнить NOTICE.txt/владельца лицензии, если материалы там тоже заимствованы из внешнего репозитория.

4. **"1C Boundary" паттерн** — во всех трёх browser-инструментах общего назначения (`playwright/SKILL.md`, `playwright-interactive/SKILL.md`, `screenshot/SKILL.md`) и в `web-test-1c/SKILL.md`/`regress.md`/`recording.md` систематически повторяется одна и та же мысль: "для 1С UI сначала va-visual-check, browser-инструмент — только как fallback с фиксацией причины и остаточного риска". Это осознанная защитная формула фреймворка против неверного выбора инструмента агентом, но она размножена практически идентичным текстом в 5+ файлах — кандидат на консолидацию в единый общий rules-файл с последующей короткой ссылкой из каждого SKILL.md, если стоит цель снизить многословность.

5. **Гипотеза `expected_in_weights` — только материал для последующей пробы, не основание для оценки полезности/многословности.** По плотности отметок "да" видно неравномерное распределение: файлы `references/cli.md`, `references/workflows.md`, `screenshot/scripts/*` содержат больше фактов, помеченных `да`/`частично` (публичные API Playwright/AppKit/CoreGraphics/.NET), тогда как `web-test-1c/*`, `recording.md`, `gui-control/SKILL.md`, `img-grid/SKILL.md` содержат больше фактов с пометкой `нет` (собственные API/CLI/конвенции фреймворка). Это наблюдение фиксируется как гипотеза для отдельной последующей проверки по матрице «вопрос × модель» и намеренно не используется здесь как аргумент за сжатие, удаление или для оценки `usefulness`/`verbosity` — эти поля в отчёте выставлены независимо, по редакторским критериям и по роли файла в рабочем процессе соответственно.

---

# ЧАСТЬ B-code-content-diag

# Аудит навыков — часть B (code-analysis, content-generation, diagnostics)

Базовый путь: `framework/skills/tool-usage/`
Обработано файлов: **20**. Суммарно строк: **3151**.

Примечание к полям (после корректировки задания):
- `probe_facts` заполняется для ВСЕХ файлов. Для "1c-доменное"/"общепрограммистское" — прямые проверяемые утверждения. Для "проектно-специфичное" — сформулированы как вопрос через ближайшее публичное знание (общий паттерн/публичный API, стоящий рядом с обвязкой фреймворка).
- `expected_in_weights` (нет|частично|да) — ГИПОТЕЗА о том, знает ли базовая LLM это без чтения файла; будет проверяться отдельно матрицей «вопрос × модель». Не используется как основание для `usefulness` или `verbosity`.
- `verbosity` — чисто редакторская оценка (вода/повторы/длина), независимая от `expected_in_weights`.
- `usefulness` — оценивается только по роли файла в реальном рабочем процессе, независимо от `expected_in_weights`.

---

### code-analysis/buddy-prompting/SKILL.md

- `lines`: 183
- `purpose`: Шаблоны промптов для запроса к внутреннему AI-инструменту "1С Напарник" (документация платформы, ИТС, diff версий, проверка BSL-кода).
- `type`: проектно-специфичное — привязано к capability `ask_ai_assistant` и внутренним псевдо-инструментам этого фреймворка (`Search_Documentation`, `Search_ITS`, `Fetch_ITS`, `Diff_Documentation_Versions`, `syntax-checker__validate`), которых нет вне проекта.
- `probe_facts` (сформулированы через ближайшее публичное знание):
  1. "Общий паттерн retrieval-агента: сначала search (получить id/список кандидатов), затем fetch по id (получить полный текст) — двухшаговая оркестрация вместо одного вызова" — секция «Оркестрация SEARCH_ITS → FETCH_ITS». `expected_in_weights`: да.
  2. "Существует ли официальный портал ИТС (its.1c.ru) как база стандартов/методологии для разработчиков 1С, отдельная от справки по встроенному языку" — секция «SEARCH_ITS — стандарты и методология». `expected_in_weights`: частично.
  3. "Общий anti-hallucination паттерн agentic-систем: явный запрет 'не отвечай по памяти, обязательно вызови инструмент X, если пусто — так и скажи'" — секция «Stop rules» п.1,3. `expected_in_weights`: да.
- `verbosity`: med
- `usefulness`: "ядро процесса" — на него ссылаются `code-verification`, `syntax-checking` (косвенно), `search-before-write`; шаблоны и stop-rules чётко структурированы и нетривиальны (запрет придумывать сигнатуры, pre-flight контекст).
- `duplicate_of`: пересекается с `code-analysis/code-verification/SKILL.md` (Слой 2 — VALIDATE_BSL — это прямое переиспользование шаблона отсюда) и с `code-analysis/search-before-write/SKILL.md` (шаги 5a/5b — те же шаблоны SEARCH_DOCS/SEARCH_ITS).

---

### code-analysis/code-navigation/SKILL.md

- `lines`: 149
- `purpose`: Как использовать LSP/BSL Language Server для навигации по коду: определения, call graph, rename, discovery метаданных через автодополнение, impact-анализ, signature help.
- `type`: 1c-доменное — описывает поведение реального BSL Language Server (Type System v2, платформенный контекст из `.hbk` синтакс-помощника, автодополнение по метаданным конфигурации), не только обвязку фреймворка.
- `probe_facts`:
  1. "BSL LS резолвит платформенный контекст из `.hbk`-файла синтакс-помощника через `-Dapp.globalConfiguration.path` / переменную `BSL_PLATFORM_BIN`, иначе — автоопределение установленной платформы" — секция «Подсказки параметров на месте вызова: signature_help». `expected_in_weights`: частично.
  2. "`get_completion` после точки на объекте конфигурации возвращает реквизиты/табличные части/колонки с типами; после `Перечисления.Имя.` — значения перечисления" — секция «Discovery метаданных через get_completion». `expected_in_weights`: нет.
  3. "`signature_help` (стандартный метод LSP) требует 0-based `line`/`character` строго между `(` и `)` вызова, иначе возвращает пустой результат" — секция «Подсказки параметров...». `expected_in_weights`: частично.
  4. "Call hierarchy не видит вызовы через декларативные механизмы: подписки на события, обработчики форм, регл.задания, расширения `&Вместо/&Перед/&После`" — секция «Импакт-анализ правки: get_symbol_impact». `expected_in_weights`: частично.
- `verbosity`: med
- `usefulness`: "ядро процесса" — детальный, нетривиальный (описывает реальные слепые пятна инструментов LSP), используется многими другими навыками как база навигации.
- `duplicate_of`: пересекается с `code-analysis/search-before-write/SKILL.md` (шаг 1 каскада — тот же `navigate_symbol`) и с `code-analysis/code-verification/SKILL.md` (Слой 3 использует `navigate_symbol`/`getMember`/`get_hover_info` из этого навыка).

---

### code-analysis/code-verification/SKILL.md

- `lines`: 132
- `purpose`: Трёхслойный протокол проверки BSL-кода после правок: LSP-диагностика → Напарник (VALIDATE_BSL) → верификация платформенного API.
- `type`: проектно-специфичное — конкретная иерархия доверия (`v8-runner syntax` > `get_diagnostics` > `bsl-platform-context` > `ask_ai_assistant`) и связка трёх слоёв — авторская методология этого фреймворка, а не общепринятый стандарт.
- `probe_facts` (через ближайшее публичное знание):
  1. "Общий industry-паттерн многослойной проверки: дешёвая/быстрая проверка (линтер/LSP) → семантический советчик → финальный авторитетный компилятор, 'fail fast, cheap checks first'" — секция «Иерархия доверия». `expected_in_weights`: да.
  2. "В BSL как языке коллекционные типы (`Структура`, `Соответствие`, `ТаблицаЗначений`, `Массив`) имеют разный набор методов — это публичное знание о встроенном языке 1С, не специфика фреймворка" — секция «Особое внимание — коллекционные типы». `expected_in_weights`: да.
  3. "Существует ли для BSL Language Server общедоступный вывод типа значения под курсором (hover-based type inference), аналогичный `textDocument/hover` в LSP-спецификации" — секция «Определение типа переменной». `expected_in_weights`: частично.
- `verbosity`: med
- `usefulness`: "ядро процесса" — чёткий, компактный протокол, критичный gate качества после правок кода.
- `duplicate_of`: сильно пересекается с `code-analysis/syntax-checking/SKILL.md` (оба используют `get_diagnostics` как первый/быстрый слой проверки) и с `code-analysis/buddy-prompting/SKILL.md` (Слой 2 — прямое использование VALIDATE_BSL).

---

### code-analysis/search-before-write/SKILL.md

- `lines`: 64
- `purpose`: Каскад поиска существующего кода/метаданных/API перед написанием нового BSL-кода.
- `type`: общепрограммистское — принцип "искать перед тем как писать" (избегание дублирования, DRY) — общепризнанная практика разработки, не специфичная для 1С, хотя реализация здесь целиком построена на capability этого фреймворка.
- `probe_facts`:
  1. "Поиск существующей реализации перед написанием нового кода — общепринятая практика снижения дублирования (аналог DRY), применимая к любому языку" — секция «Каскад поиска» в целом. `expected_in_weights`: да.
  2. "Каскадный поиск по убыванию специфичности (символы проекта → метаданные → платформенные типы → библиотечные функции → документация) — общий паттерн multi-tier fallback lookup" — секция «Каскад поиска». `expected_in_weights`: да.
  3. "Существуют ли в экосистеме 1С типовые библиотеки повторно используемых функций (аналог БСП — Библиотека стандартных подсистем), которые стоит проверять перед написанием новой бизнес-логики" — секция «Триггеры» строка про печатные формы/БСП. `expected_in_weights`: частично.
- `verbosity`: low
- `usefulness`: "полезен" — короткий, ясный, но по содержанию почти целиком дублирует шаги, уже описанные в `code-navigation` и `buddy-prompting`; самостоятельной новой информации немного.
- `duplicate_of`: `code-analysis/code-navigation/SKILL.md` (шаг 1 — тот же `navigate_symbol`) и `code-analysis/buddy-prompting/SKILL.md` (шаги 5a/5b — те же шаблоны SEARCH_DOCS/SEARCH_ITS).

---

### code-analysis/syntax-checking/SKILL.md

- `lines`: 168
- `purpose`: Двухуровневая проверка синтаксиса BSL (быстрый LSP `get_diagnostics` + финальный `v8-runner syntax`), самопроверка качества (complexity/quality diagnostics) и методика оценки обоснованности suppression-маркеров.
- `type`: 1c-доменное — конкретный, проверяемый материал о реальных подавлениях предупреждений в экосистеме 1С (АПК, BSL Language Server, EDT), не только обвязка `v8-runner`.
- `probe_facts`:
  1. "BSL Language Server поддерживает подавление правил комментариями `// BSLLS:<Rule>-off` … `// BSLLS:<Rule>-on`" — секция «Синтаксис маркеров». `expected_in_weights`: частично.
  2. "АПК (статический анализатор конфигураций 1С) использует маркер `//{ АПК:<код> - комментарий` … `//}`" — секция «Синтаксис маркеров». `expected_in_weights`: нет.
  3. "1C:EDT поддерживает подавление через `// @suppress-warning(\"module-empty-method\")` или `//@skip-check`" — секция «Синтаксис маркеров». `expected_in_weights`: частично.
  4. "Severity диагностик ранжируется `error` > `warning` > `information`/`hint` — общий паттерн LSP/линтеров" — секция «Интерпретация результатов». `expected_in_weights`: да.
  5. "Пороговые значения для рефакторинга: цикломатическая сложность > 20, когнитивная > 15 — сопоставимо с распространёнными порогами статических анализаторов (например, похожие дефолты в SonarQube)" — секция «Самопроверка качества». `expected_in_weights`: частично.
- `verbosity`: med
- `usefulness`: "ядро процесса" — обязательный gate перед коммитом; раздел про suppression-маркеры как evidence — редкая, ценная деталь (тройная поддержка: код + диапазон + ссылка на стандарт).
- `duplicate_of`: пересекается с `code-analysis/code-verification/SKILL.md` (Слой 1 — тот же `get_diagnostics`) и упоминается как зависимость в `diagnostics/runtime-investigation/SKILL.md` (L7).

---

### content-generation/codex-image-gen/references/prompt-guide.md

- `lines`: 252
- `purpose`: Развёрнутый гайд по составлению промптов для генерации/редактирования изображений через `image_generation` (оси промпта, готовые шаблоны для 10 сценариев генерации и 10 — редактирования).
- `type`: общепрограммистское — универсальные техники промпт-инжиниринга для image generation, не относящиеся к 1С; единственная 1С-специфика — упоминание UI-мокапов формы 1С в связанном SKILL.md, не в этом файле.
- `probe_facts`:
  1. "Хороший промпт описывает наблюдаемый результат (сюжет, композиция, стиль, свет, ограничения), а не абстрактную идею" — секция «1. Общий принцип». `expected_in_weights`: да.
  2. "Для редактирования эффективнее описывать дельту изменений (что сохранить/что изменить/что убрать/что добавить), а не пересобирать всю сцену" — секция «3. Что указывать в промпте для редактирования...». `expected_in_weights`: да.
  3. "Явное указание `no text`/`isolated on white background`/точного количества объектов снижает шум в результате" — секция «2. Что указывать в промпте для генерации...». `expected_in_weights`: да.
  4. "Не перегружать промпт — рекомендуется 5-8 приоритетных характеристик" — секция «2. Что указывать...». `expected_in_weights`: частично.
- `verbosity`: high
- `usefulness`: "полезен" — качественный, детальный референс с готовыми шаблонами; раздел 0 корректно разделяет ответственность wrapper/пользователь, избегая дублирования инструкций.
- `duplicate_of`: нет (комплементарен `codex-image-gen/SKILL.md` и `codex_image_gen.py`, не дублирует их).

---

### content-generation/codex-image-gen/scripts/codex_image_gen.py

- `lines`: 269
- `purpose`: CLI-обёртка над `codex exec --sandbox workspace-write`, автоматически дополняющая промпт инструкциями сохранения файла и reference-изображений, возвращающая JSON-результат (`status: ok/error`, список файлов, путь к логу).
- `type`: общепрограммистское — типовой паттерн CLI-wrapper вокруг внешнего AI-инструмента (сборка аргументов, sandboxing, обнаружение новых файлов по snapshot до/после, обработка таймаута/FileNotFoundError); ничего специфичного для 1С.
- `probe_facts`:
  1. "Codex CLI (`codex exec`) поддерживает флаг `--sandbox workspace-write` для ограничения записи рабочей директорией" — функция `run_codex()`. `expected_in_weights`: частично.
  2. "Codex CLI принимает inline override конфига через `-c 'model_reasoning_effort=\"...\"'`" — функция `run_codex()`. `expected_in_weights`: частично.
  3. "Обнаружение новых файлов через сравнение snapshot `set(listdir)` до/после запуска внешнего процесса — стандартный общий паттерн отслеживания side-effects" — функция `discover_new_files()`. `expected_in_weights`: да.
- `verbosity`: high (269 строк, но для скрипта — оправданная плотность: argparse, поиск repo root, копирование референсов, запуск subprocess, парсинг результата)
- `usefulness`: "полезен" — рабочий, аккуратно написанный wrapper с понятной обработкой ошибок (`fail()` эмитит структурированный JSON), не выглядит избыточным.
- `duplicate_of`: нет (уникальный скрипт в наборе).

---

### content-generation/codex-image-gen/SKILL.md

- `lines`: 110
- `purpose`: Когда и как делегировать генерацию/редактирование изображений Codex CLI через `codex_image_gen.py` (в т.ч. для UI-мокапов форм 1С), анти-паттерны, разбор известных ограничений.
- `type`: общепрограммистское — базовая идея (делегировать image-gen внешнему провайдеру, т.к. основная модель "не умеет рисовать") и большая часть контента (шаблоны, workflow) не специфичны для 1С; 1С встречается только как один из примеров использования (UI-мокап формы).
- `probe_facts`:
  1. "Claude/Opus как primary agent не имеет встроенного инструмента image_generation — нужна явная делегация другому провайдеру (Codex)" — секция вступление. `expected_in_weights`: да.
  2. "`cross-provider-review` работает в `--sandbox read-only`, поэтому непригоден для генерации изображений (запись на диск невозможна) — для этого нужен отдельный wrapper с `workspace-write`" — секция «Почему отдельный навык, а не cross-provider-review». `expected_in_weights`: нет (это внутреннее устройство именно этого фреймворка).
  3. "Размер изображения по умолчанию определяется моделью image_generation, конкретное значение (напр. 1024x1024) — типовой дефолт, который стоит переопределять явно при иных требованиях" — секция «Известные ограничения». `expected_in_weights`: частично.
- `verbosity`: med
- `usefulness`: "полезен" — нишевая, но чётко описанная возможность с ясными anti-patterns и границами применимости (когда НЕ применять).
- `duplicate_of`: нет прямых дублей в списке; тесно связан (не дублирует) с `prompt-guide.md` и `codex_image_gen.py` как части одной триады.

---

### content-generation/docx-convert/docx2md.sh

- `lines`: 55
- `purpose`: Bash-обёртка над `pandoc` для конвертации `.docx` → GFM Markdown с извлечением изображений и постобработкой путей/HTML-таблиц.
- `type`: общепрограммистское — стандартная задача конвертации документов, не связана с 1С по содержанию (хотя типичный сценарий использования — ТЗ заказчика в .docx).
- `probe_facts`:
  1. "`pandoc --from=docx --to=gfm --extract-media=<dir>` извлекает медиафайлы в подпапку `<dir>/media/`" — секция кода (строки 29-39), это реальное документированное поведение pandoc. `expected_in_weights`: да.
  2. "pandoc оставляет сложные таблицы как raw HTML внутри Markdown-вывода, а не конвертирует их в pipe-таблицы" — согласуется с `SKILL.md` разделом «Когда НЕ применять» и обосновывает существование `html_tables_to_md.py`. `expected_in_weights`: частично.
- `verbosity`: low
- `usefulness`: "полезен" — компактный, работающий скрипт, есть простая обработка ошибок (проверка аргументов и существования файла).
- `duplicate_of`: нет; напрямую использует `html_tables_to_md.py` как постобработку (не дублирование, а композиция).

---

### content-generation/docx-convert/html_tables_to_md.py

- `lines`: 118
- `purpose`: Постобработка Markdown после pandoc — конвертация оставшихся HTML-таблиц в pipe-таблицы, `<img>` в `![]()`, упрощение `<br>/<strong>/<em>`.
- `type`: общепрограммистское — regex-based HTML→MD трансформация, полностью общего назначения.
- `probe_facts`:
  1. "Regex-парсинг HTML-таблиц через `<tr>...</tr>` и `<t[hd]>...</t[hd]>` — эвристический подход, не полноценный HTML-парсер (может не выдержать вложенные таблицы)" — видно из реализации `html_table_to_md()`, общее ограничение regex-парсинга HTML известно широко. `expected_in_weights`: да.
  2. "Если в таблице нет `<th>`, первая строка данных используется как заголовок" — секция кода `html_table_to_md`, строки 64-66 (частный дизайн-выбор этого скрипта). `expected_in_weights`: нет.
  3. "Длинный `alt`-текст (>60 символов) от pandoc-скриншотов обрезается с многоточием" — функция `img_to_md` (произвольный порог, специфика реализации). `expected_in_weights`: нет.
- `verbosity`: med
- `usefulness`: "полезен" — решает конкретную, реальную проблему (pandoc не конвертирует сложные HTML-таблицы), достаточно компактен.
- `duplicate_of`: нет; жёстко связан с `docx2md.sh` (вызывается им), не самостоятельный дубликат.

---

### content-generation/docx-convert/SKILL.md

- `lines`: 82
- `purpose`: Когда/как использовать конвертацию DOCX→Markdown (через скрипт или вручную через pandoc/mammoth), ограничения и анти-паттерны.
- `type`: общепрограммистское — не связано с 1С по содержанию, чисто про конвертацию документов.
- `probe_facts`:
  1. "pandoc принимает только `.docx`, не старый `.doc` — формат нужно пересохранить (например через LibreOffice/Word)" — секция «Анти-паттерны». `expected_in_weights`: да.
  2. "OMML-формулы (формулы Word) конвертируются в LaTeX лишь частично" — секция «Примечания». `expected_in_weights`: частично.
  3. "WordArt/SmartArt/фигуры теряются при pandoc-конвертации" — секция «Примечания»/«Когда НЕ применять». `expected_in_weights`: да.
  4. "mammoth (Python-библиотека) — известная альтернатива pandoc, лучше сохраняющая сложные таблицы/стили при конвертации docx→html/markdown" — секция «Зависимости». `expected_in_weights`: частично.
- `verbosity`: low
- `usefulness`: "полезен" — ясный, по делу, с корректными ограничениями и альтернативами.
- `duplicate_of`: нет (комплементарен `docx2md.sh`/`html_tables_to_md.py`, описывает их использование, а не дублирует логику).

---

### diagnostics/agent-debug/references/learned-patterns.md

- `lines`: 34
- `purpose`: Единственный (пока) накопленный паттерн — как отлаживать headless-прогон внешней обработки (.epf через `1cv8 ENTERPRISE /Execute`), когда стандартные каналы наблюдаемости (ЖР, stdout, код возврата) молчат или лгут.
- `type`: 1c-доменное — специфика реального поведения платформы 1С в headless-режиме (`/Execute`, безопасный режим, серверная/клиентская запись файла, EXIT=0 не означает успех).
- `probe_facts`:
  1. "`1cv8 ENTERPRISE /Execute<epf>` открывает ФОРМУ; при раннем выходе из `ПриОткрытии` или зависании на GUI-диалоге процесс может завершиться с EXIT=0 без реального выполнения" — поле `почему`. `expected_in_weights`: частично.
  2. "Клиентская запись файла в безопасном режиме `/Execute` может падать молча" — поле `почему`. `expected_in_weights`: нет.
  3. "Прямое GUI-наблюдение (scrot/xdotool на реальном DISPLAY) — общий принцип 'канал истины последней инстанции', когда программные каналы наблюдаемости недоступны или противоречивы" — поле `приём`. `expected_in_weights`: частично.
- `verbosity`: low
- `usefulness`: "сомнителен" — единственный паттерн в файле имеет статус `candidate` (не `confirmed`), узкоспециализированный сценарий (headless .epf под конкретной типовой конфигурацией), источник — единичный кейс от 2026-06; ценность как накопленной базы знаний под вопросом, пока не появятся ещё подтверждённые записи.
- `duplicate_of`: концептуально пересекается с `diagnostics/bug-reporting/SKILL.md` (антипаттерн "EXIT=0 = успех" мог бы стать полем `hypotheses`/`self_fix_attempts`) и с `diagnostics/runtime-investigation/SKILL.md` (L6 — GUI-контроль как канал наблюдения).

---

### diagnostics/agent-debug/SKILL.md

- `lines`: 123
- `purpose`: Протокол временного логирования BSL-кода через маркеры `//[AGENTDEBUG-NNN]` и `ЗаписьЖурналаРегистрации` для проверки гипотез о поведении кода, с обязательной последующей очисткой.
- `type`: проектно-специфичное — формат маркеров (`AGENTDEBUG-NNN`, `STEP=... PROC=... MSG=... | key=value`) — изобретение этого фреймворка; хотя базовая платформенная функция `ЗаписьЖурналаРегистрации` реальна, ядро навыка — собственный протокол разметки/очистки.
- `probe_facts` (через ближайшее публичное знание):
  1. "Общий инженерный паттерн: временная debug-инструментация под конкретную гипотезу + обязательная последующая очистка перед коммитом — распространённая практика (temporary logging / tracer statements)" — секция «Процедура». `expected_in_weights`: да.
  2. "Платформенная функция 1С `ЗаписьЖурналаРегистрации(Событие, Уровень, ..., Комментарий)` пишет запись в журнал регистрации — публичный, документированный метод встроенного языка" — секция «Формат debug-блока». `expected_in_weights`: да.
  3. "Уровень `Информация` в журнале регистрации сохраняется надёжнее, чем произвольный `Комментарий`/`Примечание` — насколько это общеизвестный нюанс поведения ЖР 1С (а не специфика данного навыка)" — секция «Параметры ЗаписьЖурналаРегистрации». `expected_in_weights`: частично.
- `verbosity`: med
- `usefulness`: "полезен" — дисциплинированный протокол с чётким чек-листом очистки (важно для избежания утечки debug-кода в коммит); хорошо расписаны антипаттерны.
- `duplicate_of`: пересекается с `diagnostics/dap-bsl-code-debug-procedure/SKILL.md` (альтернативный способ той же задачи — "не подходит DAP → agent-debug", см. `runtime-investigation` L3/L4) и с `diagnostics/runtime-investigation/SKILL.md` (Фаза 2/3 использует именно этот протокол, включая безопасную сериализацию из §6 runtime-investigation, которая частично дублирует "не логировать таблицы/пароли" из agent-debug).

---

### diagnostics/bug-reporting/SKILL.md

- `lines`: 304
- `purpose`: Стандарт формы `bug-report.json` (структура, обязательные поля, критерии "когда заводить баг", лимиты самовосстановления по агентам, протоколы заполнения, жизненный цикл статусов).
- `type`: проектно-специфичное — это внутренняя схема данных и правила эскалации многоагентного пайплайна этого фреймворка (агенты `developer-code`/`tester`/`scenario-coder`, статусы `open`/`fixed_locally`/`escalated_to_user` и т.д.), не существующие вне проекта.
- `probe_facts` (через ближайшее публичное знание):
  1. "Общий QA-паттерн классификации отказа: 'баг рантайма' vs 'неполнота требований' vs 'инфраструктурная проблема' — стандартная триаж-практика в разработке/тестировании" — секция «1. Когда заводить bug-report». `expected_in_weights`: да.
  2. "Требование verbatim-цитаты ошибки и verbatim-цитаты источника ожидания (а не пересказа) — распространённая практика написания качественных bug-репортов" — секция «4.1 Общие». `expected_in_weights`: да.
  3. "Специфичная схема полей `debug_trigger`/`scenario_context` (с подполями `document`/`processor`/`function_call`/`report`) для 1С-агентного пайплайна — это внутренняя схема, не существующая вне данного фреймворка" — секция «3. Структура bug-report.json». `expected_in_weights`: нет.
- `verbosity`: high (304 строки — самый длинный файл в наборе; много вложенных JSON-примеров и таблиц)
- `usefulness`: "ядро процесса" — несмотря на длину, содержательно необходим: чётко разграничивает bug vs clarification vs environment_error, задаёт обязательные поля с verbatim-цитатами (предотвращает "выдуманные" баг-репорты).
- `duplicate_of`: тесно связан с `diagnostics/runtime-investigation/SKILL.md` (bug-report.json — входной артефакт для runtime-investigation, статусный цикл §6 здесь дублируется/расширяется в §9 runtime-investigation) и с `diagnostics/dap-bsl-code-debug-procedure/SKILL.md` (`debug_trigger.preferred_method` перечисляет те же способы запуска — yaxunit/vanessa/ui_mcp).

---

### diagnostics/dap-bsl-code-debug-procedure/SKILL.md

- `lines`: 166
- `purpose`: Процедура интерактивной отладки одной BSL-процедуры через DAP/MCP-отладчик (attach → breakpoint → запуск сценария → step/evaluate → detach), включая клиентские (Vanessa/UI MCP) и серверные (YaxUnit/временный MCP tool) сценарии инициации.
- `type`: 1c-доменное — интерактивная отладка BSL через DAP-отладчик — реальная возможность экосистемы 1С (HTTP debug server платформы, привязка к сеансам ИБ, YaxUnit, Vanessa); содержательно про поведение реальных 1С-инструментов, не выдуманную обвязку.
- `probe_facts`:
  1. "Порядок инициации всегда: сначала breakpoint, потом запуск сценария, потом polling `wait_for_stop` — общий принцип отладки через Debug Adapter Protocol (иначе участок кода пройдёт до подключения отладчика)" — секция «Как инициировать выполнение кода». `expected_in_weights`: да.
  2. "Для клиентского контекста через Vanessa нужно сопоставить сеанс ИБ (`ib_session_number`) с target отладчика через `v8-client-session-manager`" — секция «Клиентский контекст через Vanessa». `expected_in_weights`: нет.
  3. "`evaluate` в отладчике допустим только для выражений без побочных эффектов — не для записи/проведения/HTTP-вызовов — общая конвенция отладчиков (не менять состояние при инспекции)" — секция «Работа с переменными». `expected_in_weights`: да.
  4. "При зависшем `ibInDebug` после `detach` нужен `force_detach` с повторной проверкой `get_targets` — специфика поведения платформенного HTTP debug-сервера 1С" — секция «Завершение». `expected_in_weights`: нет.
- `verbosity`: med
- `usefulness`: "ядро процесса" — детальная, практичная процедура с явными предусловиями и процедурой очистки (снятие breakpoint, отключение, удаление временных MCP tools); критична для корректной точечной отладки.
- `duplicate_of`: сильно пересекается с `diagnostics/runtime-investigation/SKILL.md` (L3 в иерархии инструментов, дословно описывает тот же цикл breakpoint→wait_for_stop) и с `diagnostics/agent-debug/SKILL.md` (альтернативный подход, когда DAP не подходит); также опирается на `code-analysis/code-navigation/SKILL.md` для поиска точки останова.

---

### diagnostics/db-performance/SKILL.md

- `lines`: 175
- `purpose`: Пятишаговый алгоритм диагностики производительности БД в контексте 1С — от называния сценария и извлечения текста запроса до сбора СУБД-evidence (план запроса, блокировки, temp storage) и одного измеримого изменения.
- `type`: 1c-доменное — сочетает платформенный слой 1С (виртуальные таблицы, СКД, регистры) с реальными СУБД-инструментами (PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)`, MS SQL `sys.dm_exec_requests`) — конкретный, проверяемый материал по обеим экосистемам.
- `probe_facts`:
  1. "PostgreSQL: `EXPLAIN (ANALYZE, BUFFERS)` — основной инструмент получения фактического плана запроса; `pg_stat_activity.wait_event_type = 'Lock'` — признак блокировки" — секция «Модели доказательств по СУБД» → PostgreSQL. `expected_in_weights`: да.
  2. "MS SQL Server: `sys.dm_exec_requests.blocking_session_id` показывает блокирующую сессию; TEMPDB spill виден через `tempdb.sys.dm_db_task_space_usage`" — там же → MS SQL Server. `expected_in_weights`: да.
  3. "Файловая ИБ 1С не имеет СУБД-плана — производительность определяется структурой dbf-файлов и платформенным менеджером блокировок" — секция «Файловая ИБ». `expected_in_weights`: частично.
  4. "Текст запроса 1С обязательно нужно сопровождать хотя бы одним СУБД-артефактом — анализ только текста запроса недостаточен как доказательство" — секция «Шаг 2. Извлечь запрос + метаданные». `expected_in_weights`: да (это общий принцип доказательности диагностики, не специфика 1С).
- `verbosity`: med
- `usefulness`: "полезен" — методологически строгий (stop rules против непроверенных рекомендаций по индексам, запрет обобщать PostgreSQL-выводы на MS SQL), хорошая инженерная дисциплина "одно изменение — одна верификация".
- `duplicate_of`: пересекается с `diagnostics/tech-log-analysis/SKILL.md` и `tech-log-analysis/references/scenarios.md` (оба используют события `DBMSSQL`/`DBPOSTGRS`/`TLOCK` из технологического журнала как источник evidence).

---

### diagnostics/event-log-analysis/SKILL.md

- `lines`: 123
- `purpose`: Диагностика по журналу регистрации (ЖР) 1С — каскад фильтрации по уровню/времени, сшивка записи с кодом через `navigate_symbol`, работа с correlation id, требования маскирования персональных данных.
- `type`: 1c-доменное — журнал регистрации (ЖР) — реальный, документированный компонент платформы 1С (структура записи: `event_presentation`, `metadata_presentation`, уровни `Error`/`Warning` и т.д.).
- `probe_facts`:
  1. "Каскад: сначала последние записи `level: Error` без временного фильтра (из-за возможного timezone drift), затем без фильтра уровня, затем сужение окна ±15 минут" — секция «Каскад фильтрации». `expected_in_weights`: частично.
  2. "По умолчанию `mode: minimal` и не более 1000 записей при первичном поиске — во избежание перегрузки" — секция «Безопасность» (специфика этого MCP-инструмента, не публичный стандарт). `expected_in_weights`: нет.
  3. "Запись журнала регистрации 1С содержит поля вида `event_presentation`/`metadata_presentation`/`comment`, по которым можно перейти к затронутому объекту метаданных и коду" — секция «Сшивка записи ЖР с кодом» (общая структура ЖР — публичное знание платформы). `expected_in_weights`: частично.
- `verbosity`: med
- `usefulness`: "ядро процесса" — базовый, часто самый быстрый первый шаг диагностики (используется как L1 в `runtime-investigation`), корректно разграничивает зону ответственности с `tech-log-analysis`.
- `duplicate_of`: пересекается с `diagnostics/tech-log-analysis/SKILL.md` (оба — источники журналов, явное разграничение "ЖР для ошибок/действий пользователя, ТЖ — для SQL/блокировок/исключений платформы") и с `diagnostics/runtime-investigation/SKILL.md` (L1 в иерархии инструментов).

---

### diagnostics/runtime-investigation/SKILL.md

- `lines`: 341
- `purpose`: Полный оркестрирующий алгоритм расследования бага в рантайме — от `bug-report.json` через построение графа вызовов, выделение ключевых переменных, цикл гипотез (лимит 5+3) до фикса/эскалации и обязательной очистки debug-артефактов.
- `type`: проектно-специфичное — это методология и жизненный цикл именно этого фреймворка (роли `debugger`/`orchestrator`, файлы `call-graph.md`/`instrumentation-plan.md`/`debug-report.md`, критерий «локальный фикс vs возврат оркестратору»), связывающая воедино несколько других навыков как инструменты нижнего уровня.
- `probe_facts` (через ближайшее публичное знание):
  1. "Общая техника root-cause analysis: построить граф вызовов от точки симптома назад по стеку до точки входа сценария (backward slicing)" — секция «4. Построение графа вызовов». `expected_in_weights`: да.
  2. "Общий принцип 'иерархия инструментов от дешёвого к дорогому' (чтение кода → логи → интерактивный отладчик → тяжёлый профайлер/журнал) — распространённая практика диагностики" — секция «2. Иерархия инструментов». `expected_in_weights`: да.
  3. "Специфичная лимитная схема «5 гипотез по умолчанию, +3 расширение по согласованию с оркестратором, максимум 8» — это авторское изобретение методологии данного фреймворка, а не общепринятый стандарт" — секция «7. Лимит гипотез». `expected_in_weights`: нет.
- `verbosity`: high (341 строка — самый длинный файл во всём наборе; 10 секций с детальными шаблонами и таблицами)
- `usefulness`: "ядро процесса" — центральный orchestration-навык для дебаггера, явно ссылается на 10 других навыков/правил как зависимости; устраняет "гадание" через явные evidence-требования к каждой гипотезе (`evidence_from_trace`) и лимиты итераций.
- `duplicate_of`: агрегирует/пересекается почти со всеми остальными diagnostics-навыками из списка: `bug-reporting` (вход/выход процесса), `dap-bsl-code-debug-procedure` (L3), `agent-debug` (L4), `event-log-analysis` (L1), `tech-log-analysis` (L8), `code-analysis/syntax-checking` (L7), `code-analysis/code-navigation` (построение графа вызовов, §4).

---

### diagnostics/tech-log-analysis/references/scenarios.md

- `lines`: 150
- `purpose`: Справочник прикладных приёмов анализа технологического журнала (ТЖ) — классификация инцидентов по типу события, обязательные идентификаторы для сшивки записей, построение timeline, детальные сценарии (startup, HTTP/web-client, фоновое задание, DBMS-улики единым блоком), шаблон вывода.
- `type`: 1c-доменное — детальная, проверяемая специфика реальных событий технологического журнала платформы 1С (`EXCP`, `CALL`/`SCALL`, `DBMSSQL`/`DBPOSTGRS`, `TLOCK`/`TDEADLOCK`/`TTIMEOUT`, `SDBL`, поля `HTTPPath`/`JobID`/`CorrID`).
- `probe_facts`:
  1. "Минимальный набор событий для инцидента `startup` — `EXCP` + `CONN`; для `lock/deadlock` — `TLOCK`+`TDEADLOCK`+`TTIMEOUT`" — секция «1. Классификация записи ТЖ по типу инцидента». `expected_in_weights`: частично.
  2. "`SDBL`-событие содержит исходный запрос на языке запросов 1С, `DBMSSQL`/`DBPOSTGRS` — его SQL-трансляцию — их нужно сопоставлять" — секция «7. DBMS-улики единым блоком». `expected_in_weights`: частично.
  3. "Первая причинная запись (`EXCP`, начало `TLOCK`, первый `DBMSSQL` с превышением threshold) приоритетнее последующих (`rollback`/`retry`/`TTIMEOUT`) — они следствие, не причина; общий принцип анализа логов «первая причина важнее повторяющихся симптомов»" — секция «3. Timeline и источник времени». `expected_in_weights`: да.
  4. "При расхождении часовых поясов файлов ТЖ — обязательно привести к UTC перед построением timeline — общая практика корреляции разнородных логов" — секция «3. Timeline и источник времени». `expected_in_weights`: да.
- `verbosity`: med
- `usefulness`: "полезен" — качественное дополнение к `tech-log-analysis/SKILL.md`, содержит немало неочевидных, ценных деталей (timeline-приоритет причина/следствие, обязательность раздела "Недостающие улики").
- `duplicate_of`: нет прямого дублирования с `tech-log-analysis/SKILL.md` — SKILL.md даёт операционный цикл (save/configure/read/restore), этот файл — методику интерпретации содержимого; взаимно дополняют, не копируют друг друга. Пересекается по теме (DBMS-события) с `diagnostics/db-performance/SKILL.md`.

---

### diagnostics/tech-log-analysis/SKILL.md

- `lines`: 153
- `purpose`: Операционный цикл работы с технологическим журналом (ТЖ) 1С — сохранение конфигурации, точечное включение нужных событий, smart-polling готовности лога, чтение записей, обязательное восстановление конфигурации.
- `type`: 1c-доменное — конкретные, проверяемые факты о технологическом журнале платформы 1С (события `EXCP`/`DBMSSQL`/`TLOCK`/`TDEADLOCK`/`CALL`/`SCALL`/`CONN`/`SDBL`, процедура save/configure/restore, соответствует публичному `logcfg.xml`).
- `probe_facts`:
  1. "Стандартный набор событий для базовой диагностики — `[\"EXCP\", \"DBMSSQL\", \"TLOCK\", \"TDEADLOCK\"]`" — секция «События ТЖ». `expected_in_weights`: частично.
  2. "Технологический журнал 1С в общем случае конфигурируется файлом `logcfg.xml`, а не через код; обязательный цикл 'сохранить конфигурацию до изменений → восстановить после' — общая практика безопасного изменения общесистемного конфига" — секция «Полный цикл диагностики». `expected_in_weights`: частично.
  3. "Для Vanessa ТЖ — последний источник диагностики; сначала `event-log-analysis` и визуальная проверка UI — специфичный для этого фреймворка порядок эскалации" — вступление. `expected_in_weights`: нет.
  4. "`SDBL` — событие трансляции запросов на языке запросов 1С в SQL" — секция «События ТЖ». `expected_in_weights`: частично.
- `verbosity`: med
- `usefulness`: "ядро процесса" — обязательный протокол для тяжёлого, инвазивного инструмента диагностики; явно разграничивает роли с `event-log-analysis` и корректно требует восстановления конфигурации.
- `duplicate_of`: пересекается с `diagnostics/event-log-analysis/SKILL.md` (эскалационная пара ЖР→ТЖ) и с `diagnostics/db-performance/SKILL.md` (общие события `DBMSSQL`/`DBPOSTGRS`); дополняется (не дублируется) файлом `references/scenarios.md` этого же навыка.

---

## Сводные наблюдения

1. **Диагностический кластер (`diagnostics/`) сильно взаимосвязан.** `runtime-investigation` — фактически "дирижёр", который явно перечисляет в зависимостях 10 остальных навыков (включая все diagnostics-файлы из этого списка). Это ожидаемо, но означает, что при правке любого из низкоуровневых навыков (`event-log-analysis`, `tech-log-analysis`, `dap-bsl-code-debug-procedure`, `agent-debug`) нужно перепроверять согласованность с `runtime-investigation`.
2. **Единственный файл с сомнительной полезностью** — `agent-debug/references/learned-patterns.md`: один паттерн со статусом `candidate` (не `confirmed`), узкий кейс. Не кандидат на немедленное удаление, но требует накопления либо подтверждения статуса.
3. **Явных кандидатов на удаление/архив в этом наборе нет.** Все 20 файлов несут самостоятельную функциональную нагрузку.
4. **content-generation/** (codex-image-gen, docx-convert) — полностью общепрограммистский кластер, никак не пересекается по содержанию с 1С-доменом или остальными частями репозитория; внутренне согласован (SKILL.md + скрипт + reference не дублируют друг друга, а дополняют).
5. **code-analysis/** — вокруг двух общих принципов (`search-before-write`, `syntax-checking`), но фактическая реализация почти everywhere завязана на capability-имена этого фреймворка; наибольшая доменная конкретика (проверяемые факты о реальных публичных инструментах 1С-экосистемы) — в `syntax-checking` (суффиксы suppression-разметки АПК/BSLLS/EDT) и `code-navigation` (поведение BSL LS/.hbk).
6. **Поле `expected_in_weights` — гипотеза, требующая отдельной проверки матрицей «вопрос × модель».** В этом отчёте оно проставлено эвристически (по субъективной оценке "насколько публично/специфично утверждение") и не должно use как основание для решений о сжатии/удалении файлов без отдельного прогона проб.

---

# ЧАСТЬ C-platform-xmltop

# Аудит навыков — Part C: platform-admin + platform-data (top-level xml-generation family)

Базовая директория для относительных путей: `framework/skills/tool-usage/`

Примечание по методологии (после корректировки оркестратора):
- `probe_facts` заполняются для ВСЕХ файлов, включая «проектно-специфичное». Для проектно-специфичных файлов факт формулируется как вопрос через ближайшее публичное знание, а не как «раскрытие секрета обвязки».
- `expected_in_weights` (нет|частично|да) — независимая гипотеза «знает ли это модель „в весах“ без чтения файла», подлежит отдельной проверке по матрице «вопрос × модель». Это НЕ основание для `usefulness` или для вывода о сжатии.
- `verbosity` — чисто редакторская оценка (вода/повторы/длина), не связана с `expected_in_weights`.
- `usefulness` — оценивается только по роли файла в реальном рабочем процессе (используется ли он по смыслу инструкций/шаблонов/примеров), не связана с `expected_in_weights`.

---

### platform-admin/onec-server-maintenance-hooks/SKILL.md

- **lines**: 121
- **purpose**: Runbook по HTTP-вебхукам обслуживания стенда 1С — restart контейнера сервера 1С и очистка распакованного кеша внешних компонент (ВК).
- **type**: проектно-специфичное — конкретный webhook-сервис `onec-infra:8765`, токен из `/workspaces/work/secrets/...`, systemd-юнит `onec-restart.service`, SSH-путь к стенду. Это обвязка конкретного полигона/стенда, не публичный инструмент 1С.
- **probe_facts**:
  - Как перезапустить сервер 1С (кластер/рабочие процессы) после обновления внешней компоненты или зависания — общий вопрос через ближайшее публичное знание («restart службы `srv1cv8`/контейнера»); конкретный webhook `POST /restart/onec-server` этого стенда — секция «Перезапуск контейнера 1С». `expected_in_weights`: частично (общий принцип restart известен, конкретный endpoint — нет).
  - Нужно ли перезапускать процесс 1С после удаления файла кеша внешней компоненты, если она уже подгружена в память рабочего процесса (`rphost`/`rmngr`) — секция «Нужно ли перезапускать 1С после очистки». `expected_in_weights`: частично (общий принцип «модуль в памяти не выгружается удалением файла» — да, конкретный признак `restart_required` этого хука — нет).
  - Общий DevOps-паттерн «сначала dry-run/read-only диагностика, затем явное подтверждение перед деструктивным действием на проде» — секция «Общие правила безопасности». `expected_in_weights`: да (стандартная общепрограммистская практика).
- **verbosity**: med
- **usefulness**: полезен — узкий, но реальный инфраструктурный сценарий с нетривиальной семантикой (`restart_required` показывает, загружена ли ВК в память процесса, и что одного удаления файла кеша недостаточно); хорошо описаны safety-правила (dry_run сначала, запрет маски `*`).
- **duplicate_of**: нет

---

### platform-admin/rac-use/SKILL.md

- **lines**: 152
- **purpose**: Справочник по RAC (Remote Administration Client кластера 1С) — получение cluster UUID, работа с сеансами/соединениями/блокировками/регл. заданиями инфобазы.
- **type**: 1c-доменное — RAC является публичной штатной утилитой администрирования кластера 1С:Предприятие.
- **probe_facts**:
  - Все команды RAC требуют явного `--cluster=<uuid>`, получаемого через `rac cluster list` — секция «Первый шаг — получить cluster UUID». `expected_in_weights`: да.
  - Блокировка входа в ИБ выполняется через `rac infobase update --sessions-deny=on --permission-code=<code>` — секция «Блокировка входа в базу». `expected_in_weights`: да.
  - Принудительное завершение сеанса — `rac session terminate --session=<uuid> --error-message=...` — секция «Принудительное завершение сеанса». `expected_in_weights`: да.
  - RAC поддерживает 14 режимов (cluster, infobase, session, connection, lock, process, server, manager, agent, service, rule, profile, counter, limit) — секция «Все режимы RAC». `expected_in_weights`: частично (основные режимы да, полный список из 14 — не гарантированно точно).
- **verbosity**: med
- **usefulness**: ядро процесса — базовая утилита администрирования кластера, на которую опирается `subsystem-update` (явный `depends_on`) и любые сценарии блокировки ИБ.
- **duplicate_of**: `platform-admin/subsystem-update/SKILL.md` — шаг 2 «Заблокировать базу» повторяет те же команды `rac infobase update --sessions-deny=on` / `rac session terminate` инлайн вместо чистой ссылки на этот навык (частичное дублирование содержимого, несмотря на формальный `depends_on`).

---

### platform-admin/subsystem-update/SKILL.md

- **lines**: 199
- **purpose**: Полный цикл запуска/проверки обработчика обновления подсистемы БСП (версия в регистре → блокировка ИБ → запуск обновления через 1cv8c → снятие блокировки → проверка ЖР), плюс шаблон обязательных процедур модуля обновления.
- **type**: 1c-доменное — механизм обновления подсистем БСП (СтандартныеПодсистемы) является публичным механизмом типовой библиотеки БСП; часть путей (`configs/yaxunit-runner.yml`, `cluster_map.yaml`) — проектно-специфичные детали подключения, но основной контент — про домен.
- **probe_facts**:
  - БСП запускает обработчик обновления, только если версия в `РегистрСведений.ВерсииПодсистем` меньше версии, заявленной обработчиком — секция «Шаг 1. Проверить текущую версию». `expected_in_weights`: да.
  - Обработчики с `МонопольныйРежим = Истина` требуют отсутствия активных сеансов (блокировка + принудительное завершение) — секция «Шаг 2. Заблокировать базу». `expected_in_weights`: да.
  - Запуск обновления инициируется клиентом `1cv8c` с параметром `/C"ЗапуститьОбновлениеИнформационнойБазы"` и кодом разрешения `/UC`, совпадающим с `--permission-code` — секция «Шаг 3. Запустить обновление». `expected_in_weights`: частично (общий факт про `/C` и запуск обработки — да, точное имя внутренней глобальной процедуры и связка с `/UC` — менее гарантированно).
  - Модуль подсистемы обновления БСП обязан содержать 6 конкретных экспортных процедур-заглушек (`ПередОбновлениемИнформационнойБазы`, `ПослеОбновленияИнформационнойБазы`, `ПриПодготовкеМакетаОписанияОбновлений`, `ПриОпределенииРежимаОбновленияДанных`, `ПриДобавленииОбработчиковПереходаСДругойПрограммы`, `ПриЗавершенииПереходаСДругойПрограммы`) — секция «Шаблон модуля обновления подсистемы». `expected_in_weights`: частично (общий факт «нужны callback-заглушки БСП» — да, точный список всех 6 имён — не гарантированно точно).
- **verbosity**: high (включает полный BSL-шаблон модуля ~50 строк и таблицу типичных ошибок)
- **usefulness**: ядро процесса — критичный, подверженный ошибкам workflow (монопольный режим, версия в регистре, зависшие сеансы); таблица типичных ошибок сильно снижает риск непонятных сбоев.
- **duplicate_of**: `platform-admin/rac-use/SKILL.md` — команды блокировки ИБ и завершения сеансов продублированы инлайн вместо ссылки (см. выше).

---

### platform-data/platform-data-core/references/query-syntax-cheatsheet.md

- **lines**: 262
- **purpose**: Справочник синтаксиса языка запросов 1С (источники данных, виртуальные таблицы, NULL, ВЫРАЗИТЬ, операторы ГДЕ, даты, строки, готовые примеры запросов).
- **type**: 1c-доменное — язык запросов 1С является публичной частью платформы 1С:Предприятие.
- **probe_facts**:
  - Виртуальные таблицы регистра накопления — `Остатки()`, `Обороты()`, `ОстаткиИОбороты()` с суффиксами ресурсов `Остаток`/`Приход`/`Расход`/`Оборот` — секции «Источники данных» и «Суффиксы ресурсов регистров». `expected_in_weights`: да.
  - Проверка NULL — только через `ЕСТЬ NULL`/`НЕ ЕСТЬ NULL`, никогда `= NULL`; для замены значения — `ЕСТЬNULL()` — секция «Работа с NULL». `expected_in_weights`: да.
  - `ВЫРАЗИТЬ(Регистратор КАК Документ.Имя)` сужает составной тип и убирает неявный JOIN ко всем возможным типам — секция «ВЫРАЗИТЬ для составных типов». `expected_in_weights`: да.
  - Формат литерала даты — `ДАТАВРЕМЯ(Год, Месяц, День[, Час, Минута, Секунда])` — секция «Работа с датами». `expected_in_weights`: да.
  - Оператор `ИЛИ` в `ГДЕ` ухудшает использование индексов, рекомендуется `В (...)` или `ОБЪЕДИНИТЬ ВСЕ` — секция «Операторы ГДЕ». `expected_in_weights`: да.
- **verbosity**: high (262 строки, но в основном плотные справочные таблицы и примеры)
- **usefulness**: полезен — качественный компактный справочник по языку запросов, используется как reference из `platform-data-core/SKILL.md`.
- **duplicate_of**: `platform-data/platform-data-core/SKILL.md` — §2 «Критические ограничения MCP» дословно повторяет примеры по NULL, датам (`ДАТАВРЕМЯ`) и строковым литералам вместо чистой ссылки на этот файл.

---

### platform-data/platform-data-core/SKILL.md

- **lines**: 182
- **purpose**: Связывает три MCP-операции работы с данными платформы — поиск метаданных (`list_metadata_objects`/`get_metadata_structure`), выполнение запросов (`validate_query`/`execute_query`) и навигационные ссылки (`parse_nav_link`/`get_nav_link`) в единый рабочий цикл.
- **type**: проектно-специфичное — центрируется вокруг набора кастомных MCP capabilities этого фреймворка (`uses_capabilities` в фронтматтере), которые не являются стандартным публичным инструментом 1С-экосистемы, а специфичной обвязкой харнесса.
- **probe_facts**:
  - Как получить список реквизитов/структуру объекта метаданных 1С (справочника/документа/регистра) до построения запроса по нему — общий вопрос через ближайшее публичное знание («открыть конфигурацию/структуру объекта в Конфигураторе или EDT»); конкретные capability `list_metadata_objects`/`get_metadata_structure` — секция §1 «Metadata Discovery». `expected_in_weights`: частично (общий принцип — да, конкретные имена инструментов — нет).
  - Поддерживают ли параметризованные запросы (`&Параметр`) конкретную HTTP/MCP-обёртку `execute_query`, или нужно подставлять значения прямо в текст запроса — секция «Критические ограничения MCP» п.2-3. `expected_in_weights`: нет (это специфика конкретной MCP-интеграции execute_query, а не общее свойство языка запросов 1С).
  - Как разобрать входящую навигационную ссылку вида `e1cib/data/...` на тип объекта и Ссылку и как сформировать её обратно — секция §3 «Nav Link». `expected_in_weights`: частично (формат `e1cib/data/...` — публичный и известный формат навигационных ссылок 1С, а конкретные capability `parse_nav_link`/`get_nav_link` этого фреймворка — нет).
- **verbosity**: high
- **usefulness**: ядро процесса — центральный связывающий workflow для повседневной работы с живыми данными 1С через MCP; таблица «критических ограничений MCP» (нет параметров `&Ссылка`, нужен `ПЕРВЫЕ N`) фиксирует важные и неочевидные ограничения конкретной интеграции.
- **duplicate_of**: `platform-data/platform-data-core/references/query-syntax-cheatsheet.md` — §2 повторяет примеры NULL/даты/строки из cheatsheet почти дословно вместо ссылки.

---

### platform-data/xml-generation/SKILL.md

- **lines**: 197
- **purpose**: Верхнеуровневый роутер/обзор всего тулкита `xml-gen` — три рабочие поверхности CLI, индекс 11 под-навыков, сквозные принципы (encoding, line endings, idempotency, vendor-support guard), быстрые workflow-примеры и антипаттерны.
- **type**: проектно-специфичное — `xml-gen` является собственным CLI-инструментом этого фреймворка (устанавливается `tools/install.py --install-xml-gen`), не публичным инструментом 1С, хотя оперирует форматом Designer XML.
- **probe_facts**:
  - Формат выгрузки конфигурации 1С в XML поддерживается только Конфигуратором (Designer), у EDT — другой XML-формат, конвертация не тривиальна — секция §4 п.1 и «Не используй когда». `expected_in_weights`: да (широко известный факт о несовместимости форматов Designer vs EDT-выгрузки).
  - Designer XML-выгрузка 1С кодируется UTF-8 с BOM, между тегами — CRLF, внутри текстовых блоков (`<v8:content>`) — bare LF — секция §4 п.2-3. `expected_in_weights`: частично (общий факт про BOM/CRLF в выгрузке 1С известен опытным разработчикам, но точная спецификация по конкретным полям — менее гарантирована).
  - Как определить, что объект типовой конфигурации находится «на поддержке» поставщика и его нельзя редактировать напрямую — через служебный файл `Ext/ParentConfigurations.bin` с флагами уровня поддержки (`G=1` блокирует всю конфигурацию, `f1=0` — объект, `f1=2` нужен для удаления) — секция §4 п.8 / §5. `expected_in_weights`: нет (внутренний бинарный формат support-флагов малодокументирован публично, редко фигурирует в общих источниках).
- **verbosity**: high
- **usefulness**: ядро процесса — обязательная точка входа для всего семейства xml-generation; без него неясно, какой из 11 под-навыков выбрать для конкретной задачи.
- **duplicate_of**: частичное — §3 «Универсальные команды» кратко пересказывает то же содержимое, что подробно раскрыто в `references/universal-commands.md`; §3.1 аналогично дублирует вводную часть `references/behavioral-oracles.md` (это осознанный паттерн роутер→деталь, но по факту одна и та же информация присутствует дважды на разных уровнях детализации). §5 «Создать расширение и заимствовать объект» дублирует базовые команды, которые также приведены в `extension-operations/SKILL.md`.

---

### platform-data/xml-generation/references/behavioral-oracles.md

- **lines**: 109
- **purpose**: Справочник diagnostic/oracle-команд для сопровождения самого инструмента `xml-gen` (round-trip DSL vs CLI-реконструкция, PredefinedData/ExchangePlanContent oracle, demo-аудит, EDT-derived invariants).
- **type**: проектно-специфичное — это внутренняя QA/тестовая инфраструктура конкретного CLI-инструмента (`xml-gen oracle ...`), а не публичный 1С-инструмент и не то, что нужно для обычной задачи генерации XML.
- **probe_facts**:
  - Как убедиться, что декомпиляция/компиляция XML-артефакта (round-trip) не теряет данные — общий инженерный паттерн «oracle/round-trip тестирования»; конкретные режимы `xml-gen oracle mxl --mode dsl|cli|both` — секция «MXL oracle». `expected_in_weights`: частично (общий QA-паттерн round-trip — да как общепрограммистское знание, конкретные флаги/режимы этого инструмента — нет).
  - Что такое `PredefinedData` (предопределённые элементы справочников/планов видов характеристик) и `ExchangePlanContent` (состав плана обмена) в конфигурации 1С на уровне `Ext/Predefined.xml` / `Ext/Content.xml` — секция «PredefinedData oracle» / «ExchangePlanContent oracle». `expected_in_weights`: частично (само существование этих сущностей в 1С — да, детальный XML-состав вроде `ActionPeriodIsBase`, `Displaced` — нет).
  - Какие артефакты XCF (`Ext/Content.xml`, `Ext/Predefined.xml`, `Ext/Aggregates.xml`, `Ext/Flowchart.xml` и др.) не имеют полного decompiler/CommandPlan oracle и остаются в статусе `validation_only_no_decompiler` — секция «Demo oracle». `expected_in_weights`: нет (это внутренний статус конкретного инструмента, не публичное знание).
- **verbosity**: med
- **usefulness**: сомнителен (для типичного dev-агента) — сам SKILL.md xml-generation явно говорит «в обычных задачах генерации/правки XML они не нужны»; полезен только при сопровождении/тестировании самого инструмента xml-gen, то есть для узкого круга задач maintenance тулинга, а не для рядового агента, генерирующего форму/роль/расширение.
- **duplicate_of**: `platform-data/xml-generation/SKILL.md` §3.1 (краткое резюме этого файла дублируется в диспетчере).

---

### platform-data/xml-generation/references/universal-commands.md

- **lines**: 62
- **purpose**: Детальный CLI-справочник по сквозным командам `xml-gen`: `validate` (флаги, exit codes), универсальные `form/template/help add`, побайтовая `edit replace-text`.
- **type**: проектно-специфичное — это документация собственного CLI-инструмента (флаги, exit codes, формат stdout JSON), специфичная для `xml-gen`.
- **probe_facts**:
  - Общая проблема побайтовой правки XML, выгруженного 1С Конфигуратором (риск сломать line endings/BOM при обычном текстовом редактировании мультилайн-полей вроде тултипов/описаний) — секция «Побайтовая замена текста (edit replace-text)». `expected_in_weights`: частично (сама проблема известна тем, кто работал с выгрузками 1С, конкретная команда `xml-gen edit replace-text` и её флаги — нет).
  - Общий CLI-паттерн exit-кодов: `0` = успех, `1` = ошибка/не найдено, `2` = предупреждения/можно продолжать — секция «validate» / «edit replace-text». `expected_in_weights`: да (стандартный общепрограммистский паттерн для CLI-утилит, не специфичен для 1С).
  - Команды `xml-gen form add` / `template add` / `help add` для регистрации нового артефакта (формы, макета, справки) у объекта метаданных без полной пересборки конфигурации — секция «Универсальные add-операции». `expected_in_weights`: нет (конкретный CLI-интерфейс этого инструмента, не публичный API 1С).
- **verbosity**: low
- **usefulness**: полезен — компактный и часто нужный справочник (validate и edit replace-text используются практически в любой задаче xml-generation); формат вывода JSON и exit codes задокументированы точно.
- **duplicate_of**: `platform-data/xml-generation/SKILL.md` §3 — тот же набор команд перечислен кратко в диспетчере (роутер дублирует детальную страницу на уровне обзора).

---

### platform-data/xml-generation/config-operations/SKILL.md

- **lines**: 68
- **purpose**: Работа с корнем конфигурации 1С через `xml-gen config init/info/edit/validate` — свойства Configuration.xml и состав ChildObjects.
- **type**: 1c-доменное — формат `Configuration.xml` и канонический порядок ChildObjects — это факт платформы 1С:Предприятие (Конфигуратор/EDT это же требование проверяют при загрузке конфигурации).
- **probe_facts**:
  - `Configuration.xml` ChildObjects требует строгий канонический порядок из 44 типов, начиная с `Language → Subsystem → StyleItem → ... → IntegrationService` — секция «ChildObjects — порядок 44 типов». `expected_in_weights`: частично (общая структура ChildObjects известна опытным разработчикам 1С, но точное воспроизведение полного порядка всех 44 типов подряд — маловероятно).
  - `config validate` выполняет 10 проверок, включая `InternalInfo` с 7 `ClassId` и 11 enum-свойств — секция «config validate». `expected_in_weights`: нет (это специфика набора проверок конкретного инструмента `xml-gen`, не публичный стандарт валидации).
  - `config init` создаёт `Configuration.xml` + `Languages/Русский.xml` + `ConfigDumpInfo.xml` + заглушки модулей — секция «config init». `expected_in_weights`: частично (общее знание о базовых файлах выгрузки пустой конфигурации — да, что именно генерирует эта CLI-команда — нет).
- **verbosity**: low
- **usefulness**: полезен — компактный и корректный справочник, нужен реже, чем форм/роли/расширения (обычно конфигурация уже существует), но критичен при инициализации новой CF или ручной правке ChildObjects.
- **duplicate_of**: нет

---

### platform-data/xml-generation/extension-operations/SKILL.md

- **lines**: 160
- **purpose**: Полный операционный цикл работы с расширениями конфигурации (CFE) — init, borrow объектов/форм, patch-method (перехватчики), diff, validate.
- **type**: 1c-доменное — расширения конфигурации (CFE), ObjectBelonging=Adopted, ID-диапазоны Base/Extension, перехватчики методов (`&Перед`/`&После`/`&Вместо`/`&ИзменениеИКонтроль`) — это платформенные концепции 1С:Предприятие.
- **probe_facts**:
  - Заимствованные объекты CFE помечаются атрибутом `ObjectBelonging=Adopted`, отсутствие атрибута означает собственный объект — секция «Ключевые концепции CFE». `expected_in_weights`: частично (сама концепция заимствования объекта в расширении широко известна, точное имя XML-атрибута — знают не все).
  - ID-диапазоны объектов: Base 1–999999, Extension 1000000+ — секция «Ключевые концепции CFE». `expected_in_weights`: да (широко известный и часто цитируемый факт про диапазоны ID у расширений 1С).
  - Перехватчики методов оформляются декораторами `&Перед`/`&После`/`&Вместо`/`&ИзменениеИКонтроль` над процедурой с префиксом расширения — секция «Ключевые концепции CFE» / «extension patch-method». `expected_in_weights`: да (это публичный, широко документированный синтаксис BSL для расширений конфигурации).
  - `extension validate` выполняет 9 проверок, включая `InternalInfo` (7 ClassId) и заимствованные объекты (`ObjectBelonging=Adopted` + `ExtendedConfigurationObject`) — секция «extension validate». `expected_in_weights`: нет (специфика набора проверок конкретного инструмента `xml-gen`).
- **verbosity**: high
- **usefulness**: ядро процесса — работа с расширениями (заимствование объектов типовой, патчи методов) — один из самых частых сценариев кастомизации 1С; хорошо задокументированы неочевидные моменты (`--borrow-main-attribute`, поведение borrow без флага).
- **duplicate_of**: `platform-data/xml-generation/SKILL.md` §5 «Создать расширение и заимствовать объект» — те же 3 команды (`extension init/borrow/diff`) приведены как quick example в диспетчере.

---

### platform-data/xml-generation/form-dsl/SKILL.md

- **lines**: 113
- **purpose**: Компиляция управляемой формы 1С из JSON DSL (`form compile`, включая `--from-object` по метаданным объекта) и draft `form decompile`; структура DSL (attributes/elements), явные ограничения (что специально не переносится в DSL).
- **type**: 1c-доменное — итоговые XML-типы элементов формы (InputField, UsualGroup, Table и т.д.), рантайм-типы формы (FormDataStructure и др.) и правило соответствия директив компиляции контексту (`&НаСервере`/`&НаКлиенте`) — платформенные факты 1С, хотя JSON DSL как таковой — собственная абстракция инструмента.
- **probe_facts**:
  - Рантайм-типы `FormDataStructure`/`FormDataCollection`/`FormDataTree` не существуют в XML-схеме формы и вызывают XDTO-ошибку при загрузке — секция «Запрещённые runtime-типы». `expected_in_weights`: частично (общее знание о том, что не все рантайм-типы формы сериализуемы в XML — да у опытных разработчиков, конкретная формулировка ошибки XDTO — нет).
  - Какой XML-тип элемента управляемой формы 1С соответствует группе/таблице/переключателю (`UsualGroup`/`Table`/`RadioButtonField`) — секция «UI-элементы (elements)»; конкретное соответствие DSL-имён (`group`, `table`, `radio`) этого инструмента — там же. `expected_in_weights`: частично (сама XML-схема формы — да публичный факт, соответствие DSL-имён — нет).
  - Директивы обработчиков событий формы должны соответствовать контексту выполнения: `onCreateAtServer` → `&НаСервере`, `onOpen`/`onClose`/`beforeClose` → `&НаКлиенте` — секция «Команды и события». `expected_in_weights`: да (базовое, повсеместно известное правило директив контекста BSL).
- **verbosity**: med
- **usefulness**: ядро процесса — формы являются центральным UI-объектом почти любой 1С-конфигурации; навык явно и полезно разграничивает, что генерируется DSL, а что должно писаться кодом (условное оформление, отборы списка).
- **duplicate_of**: нет прямого — только упоминание в индексной таблице `xml-generation/SKILL.md` §2 (не содержательное дублирование).

---

### platform-data/xml-generation/role-dsl/SKILL.md

- **lines**: 83
- **purpose**: Компиляция ролей из JSON DSL (`role compile`) и точечное редактирование `Rights.xml` (`role add-object`, `role add-right`).
- **type**: 1c-доменное — роли/права объектов конфигурации 1С (`RoleRight` enum, структура `Rights.xml`) — платформенная концепция.
- **probe_facts**:
  - Права роли задаются перечислением `RoleRight` строго в PascalCase (`Read`, `Insert`, `Update`, `Delete`, `View`, `Edit`, `InteractiveInsert`, `InteractiveDelete`, `Posting`, `UndoPosting`) — секция «Структура DSL» / «Ловушки». `expected_in_weights`: да (публичная, широко известная схема прав в ролях конфигурации 1С).
  - `role compile` создаёт `output_dir/Roles/<Name>.xml` + `output_dir/Roles/<Name>/Ext/Rights.xml` — секция «Команды». `expected_in_weights`: частично (общая структура каталога `Roles/<Name>/Ext/Rights.xml` в выгрузке 1С известна, что именно генерирует конкретная CLI-команда — нет).
  - Корневой JSON DSL роли — массив `objects` (не map-форма), с 8 корневыми полями (`name`, `objects`, `templates`, `comment`, `synonym`, `setForNewObjects`, `setForAttributesByDefault`, `independentRightsOfChildObjects`) — секция «Структура DSL». `expected_in_weights`: нет (собственная JSON-схема этого инструмента, не часть 1С-экосистемы).
- **verbosity**: low
- **usefulness**: полезен — компактный, покрывает частую, но не самую массовую задачу (настройка прав); хорошо задокументированы 3 конкретные ловушки формата DSL.
- **duplicate_of**: нет прямого (антипаттерн `role compile` с файлом на выход из `xml-generation/SKILL.md` §6 дополняет, а не дублирует содержимое этого файла).

---

### platform-data/xml-generation/subsystem-interface/SKILL.md

- **lines**: 129
- **purpose**: Работа с подсистемами конфигурации (`subsystem compile/info/edit/validate`) и командным интерфейсом (`interface edit/validate` для `CommandInterface.xml`) — видимость/порядок/размещение команд.
- **type**: 1c-доменное — подсистемы и `CommandInterface.xml` — публичные структуры конфигурации 1С:Предприятие.
- **probe_facts**:
  - `CommandInterface.xml` поддерживает секции `CommandsVisibility`/`CommandsPlacement`/`CommandsOrder`/`SubsystemsOrder`/`GroupsOrder` в каноническом порядке — секция «interface validate». `expected_in_weights`: частично (общая структура файла командного интерфейса известна опытным разработчикам, точный канонический порядок секций — менее гарантирован).
  - `subsystem validate` выполняет 13 проверок структуры, Properties, Content, ChildObjects, файлов, CommandInterface — секция «subsystem validate». `expected_in_weights`: нет (специфика набора проверок конкретного инструмента `xml-gen`).
  - Ссылки на команды используют форматы `CommonCommand.X`, `Тип.Имя.StandardCommand.Create`, `Тип.Имя.Command.X`, `Report.Имя.Command.X`, UUID-ссылка `0:<uuid>` — секция «Формат ссылок на команды». `expected_in_weights`: да (это публичный, широко документированный формат ссылок на команды в конфигурации 1С).
- **verbosity**: med
- **usefulness**: полезен — организация интерфейса конфигурации (видимость/порядок команд) — регулярная, хотя не самая частая задача; явно указано требование, что субагент `reviewer` обязан вызывать `interface validate` перед финальным ревью.
- **duplicate_of**: нет

---

## Сводка дублирований (сквозной обзор)

1. **rac-use ↔ subsystem-update** — команды блокировки ИБ/завершения сеансов продублированы инлайн в `subsystem-update`, несмотря на формальный `depends_on: rac-use`.
2. **query-syntax-cheatsheet.md ↔ platform-data-core/SKILL.md** — примеры по NULL, датам (`ДАТАВРЕМЯ`) и строковым литералам дословно повторены в §2 SKILL.md вместо чистой ссылки на cheatsheet.
3. **xml-generation/SKILL.md ↔ references/universal-commands.md** — §3 диспетчера кратко пересказывает содержимое этого reference-файла (осознанный паттерн роутер→деталь, но содержательно одно и то же в двух местах).
4. **xml-generation/SKILL.md ↔ references/behavioral-oracles.md** — §3.1 диспетчера аналогично резюмирует reference-файл.
5. **xml-generation/SKILL.md §5 ↔ extension-operations/SKILL.md** — quick-example команды `extension init/borrow/diff` повторены в диспетчере.

Подтверждение: config-operations, form-dsl, role-dsl и subsystem-interface содержательных дублирований между собой не имеют — это независимые под-инструменты одного диспетчера.

---

# ЧАСТЬ D-xml-subdirs

# Аудит: xml-generation/{epf-full, forms-toolkit, meta-operations, mxl-dsl, skd-dsl, skd-edit}

Базовый путь: `framework/skills/tool-usage/platform-data/xml-generation/`
Обработано файлов: 25. Суммарно строк: **3904**.

Примечание по методике (после корректировки задания):
- `probe_facts` присутствуют у ВСЕХ файлов (минимум 2-3), включая чисто DSL-командные. Для проектно-специфичных файлов факт сформулирован как вопрос через ближайшее публичное знание (например, «знает ли модель общий паттерн X», где X — независимый от фреймворка аналог).
- `expected_in_weights` (нет|частично|да) — гипотеза «знает ли модель это без чтения файла», для последующей матрицы «вопрос × модель». Это НЕ основание для usefulness/verbosity.
- `verbosity` — чисто редакторская оценка (вода/повторы/длина), не связана со знанием модели.
- `usefulness` — оценка роли файла в реальном рабочем процессе навыка, не связана со знанием модели.

---

### epf-full/SKILL.md
- lines: 92
- purpose: Индекс/оркестрация полного цикла EPF/ERF (init → add-form → add-template → BSP-регистрация в BSL).
- type: проектно-специфичное
- probe_facts:
  - Типична ли для CLI-генераторов конфигурационных объектов многошаговая модель «создать базовый объект → добавить дочерние сущности (форма/макет)» — как в `epf init → add-form → add-template`? — expected_in_weights: частично
  - Известно ли модели, что регистрация обработки в подсистеме БСП «Дополнительные отчёты и обработки» выполняется правкой BSL-функции `СведенияОВнешнейОбработке()` в модуле объекта, а не декларативным манифестом? — expected_in_weights: частично
  - Известна ли модели файловая структура выгрузки конфигурации 1С в формате Конфигуратора (корневой XML объекта + `Ext/ObjectModule.bsl` + `Forms/<Name>/Ext/Form.xml` + `Templates/<Name>/Ext/Template.xml`)? — expected_in_weights: да
- verbosity: low
- usefulness: ядро процесса — единственная точка входа в workflow EPF, компактный индекс команд с корректными путями Designer.
- duplicate_of: нет (детализация вынесена в epf-base.md/templates.md/epf-bsp.md — ожидаемое разбиение индекс→детали).

### epf-full/references/epf-base.md
- lines: 104
- purpose: Синтаксис CLI-команд `epf init/add-form/add-template/add-attribute/add-tabular-section`.
- type: проектно-специфичное
- probe_facts:
  - Знает ли модель, что внешний отчёт (ERF) и внешняя обработка (EPF) в 1С различаются только типом объекта, при этом внутренняя структура (форма + макеты + реквизиты) идентична? — expected_in_weights: частично
  - Известно ли модели, что реквизиты и табличные части добавляются в корневой XML объекта метаданных, а не в XML формы? — expected_in_weights: да
  - Ожидает ли модель типичную unix-style CLI-конвенцию, где часть аргументов именованная (`--name`), а один — позиционный, в конце команды? — expected_in_weights: да
- verbosity: med
- usefulness: полезен — практический референс с блоками «Правильно/Неправильно», снимает конкретные ошибки использования (позиционные vs именованные аргументы).
- duplicate_of: нет.

### epf-full/references/epf-bsp.md
- lines: 346
- purpose: Шаблоны BSL-кода для регистрации EPF/ERF в подсистеме БСП «Дополнительные отчёты и обработки» (СведенияОВнешнейОбработке, команды).
- type: 1c-доменное
- probe_facts:
  - Функция `СведенияОВнешнейОбработке()` строится через `ДополнительныеОтчетыИОбработки.СведенияОВнешнейОбработке("2.2.2.1")` — версия формата описания подсистемы БСП — сверяется с ИТС/документацией БСП «Дополнительные отчёты и обработки». — expected_in_weights: частично
  - Виды обработки: глобальные (ДополнительнаяОбработка, ДополнительныйОтчет — без `Назначение`) vs назначаемые (ЗаполнениеОбъекта, Отчет, ПечатнаяФорма, СозданиеСвязанныхОбъектов — требуют `Назначение.Добавить(...)`) — реальный API `ДополнительныеОтчетыИОбработкиКлиентСервер.ВидОбработкиХХХ()`. — expected_in_weights: частично
  - Типы команд по умолчанию по виду обработки (`ТипКомандыОткрытиеФормы`, `ТипКомандыВызовСерверногоМетода` и др.) — сверяется с модулем БСП. — expected_in_weights: частично
  - Модификатор `"ПечатьMXL"` для команд `ПечатнаяФорма`, использующийся вместе с `УправлениеПечатью.СведенияОПечатнойФорме` — реальный контракт подсистемы «Печать» БСП. — expected_in_weights: нет
- verbosity: high
- usefulness: полезен — точный и насыщенный шаблонами материал по интеграции с БСП, но крупный (346 строк) для одной операции.
- duplicate_of: нет.

### epf-full/references/learned-patterns.md
- lines: 29
- purpose: Зафиксированный приём — Designer-load (`1cv8 DESIGNER /LoadExternalDataProcessorOrReportFromFiles`) как единственная сильная проверка собранного EPF, сильнее чем `xml-gen validate`.
- type: 1c-доменное
- probe_facts:
  - Параметр командной строки Конфигуратора `/LoadExternalDataProcessorOrReportFromFiles <root.xml> <output.epf>` для загрузки/компиляции внешней обработки/отчёта из дерева исходников — документируется в ИТС «Параметры командной строки запуска конфигуратора и предприятия». — expected_in_weights: частично
  - Семантика кода возврата (EXIT=0 успех / EXIT=1 «Ошибка загрузки документа») для пакетного режима Конфигуратора — проверяемо по той же документации. — expected_in_weights: да
  - Известно ли модели, что встроенный валидатор структуры (в данном случае — `xml-gen validate`) в принципе может пропускать дефекты разметки, которые ловит только реальный загрузчик платформы — общий паттерн «линтер слабее компилятора»? — expected_in_weights: да
- verbosity: low
- usefulness: полезен — единственная, но ценная лемма про недостаточность xml-gen validate; статус `candidate` (не подтверждён повторно), файл содержит всего одну запись.
- duplicate_of: нет.

### epf-full/references/templates.md
- lines: 253
- purpose: Команды `template add/remove/add-help` — макеты и встроенная справка для произвольных объектов метаданных (Catalog/Document/Report/DataProcessor и др.).
- type: 1c-доменное
- probe_facts:
  - Регистрация макета через `<Template>Имя</Template>` в `ChildObjects` корневого XML объекта метаданных — реальная структура XML-описания конфигурационного объекта 1С. — expected_in_weights: да
  - `MainDataCompositionSchema` — свойство объекта `Report`, указывающее основную схему компоновки данных отчёта — проверяемо по структуре XML отчёта. — expected_in_weights: да
  - Допустимые типы макетов (`SpreadsheetDocument`, `HTMLDocument`, `TextDocument`, `BinaryData`, `DataCompositionSchema`) как реальная типология макетов объектов метаданных 1С. — expected_in_weights: да
  - Файл `Help.xml` + `IncludeHelpInContents` в Form.xml — механизм встроенной справки, включающий кнопку справки в `AutoCommandBar` формы — проверяемо по структуре Form.xml. — expected_in_weights: частично
- verbosity: high
- usefulness: полезен — детальный и практичный, включая конвенцию именования (`ПФ_` для печатных форм).
- duplicate_of: нет (§4 в epf-full/SKILL.md — краткая сводка этого же материала, ожидаемое разбиение индекс/детали, не дублирование).

---

### forms-toolkit/SKILL.md
- lines: 78
- purpose: Индекс операций работы с формами (`form-info/edit/validate/decompile`, `epf-validate`, `element-mapping`) и порядок их применения.
- type: проектно-специфичное
- probe_facts:
  - Известен ли модели типовой цикл разработки управляемой формы 1С: анализ структуры → редактирование → валидация → повторный анализ — как общая практика итеративной разработки UI-артефактов? — expected_in_weights: частично
  - Знает ли модель, что для внешних обработок (EPF/ERF) обычно нужна отдельная процедура валидации, отличная от валидации отдельной формы (разные уровни структуры)? — expected_in_weights: частично
  - Известно ли модели, что сопоставление видимого текста (Title) элемента формы с его программным именем (Name) — частая задача при написании автотестов UI (например, Vanessa ADD) в 1С? — expected_in_weights: частично
- verbosity: low
- usefulness: ядро процесса — компактная точка входа с таблицей триггеров и жизненным циклом.
- duplicate_of: нет.

### forms-toolkit/references/edit.md
- lines: 134
- purpose: JSON DSL для `form edit` — типы элементов формы, система типов реквизитов, события, кнопки-команды, генерация обработчиков BSL.
- type: 1c-доменное
- probe_facts:
  - Соответствие `kind` → реальный XML-тег элемента формы 1С (`InputField`, `CheckBoxField`, `LabelDecoration`, `Table`, `Button`, `UsualGroup`, `Pages`, `CommandBar`) — проверяемо по структуре Form.xml/XSD платформы. — expected_in_weights: да
  - Companion-элементы (`ContextMenu`, `ExtendedTooltip`, `AutoCommandBar`, `SearchStringAddition` и др.), обязательные для соответствующих типов элементов формы — реальный платформенный контракт Form.xml. — expected_in_weights: частично
  - `MainAttribute` — флаг основного реквизита формы (`<MainAttribute>true</MainAttribute>`) — реальное свойство реквизита формы. — expected_in_weights: да
  - Директивы компиляции обработчиков (`&НаКлиенте`/`&НаСервере`) в модуле формы — часть синтаксиса BSL, проверяемая по документации платформы. — expected_in_weights: да
- verbosity: high
- usefulness: ядро процесса — центральная операция навыка (редактирование форм), детально и практично.
- duplicate_of: таблица «kind → XML-тег» частично пересекается с таблицей сокращений типов элементов в info.md (оба описывают один и тот же набор тегов формы под разными масками — «kind для записи» vs «сокращение для чтения»). Не строгий дубль (разное направление: write vs read), но общая модель данных продублирована в двух файлах.

### forms-toolkit/references/element-mapping.md
- lines: 108
- purpose: Алгоритм поиска программного имени (`Name`) элемента формы по видимому тексту (`Title`) — для написания Vanessa-сценариев.
- type: 1c-доменное
- probe_facts:
  - Form.xml хранит для каждого элемента `Name` (программное имя) и `Title` (видимый текст, может быть мультиязычным через `<v8:item><v8:lang>/<v8:content>`) — реальная структура XML формы 1С. — expected_in_weights: да
  - `Title` элемента формы может наследоваться из `Synonym` реквизита объекта метаданных (Документ/Справочник) — реальное поведение платформы. — expected_in_weights: частично
  - Формат отображения значений в таблицах 1С (число с разделителем разрядов и NN знаками после запятой, булево как «Да/Нет») — проверяемо по документации форматов чисел/дат платформы 1С. — expected_in_weights: частично
- verbosity: med
- usefulness: полезен — узкоспециализирован (Vanessa/BDD), но закрывает реальную и частую проблему автоматизации UI-тестов.
- duplicate_of: нет прямого — тематически смежен с info.md (оба читают Form.xml), но разное назначение: info.md — вывод CLI-инструмента, element-mapping.md — ручной grep-алгоритм поиска. Не дублирование.

### forms-toolkit/references/info.md
- lines: 176
- purpose: Формат вывода `form info` (Properties/Events/Elements/Attributes/Parameters/Commands) и таблица сокращений типов элементов.
- type: проектно-специфичное
- probe_facts:
  - Известна ли модели общая практика управляемых форм 1С, что реквизит формы может быть помечен как основной (`MainAttribute`), и на форме допустим только один такой реквизит? — expected_in_weights: да
  - Известна ли модели типология элементов формы 1С (поле ввода, флажок, надпись, таблица, группа, страницы, командная панель) и их программные имена/теги? — expected_in_weights: да
  - Знает ли модель про параметр формы `KeyParameter` — параметр, участвующий в определении уникальности/идентификации формы? — expected_in_weights: частично
- verbosity: high
- usefulness: полезен — облегчает чтение структуры формы без парсинга XML вручную.
- duplicate_of: таблица сокращений элементов дублирует по смыслу таблицу `kind → XML-тег` в edit.md (см. выше) — один и тот же набор типов элементов формы описан дважды в разных файлах референсов одного навыка.

### forms-toolkit/references/validate.md
- lines: 213
- purpose: Чеклист проверок `form-validate` (структура Form.xml, ID-пулы, DataPath-резолв) и `epf-validate` (структура EPF/ERF), коды ошибок.
- type: 1c-доменное
- probe_facts:
  - Корневой элемент Form.xml с атрибутом `version="2.17"` — версия формата описания управляемой формы, соответствующая текущим релизам платформы — проверяемо по формату файла Form.xml. — expected_in_weights: нет
  - `AutoCommandBar` с зарезервированным `id="-1"` — обязательный элемент формы — реальная структура Form.xml. — expected_in_weights: нет
  - Структура корневого XML внешней обработки: `MetaDataObject/ExternalDataProcessor`, `InternalInfo` (ClassId, ContainedObject, GeneratedType), `ChildObjects` (Attribute/TabularSection/Form) — реальная структура XML-описания EPF, проверяемая по формату конфигурации 1С. — expected_in_weights: частично
  - `BaseForm` и `version` для заимствованных форм расширений — реальный механизм заимствования форм (borrowed forms) в расширениях конфигурации 1С. — expected_in_weights: частично
- verbosity: high
- usefulness: ядро процесса — валидация обязательна перед сборкой EPF/формы, детальный и насыщенный конкретными кодами ошибок материал.
- duplicate_of: нет.

---

### meta-operations/SKILL.md
- lines: 157
- purpose: Команды `meta compile/info/edit/validate/remove` для 23 типов объектов метаданных 1С (создание, реквизиты, ТЧ, предопределённые данные, состав плана обмена).
- type: 1c-доменное
- probe_facts:
  - Полный набор свойств `Catalog` (hierarchical, hierarchyType: HierarchyFoldersAndItems|HierarchyItemsOnly, codeLength, codeType, autonumbering и т.д.) — соответствует реальным свойствам объекта метаданных «Справочник» 1С. — expected_in_weights: да
  - Список зарезервированных платформой имён реквизитов, запрещённых для пользовательских (Ref/Ссылка, Code/Код, Description/Наименование, Parent/Родитель, Owner/Владелец, IsFolder/ЭтоГруппа, PostingMode/РежимПроведения, Posted/Проведен, Date/Дата, Number/Номер и др.) — реальное платформенное ограничение. — expected_in_weights: да
  - `FillFromFillingValue`/`FillValue`/`DataHistory` допустимы только для реквизитов `InformationRegister` (РегистрСведений); для прочих регистров — XSD-ошибка при загрузке — проверяемо по документации платформы. — expected_in_weights: нет
  - Номенклатура 23 типов метаданных (Catalog, Document, Enum, 4 вида регистров, BusinessProcess/Task, HTTPService/WebService и др.) соответствует реальному составу объектов конфигурации 1С. — expected_in_weights: да
- verbosity: high
- usefulness: ядро процесса.
- duplicate_of: нет.

### meta-operations/references/batch-patch.md
- lines: 264
- purpose: Полная спецификация JSON batch-патча `meta edit --batch` (add/remove/modify, мультиобъектные патчи, позиционная вставка, синонимы ключей).
- type: проектно-специфичное
- probe_facts:
  - Известна ли модели общая концепция batch/patch-файла с секциями add/remove/modify для декларативного описания набора изменений (аналог JSON Patch RFC 6902 или скриптов миграции схемы БД)? — expected_in_weights: да
  - Знает ли модель, что транзакционность batch-операции («либо всё применяется, либо ничего откатывается») — стандартное требование для инструментов миграции схем данных? — expected_in_weights: да
  - Известно ли модели типовое разделение операций на add/remove/modify применительно к структуре метаданных объекта (реквизиты, табличные части, ресурсы, измерения)? — expected_in_weights: частично
- verbosity: high
- usefulness: полезен — детальный референс для сложных пакетных правок; относительно длинный (264 строки) для одной CLI-опции.
- duplicate_of: закономерное расширение раздела «Batch JSON-патч» в meta-operations/SKILL.md (там — краткое упоминание с отсылкой сюда) — не дублирование, ожидаемая структура индекс→детали.

---

### mxl-dsl/SKILL.md
- lines: 163
- purpose: Обзор MXL DSL — JSON-формат описания табличных документов (печатных форм) 1С и команды compile/decompile/info/validate.
- type: 1c-доменное
- probe_facts:
  - BSL API `ТабличныйДокумент.ПолучитьМакет("Имя")` / `Макет.ПолучитьОбласть("Имя")` / `Область.Параметры.X` — реальный API работы с макетами табличных документов 1С. — expected_in_weights: да
  - Для пересекающихся именованных областей (Rows × Columns, например этикетки) обращение через разделитель `"|"`: `ПолучитьОбласть("Высота|Ширина")` — реальный платформенный синтаксис пересечения областей макета. — expected_in_weights: частично
  - Форматные токены 1С в ячейках макета (`"ЧДЦ="` — число десятичных цифр, `"ДФ="` — формат даты) — реальные токены формата данных 1С. — expected_in_weights: частично
- verbosity: med
- usefulness: ядро процесса — точка входа в MXL/печатные формы, с явным разделением «что делает DSL» vs «что делается кодом» (рантайм-условное оформление).
- duplicate_of: нет.

### mxl-dsl/references/dsl-spec.md
- lines: 257
- purpose: Полная спецификация полей MXL JSON DSL (columns, fonts, styles, areas, rows, cells, rowStyle, rowspan, detail, форматы).
- type: проектно-специфичное
- probe_facts:
  - Известна ли модели общая концепция табличного документа 1С (SpreadsheetDocument) как сетки ячеек с именованными областями, стилями и параметрами заполнения? — expected_in_weights: частично
  - Знает ли модель форматные токены 1С для чисел/дат, применимые к ячейкам макета (`ЧДЦ=`, `ДФ=`, `ЧРГ=`, `Л=`)? — expected_in_weights: частично
  - Известно ли модели, что объединение ячеек в табличном документе 1С может быть как горизонтальным (аналог colspan), так и вертикальным (аналог rowspan), и это разные механизмы в XML макета? — expected_in_weights: частично
- verbosity: high
- usefulness: ядро процесса — необходимый детальный референс для `mxl compile`, включает полный пример.
- duplicate_of: нет (ожидаемая детализация SKILL.md).

### mxl-dsl/references/info-modes.md
- lines: 137
- purpose: Как читать вывод `mxl info` — типы областей (Rows/Columns/Rectangle/Drawing), пересечения, параметры, detail, JSON-режим.
- type: 1c-доменное
- probe_facts:
  - Именованные области макета табличного документа 1С бывают четырёх типов: Rows (диапазон строк), Columns (диапазон колонок), Rectangle (прямоугольник с собственным набором колонок `columnsID`), Drawing (рисунок/штрихкод) — реальная типология областей SpreadsheetDocument. — expected_in_weights: частично
  - Параметр расшифровки (detail) — платформенный механизм перехода по клику на ячейку макета к связанному объекту — реальная функциональность печатных форм 1С. — expected_in_weights: частично
  - Доступ к рисункам макета через коллекцию `Макет.Рисунки.Найти("Имя")` — реальный BSL API. — expected_in_weights: нет
- verbosity: med
- usefulness: полезен.
- duplicate_of: нет.

### mxl-dsl/references/validate-classes.md
- lines: 122
- purpose: Классы ошибок валидатора собранного Template.xml (bad-column-index, bad-format-index, bad-height, palette-refs, area-ranges, merge-ranges).
- type: 1c-доменное
- probe_facts:
  - SpreadsheetDocument XML использует индексированные палитры (шрифты, рамки, цвета, форматы), на которые ячейки ссылаются по числовому индексу — реальная внутренняя структура формата макета 1С. — expected_in_weights: нет
  - Именованные области (Rows/Columns/Rectangle) задаются диапазонами координат, Rectangle дополнительно ссылается на набор колонок `columnsID` — реальная структура области макета. — expected_in_weights: нет
  - Объединённые ячейки (`<merged>`) задаются прямоугольником координат, не должны пересекаться и не быть вырожденными 1×1 — реальное ограничение формата макета 1С. — expected_in_weights: частично
- verbosity: med
- usefulness: полезен.
- duplicate_of: нет.

---

### skd-dsl/SKILL.md
- lines: 281
- purpose: Полная спецификация DSL для создания схемы компоновки данных (СКД) с нуля через `xml-gen skd compile` (наборы, поля, параметры, фильтры, структура, шаблоны).
- type: 1c-доменное
- probe_facts:
  - Три типа наборов данных СКД: `DataSetQuery` (запрос), `DataSetObject` (внешний набор, данные передаются через `ПроцессорКомпоновкиДанных.Инициализировать(Макет, Структура)`), `DataSetUnion` (объединение) — реальная типология наборов данных системы компоновки данных 1С. — expected_in_weights: да
  - Роли полей СКД: измерение (`dimension`), период (`period`), счёт (`account`), балансовый показатель (`balance`) — реальные роли полей схемы компоновки данных (используются, в частности, в бухгалтерских/оборотных отчётах). — expected_in_weights: да
  - Параметр типа `StandardPeriod` с флагом `autoDates` генерирует связанные параметры `НачалоПериода`/`КонецПериода` — реальный механизм СКД для работы со стандартным периодом. — expected_in_weights: частично
  - Условное оформление (`conditionalAppearance`) с полями `appearance` (Текст, ЦветТекста и др.), `filter`, `presentation` — реальный элемент СКД для настройки внешнего вида результата компоновки. — expected_in_weights: частично
- verbosity: high
- usefulness: ядро процесса — центральный навык генерации отчётов СКД.
- duplicate_of: см. сводное сравнение skd-dsl vs skd-edit в конце отчёта.

### skd-dsl/references/info-modes.md
- lines: 278
- purpose: 11 режимов `skd info` (overview/query/fields/links/calculated/resources/params/variant/templates/trace/full) с примерами вывода.
- type: 1c-доменное
- probe_facts:
  - Ресурсы (resources) СКД — агрегатные выражения, которые могут иметь групповые формулы, различающиеся по группировке — реальный механизм ресурсов схемы компоновки данных. — expected_in_weights: частично
  - Структура варианта настроек СКД строится из группировок (Group), таблиц (Table, со столбцами/строками) и диаграмм (Chart) — реальные типы структуры отчёта СКД. — expected_in_weights: да
  - Три типа шаблонов вывода СКД: `fieldTemplate` (к полю), `groupTemplate` (к группировке: Header/Footer/OverallHeader), `groupHeaderTemplate` (заголовок группы) — реальные типы макетов оформления группировок СКД. — expected_in_weights: нет
- verbosity: high
- usefulness: полезен — режим `trace` явно назван «самым ценным для аналитика/архитектора», реальный диагностический инструмент.
- duplicate_of: нет прямого (companion к SKILL.md, где таблица режимов дана кратко).

### skd-dsl/references/templates-dsl.md
- lines: 138
- purpose: Синтаксис шаблонов вывода СКД (текст/параметр/объединение ячеек, стили, drilldown, привязка groupTemplates к группировкам).
- type: 1c-доменное
- probe_facts:
  - `DetailsAreaTemplateParameter` с `mainAction=DrillDown` — реальный элемент XML шаблона СКД, реализующий расшифровку (переход по клику) в отчёте. — expected_in_weights: нет
  - Привязка шаблонов к группировкам через `groupField`/`groupName` и `templateType` (`Header`/`OverallHeader`/`GroupHeader`) — реальный механизм СКД, определяющий макет для строк данных/итогов/заголовка группы. — expected_in_weights: нет
  - Известна ли модели общая концепция шаблона вывода с ячейками-параметрами (`{Имя}`) и объединением ячеек по вертикали/горизонтали (`|`/`>`) как аналог мыслимой сетки табличного отчёта? — expected_in_weights: частично
- verbosity: med
- usefulness: полезен.
- duplicate_of: нет.

---

### skd-edit/SKILL.md
- lines: 107
- purpose: Индекс атомарных точечных правок существующей Schema.xml (`skd edit`: add-field, add-parameter, set-query, patch-query, modify-structure и др.), контракт атомарности/идемпотентности.
- type: проектно-специфичное
- probe_facts:
  - Известна ли модели концепция «атомарной точечной правки» XML-документа (read-modify-validate-write с откатом при ошибке) как общий паттерн безопасного редактирования конфигурационных файлов? — expected_in_weights: да
  - Знает ли модель, что для схемы компоновки данных 1С типично разделение между полным пересозданием (compile) и точечным редактированием отдельных узлов (add-field, set-query и т.п.)? — expected_in_weights: частично
  - Известно ли модели общее требование идемпотентности операций modify/remove (повторный вызов не должен менять результат) как практика проектирования CLI-инструментов? — expected_in_weights: да
- verbosity: med
- usefulness: ядро процесса — чёткий индекс операций с явными инвариантами (атомарность, идемпотентность, `@once`).
- duplicate_of: см. сводное сравнение ниже.

### skd-edit/references/fields.md
- lines: 109
- purpose: Операции `add-field/modify-field/remove-field/set-field-role` — shorthand-синтаксис и семантика ролей полей.
- type: 1c-доменное
- probe_facts:
  - Роль поля «балансовый показатель» (`@balance`) с параметрами `balanceGroupName`/`balanceType` (`OpeningBalance`/`ClosingBalance`) — реальный механизм СКД для расчёта начальных/конечных остатков (бухгалтерские отчёты). — expected_in_weights: частично
  - Роль «период» (`@period`) с `periodType` (Year/HalfYear/Quarter/Month/Week/Day/Hour/Minute/Second) и `periodNumber` — реальная система компонентов периода в СКД. — expected_in_weights: частично
  - Роль «счёт» (`@account`) с `accountTypeExpression` — механизм СКД для типизации счёта в отчётах по плану счетов. — expected_in_weights: нет
- verbosity: med
- usefulness: полезен.
- duplicate_of: пересекается по содержанию с разделом «Роли» в skd-dsl/SKILL.md (та же четвёрка ролей `@dimension/@balance/@account/@period` описана и там, короче, в контексте compile). Здесь — тот же список ролей, но с полным набором kv-параметров для `set-field-role`. Разделение по фазе (create vs edit) оправдано, но фактическое описание семантики ролей продублировано в двух файлах.

### skd-edit/references/parameters.md
- lines: 125
- purpose: Операции `add-parameter/modify-parameter/remove-parameter/rename-parameter/reorder-parameters` — shorthand и семантика флагов параметров.
- type: 1c-доменное
- probe_facts:
  - Параметр СКД типа `StandardPeriod` с флагом `autoDates` генерирует пару скрытых параметров `ДатаНачала`/`ДатаОкончания` — реальный платформенный механизм СКД. — expected_in_weights: частично
  - Свойство параметра `use=Always` — обязательная подстановка параметра в запрос независимо от пользовательских настроек — реальное свойство схемы компоновки данных. — expected_in_weights: частично
  - `denyIncompleteValues` — свойство параметра СКД, запрещающее неполные значения (например, незаполненный конец периода) — реальное свойство. — expected_in_weights: нет
- verbosity: med
- usefulness: полезен.
- duplicate_of: пересекается с разделом «Параметры» в skd-dsl/SKILL.md (те же флаги `@autoDates`/`@hidden`/`@always` описаны и там, в контексте compile) — аналогичная ситуация с fields.md: описание семантики флагов параметров продублировано в двух навыках под разными командами (compile vs edit).

### skd-edit/references/query.md
- lines: 102
- purpose: Операции `set-query` (полная замена текста запроса набора) и `patch-query` (точечная замена с флагом `@once` для проверки уникальности подстроки).
- type: проектно-специфичное
- probe_facts:
  - Известна ли модели общая практика различения «полной замены содержимого» и «точечного patch по подстроке» при редактировании текстовых артефактов (аналог find-replace vs полной перезаписи файла)? — expected_in_weights: да
  - Знает ли модель, что текст запроса СКД пишется на языке запросов 1С (аналог SQL с русскими ключевыми словами: ВЫБРАТЬ, ИЗ, ГДЕ) и хранится как текстовый узел внутри XML набора данных? — expected_in_weights: да
  - Известна ли модели практика защитной проверки уникальности подстроки перед точечной заменой (assert «ровно одно совпадение») как общий паттерн безопасного рефакторинга текста? — expected_in_weights: да
- verbosity: med
- usefulness: полезен — чёткий контракт «когда что» (set-query vs patch-query vs patch-query @once), включая reviewer-инвариант.
- duplicate_of: нет.

### skd-edit/references/structure.md
- lines: 68
- purpose: Операция `modify-structure` — точечное изменение полей группировки (`groupItems`) существующей именованной группы без потери selection/order/filter/CA.
- type: 1c-доменное
- probe_facts:
  - Структура варианта отчёта СКД строится деревом группировок, каждая из которых задаётся именем (`<dcsset:name>`) и `groupItems` (полями группировки) либо детальными записями (`details`) — реальный элемент структуры XML схемы компоновки данных. — expected_in_weights: нет
  - Группировка хранит отдельно от `groupItems` свойства `selection`/`order`/`filter`/`conditionalAppearance`/`outputParameters` — реальное разделение атрибутов группировки в XML СКД. — expected_in_weights: частично
- verbosity: low
- usefulness: полезен — самый компактный файл в skd-edit, но чётко очерчивает границы применимости («когда НЕ использовать»).
- duplicate_of: нет.

### skd-edit/references/totals.md
- lines: 63
- purpose: Операции `add-total/remove-total` — итоговые выражения (`totalFields`) СКД и автообёртка агрегатных функций.
- type: 1c-доменное
- probe_facts:
  - `totalFields` (итоги) СКД — набор выражений агрегации для полей, вычисляемых по группировкам и общим итогам — реальный элемент схемы компоновки данных. — expected_in_weights: частично
  - Встроенные агрегатные функции языка выражений СКД: Сумма, Среднее, Количество, Минимум, Максимум — реальный, фиксированный набор функций. — expected_in_weights: да
  - Итог может ссылаться на вычисляемое поле (`calculatedField`), а не только на физическое поле набора данных — реальное поведение СКД. — expected_in_weights: частично
- verbosity: low
- usefulness: полезен.
- duplicate_of: нет.

---

## Сводное сравнение: skd-dsl vs skd-edit

Оба навыка посвящены системе компоновки данных (СКД), но не дублируют друг друга по назначению:

- **skd-dsl** — создание схемы **с нуля** через компиляцию JSON DSL (`skd compile`), плюс read-only анализ (`skd info`, 11 режимов).
- **skd-edit** — **точечное** редактирование уже существующей `Schema.xml` через атомарные CLI-операции (`skd edit add-field`, `set-query`, `modify-structure` и т.п.), с явным контрактом идемпотентности/атомарности, которого нет (и не нужно) в skd-dsl.

Прямого дублирования командной семантики нет — файлы явно перекрёстно ссылаются друг на друга («для создания с нуля см. skd-dsl», «модификация — см. skd-edit»). Однако **описание доменных понятий** (роли полей `@dimension/@balance/@account/@period`, флаги параметров `@autoDates/@hidden/@always`) присутствует в обоих деревьях: кратко — в skd-dsl/SKILL.md (раздел «Роли»/«Параметры», в контексте компиляции), и куда подробнее — в skd-edit/references/fields.md и parameters.md (полный набор kv-параметров для операции `set-field-role`/`modify-parameter`). Это не ошибочное дублирование (разный уровень детализации для разных операций), но кандидат на унификацию: имеет смысл вынести общее описание ролей/флагов в единый общий референс и ссылаться на него из обоих навыков, чтобы не расходились формулировки при будущих правках.

## Сводное сравнение: forms-toolkit/references (edit / element-mapping / info / validate)

- **edit.md** и **info.md** оба содержат таблицу соответствия «тип элемента формы ↔ XML-тег» (`kind`→тег для записи в edit.md; сокращение→тег для чтения в info.md) — один и тот же набор сущностей формы (InputField, CheckBoxField, Table, Button, UsualGroup, Pages и т.д.) описан дважды, в двух разных таблицах двух разных файлов. Не критично (разное направление использования: генерация vs чтение), но фактическое содержание таблиц пересекается почти полностью — кандидат на вынос в общий словарь элементов формы.
- **element-mapping.md** — не дублирует ни edit.md, ни info.md: это отдельный алгоритм (grep-эвристики) для сопоставления видимого `Title` программному `Name`, ориентированный на Vanessa-сценарии, а не на CLI-инструмент.
- **validate.md** — не пересекается по содержанию с остальными тремя: это чеклист проверок и коды ошибок, самостоятельная предметная область.

Итог: единственное реальное пересечение внутри forms-toolkit — таблицы типов элементов в edit.md и info.md.

---

# ЧАСТЬ E-review-v8

# Аудит части E — review / v8-runner / v8-session-manager

Прочитано полностью 22 файла, суммарно **4584** строки (`wc -l`).

Примечание к методологии (по корректировке оркестратора): `probe_facts` заданы для ВСЕХ файлов, включая «проектно-специфичное» — для таких файлов факты сформулированы как вопрос через ближайшее публичное знание (например, «какие ключи запуска пакетного режима есть у 1cv8» вместо утверждения о конкретной реализации v8-runner). У каждого факта — поле `expected_in_weights: нет|частично|да` — это отдельная гипотеза для последующей пробы «вопрос × модель», не связанная с `verbosity`/`usefulness`. `verbosity` — чисто редакторская оценка (вода/повторы). `usefulness` — оценка роли файла в реальном рабочем процессе фреймворка, независимо от того, знает ли модель факт «в весах».

---

### review/cross-provider-review/SKILL.md

- `lines`: 230
- `purpose`: Единая точка входа для cross-family second-opinion ревью (Claude↔Codex): routing адаптеров, режимы advisory/gate, lifecycle sandbox (start/ask/debate/sync/status/log/stats/show/close), обязательная очистка `.review-sandboxes/`.
- `type`: проектно-специфичное — это описание собственного инструмента (два Python-адаптера, `.review-sandboxes/`, свои CLI-флаги `--copy-mode`, `--keep-sandbox` и т.д.), не публичная 1С- или общая практика.
- `probe_facts`:
  - «Как в общем виде организовать межпровайдерное (cross-family) ревью кода через параллельные CLI разных LLM-провайдеров, чтобы избежать same-family self-review bias?» — секция «Routing». `expected_in_weights`: частично.
  - «Какие флаги CLI `claude` и `codex` делают запуск read-only/sandboxed (например `--permission-mode plan`, `--sandbox read-only`, `--strict-mcp-config`)?» — секция «Safety». `expected_in_weights`: частично.
  - «Какая типовая практика различает advisory (совещательное) ревью от blocking gate-ревью перед закрытием задачи в CI/CD и agent-оркестрации?» — секция «Режимы». `expected_in_weights`: да.
- `verbosity`: high — много повторов между разделами «Acceptance-Bound Protocol» и «Finalization Gate Protocol»; раздел `Safety` частично дублирует то, что уже задано в самих `.py`-скриптах (permission-mode, sandbox read-only).
- `usefulness`: ядро процесса — единственный формализованный механизм второго мнения между разными LLM-семьями в фреймворке; секция про обязательный `close`/CHECKPOINT устраняет реальную известную проблему (осиротевшие sandbox-каталоги).
- `duplicate_of`: нет отдельного файла-дубликата; частичное пересечение с содержимым обоих `.py`-скриптов (списки CLI-опций, protocol semantics) — обычный skill/implementation overlap, не критично.

---

### review/cross-provider-review/references/finalization-prompt.md

- `lines`: 167
- `purpose`: Шаблон промпта для blocking gate-ревью перед закрытием задачи: bidirectional rule compliance (Log→Rules и Rules→Log), goal-verification traceability-таблица, 8 anti-deception векторов, протокол 3 раундов с эскалацией.
- `type`: общепрограммистское — методология финальной приёмки (traceability, anti-deception checklist, эскалация после N раундов) обобщаема и не завязана на 1С; project-specific в нём только конкретные пути evidence pack (`framework/workflows/orchestrator/SKILL.md` и т.п.), которые лишь заполняют шаблон.
- `probe_facts`:
  - утверждение «пропущенное обязательное действие — такое же тяжёлое нарушение, как явное» — секция «A2. Rules → Log». `expected_in_weights`: частично.
  - traceability-таблица «Критерий / Файл:строка / Тест / Stdout / Verdict» как обязательный формат goal verification (аналог requirements traceability matrix) — секция «B. Goal verification». `expected_in_weights`: да.
  - 8 фиксированных anti-deception векторов (scope shrinkage, test theater, fake acceptance, artifact drift, regression blindness, hallucinated coverage, cherry-picked logs, classification bypass) как чек-лист против self-reported «готово» — секция «C. Anti-deception». `expected_in_weights`: частично.
  - протокол ровно 3 раундов с обязательным `escalate_to_user`/`dispute_summary` на 3-м раунде без PASS — секция «Протокол итераций». `expected_in_weights`: частично.
- `verbosity`: med — плотный, мало воды, но формат вывода (пример шаблона) занимает почти треть файла.
- `usefulness`: полезен — даёт конкретную, проверяемую структуру для gate-ревью; хорошо согласован с `SKILL.md` (использует те же термины verdict/iteration).
- `duplicate_of`: нет прямого дублирования с `review-prompt.md` — они покрывают разные режимы (gate vs advisory), пересечение только в общих принципах (evidence > слова, findings severity), что оправдано.

---

### review/cross-provider-review/references/review-prompt.md

- `lines`: 66
- `purpose`: Дефолтный промпт для advisory-ревью: роль read-only второго мнения, рекомендованная структура artifact review (Task/Artifact/Criteria/Context), finding protocol (BLOCK/WARN/INFO, F-01...), iteration protocol (agree/partial/disagree/withdrawn/out_of_scope).
- `type`: общепрограммистское — типовая, независимая от 1С структура промпта для код-/спек-ревью и его итерационный протокол.
- `probe_facts`:
  - структура промпта `Task / Artifact / Criteria / Context` для artifact-ревью — секция «Рекомендованная Структура Artifact Review». `expected_in_weights`: да.
  - порядок severity BLOCK → WARN → INFO и стабильные ID `F-01, F-02...` как общепринятый паттерн code-review тулинга — секция «Finding Protocol». `expected_in_weights`: да.
  - явное требование отделять evidence от inference в findings — секция «Finding Protocol». `expected_in_weights`: частично.
  - вердикты итерации `agree/partial/disagree/withdrawn/out_of_scope` и правило «начиная с round 3 — только BLOCK/WARN с evidence» — секция «Iteration Protocol». `expected_in_weights`: частично.
- `verbosity`: low — короткий, по существу, минимум повторов.
- `usefulness`: ядро процесса — компактный, переиспользуемый промпт, реально вызываемый обоими адаптерами (`load_review_prompt`/`load_review_system_prompt` в `.py`-скриптах), т.е. не мёртвая документация, а исполняемый system prompt.
- `duplicate_of`: нет.

---

### review/cross-provider-review/scripts/claude_opus_review.py

- `lines`: 1024
- `purpose`: CLI-раннер сессионного ревью через Claude (`claude -p --permission-mode plan --strict-mcp-config --tools=Read,Grep,Glob,LS`), с материализацией sandbox (hardlink/copy), runtime/progress-трекингом и подкомандами `start/ask/debate/sync/show/status/log/stats/close`.
- `type`: проектно-специфичное — обвязка вокруг конкретного CLI (`claude`) и собственного протокола `.review-sandboxes/<id>/`.
- `probe_facts`:
  - «Как обычно вызывается `claude` CLI в неинтерактивном (headless) режиме для получения потокового JSON-ответа (`-p`/`--print`, `--output-format stream-json`, `--include-partial-messages`)?» — функция `run_claude`. `expected_in_weights`: частично.
  - «Как в целом устроена сессионная модель (session_id / `--resume`) для многошагового диалога с CLI-агентом без потери контекста между вызовами?» — `cmd_ask`/`cmd_debate`. `expected_in_weights`: да.
  - «Какой общий паттерн материализации песочницы для ревью через hardlink (вместо full copy), чтобы почти не тратить место на диске и оставаться read-only?» — `hardlink_path`/`materialize_path`. `expected_in_weights`: частично.
- `verbosity`: high — ~1000 строк с большим количеством вспомогательных функций (`collect_tool_calls`, `count_tool_results`, `extract_server_tool_use`, `merge_progress_event`), значительная часть которых бит-в-бит совпадает с `codex_review.py`.
- `usefulness`: полезен — рабочий инструмент cross-family review lifecycle, hardlink-материализация и progress-парсинг стрима — разумная инженерия, не декоративная.
- `duplicate_of`: **`codex_review.py`** — функции `copy_path`, `hardlink_path`, `materialize_path`, `remove_dest`, `sync_sources`, `should_exclude`, `copy_full_context`, `iter_json_objects`, `event_type_name` (почти), `tool_name_from`, `collect_tool_calls`, `count_tool_results`, `count_permission_denials`, `extract_server_tool_use`, `empty_progress`, `merge_progress_event`, весь блок `runtime_path/.../mark_phase`, `append_log/read_log/load_meta/save_meta`, структура `cmd_start/cmd_ask/cmd_debate/cmd_sync/cmd_show/cmd_status/cmd_log/cmd_stats/cmd_close` и `build_parser` — практически идентичны между файлами (отличия только в вызове конкретного provider CLI: `run_claude` vs `run_codex`). Явный кандидат на вынесение общего модуля/shared lib.

---

### review/cross-provider-review/scripts/codex_review.py

- `lines`: 1119
- `purpose`: Тот же session-based sandbox review lifecycle, что и `claude_opus_review.py`, но адаптированный под `codex exec` (read-only sandbox, `-c model_reasoning_effort=...`, `--json`, чтение `session_id` через regex по UUID, результат читается из отдельного `last-response.md`, а не из stream `result`-события).
- `type`: проектно-специфичное — обвязка вокруг `codex` CLI.
- `probe_facts`:
  - «Как вызывается `codex exec` в неинтерактивном режиме, включая `resume <session_id>` и флаг `--sandbox read-only`?» — функция `run_codex`. `expected_in_weights`: частично.
  - «Как обычно передаётся уровень reasoning effort в CLI провайдера через `-c key="value"` overrides?» — `-c model_reasoning_effort="{reasoning_effort}"`. `expected_in_weights`: частично.
  - «Как в целом парсить построчный JSONL-вывод CLI-агента, чтобы извлечь токен-статистику и вызовы инструментов без знания полной схемы событий?» — `extract_usage`/`collect_tool_calls`. `expected_in_weights`: да.
- `verbosity`: high — на 95 строк длиннее «двойника», в основном за счёт доп. функций `find_session_id`/`extract_usage`/`write_review_context` и более развёрнутого `build_brief`/`build_prompt` (разбиение на `# Task/# Goal/# Artifact/...` markdown-секции).
- `usefulness`: полезен — то же самое, что и Claude-адаптер, обеспечивает симметричный опыт для Codex-family; специфика `codex exec resume` и разбор `--json`-событий реализована корректно и не выглядит избыточной.
- `duplicate_of`: **`claude_opus_review.py`** (двусторонняя ссылка). Основной структурный finding части E: ~700+ строк общей логики (materialize/hardlink, runtime state machine, progress-парсинг, commands scaffolding) продублированы почти построчно между двумя файлами вместо общего `_common.py`/shared module.

---

### v8-runner/SKILL.md

- `lines`: 362
- `purpose`: Точка входа для навыка v8-runner: форма команды, routing по reference-файлам, жизненный цикл запущенных 1С-клиентов, первый проход (`config init`/`init`), маршрутизация типовых сценариев, детальный WS-протокол сопряжения с session-manager, headless-запуск внешней обработки (`.epf`), защитные правила.
- `type`: смешанный, доминанта — проектно-специфичное (обвязка вокруг бинаря `v8-runner`, форков SteelMorgan, `v8project.yaml`); секция «Headless-запуск внешней обработки (.epf)» (строки 301–343) по существу 1c-доменное знание о платформе, не зависящее от v8-runner как инструмента.
- `probe_facts`:
  - «`/Execute<epf>` в 1cv8 эмулирует «Открыть обработку» и не вызывает экспортный метод модуля объекта напрямую — верно?» — секция «Headless-запуск внешней обработки». `expected_in_weights`: частично.
  - «Первый запуск внешней обработки в 1С триггерит диалог «Защита от опасных действий»; каким ключом командной строки он подавляется (`/DisableUnsafeActionProtection`)?» — та же секция. `expected_in_weights`: частично.
  - «Как в BSL прочитать параметр запуска (`/C`), переданный командной строкой 1cv8 (функция `ПараметрЗапуска()`)?» — та же секция. `expected_in_weights`: да.
  - «Какие типовые ключи командной строки запуска 1cv8 в тестовом/пакетном режиме существуют (`/N`, `/P`, `/TESTMANAGER`, `/TESTCLIENT -TPort`, `/DisableStartupDialogs`)?» — секция «UI MCP через платформенный тест-клиент». `expected_in_weights`: частично.
  - «Как обычно реализуется auto-detect транспорта (короткий TCP-probe порта) для выбора WebSocket vs локальный HTTP fallback в клиент-серверных интеграциях?» — секция «WS-параметры сопряжения». `expected_in_weights`: да.
- `verbosity`: high — WS-параметры сопряжения (115–300) во многом дублируют `references/project-workflows.md` и частично `references/testing.md` (те же таблицы `/C`-подстановки и `kind`-mapping почти дословно).
- `usefulness`: ядро процесса — единственная точка входа skill'а (frontmatter `provides_capabilities`), корректно ссылается на все reference-файлы; объём и повторы (см. duplicate_of) снижают экономичность контекста, но не роль файла в процессе.
- `duplicate_of`: **`references/project-workflows.md`** (раздел «WS-режим к session-manager» почти дословно повторяет «WS-параметры сопряжения с session-manager» из SKILL.md) и частично **`references/testing.md`** (раздел про WS-сопряжение test yaxunit/va повторяет ту же таблицу флагов и kind-mapping третий раз).

---

### v8-runner/agents/openai.yaml

- `lines`: 4
- `purpose`: Метаданные интерфейса агента (display_name/short_description/default_prompt) для интеграции v8-runner-скилла с openai-подобным agent harness.
- `type`: проектно-специфичное — голая конфигурация привязки навыка к конкретному agent-harness формату, без содержательных утверждений.
- `probe_facts`: не подлежит пробе (голый реестр 3 строковых полей, не несёт проверяемого утверждения).
- `verbosity`: low.
- `usefulness`: сомнителен — 4 строки чистых метаданных без содержательной логики; не обнаружено ссылок на этот файл в прочитанных материалах — стоит проверить отдельно, не мёртвый ли это артефакт.
- `duplicate_of`: нет.

---

### v8-runner/references/auth-guard.md

- `lines`: 80
- `purpose`: Hard-stop правила по license-паттернам, «правило двух кандидатов» логина (Администратор→Admin), трёхпутевая классификация ошибки (license/auth/path), порядок приоритета credentials и где их хранить (`v8project.local.yaml`).
- `type`: 1c-доменное — паттерны license-сообщений платформы и общепринятые дефолтные учётки 1С (`Администратор`, `Admin`) — факты о платформе/типовых конфигурациях, не специфика самого v8-runner-инструмента.
- `probe_facts`:
  - паттерны hard-stop по лицензии: `лицензия`, `License`, `HASP`, `nethasp`, «Не обнаружена лицензия» как типовые сообщения платформы 1С о проблемах лицензирования — секция «Жёсткие стопы по лицензии». `expected_in_weights`: да.
  - дефолтные кандидаты логина при отсутствии явных credentials: сначала `Администратор` с пустым паролем, затем `Admin` с пустым паролем — секция «Правило двух кандидатов». `expected_in_weights`: частично.
  - трёхклассовая классификация ошибки подключения к ИБ: license / auth / path — секция «Трёхпутевая классификация ошибки». `expected_in_weights`: нет (это решение агента/фреймворка, не документированная платформой таксономия).
- `verbosity`: low — компактно, без повторов.
- `usefulness`: ядро процесса — реально предотвращает деструктивные действия (перебор credentials, игнорирование license-стопов); прямо переиспользуется в SKILL.md («Защитные правила» ссылается на этот файл).
- `duplicate_of`: нет.

---

### v8-runner/references/bootstrap.md

- `lines`: 127
- `purpose`: Дерево решений для генерации `v8project.yaml` из существующего репозитория: определение format (DESIGNER/EDT) по файловой системе, выбор builder (DESIGNER/IBCMD), выбор connection (File/Srvr), когда спрашивать пользователя, а когда — нет.
- `type`: проектно-специфичное — целиком about формат конфигурации самого v8-runner (`v8project.yaml`, `config init` флаги).
- `probe_facts`:
  - «Как обычно определяется формат исходников 1С (сырое Designer-XML-дерево vs EDT `.mdo`/`DT-INF`) по структуре каталогов репозитория?» — секция «Определи формат исходников по файловой системе». `expected_in_weights`: частично.
  - «С какой версии платформы `ibcmd` официально поддерживается и когда он предпочтительнее полнофункционального Конфигуратора для пакетных операций (≥ 8.3.20)?» — секция «Реши, какой builder backend использовать». `expected_in_weights`: частично.
  - «Какие типовые формы строки подключения к информационной базе 1С существуют (`File=...`, `Srvr=...;Ref=...`)?» — секция «Реши, какое подключение к ИБ использовать». `expected_in_weights`: да.
- `verbosity`: med — есть немного повторов между «Дерево решений» и «Примеры взаимодействия», но в целом оправдано (примеры — конкретизация дерева).
- `usefulness`: полезен — экономит вопросы пользователю за счёт чёткого дерева автоопределения, хорошо расписан edge-case «оба дерева Designer+EDT рядом».
- `duplicate_of`: нет.

---

### v8-runner/references/command-selection.md

- `lines`: 173
- `purpose`: Список команд v8-runner, сгруппированных по намерению пользователя (init/build/syntax/test/extensions/dump/convert/load/make/launch), с примерами CLI-вызовов.
- `type`: проектно-специфичное — построчный справочник CLI-поверхности одного инструмента.
- `probe_facts`:
  - «Какие общие категории команд характерны для build-tool CLI над репозиторием конфигурации 1С (init/build/syntax-check/test/dump/launch), аналогично типовым CI/build-инструментам?» — весь файл. `expected_in_weights`: частично.
  - «Есть ли в 1С:Предприятие публично документированные ключи пакетного режима Конфигуратора для проверки конфигурации (аналог `/CheckConfig`, `/CheckModules`)?» — секция «Синтаксис». `expected_in_weights`: частично.
  - «Какая общая практика разделяет полную пересборку (`--full-rebuild`) от инкрементальной сборки при смене ветки/rebase в build-инструментах?» — секция «Сборка и восстановление». `expected_in_weights`: да.
- `verbosity`: med — почти чистый листинг команд, минимум prose, но пересекается по содержанию с `project-workflows.md`.
- `usefulness`: полезен как компактная шпаргалка по командам, но пересекается по факту с `project-workflows.md`, который расписывает те же команды подробнее с контекстом «когда применять».
- `duplicate_of`: **`references/project-workflows.md`** — оба файла перечисляют один и тот же набор команд (init/build/syntax/test/dump/extensions/launch) почти в одинаковом порядке; `command-selection.md` — сжатая версия, `project-workflows.md` — развёрнутая с добавлением WS-режима.

---

### v8-runner/references/config-and-backends.md

- `lines`: 132
- `purpose`: Семантика `v8project.yaml`/`v8project.local.yaml`: ключевые поля (basePath/workPath/format/builder/connection/source-set/tools.*), матрица поддержки операций по паре (format, builder), правила source-set, конфигурация Vanessa Automation (epf_path, VAParams, TestClient-профиль).
- `type`: проектно-специфичное — целиком про формат конфигурации собственного инструмента.
- `probe_facts`:
  - «Какие типовые backend'ы для пакетной работы с конфигурацией 1С существуют публично (полнофункциональный Конфигуратор в batch-режиме vs `ibcmd`) и в чём их принципиальные ограничения (например `ibcmd` только для файловых ИБ)?» — секция «Правила формата и backend'а». `expected_in_weights`: частично.
  - «Как обычно организована внешняя обработка Vanessa Automation (`.epf`) и передача ей JSON-параметров сценария (`VAParams`)?» — секция «Vanessa Automation в v8project.yaml». `expected_in_weights`: частично.
  - «Какие типы объектов метаданных 1С обычно выделяют в отдельные source-set'ы при экспорте/импорте (конфигурация, расширение, внешние обработки, внешние отчёты)?» — секция «Заметки по source-set». `expected_in_weights`: частично.
- `verbosity`: med — секция про VAParams довольно подробная (JSON-пример на ~20 строк), но обоснована — без неё непонятен формат профиля TestClient.
- `usefulness`: ядро процесса — единственное место, где явно расписана матрица (format×builder) → доступные операции; важно для диагностики «почему команда недоступна».
- `duplicate_of`: нет прямого дублирования; раздел «Vanessa Automation в v8project.yaml» перекрёстно ссылается с `testing.md` (нормальная перекрёстная ссылка, не дублирование).

---

### v8-runner/references/file-and-artifact-workflows.md

- `lines`: 88
- `purpose`: Команды dump/convert/load/make(artifacts) с ограничениями по backend (partial dump требует object, IBCMD деградирует до incremental, load только для DESIGNER/DESIGNER, make требует builder=DESIGNER).
- `type`: проектно-специфичное — специфика команд конкретного CLI и ограничений backend'ов инструмента.
- `probe_facts`:
  - «Как Конфигуратор 1С публично поддерживает выгрузку/загрузку конфигурации в файлы из командной строки (аналог `/DumpConfigToFiles`, `/LoadConfigFromFiles`, `/UpdateDBCfg`)?» — секция «Dump». `expected_in_weights`: частично.
  - «Как обычно упаковываются релизные артефакты 1С (`.cf`/`.cfe`/`.epf`/`.erf`) через пакетный экспорт конфигурации Конфигуратором?» — секция «Make и artifacts». `expected_in_weights`: да.
  - «Чем принципиально отличается конвертация исходников между форматами (файловый CLI, не трогающий ИБ) от dump (синхронизация именно состояния ИБ)?» — секция «Convert». `expected_in_weights`: частично.
- `verbosity`: low — по существу, минимум повторов.
- `usefulness`: полезен — чёткая таблица ограничений backend'ов критична для того, чтобы агент не пытался вызвать неподдерживаемую комбинацию.
- `duplicate_of`: частично пересекается с разделами «Dump/Convert/Load/Make» в `command-selection.md` и `project-workflows.md` (те же команды перечислены ещё дважды, без ограничений backend) — не полный дубль, скорее расслоение одного материала по трём файлам.

---

### v8-runner/references/learned-patterns.md

- `lines`: 25
- `purpose`: Два «выученных паттерна»: (1) race condition в BSL idle-handler `client_mcp`, из-за которой yaxunit-сессия не успевала зарегистрироваться в session-manager при быстрых тестах; (2) требование покрывать тестами все точки входа при правке общего launch-helper'а.
- `type`: смешанный — первая запись 1c-доменное (поведение платформенного API `ПодключитьОбработчикОжидания`), вторая — общепрограммистское (практика тестового покрытия shared helper'ов). Классифицирую файл как `1c-доменное`, т.к. первая запись доминирует по объёму и специфичности.
- `probe_facts`:
  - «Каково типовое поведение `ПодключитьОбработчикОжидания` в 1С/BSL — минимальный практический интервал тика idle-обработчика и риск, что короткий сценарий завершится раньше первого тика?» — секция «UI MCP через платформенный тест-клиент требует двух клиентских ролей». `expected_in_weights`: нет (специфический недокументированный инцидент конкретного расширения этого проекта, а не общее платформенное правило).
  - «Какая общая инженерная практика требует при расширении shared launch-helper'а находить все call sites через grep/rg и закреплять тестами каждого потребителя перед мержем?» — секция «Общие launch-helper-ы требуют матрицы entry-point тестов». `expected_in_weights`: да.
- `verbosity`: low — предельно сжато, оба паттерна уместились в 25 строк без воды.
- `usefulness`: полезен — редкий пример «памяти об инциденте» с конкретным root cause и фиксом; статус `status: candidate` (не выдаётся за истину в последней инстанции) — методологически корректно.
- `duplicate_of`: первый паттерн дублирует содержание секции «Resolved: WS-сессии в test yaxunit (DRIVE 2026-05-11)» в `SKILL.md` и «Resolved (DRIVE 2026-05-11)» в `testing.md` — один и тот же инцидент описан трижды в трёх разных файлах почти одинаковыми словами.

---

### v8-runner/references/project-workflows.md

- `lines`: 212
- `purpose`: Развёрнутые сценарии по намерению (init/build/syntax/dump/extensions/launch) плюс отдельный подробный блок «WS-режим к session-manager» (транспорт auto/ws/mcp, таблица `/C`-подстановки, kind-mapping, JSON-output).
- `type`: проектно-специфичное — WS-специфика — это форк SteelMorgan и внутренняя реализация v8-runner, не публичная 1С-функциональность.
- `probe_facts`:
  - «Какие типовые ключи запуска толстого/тонкого клиента 1С (`ENTERPRISE`, `/F`, `/S`, `/N`, `/P`) публично документированы Фирмой «1С»?» — секция «Launch». `expected_in_weights`: да.
  - «Как обычно реализуется TCP auto-probe (короткий таймаут ~200мс) для выбора между WebSocket и локальным HTTP при подключении клиента к внешнему сервису?» — секция «Транспорт и автоопределение». `expected_in_weights`: да.
  - «Есть ли в открытых источниках конвенция передавать служебные параметры клиенту 1С через `/C"key=value;..."` строку (внутренний формат, не документированный платформой напрямую)?» — секция «Что v8-runner подставляет в /C». `expected_in_weights`: частично.
- `verbosity`: high — WS-раздел (145–212) почти дословно повторяет одноимённый раздел `SKILL.md`.
- `usefulness`: полезен как развёрнутая версия command-selection.md с добавлением контроля результата (`Monitor`-инструмент для длительного build), но много контента продублировано из SKILL.md.
- `duplicate_of`: **`v8-runner/SKILL.md`** (раздел «WS-параметры сопряжения с session-manager» ≈ раздел «WS-режим к session-manager» здесь) и частично **`command-selection.md`**.

---

### v8-runner/references/testing.md

- `lines`: 222
- `purpose`: YaXUnit/Vanessa Automation тестирование: WS-сопряжение для test-команд (порядок флагов ДО подкоманды), диагностика WS (4 источника логов), pre-run config check для VA, обязательный мониторинг long-running тестов через Monitor/stdout-маркеры, артефакты падений.
- `type`: проектно-специфичное — специфика тестовых раннеров и их интеграции с session-manager через данный форк.
- `probe_facts`:
  - «YaXUnit — известный open-source фреймворк модульного тестирования для 1С:Предприятие; какие типовые команды/сценарии запуска (все тесты / один модуль) для него характерны?» — секция «YaXUnit». `expected_in_weights`: частично.
  - «Vanessa Automation — известный open-source BDD-фреймворк для 1С на основе Gherkin-подобных `.feature`-сценариев; как в целом устроен профиль/тег-фильтр запуска сценариев?» — секция «Vanessa Automation». `expected_in_weights`: частично.
  - «Какая общая практика мониторинга долгих CLI-тестов через фоновый процесс + отслеживание лог-файла на маркеры `ERROR:`/`PASS`/exit-код (аналог CI watch-loop) существует в инструментах автоматизации?» — секция «Мониторинг прогона Vanessa (MUST)». `expected_in_weights`: да.
- `verbosity`: high — самый длинный reference-файл; разделы «Мониторинг прогона Vanessa (MUST)» и «Контроль результата при длительных прогонах» частично пересекаются друг с другом внутри одного файла (два похожих чек-листа условий завершения ожидания описаны дважды с разной формулировкой).
- `usefulness`: ядро процесса — таблица MUST-требований по мониторингу VA (ложный успех Vanessa при пропущенных шагах, обязательность анализа артефактов) закрывает реальный класс проблем «тест зелёный, но по факту сломан»; высокоценный контент, нуждающийся в дедупликации внутри самого файла.
- `duplicate_of`: раздел «WS-сопряжение с session-manager на test yaxunit / test va» дублирует то же самое в `SKILL.md`/`project-workflows.md` (третье повторение той же информации); плюс внутренний self-duplicate между двумя мониторинговыми разделами (см. verbosity).

---

### v8-runner/references/troubleshooting.md

- `lines`: 51
- `purpose`: Начальные проверки (git status, наличие v8project.yaml), какие поля конфига смотреть при сбоях, типовые ситуации (отсутствие платформы/EDT CLI, устаревшее инкрементальное состояние → `--full-rebuild`), runtime-каталоги внутри `workPath`.
- `type`: проектно-специфичное — про диагностику состояния конкретного инструмента и его runtime-каталогов.
- `probe_facts`:
  - «Как в целом различают ошибки окружения (отсутствие бинарника/утилиты в PATH) от ошибок исходного кода проекта при диагностике сборки?» — секция «Типовые ситуации». `expected_in_weights`: да.
  - «Какая общая практика build-систем требует полной пересборки вместо инкрементальной после переключения ветки/rebase из-за устаревшего кэша зависимостей?» — та же секция. `expected_in_weights`: да.
  - «Существует ли в 1С публичная концепция «частичной»/инкрементальной выгрузки конфигурации, которая может деградировать до полной при определённых ограничениях backend'а?» — секция «Типовые ситуации» (partial dump → incremental). `expected_in_weights`: нет (это специфика ограничения `ibcmd`-backend этого инструмента, не общая платформенная концепция).
- `verbosity`: low — компактный, по существу.
- `usefulness`: полезен — короткая, но полная starting-point диагностика; хорошо разделяет «проблема окружения» от «проблема исходников».
- `duplicate_of`: нет.

---

### v8-session-manager/SKILL.md

- `lines`: 120
- `purpose`: Точка входа навыка session-manager: что даёт менеджер (session_list, tools_cache_reset, проксирование tools, FIFO, soft-reconnect), суть persistent-кеша (ADR-0035) и его следствие «имя tool ≠ доступность», диагностика UI MCP-сессий, границы ответственности, hard guardrails.
- `type`: проектно-специфичное — описывает собственную (proprietary) систему `v8-client-session-manager` (Rust MCP-агрегатор), не публичную часть платформы 1С.
- `probe_facts`:
  - «Как в целом устроен MCP-агрегатор/API-gateway, который принимает подключения нескольких backend-клиентов по WebSocket и публикует их инструменты на едином HTTP MCP-эндпоинте для AI-агента?» — раздел «Что даёт сам менеджер». `expected_in_weights`: частично.
  - «Какой общий паттерн решает проблему нестабильной обработки `notifications/tools/list_changed` у MCP-харнесов — persistent-кеш витрины инструментов, переживающий рестарт сервера?» — раздел «Кеш проксированных tools (ADR-0035)». `expected_in_weights`: частично.
  - «Существует ли в публичной MCP-спецификации стандартизированный код ошибки для «инструмент известен, но сейчас недоступен для вызова» (аналог `no_live_session`)?» — раздел «Кеш проксированных tools». `expected_in_weights`: нет.
- `verbosity`: med — насыщенно, минимум воды, но диагностический алгоритм («Диагностика UI MCP-сессий») почти дословно повторяется в `references/troubleshooting.md` и в `v8-runner`-файлах.
- `usefulness`: ядро процесса — критично важный контракт («имя tool в tools/list ≠ доступность вызова», `no_live_session`) без которого агент будет ошибочно диагностировать рабочие сессии как сломанные; guardrails корректно ограничивают агента от правок upstream-репозитория.
- `duplicate_of`: раздел «Диагностика UI MCP-сессий» пересекается с `references/troubleshooting.md` (кейсы 2, 3, 5, 7) и с аналогичным материалом в `v8-runner/SKILL.md`/`testing.md` про readiness live-сессии VA — один и тот же readiness-паттерн описан в обоих навыках почти одинаковыми словами.

---

### v8-session-manager/references/architecture.md

- `lines`: 45
- `purpose`: Пятислойная архитектура стека (L0 addin → L1 devkit BSL → L2 client_mcp → L2.5 прикладные расширения → L3 сам менеджер → L4 AI-агент), поток вызова ASCII-диаграммой, таблица «что меняется в каком репозитории».
- `type`: проектно-специфичное — описание архитектуры собственного продукта/стека интеграции.
- `probe_facts`:
  - «Как в целом устроена многослойная архитектура интеграции нативного addin'а с внешним AI-агентом (native transport layer → protocol adapter → aggregator/gateway → agent)?» — раздел «Слои». `expected_in_weights`: частично.
  - «Существует ли публично компонента-аддин `session_y8` или подобный транспортный addin для 1С как открытый проект?» — строка L0. `expected_in_weights`: нет (собственный/форкнутый компонент проекта, не публично известный артефакт).
  - «Общий принцип «код на каждом слое версионируется и тестируется отдельно» — насколько это типовая практика для многослойных интеграций?» — раздел «Что где живёт». `expected_in_weights`: да.
- `verbosity`: low — компактно и по существу, ASCII-диаграмма ёмкая.
- `usefulness`: полезен — хорошая ментальная модель для агента при отладке «на каком слое искать проблему»; таблица «что меняется в каком репозитории» прямо предотвращает попытки редактировать не тот репозиторий.
- `duplicate_of`: нет.

---

### v8-session-manager/references/bootstrap.md

- `lines`: 79
- `purpose`: Минимальный конфиг менеджера (`workPath` — единственный обязательный ключ), когда менять bind_address/auth_token/metrics, конфигурация persistent tools-cache (ADR-0035), команды запуска (канонический путь / dev / systemd), как подключается 1С-клиент (отсылка к `v8-runner`).
- `type`: проектно-специфичное — конфигурация конкретного бинарника менеджера.
- `probe_facts`:
  - «Какой обычно единственный обязательный параметр (рабочий каталог/workPath или аналог `data-dir`) достаточен для минимального запуска сервисного демона, а остальное — разумные дефолты?» — раздел «Минимальный конфиг». `expected_in_weights`: частично.
  - «Насколько типична практика деплоя Rust/сетевого сервиса через systemd-юнит как production-путь (в противовес `cargo run --release` для dev)?» — раздел «Запуск менеджера». `expected_in_weights`: да.
  - «Является ли включение Prometheus-метрик на отдельном bind_address (например `127.0.0.1:9100`) стандартной практикой observability для сервисов?» — раздел «Когда менять дефолты». `expected_in_weights`: да.
- `verbosity`: low — компактно, таблицы по существу.
- `usefulness`: полезен — чётко разграничивает «обязательный минимум» от «трогать только при тюнинге», что снижает риск ненужных правок конфига.
- `duplicate_of`: нет существенного; раздел «Подключение 1С-клиента» кратко (1 абзац) отсылает к тому, что подробно расписано в `v8-runner/SKILL.md`/`project-workflows.md` — уместная сжатая отсылка, не полноценное дублирование.

---

### v8-session-manager/references/extending-tools.md

- `lines`: 46
- `purpose`: Guardrail «создание/изменение/удаление MCP-tool требует явного разрешения пользователя», где живут tools (по расширениям 1С), алгоритм добавления tool после разрешения, что нельзя трогать, признаки правильной реализации.
- `type`: проектно-специфичное — про процесс расширения собственной инфраструктуры MCP-tools.
- `probe_facts`:
  - «Какая общая governance-практика требует явного human approval перед добавлением/изменением публичного API-метода (tool) в расширяемой системе?» — вводный «Hard guardrail». `expected_in_weights`: да.
  - «Является ли конвенция именования `<namespace>__<method>` для устранения коллизий имён между несколькими поставщиками одного интерфейса общепринятой практикой в API/plugin-системах?» — раздел «Алгоритм добавления tool», шаг 7. `expected_in_weights`: частично.
- `verbosity`: low.
- `usefulness`: ядро процесса — единственное место с hard guardrail против несанкционированного изменения публичного контракта tools; короткий и однозначный.
- `duplicate_of`: нет.

---

### v8-session-manager/references/sessions-and-tools.md

- `lines`: 113
- `purpose`: Как формируется витрина tools (встроенные + persistent-кеш ADR-0035), структура полей `session_list`, формат имени на витрине `<kind>__<tool>`, свёртка однотипных tools и правило `session_id`, lifecycle сессии (FIFO/soft-reconnect/idle-killing/round-robin), детали persistent-кеша и когда нужен `tools_cache_reset`.
- `type`: проектно-специфичное — целиком о внутреннем протоколе и API конкретного менеджера (ADR-0035, `config_id`, `schema_hash`).
- `probe_facts`:
  - «Как в целом происходит роутинг вызова к нужному экземпляру backend'а при нескольких равнозначных подключениях с одинаковым интерфейсом (round-robin / session affinity через явный session_id)?» — раздел «Свёртка однотипных tools и параметр session_id». `expected_in_weights`: да.
  - «Является ли FIFO-очередь вызовов per-session (без параллелизма внутри одной сессии) типовым паттерном для интеграций с stateful-клиентом?» — раздел «Lifecycle сессии». `expected_in_weights`: да.
  - «Существует ли общепринятая практика «мягкого» переподключения (grace period перед удалением записи) при разрыве долгоживущего соединения (soft-reconnect с сохранением очереди)?» — раздел «Lifecycle сессии». `expected_in_weights`: частично.
- `verbosity`: med — подробно, но не избыточно; единственный файл, где по-настоящему объясняется механика `no_live_session` и структура кеша — без него это пришлось бы объяснять в SKILL.md, что раздуло бы его.
- `usefulness`: ядро процесса — самый содержательный технический reference в этом навыке; корректно объясняет неочевидный контракт (кеш переживает рестарт, имя ≠ доступность).
- `duplicate_of`: нет прямого дублирования; troubleshooting.md использует эти же концепции, но в форме диагностики по симптомам (жанрово другое, взаимодополняющее, не копипаста).

---

### v8-session-manager/references/troubleshooting.md

- `lines`: 99
- `purpose`: 7 типовых отказов менеджера (не стартует / клиент не появляется / tool не публикуется / schema_conflict / no_live_session / кеш не очищается / кеш «правильный», но no_live_session после рестарта) с симптомом → проверкой → решением для каждого.
- `type`: проектно-специфичное — диагностика конкретного продукта (портов :4000/:4001, конкретных лог-паттернов менеджера).
- `probe_facts`:
  - «Как в целом диагностируют ошибку «адрес уже используется» (`Address already in use`) при старте сетевого сервиса — стандартная связка `ss -lntp`/`lsof` для поиска держателя порта?» — кейс 1. `expected_in_weights`: да.
  - «Является ли предупреждение о конфликте схемы (`schema_conflict`) при регистрации двух источников с одинаковым именем метода, но разной сигнатурой — типовой защитной практикой API-агрегаторов?» — кейс 4. `expected_in_weights`: частично.
  - «Существует ли в публичной MCP-спецификации различие между JSON-RPC ошибкой `method not found` (-32601) и прикладной ошибкой tool-уровня «инструмент временно недоступен»?» — кейс 5. `expected_in_weights`: нет.
- `verbosity`: med — по формату «симптом/проверка/решение» без явных повторов внутри себя (в отличие от `testing.md` в v8-runner), хотя кейсы 5–7 неизбежно пересекаются с материалом `sessions-and-tools.md` (тот же ADR-0035 контракт) из-за разницы жанра (troubleshooting vs reference).
- `usefulness`: ядро процесса — конкретные, действенные шаги для каждого отказа (например, различение `Address already in use` vs `workPath does not exist`), редкий случай reference-файла без «воды».
- `duplicate_of`: нет полноценного файла-дубликата; пересечение с `sessions-and-tools.md` и `SKILL.md` (кейс «Диагностика UI MCP-сессий») оправдано жанровым разделением, но фактическое содержание (`no_live_session`, кеш TTL) повторяется по существу 2-3 раза по всему навыку.

---

## Сквозные находки (across-file)

1. **claude_opus_review.py / codex_review.py — почти полный структурный дубль** (~700+ строк общей логики: hardlink-материализация, runtime state machine, progress-парсинг JSON-событий, argparse-скаффолдинг команд). Самый явный кандидат на рефакторинг во всей проверенной части — стоит вынести общий модуль.
2. **WS-режим сопряжения с session-manager** описан **трижды почти одинаковым текстом** в `v8-runner/SKILL.md`, `v8-runner/references/project-workflows.md` и частично в `v8-runner/references/testing.md` (одни и те же таблицы `/C`-подстановки и kind-mapping). Плюс краткое эхо в `v8-session-manager/references/bootstrap.md`.
3. **Инцидент idle-handler race condition (DRIVE 2026-05-11)** описан **трижды**: `v8-runner/SKILL.md` («Resolved: WS-сессии в test yaxunit»), `v8-runner/references/testing.md` («Resolved (DRIVE 2026-05-11)») и `v8-runner/references/learned-patterns.md`.
4. **command-selection.md vs project-workflows.md** внутри v8-runner: оба перечисляют одинаковый набор команд (init/build/syntax/test/dump/extensions/launch) с разной степенью детализации — расслоение одного контента по двум файлам без чёткого разграничения назначения.
5. Единственный подозрительный «мёртвый» артефакт — **`v8-runner/agents/openai.yaml`** (4 строки метаданных) — не найдено ссылок на него в прочитанных файлах; стоит проверить отдельно, используется ли он загрузчиком фреймворка.
6. По гипотезе `expected_in_weights`: наибольшее число фактов с меткой «да»/«частично» сосредоточено в общепрограммистских промптах ревью (`review-prompt.md`, `finalization-prompt.md`) и в CLI-механике адаптеров (`claude_opus_review.py`/`codex_review.py` — вызов headless-режима, session/resume, JSONL-парсинг), а также в базовых ключах запуска 1cv8 (`/N`, `/P`, строка подключения `File=`/`Srvr=`). Факты с меткой «нет» сосредоточены в собственных протокольных решениях фреймворка: трёхклассовая ошибка auth-guard, `no_live_session`/ADR-0035 кеш-контракт, `kind`-mapping v8-runner, конкретный BSL-инцидент idle-handler — эти сущности не являются публичным знанием и полностью специфичны для данного репозитория.

---

# ЧАСТЬ F-vanessa

# Аудит навыков Vanessa Automation (BDD-фреймворк тестирования 1С)

Проверены полностью (Read целиком) 5 из 6 файлов; `steps.json` — выборочно (первые 150 строк + два среза в середине/конце), т.к. это машиночитаемые данные (5581 строка).

Итого обработано файлов: **6**
Суммарное число строк: **233 + 175 + 140 + 5581 + 108 + 102 = 6339**

> Обновление по указанию оркестратора: `probe_facts` добавлены для ВСЕХ файлов, включая "проектно-специфичное". Для публичных/общеизвестных утверждений (публичный синтаксис Vanessa/Gherkin, командный запуск и т.п.) — это кандидаты на проверку "уже в весах модели". Для нашей обвязки — probe_facts сформулированы как вопрос через ближайшее публичное знание, с полем `expected_in_weights: нет|частично|да`.

---

### vanessa-authoring/SKILL.md

- `lines`: 233
- `purpose`: Главный навык-алгоритм написания/уточнения feature-сценариев Vanessa: порядок исследования формы через VA MCP, обязательное ручное заполнение перед записью `.feature`, анатомия feature-файла, пользовательский контекст, двухсессийный сплит setup/бизнес-флоу, поиск шагов, теги, антипаттерны.
- `type`: смешанный — большая часть проектно-специфичная (обвязка v8-runner/v8-client-session-manager, ссылки на GBIG PAM-контекст, `AgentAI`), но есть раздел "Анатомия feature-файла" и "Теги", которые чисто про публичный синтаксис Gherkin/Vanessa Automation → 1c-доменное.
- `probe_facts`:
  1. Структура feature-файла: `# language: ru`, `# encoding: utf-8`, `Функциональность:`, `Как/Я хочу/Чтобы`, `Контекст:`, `Сценарий:` — секция "Анатомия feature-файла" (строки 82-111). `expected_in_weights`: частично (общий Gherkin-синтаксис известен модели, но русская локализация ключевых слов и VA-специфичные директивы типа "или подключаю уже существующий" — менее вероятны).
  2. Ключевые слова шагов `Дано`, `Когда`, `Тогда`, `И`, `Затем` синтаксически взаимозаменяемы (матчинг по regex, а не по семантике ключевого слова) — строка 106. `expected_in_weights`: да (стандартное поведение Cucumber/Gherkin-движков, широко документировано).
  3. `@tree` включает Turbo Gherkin: отступы табами вместо пробелов задают дерево шагов — строка 109. `expected_in_weights`: нет (нишевое расширение именно Vanessa Automation, маловероятно широко присутствует в весах).
  4. `Структура сценария:` + `Примеры:` — русский аналог Scenario Outline/Examples с таблицей параметров — строка 108. `expected_in_weights`: да (стандартный паттерн Gherkin, просто переведённые ключевые слова).
  5. `@exportscenarios` делает сценарий вызываемым как подсценарий из другого feature-файла — строка 110, таблица тегов строка 208. `expected_in_weights`: нет (специфическая фича VA, не часть базового Gherkin/Cucumber).
  6. (обвязка) "Как через MCP-инструменты подключиться к тестовому клиенту 1С и получить структуру открытой формы (`connect_test_client`, `get_form_analysis`)?" — секция "MCP-исследование через Vanessa Automation" (строки 23-62). `expected_in_weights`: нет — это конкретная MCP-обвязка `v8-client-session-manager` этого фреймворка, не публичный API VA.
  7. (обвязка) "Общая практика тестирования: не гонять бизнес-сценарий под полными правами администратора, а под пользователем с реальными ограничениями?" — секция "Двухсессийный сплит" (строки 147-169). `expected_in_weights`: частично — принцип least-privilege/ролевого тестирования общеизвестен как практика, но конкретное разделение setup/бизнес-флоу под техническим пользователем `AgentAI` — специфика проекта.
- `verbosity`: high (233 строки, плотная структура с 6+ разделами, MCP workflow, таблицами, антипаттернами).
- `usefulness`: "ядро процесса" — единственный файл, задающий сквозной алгоритм авторинга сценариев (от MCP-исследования формы до записи `.feature`), на него ссылаются остальные файлы группы.
- `duplicate_of`: нет прямого дублирования; раздел "Поиск шагов" ссылается на `steps-cheatsheet.md` и `steps.json` как на внешние источники, не повторяя их содержимое.

---

### vanessa-authoring/references/learned-patterns.md

- `lines`: 175
- `purpose`: Журнал накопленных находок/антипаттернов по заполнению форм 1С и работе с конкретными типами полей (Tumbler, Switcher) в Vanessa-сценариях, с пометкой confirmed/candidate.
- `type`: смешанный — большинство записей "универсальное поведение платформы 1С:Предприятие" (1c-доменное) и две записи проектно-специфичные с привязкой к конкретной задаче (`task-103 GBIG PAM`, конкретные имена шагов и элементов формы этого проекта).
- `probe_facts`:
  1. Обязательные незаполненные поля в 1С визуально отмечены красной пунктирной подчёркой (red dashed underline) — блок "заполнение формы документа" (строки 8-22). `expected_in_weights`: частично (общеизвестное поведение управляемых форм 1С:Предприятие, средняя вероятность присутствия в весах).
  2. Символ «*» в заголовке формы означает несохранённые изменения, при закрытии — диалог «Данные были изменены. Сохранить изменения?» с кнопками Да/Нет/Отмена — блок "закрытие модифицированной формы" (строки 93-114). `expected_in_weights`: частично (широко известное поведение платформы 1С).
  3. Для `CheckBoxType=Switcher` не работает `УстановитьОтметку()` (шаг "я устанавливаю флаг"), нужен шаг "я изменяю флаг с заголовком" — блок (строки 141-155). `expected_in_weights`: нет (узкая деталь реализации Vanessa TestClient, найденная эмпирически, не документирована широко).
  4. Поиск элементов по заголовку (Title из Form.xml) может расходиться с именем элемента (name) — блок "поиск элементов формы — заголовок ≠ имя" (строки 159-175). `expected_in_weights`: частично (общая идея, что Vanessa различает Name/Title, присутствует в cheatsheet/доках; конкретная ловушка, найденная в task-103, — нет).
  5. (обвязка) "Как автоматизировать переключение RadioButtonField с типом Tumbler (не обычная кнопка) стандартными шагами тестового клиента 1С?" — блок "RadioButtonField с RadioButtonType=Tumbler" (строки 118-137). `expected_in_weights`: нет — найдено эмпирически за 12 итераций в конкретном проекте (task-103 GBIG PAM), не задокументировано в публичных источниках VA.
- `verbosity`: med (175 строк, но формат компактный, по записи на блок).
- `usefulness`: "полезен" — фиксирует неочевидные находки (особенно Tumbler/Switcher), которые иначе пришлось бы переоткрывать заново; но 2 из 5 записей узко привязаны к одной задаче одного проекта и могут не обобщаться на другие проекты.
- `duplicate_of`: нет прямого дублирования с другими файлами группы; частично пересекается по теме (обязательные поля, сообщения об ошибках) с процедурными требованиями в `vanessa-authoring/SKILL.md` ("Ручное заполнение формы перед сценарием"), но это развитие конкретными кейсами, а не копипаста.

---

### vanessa-authoring/references/steps-cheatsheet.md

- `lines`: 140
- `purpose`: Человекочитаемая шпаргалка самых частых Gherkin-шагов Vanessa Automation с готовым синтаксисом (навигация, окна, поля, кнопки, таблицы/ТЧ, состояние элементов, флаги, сообщения, переменные, TestClient-сессии, условия/пауза).
- `type`: 1c-доменное — целиком про публичный синтаксис шагов Vanessa Automation (не про обвязку этого фреймворка).
- `probe_facts`:
  1. Различие "в поле с именем" (программное имя, name) vs "в поле с заголовком" (видимый Title, может быть обрезан), рекомендация предпочитать имя — секция "Поля формы" (строки 19-31). `expected_in_weights`: частично (согласуется с записями `steps.json` "UI.Формы.Поля", где шаги дублируются в вариантах "с именем"/"с заголовком"; сам факт наличия обоих вариантов может быть в весах слабо, т.к. VA — нишевой инструмент).
  2. Навигация по таблице ТЧ по одной/нескольким колонкам через `в таблице "List" я перехожу к строке:` с Gherkin-таблицей параметров — секция "Таблицы (ТЧ)" (строки 40-77). `expected_in_weights`: частично.
  3. Переключение между несколькими TestClient через "я подключаю TestClient", "я активизирую TestClient", "я закрываю TestClient" — секция "TestClient: управление сессиями" (строки 118-131). `expected_in_weights`: нет (специфичный для VA механизм многосессионного тестирования, маловероятно широко представлен в обучающих данных).
  4. Ключевые слова "Если ... Тогда" для условий и "пауза N" для паузы — секция "Условия и пауза" (строки 133-140). `expected_in_weights`: частично (базовые управляющие конструкции, отчасти угадываемые по аналогии с обычным Gherkin, но конкретный русский синтаксис VA — нет).
- `verbosity`: med (140 строк, но плотно — почти весь текст полезная нагрузка, минимум прозы).
- `usefulness`: "ядро процесса" — прямой референс для написания шагов без grep по 1116-строчному `steps.json`; экономит основной контекст на частых случаях.
- `duplicate_of`: **да, частично дублирует `steps.json`** — человекочитаемая выжимка часто используемых шагов из того же машиночитаемого каталога (в `vanessa-authoring/SKILL.md`: "Шпаргалка частых шагов с синтаксисом → references/steps-cheatsheet.md" рядом с "Полная библиотека: references/steps.json"). Дублирование намеренное и оправданное (кэш горячих путей vs полный индекс для grep).

---

### vanessa-authoring/references/steps.json

- `lines`: 5581 (данные, не проза)
- `purpose`: Полный машиночитаемый каталог шагов библиотеки Vanessa Automation (упомянуто в SKILL.md: "1116 шагов") в формате JSON-массива объектов `{ИмяШага, ОписаниеШага, ПолныйТипШага}`, предназначенный для поиска через grep, а не для чтения целиком.
- `type`: 1c-доменное (данные о самом инструменте Vanessa Automation — экспорт встроенной библиотеки шагов VA/VanessaExt, не специфика проекта).
- `probe_facts`:
  1. Формат записи — плоский JSON-объект с тремя полями: `ИмяШага` (пример вызова, часто с placeholder-параметрами в кавычках и Gherkin-таблицами прямо в строке через `\t`/`\n`), `ОписаниеШага` (текст описания), `ПолныйТипШага` (иерархическая категория через точку, напр. "UI.Формы.Ввод на основании", "Прочее.VanessaExt.Клик на картинку", "Файлы.Перебор файлов") — подтверждено чтением строк 1-150, 2700-2780, 5500-5581. `expected_in_weights`: нет (это конкретный экспортный формат данного проекта/инструмента, не публичная спецификация).
  2. Категория "Прочее.VanessaExt.*" покрывает SikuliX-функциональность (клик/поиск по картинке на экране, рисование стрелок/рамок, EnjoyHint-подсказки) — требует отдельного компонента VanessaExt — строки 57-126. `expected_in_weights`: частично (VanessaExt/SikuliX упоминаются в комьюнити VA, но это нишевое знание).
  3. Часть шагов ("Прочее.Буфер обмена") требует "включить использование компоненты VanessaExt" — упомянуто в описаниях (строки 28-55) — то есть каталог смешивает шаги базового ядра и шаги, зависящие от опционального расширения, без единого явного маркера "требует VanessaExt" на уровне поля. `expected_in_weights`: нет.
  4. Циклы и условия — отдельный класс шагов ("Прочее.Циклы": "я делаю N раз", "в течение N секунд я выполняю", "я прерываю цикл"/"я продолжаю цикл" как аналоги Прервать/Продолжать) — строки 5548-5571 (конец файла). `expected_in_weights`: частично (общий паттерн "циклы/условия как шаги BDD" существует в разных Gherkin-расширениях, но конкретные русские формулировки VA — нет).
- `verbosity`: high (5581 строка), но по формату — ожидаемо для машиночитаемого справочника, не избыточность прозы.
- `usefulness`: "ядро процесса", но как **данные для grep, не для чтения** — прямо помечено в SKILL.md ("Не читай целиком — используй grep"). Критически важен как источник для `steps-cheatsheet.md` и для поиска несловарных шагов агентом.
- `duplicate_of`: `steps-cheatsheet.md` — да, cheatsheet является ручной выборкой топовых записей этого каталога. Само `steps.json` не дублирует ничего — первичный источник.

---

### vanessa-diagnostics/SKILL.md

- `lines`: 108
- `purpose`: Порядок разбора упавшего прогона Vanessa: где лежат артефакты (два независимых слоя — VA-плеер и v8-runner), как мониторить прогресс через инструмент Monitor не полагаясь на `va-status.json`, обязательный порядок диагностики (status → execution.log → event-log → визуальный скриншот → tech-log), классификация ошибок (7 классов) и итоговый формат отчёта агента.
- `type`: проектно-специфичное — целиком про обвязку конкретного фреймворка (`v8-runner`, пути артефактов, профили `tests.va`/`va-params`, интеграция с `Monitor`-инструментом, классы ошибок специфичные для данного пайплайна диагностики).
- `probe_facts`:
  1. "Как понять, что прогон Vanessa Automation завершился аварийно, если типовой отчётный файл (junit.xml/CucumberJson.json) не создан?" — секция "Мониторинг прогресса"/"Когда применять" (ответ фреймворка: `va-status.log` создаётся и при успехе, и при ошибке, в отличие от `va-status.json`, который создаётся только при штатном завершении — строки 26-31, 39). `expected_in_weights`: нет — это специфика конкретного раннера/пайплайна этого проекта, не публичное поведение самой Vanessa Automation.
  2. "Какие типовые классы ошибок стоит выделять при диагностике упавших BDD/UI-тестов?" — секция "Классы ошибок" (7 классов: `scenario_error`, `step_resolution_error`, `assertion_error`, `test_data_error`, `environment_error`, `product_ui_error`, `product_logic_error`, строки 65-75). `expected_in_weights`: частично — общая идея таксономии ошибок тестирования (assertion vs environment vs data) известна как паттерн из общей практики QA, но конкретный набор из 7 именно этих классов и их привязка к 1С/VA — специфика проекта.
  3. "Где Vanessa Automation обычно пишет отчёты о прогоне (например в форматах JUnit/Cucumber JSON) и логи выполнения?" — секция "Артефакты прогона" (таблица, строки 14-18). `expected_in_weights`: частично — форматы JUnit XML и CucumberJson широко известны из экосистемы Cucumber в целом, но то, что именно VA их генерирует и по каким путям (`tests.va`/`va-params`, `workPath/temp/<runner-id>/runs/<run-id>/`) — специфика проекта, не в весах.
- `verbosity`: med (108 строк, компактные таблицы, без воды).
- `usefulness`: "полезен" — заполняет реальный пробел (частая ловушка: ожидание `va-status.json`, которого не будет при раннем падении; смешение двух слоёв артефактов), обязательные ссылки на `va-visual-check` для визуальной диагностики логично интегрированы.
- `duplicate_of`: **пересекается с `va-visual-check`** по назначению, но не по содержанию — многократно ссылается на `va-visual-check` как на внешнюю процедуру ("Триггер: Подозрение на блокировку GUI → Действие: Визуальная диагностика по va-visual-check"), не повторяя её шаги. Корректная композиция навыков, а не дублирование.

---

### va-visual-check/SKILL.md

- `lines`: 102
- `purpose`: Процедура получения визуального PNG-скриншота формы 1С через Vanessa Automation MCP (`connect_test_client` → `get_form_analysis`/`get_window_list_os` → `get_window_screenshot_os`), с рецептом для Linux/Xvfb без window-manager и правилами browser-fallback, когда VA MCP недоступен.
- `type`: проектно-специфичное — почти целиком обвязка конкретного MCP/тулинга (`v8-runner`, `v8-client-session-manager`, конкретные названия MCP-инструментов, X11/xdotool-рецепт для контейнерного окружения).
- `probe_facts`:
  1. "Как сделать скриншот конкретного окна в Linux под X11/Xvfb без графического окружения (window manager)?" — секция "Linux headless X11/Xvfb без window-manager" (`xwininfo -root -tree`, `xprop -id <id> _NET_WM_PID`, `xdotool windowmove/windowsize/windowraise/windowactivate`, строки 31-67). `expected_in_weights`: частично — сами утилиты `xwininfo`/`xprop`/`xdotool` и их базовое назначение общеизвестны в Linux-практике; конкретная связка именно с VA test-client окном и обходом отсутствия window-manager — нет.
  2. "Что делать, если специализированный инструмент для скриншота конкретного окна возвращает чёрный/пустой PNG?" — секция "Что не делать" + Linux-рецепт (fallback-цепочка: VA MCP → Linux/Xvfb рецепт → browser fallback с фиксацией причины, строки 31-96). `expected_in_weights`: нет — конкретный troubleshooting-путь именно для VA MCP screenshot API этого проекта.
  3. "Общая практика: когда специализированный API инструмента не справляется, использовать generic browser-автоматизацию (например Playwright) как fallback, с фиксацией причины перехода?" — секция "Browser fallback" (строки 69-88). `expected_in_weights`: частично — сам паттерн graceful degradation/fallback с логированием причины является общей инженерной практикой, но конкретный список условий перехода (какая capability не сработала, остаточный риск web vs толстый/тонкий клиент 1С) — специфика проекта.
- `verbosity`: med (102 строки, шаговая процедура + fallback-раздел + запреты).
- `usefulness`: "полезен" — закрывает конкретную операционную проблему (чёрный/пустой скриншот в headless X11 без WM) воспроизводимым рецептом, явно разграничивает VA MCP vs browser fallback с требованием фиксировать причину перехода — снижает риск подмены методологии тестирования без объяснения.
- `duplicate_of`: нет прямого дублирования содержимого; пересечение с `vanessa-diagnostics` — оба ссылаются друг на друга/на общую процедуру визуальной проверки, но `va-visual-check` — единственное место, где описан сам механизм скриншота (остальные файлы лишь ссылаются на него как на процедуру). Композиция, а не копипаста; аналогично не дублирует `vanessa-authoring`, хотя тот многократно упоминает `va-visual-check`.

---

## Сводные наблюдения

1. **Явное разделение по слоям**: `vanessa-authoring` (как писать сценарии) → `vanessa-diagnostics` (как разбирать падения) → `va-visual-check` (как снять скриншот, общий примитив для обоих). Композиция логичная, дублирования контента между тремя SKILL.md не обнаружено — только целенаправленные перекрёстные ссылки.
2. **steps-cheatsheet.md vs steps.json**: осознанное, задокументированное дублирование в духе "hot cache vs full index" — cheatsheet берёт наиболее частые шаги из полного каталога в человекочитаемом виде. Не является избыточностью, которую стоит устранять.
3. **Потенциальный пробел**: ни в `SKILL.md`, ни в `steps-cheatsheet.md` явно не отмечено, что часть шагов `steps.json` (категория "Прочее.Буфер обмена", "Прочее.VanessaExt.*") требует отдельно подключённого расширения VanessaExt — агент, ищущий шаг через grep по `steps.json`, может выбрать шаг, который не работает без этого расширения, и не узнает об этом ограничении заранее.
4. **learned-patterns.md** содержит смесь универсальных 1С-паттернов и узко-проектных находок одной задачи (task-103 GBIG PAM) — со временем такой файл рискует разрастись артефактами одного проекта; кандидат на периодическую чистку/обобщение по критерию актуальности/относимости к процессу, но сейчас все записи помечены `confirmed` и выглядят валидными.
5. **Про поле `expected_in_weights`**: это отдельная непроверенная гипотеза для последующей пробы по матрице «вопрос × модель» (какая модель уже знает факт из весов, какая нет) — не основание для оценок `verbosity`/`usefulness` в этом отчёте и не рекомендация к сжатию. `verbosity` в отчёте — чисто редакторская оценка (плотность/повторы/вода), `usefulness` — оценка роли файла в реальном рабочем процессе (нужен ли по смыслу инструкций/шаблонов/примеров). Эти три поля друг от друга не зависят.

---

# ЧАСТЬ G-onescript-readme

# Часть G — onescript/ + tool-usage/README.md (обработано ведущим агентом)

Контекст: файлы `framework/skills/onescript/{onescript,autumn,autumn-cli,winow}` перенесены as-is из внешнего репозитория `yellow-hammer/skills-onescript` (см. `docs/specs-and-analisys/external-skills-mapping.md`, §3.8, rev.11, 2026-06-30) — это публичная документация экосистемы OneScript, адаптированная под формат SKILL.md, а не изобретённый фреймворком контент.

---

### onescript/onescript/SKILL.md
- lines: 146
- purpose: Базовый синтаксис и структура проекта OneScript (кроссплатформенный интерпретатор языка 1С без платформы).
- type: 1c-доменное
- probe_facts:
  1. "Модуль OneScript состоит из трёх секций: переменные, методы, тело модуля" — секция «Структура модуля». expected_in_weights: частично (общая концепция BSL-подобных языков известна, но именно порядок секций OneScript — нишевое знание).
  2. "Манифест пакета — файл без расширения `packagedef` со свойствами Имя/Версия/ВерсияСреды/ЗависитОт/ВключитьФайл/ИсполняемыйФайл" — секция «Манифест packagedef». expected_in_weights: нет (специфика инструмента OPM, низкая представленность в обучающих данных).
  3. "Пакетный менеджер: `opm build .`, `opm push file.ospx --token`, `opm install`" — секция «OPM». expected_in_weights: частично.
  4. "Директива подключения `#Использовать` — по имени библиотеки или по относительному пути" — секция «Библиотеки и #Использовать». expected_in_weights: частично (аналог `#Использовать` в 1С известен модели, но синтаксис путей OneScript — нет).
- verbosity: med
- usefulness: полезен — если агент когда-либо пишет/читает `.os`-скрипты (инструментарий фреймворка, вспомогательные CLI), это единственный источник базового синтаксиса; для BSL/1С-разработки самого приложения не требуется.
- duplicate_of: нет (reference.md — дополняющий, не дублирующий, файл с более редкими деталями).

### onescript/onescript/reference.md
- lines: 53
- purpose: Точечные отличия OneScript от платформенного 1С (формат дат, конструктор в выражении, параметризованные исключения, package-loader).
- type: 1c-доменное
- probe_facts:
  1. "Формат `ДФ=...ррр` для дробных секунд/кириллицы в функции `Формат` — расширение, отсутствующее в 1С" — секция «Формат даты». expected_in_weights: нет — очень нишевая деталь.
  2. "В OneScript разрешено `Если Новый Файл(...).Существует() Тогда` (обращение к результату конструктора в выражении), в 1С это запрещено" — секция «Конструктор в выражении». expected_in_weights: частично.
  3. "Загрузчик библиотек: `package-loader.os` в корне с процедурой `ПриЗагрузкеБиблиотеки(Путь, СтандартнаяОбработка, Отказ)`" — секция «Загрузчик библиотек». expected_in_weights: нет.
- verbosity: low
- usefulness: полезен — короткий, плотный список ловушек, которые иначе привели бы к ошибкам при переносе кода 1С↔OneScript.
- duplicate_of: нет.

### onescript/autumn/SKILL.md
- lines: 77
- purpose: DI/IoC-фреймворк Autumn для OneScript — принцип работы, аннотации &Желудь/&Дуб/&Завязь/&Верховный.
- type: 1c-доменное
- probe_facts:
  1. "Аннотация `&Желудь(\"Идентификатор\")` регистрирует класс как DI-компонент; без параметра — используется имя класса" — секция «Ключевые аннотации». expected_in_weights: нет — сторонняя нишевая библиотека, маловероятна в обучающих данных.
  2. "Получение компонента через `Осень.НайтиЖелудь(\"ИмяКласса\")`" — секция «Получение компонента». expected_in_weights: нет.
  3. "`&Дуб` — класс-фабрика не как желудь сам по себе, а через метод с `&Завязь`, возвращающий экземпляр — используется для миграции легаси-кода" — секция «Ключевые аннотации». expected_in_weights: нет.
- verbosity: low
- usefulness: полезен — компактное описание нетривиальной DI-модели, без которого агент придумает несуществующий синтаксис.
- duplicate_of: пересекается частично с autumn/reference.md (см. ниже) — SKILL.md даёт практические примеры, reference.md — более сжатый справочник тех же аннотаций плюс список соседних модулей экосистемы (autumn-collections, autumn-logos и т.п.).

### onescript/autumn/reference.md
- lines: 25
- purpose: Расширенная справка по аннотациям Autumn и модулям экосистемы (autumn-collections, autumn-logos, autumn-async и др.).
- type: 1c-доменное
- probe_facts:
  1. "Библиотека `annotations` — аннотации как объекты первого класса, поддерживаются мета-аннотации (агрегаторы)" — секция «Библиотека annotations». expected_in_weights: нет.
  2. "Настройки приложения задаются в `autumn-properties.json` рядом с точкой входа" — секция «Настройки приложения». expected_in_weights: нет.
  3. "Экосистема включает отдельные модули: autumn-collections, autumn-cli, autumn-logos, autumn-async, autumn-synchronized, autumn-event-publisher" — секция «Модули экосистемы». expected_in_weights: нет.
- verbosity: low
- usefulness: сомнителен — большая часть контента (список модулей-соседей) не операционна для агента, действующего в этом фреймворке (autumn-collections/logos/async не имеют собственных SKILL.md здесь); справочная, а не рабочая ценность.
- duplicate_of: autumn/SKILL.md — секция «Ключевые аннотации» дублирует смысл раздела «Аннотации ядра» этого файла почти без нового.

### onescript/autumn-cli/SKILL.md
- lines: 141
- purpose: Построение консольных CLI-приложений (команды/подкоманды/аргументы/опции) на базе autumn-cli поверх Autumn.
- type: 1c-доменное
- probe_facts:
  1. "`&КомандаПриложения(Имя = \"p plus\", Описание = \"...\", Подкоманда = \"...\")` объявляет команду и её подкоманды" — секция «Команда приложения» / «Подкоманды». expected_in_weights: нет.
  2. "Типы аргументов/опций задаются аннотациями `&ТЧисло`, `&ТСтрока`, `&ТБулево`, `&ТДата`, `&ТМассивСтрок` и т.п., доп. `&ВОкружении`, `&ПоУмолчанию`, `&Обязательный`" — секция «Типы аргументов и опций». expected_in_weights: нет.
  3. "Имя/версия приложения задаются через `autumn-properties.json` (ключ `cli.ИмяПриложения` и т.п.) или желудь с методами ИмяПриложения/ВерсияПриложения" — секция «Имя и версия приложения». expected_in_weights: нет.
  4. "Миграция легаси-кода: методы `ОписаниеКоманды`/`ВыполнитьКоманду` можно оставить, просто добавив аннотации, либо обернуть класс в Дуб с &Завязь" — секция «Миграция с чистой cli». expected_in_weights: нет.
- verbosity: med
- usefulness: полезен, но нишево — актуален только если фреймворк/агент пишет собственные OneScript CLI-утилиты; для основной задачи (агентная разработка 1С-конфигураций) вспомогательный.
- duplicate_of: нет прямого дублирования, но идейно пересекается с autumn/SKILL.md (общие аннотации &Желудь/&Дуб/&Завязь описаны в обоих, здесь — в применении к CLI).

### onescript/winow/SKILL.md
- lines: 89
- purpose: Минималистичный веб-сервер/роутинг (Winow) на OneScript+Autumn — контроллеры, маршруты, запрос/ответ.
- type: 1c-доменное
- probe_facts:
  1. "Класс контроллера помечается `&Контроллер(\"/базовый/путь\")`, обработчик — `&ТочкаМаршрута(\"имя\")`" — секция «Контроллер и маршрут». expected_in_weights: нет.
  2. "По умолчанию сервер слушает `localhost:3333`; нет HTTPS, не рассчитан на высокие нагрузки" — секция преамбула/«Точка входа». expected_in_weights: нет.
  3. "GET-параметры доступны через `Запрос.ПараметрыИменные[\"name\"]`, тип контента — `Ответ.УстановитьТипКонтента(\"html\")`, тело — `Ответ.ТелоТекст`" — секция «Параметры запроса» / «Ответ». expected_in_weights: нет.
- verbosity: low
- usefulness: сомнителен — узкоспециализированный навык (веб-сервер на OneScript), не пересекается с типовым циклом агентной разработки 1С-конфигурации; полезен только если фреймворк реально строит вспомогательные веб-инструменты на Winow (в репозитории явных потребителей не найдено — grep по "winow"/"autumn" вне skills/onescript и .skills-sync-state.json/docs-карты пуст).
- duplicate_of: нет — но зависит от autumn/SKILL.md (использует те же базовые аннотации DI).

---

### tool-usage/README.md
- lines: 454
- purpose: Навигационная карта «роль жизненного цикла (БА→ФА→СА→Программист→QA) × навык × MCP-инструмент» для каталога tool-usage/, плюс gap-анализ покрытия и структура каталога.
- type: проектно-специфичное
- probe_facts (по корректировке — обвязка описывается через ближайшее публичное знание):
  1. "Утверждается модель ролей разработки 1С: БА → ФА → СА → Программист → QA с оценками покрытия агентом ~50/70/90/75%" — секция 2 «Путь задачи». expected_in_weights: нет — это внутренняя оценка процесса конкретного фреймворка, не публичный факт (близкий публичный аналог — общий SDLC waterfall/agile-роли, которые модель знает, но не эти конкретные проценты покрытия).
  2. "Утверждается, что MCP-инструмент `ask_ai_assistant` соответствует `ask_1c_ai` в реальном MCP-сервере `1c-copilot-proxy`" — секция «Об именах инструментов» / таблица 5.1. expected_in_weights: нет — capability-имя это внутренний реестр (`registry.yaml`) этого фреймворка, не публичный факт.
  3. "Заявлено существование альтернативных MCP-провайдеров `1c-batch`, `1c-mcp-tools`, `1c_mcp` как замены `test-runner`/`1c-mcp`" — секция 5.1 примечание. expected_in_weights: нет — целиком внутренняя инфраструктура, ближайший публичный аналог (что вообще такое MCP-сервер для 1С) модель знает лишь в общих чертах.
  4. "Таблица 4 утверждает, что каталог `tool-usage/` содержит 20 навыков, а весь фреймворк — 35 навыков в 4 категориях" — секция 1 «Назначение каталога». expected_in_weights: нет — фактическая численность конкретного репозитория, не проверяется по весам модели, а только по факту на диске (при аудите стоит сверить с реальным find, т.к. такие числа быстро расходятся с кодом при добавлении/удалении навыков).
- verbosity: high — 454 строки таблиц и примеров ради навигационной карты; много повторяющихся столбцов «MCP Tools» на роль (одни и те же инструменты перечисляются по 3-4 раза в разных таблицах ролей).
- usefulness: полезен — как единственная карта «зачем этот навык и для какой роли», ценна для онбординга человека/агента-разработчика самого фреймворка, но не используется агентом в момент выполнения обычной задачи (задачи ссылаются на конкретный SKILL.md, а не на README). Риск устаревания: числа (20 навыков, 35 навыков, ~50/70/90/75% покрытие) требуют ручной синхронизации при каждом изменении каталога — это создаёт долг обслуживания.
- duplicate_of: пересекается по содержанию с `capabilities/registry.yaml` и `docs/mcp-inventory.md` (сам README ссылается на них как на источник истины для маппинга capability→инструмент, п.13, но параллельно дублирует часть этого маппинга в разделе 5.1 «полная инвентаризация» — потенциальный дрейф между README и registry.yaml).

---
