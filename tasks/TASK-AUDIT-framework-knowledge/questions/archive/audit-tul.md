# Аудит банка вопросов TUL (инструменты)

Дата: 2026-07-02. Файл: `questions-tul.jsonl` (было 162 вопроса).

## Итог

| Вердикт | Кол-во |
|---------|--------|
| keep | 126 |
| rewrite | 24 |
| drop | 12 |
| **осталось в банке** | **150** |

Критерий (указание владельца фреймворка): знание = смысл инструкций (что делать / что произойдёт / чем пользоваться / какая команда), а не энциклопедическая статистика. Вопрос, на который опытный пользователь инструмента не ответит по памяти, ничего не измеряет.

Основные причины drop: (1) внутренние контракты/статусы наших скриптов и обвязки, на которые публичное знание в принципе не отвечает (`expected_in_weights=нет`, измеряют только галлюцинацию); (2) детали структуры внутренних файлов-конспектов/каталогов (README-меппинги, перечислительные каталоги); (3) неоперационные эссе/agreement-bait вопросы без проверяемого ядра.

Основные причины rewrite: вопрос подсказывал ответ (перечислял API/ключи в формулировке); запрещённые формы (полные перечисления, счётные); зависимость от внутренних имён файлов/путей при наличии публичного ядра — сведено к публичному аналогу; неточный эталон — сверен и исправлен по первоисточнику.

## Повопросные вердикты

Q-TUL-001 | rewrite | Good X11/GUI-automation knowledge but the question spoon-fed the answer (named get_wm_name/get_wm_class/query_tree/fake_input). Reformulated operationally without leaking API names; reference verified against gui-control/SKILL.md.
    OLD: Как через python-xlib перечислить дочерние окна корневого окна X11 и прочитать их заголовок/класс (`get_wm_name`, `get_wm_class`, `root.query_tree().children`), и как затем симулировать нажатие клавиши Enter в этом окне через расширение XTEST (`Xlib.ext.xtest.fake_input`)?
    NEW: В headless-окружении Linux с Xvfb нужно программно на Python обнаружить всплывшее модальное окно приложения (прочитать его заголовок и класс) и закрыть его, синтезировав нажатие клавиши Enter именно этому окну X11. Какую Python-библиотеку и какие её возможности для перечисления окон и чтения их метаданных для этого применяют, каким расширением X-сервера синтезируют само нажатие клавиши, и что обязательно настроить в окружении до импорта этой библиотеки?
Q-TUL-002 | keep | General Pillow how-to (ImageDraw.line/text, save RGB PNG); expert can answer approach from memory. Script-specific margins deprioritized in grading. Verified vs img-grid/SKILL.md.
Q-TUL-003 | drop | Pure internal CLI-contract trivia of tools/img-grid/grid.py (--cell-size vs -c/-r); public knowledge cannot answer, question itself admits "не знаю точных флагов" — measures nothing.
Q-TUL-004 | rewrite | Standard Apache 2.0 knowledge but original leaked the answer (stated 4a/4b and asked "верно ли это"). Reformulated as non-leading. Verified all three LICENSE.txt are Apache 2.0, §4 Redistribution (Microsoft copyright for playwright/-interactive, placeholder for screenshot).
    OLD: Apache License 2.0 требует при распространении производной работы (a) приложить копию самой лицензии и (b) сохранить/добавить уведомление о внесённых изменениях в изменённые файлы. Верно ли это, и в каком разделе лицензии это закреплено?
    NEW: Какие два основных обязательства накладывает раздел «Redistribution» лицензии Apache License 2.0 на того, кто распространяет производную работу (в частности при изменении исходных файлов), и каким номером этот раздел обозначен в тексте лицензии?
Q-TUL-005 | rewrite | Original required a full command enumeration (forbidden form) plus guessing the internal package name `@playwright/cli` (trivia). Refocused on the meaningful concepts: why stable eN refs vs selectors, and session isolation. Verified vs cli.md/workflows.md.
    OLD: Через CLI-обёртку Playwright (`pwcli`, поверх npm-пакета `@playwright/cli`) команда `snapshot` даёт стабильные ссылки на элементы вида `e3`, `e7`, которые затем используются в `click`/`fill`/`hover`. Как называется этот CLI-пакет, какие базовые команды он предоставляет, и как изолировать параллельную работу через сессии?
    NEW: Ряд CLI- и агентных инструментов автоматизации браузера в стиле Playwright accessibility-snapshot устроены так: команда `snapshot` возвращает стабильные короткие ссылки на элементы (например `e3`, `e7`), которые затем передаются в команды взаимодействия. Зачем такой инструмент выдаёт именно такие ссылки вместо того, чтобы принимать CSS/XPath-селекторы, и каким механизмом он изолирует несколько параллельно идущих задач автоматизации друг от друга?
Q-TUL-006 | rewrite | Core of original was the internal config file name `playwright-cli.json` (unanswerable trivia). Kept the genuine expert kernel: headless=launchOptions vs viewport=contextOptions and why. Verified vs workflows.md config example + snippets.md.
    OLD: CLI-обёртка Playwright (`pwcli`/`@playwright/cli`) по умолчанию читает конфиг из текущей директории. Как называется этот файл, каким флагом задать другой путь, и какие вложенные ключи конфигурации отвечают за headless-режим и размер viewport?
    NEW: В Playwright опции запуска делятся на две группы: одни применяются при старте самого браузера, другие — при создании контекста (страницы). В какой из этих групп задаётся headless-режим, а в какой — размер viewport, и чем объясняется такое разделение?
Q-TUL-007 | keep | General npx one-shot pattern + bash hardening (set -euo pipefail, command -v). Verified vs playwright_cli.sh; impl detail (--session dedup) already deprioritized in grading.
Q-TUL-008 | keep | Good-form standard agentic browser loop (open→snapshot→interact→re-snapshot). Verified vs playwright/SKILL.md Core workflow.
Q-TUL-009 | drop | Internal framework routing rule (va-visual-check priority); no public/expert can answer, expected_in_weights=нет — measures nothing about a real tool.
Q-TUL-010 | keep | General Playwright command `npx playwright install chromium` + Electron dep. Verified vs playwright-interactive/SKILL.md and snippets.md.
Q-TUL-011 | keep | Pure public Playwright API (chromium.launch/_electron.launch/newContext viewport/isMobile/hasTouch). Verified vs snippets.md.
Q-TUL-012 | keep | Public Electron API (capturePage/NativeImage.resize/toJPEG). Verified vs snippets.md emitElectronScreenshotCssScaled.
Q-TUL-013 | keep | General Linux screenshot tool order + ffmpeg x11grab + xdpyinfo. Verified vs screenshot/SKILL.md.
Q-TUL-014 | keep | Public CoreGraphics API (CGPreflight/CGRequestScreenCaptureAccess, macOS 10.15+). Verified vs macos_permissions.swift; CODEX_SANDBOX/exit 3 confirmed in ensure_macos_permissions.sh.
Q-TUL-015 | keep | Classic P/Invoke user32.dll + CopyFromScreen and macOS screencapture flags. Verified vs take_screenshot.ps1 and screenshot/SKILL.md.
Q-TUL-016 | rewrite | Original hinged on internal file-structure (deprecated redirect target). Rewrote to a pure 1C-domain question about Vanessa Automation (BDD/Gherkin + TestClient), which is real in-weights knowledge.
    OLD: Существует ли в экосистеме 1С:Предприятие инструмент 'Vanessa Automation' — открытая BDD/Gherkin-платформа функционального тестирования через TestClient? И как называется навык-редирект в этом фреймворке, на который указывает устаревший файл `visual-check/SKILL.md` для визуальной проверки форм?
    NEW: В экосистеме 1С:Предприятие существует популярный инструмент функционального тестирования Vanessa Automation. На каком подходе к описанию тестов он построен и через какой механизм платформы он управляет приложением 1С?
Q-TUL-017 | keep | Genuine 1C platform hotkeys (F4/Shift+F4/F8/Alt+F). Reference matches web-test-1c/SKILL.md hotkey table; grading lenient. Good "which keys for Y" form.
Q-TUL-018 | keep | Meaningful "why" question (1C web-client needs trusted events, so Ctrl+V instead of page.fill). Verified vs web-test-1c/SKILL.md; has public analog (trusted events).
Q-TUL-019 | rewrite | Original compared two internal recording paths (mostly framework-internal). Salvaged the general kernel: ffmpeg is the required external tool for muxing MP4+audio. Verified vs recording.md.
    OLD: В этом фреймворке для записи демо-видео 1С есть два пути — через Vanessa Automation и через Playwright (`web-test-1c`). Чем они различаются по способу получения субтитров, и какой внешний инструмент обязателен для Playwright-пути?
    NEW: При программной записи демонстрационного видео браузерной автоматизации (например поверх Playwright) с добавлением TTS-озвучки и текстовых субтитров — какой внешний инструмент командной строки обязателен для финальной сборки/кодирования итогового MP4 и наложения аудиодорожки?
Q-TUL-020 | drop | Internal-tool implementation status (multi-user contexts "not yet in runtime" of tools/web-test); not derivable from general knowledge, measures nothing.
Q-TUL-021 | keep | Real 1C admin domain knowledge (logcfg.xml, config/log/event/property structure). Reference is factually correct 1C platform knowledge; good "which config file for X" form.
Q-TUL-022 | keep | Genuine 1C tech-journal events DBMSSQL/DBPOSTGRS. Verified vs tech-log SKILL.md events table.
Q-TUL-023 | keep | TLOCK/TDEADLOCK/TTIMEOUT (exactly 3, allowed). Verified vs SKILL.md and scenarios.md.
Q-TUL-024 | rewrite | Good "correct order of actions" question but reference leaked/used internal API names (logc_save_techlog/logc_restore_techlog). Reformulated around the operational principle (save-before / restore-after / TJ loads system). Verified vs tech-log SKILL.md "Полный цикл".
    OLD: Какой обязательный порядок действий предписан при точечном включении технологического журнала 1С для диагностики: что нужно сделать ДО изменения конфигурации ТЖ и что — ПОСЛЕ завершения диагностики?
    NEW: При разовой точечной диагностике через технологический журнал 1С:Предприятие: какое действие с текущей конфигурацией ТЖ обязательно выполнить ДО внесения изменений, что обязательно сделать ПОСЛЕ завершения диагностики, и почему технологический журнал нельзя оставлять постоянно включённым?
Q-TUL-025 | keep | Meaningful "what does event Z show / why pair it" (SDBL vs DBMSSQL/DBPOSTGRS). Verified vs scenarios.md §7.
Q-TUL-026 | keep | General diagnostic reasoning (UTC normalization + first-cause-over-consequence). Verified vs scenarios.md §3.
Q-TUL-027 | keep | "Which events for startup incident" (EXCP+CONN, 2 items). Verified vs scenarios.md §1.

Q-TUL-028 | rewrite | Invented встроенный-язык объект "ЖурналРегистрацииЗапись" (не существует) + энциклопедическое перечисление полей. Переформулировано в операционный вопрос о сшивке записи ЖР с кодом (grounded в разделе "Сшивка записи ЖР с кодом").
    OLD: Что такое журнал регистрации (ЖР) в 1С:Предприятие и какие поля есть у записи журнала (объект ЖурналРегистрацииЗапись встроенного языка), позволяющие идентифицировать связанный объект метаданных?
    NEW: При расследовании сообщённой ошибки через журнал регистрации 1С — какое поле записи журнала позволяет перейти к затронутому объекту метаданных (и его коду), и как поступить, если в записи назван объект метаданных, но не конкретная процедура?
Q-TUL-029 | keep | Операционная методология (порядок каскада фильтрации + причина). Проверено против раздела "Каскад фильтрации" (Error без времени → без уровня → ±15 мин; timezone drift/ClickHouse). Верно.
Q-TUL-030 | rewrite | Содержал точное значение (1000 записей) и внутреннее имя режима (mode:minimal) — тривия внутренней политики. Сведено к операционному общему принципу: что маскировать + зачем ограничивать первичную выборку.
    OLD: Какие ограничения безопасности накладываются при работе с журналом регистрации 1С в этом навыке диагностики: что обязательно маскировать в выводе и сколько записей максимум запрашивать при первичном поиске?
    NEW: При выводе записей журнала регистрации 1С в ходе диагностики — какие категории данных обязательно маскировать и почему первичный поиск стоит ограничивать (по периоду и объёму выборки), а не выгружать журнал целиком?
Q-TUL-031 | keep | Существование ИТС (its.1c.ru) как отдельной базы стандартов + паттерн search-then-fetch. Общее знание, подтверждено шаблонами SEARCH_ITS/FETCH_ITS. Верно.
Q-TUL-032 | rewrite | Спрашивал точные имена JVM-параметра/env-var (-Dapp.globalConfiguration.path / BSL_PLATFORM_BIN) — проектно-специфичные детали run-скрипта. Сведено к публичному аналогу: симптом отсутствия контекста платформы (.hbk) и как его определить.
    OLD: Какой JVM-параметр или переменная окружения позволяет BSL Language Server явно указать путь к файлу синтакс-помощника платформы (.hbk) для резолва платформенного контекста (глобальных методов, типов), и что происходит, если платформенный контекст не найден?
    NEW: BSL Language Server для резолва платформенного контекста (глобальных методов, платформенных типов) требует загруженного контекста платформы из синтакс-помощника (.hbk). Что произойдёт с hover / автодополнением / signature_help по платформенным типам, если этот контекст не загружен, и как определить это по поведению сервера?
Q-TUL-033 | keep | Поведение автодополнения (get_completion) после Объект. vs Перечисления.Имя. — стандартная семантика LSP-completion, сводится к публичному аналогу. Подтверждено таблицей "Discovery метаданных". Верно.
Q-TUL-034 | keep | Общий паттерн layered checking + финальный вердикт компилятора; AI-ассистент не финальный авторитет. Подтверждено "Иерархия доверия". Верно (вопрос не требует полного перечисления).
Q-TUL-035 | keep | API коллекционных типов различается — экспертное 1C-знание. Подтверждено разделом "коллекционные типы". Методы в reference корректны.
Q-TUL-036 | keep | DRY + каскад поиска по убыванию специфичности. Общий принцип, подтверждён "Каскад поиска". Мягкий, но проходит экспертный тест.
Q-TUL-037 | keep | Синтаксис подавления АПК / BSL LS / EDT (3 инструмента, на границе). Форма "какой синтаксис у инструмента X". Подтверждено таблицей "Синтаксис маркеров". Верно.
Q-TUL-038 | rewrite | Содержал точные пороги (цикломатическая >20 / когнитивная >15) — конфиг-числа, эксперт их наизусть не помнит. Сведено к вопросу о двух метриках и их смысле.
    OLD: В навыке самопроверки качества BSL-кода через BSL Language Server указаны пороговые значения цикломатической и когнитивной сложности метода, после превышения которых метод считается кандидатом на рефакторинг. Какие это пороги?
    NEW: Самопроверка качества BSL-кода через BSL Language Server (get_method_complexity) сигнализирует, что метод пора рефакторить, по двум метрикам сложности. Какие это две метрики и что каждая из них отражает?
Q-TUL-039 | keep | Делегирование image-gen в Codex CLI (у Claude нет встроенного image_generation) + sandbox workspace-write vs read-only. Подтверждено SKILL (строки 9, 31, 104). Верно.
Q-TUL-040 | keep | Общие принципы промптов image-gen: наблюдаемые детали vs абстракция; дельта vs пересборка сцены. Подтверждено prompt-guide §1/§3. Верно.
Q-TUL-041 | keep | pandoc: .doc не принимается; --extract-media → <dir>/media/; сложные таблицы остаются raw HTML. Подтверждено SKILL + docx2md.sh (проверка папки media/). Верно.
Q-TUL-042 | keep | ЗаписьЖурналаРегистрации + параметры + паттерн temporary logging с обязательной очисткой. Подтверждено разделами "Параметры" и "Процедура/Чек-лист очистки". Верно.
Q-TUL-043 | keep | /Execute открывает форму → EXIT=0 не доказывает успех. Диагностическое рассуждение "симптом → что произошло". Подтверждено learned-patterns. Верно.
Q-TUL-044 | keep | Verbatim-цитаты (почему) + три категории триажа. Общая QA-практика, подтверждено §1/§4.1. Верно (3 категории — на границе допустимого).
Q-TUL-045 | keep | Порядок DAP: breakpoint → запуск → polling; evaluate без побочных эффектов. Подтверждено разделами "Как инициировать" и "Работа с переменными". Верно.
Q-TUL-046 | keep | PostgreSQL EXPLAIN (ANALYZE, BUFFERS) + MS SQL sys.dm_exec_requests.blocking_session_id. Стандартное DBA-знание, подтверждено "Модели доказательств по СУБД". Верно.
Q-TUL-047 | keep | backward slicing (граф от симптома назад) + принцип cheap-to-expensive. Подтверждено §2/§4. Верно (термин + эквивалентное описание допускаются).
Q-TUL-048 | keep | Первый шаг RAC (cluster list → UUID), session list, session terminate --session. Подтверждено SKILL. Верно.
Q-TUL-049 | keep | rac infobase update --sessions-deny=on/off + --permission-code. Подтверждено rac-use и subsystem-update. Верно.
Q-TUL-050 | rewrite | Вопрос-счётчик + полное перечисление 14 режимов (запрещённая форма). Переформулировано на операционное: 3 ключевых режима по назначению + `rac help <mode>`.
    OLD: Сколько режимов (доменов команд) поддерживает RAC и какие это режимы?
    NEW: Команды RAC организованы по режимам (доменам) вида `rac <mode> --cluster=<uuid> <действие>`. Назови режим для работы с информационными базами, режим для сеансов и режим для соединений, и какой командой получить справку по конкретному режиму.
Q-TUL-051 | keep | Загруженная в память ВК не выгружается удалением файла → нужен перезапуск. Общий рантайм-принцип (dlopen), подтверждён "Нужно ли перезапускать". Верно.
Q-TUL-052 | drop | Внутренняя обвязка конкретного стенда (webhook onec-infra:8765): точные имена полей ответа (restart_required/matches/loaded) + токен-политика. Не публичное знание; сам grading_notes фиксирует это. Салвэдж (dry-run) слишком генеричен для tool-knowledge пробы.
Q-TUL-053 | keep | БСП запускает обработчик если версия в РегистрСведений.ВерсииПодсистем < версии обработчика; для МонопольныйРежим нужно исключить сеансы. Подтверждено Шаг 1/Шаг 2. Верно.
Q-TUL-054 | keep | Ключи 1cv8c /C и /UC; /UC = --permission-code от RAC. Публичные ключи клиента 1С, подтверждено Шаг 3. Верно.

Q-TUL-055 | rewrite | Original demanded a full 6-item enumeration of stub-procedure names (forbidden form, expert won't recall verbatim). Reformulated as symptom→cause→fix around the "method not found" error; verified against subsystem-update/SKILL.md (Типичные ошибки + Шаблон модуля).
    OLD: Какие обязательные экспортные процедуры-заглушки должен содержать модуль подсистемы обновления БСП (модуль вида ОбновлениеИнформационнойБазыXXX), иначе платформа выдаст ошибку «метод объекта не обнаружен»?
    NEW: После регистрации новой подсистемы обновления БСП (модуль вида ОбновлениеИнформационнойБазыXXX) при запуске обновления платформа выдаёт «Метод объекта не обнаружен (ПередОбновлениемИнформационнойБазы)». В чём причина и как это исправить?
Q-TUL-056 | keep | Virtual tables + resource suffixes; fundamental 1C query-language knowledge. Verified against query-syntax-cheatsheet.md (Источники данных + Суффиксы ресурсов).
Q-TUL-057 | keep | ЕСТЬ NULL vs = NULL, ЕСТЬNULL() function; core query knowledge. Verified against cheatsheet (Работа с NULL) + platform-data-core SKILL.
Q-TUL-058 | keep | ВЫРАЗИТЬ for composite types / implicit JOINs; well-known optimization. Verified against cheatsheet (ВЫРАЗИТЬ для составных типов).
Q-TUL-059 | keep | ДАТАВРЕМЯ literal + args; standard query literal. Verified against cheatsheet (Работа с датами).
Q-TUL-060 | keep | Metadata-first principle + which MCP tools (list_metadata_objects / get_metadata_structure); "which tool for a task" is good form, principle meaningful, grading honest about tool names. Verified against platform-data-core SKILL §1.
Q-TUL-061 | rewrite | Original was framework-specific (execute_query MCP limitation, unknowable in-weights) and leaked the answer ("без параметров"). Reduced to a public 1C analog about comparing via primitive attributes; Код uniqueness. Verified against platform-data-core SKILL §Критические ограничения.
    OLD: В данной MCP-интеграции запросов 1С (`execute_query` по HTTP/MCP без параметров) — можно ли использовать параметризованный текст запроса вида `ГДЕ Товар = &Товар` и прямое сравнение ссылочных полей (`ГДЕ Документ.Контрагент = Справочник.Контрагенты.Ссылка`), и как обойти это ограничение?
    NEW: Если инструмент выполнения запроса 1С не позволяет передавать параметры (`&Имя`) и не поддерживает прямое сравнение ссылочных полей, как в самом тексте запроса отобрать записи по конкретному элементу справочника, и какой примитивный реквизит предпочтителен для однозначной идентификации (и почему Наименование ненадёжно)?
Q-TUL-062 | keep | Designer XML vs EDT XML not interchangeable; well-known. Verified against xml-generation SKILL §4 п.1 + "Не используй когда".
Q-TUL-063 | keep | UTF-8 BOM encoding + mixed line endings (CRLF between tags, bare LF in v8:content); operationally meaningful. Verified against xml-generation SKILL §4 п.2-3.
Q-TUL-064 | rewrite | Original demanded exact binary flag values (G=1/f1=0/f1=2) that fail the expert test (no 1C engineer recalls them; no operational value from reciting). Kept the file name + operational meaning of the vendor-support lock; dropped the flag values. Verified against xml-generation SKILL §4 п.8.
    OLD: В каком служебном файле выгрузки конфигурации 1С хранится информация о том, что объект (или вся конфигурация) находится «на поддержке» поставщика и не может редактироваться напрямую, и какие значения флагов там означают полную блокировку конфигурации и блокировку конкретного объекта?
    NEW: В каком служебном файле выгрузки конфигурации 1С хранится информация о поддержке поставщика («замок»), и что наличие такого замка означает для возможности прямого редактирования XML объекта типовой конфигурации?
Q-TUL-065 | drop | Internal xml-gen QA oracle modes (dsl/cli/both). Public knowledge cannot answer; does not reduce to a meaningful public analog; measures knowledge of an internal tool, not a skill. (behavioral-oracles.md)
Q-TUL-066 | keep | validate exit-code convention (0/1/2) + why byte-precise replace-text for multiline XML; exit codes have operational meaning (hooks/CI), verified against universal-commands.md.
Q-TUL-067 | keep | ChildObjects requires strict canonical order + first types; does not demand all 44 (grading allows partial). Verified against config-operations/SKILL.md (ChildObjects — порядок 44 типов).
Q-TUL-068 | keep | ObjectBelonging="Adopted" + ID ranges (Base 1–999999, Extension 1000000+); widely-cited CFE facts. Verified against extension-operations/SKILL.md (Ключевые концепции CFE).
Q-TUL-069 | keep | BSL interceptor decorators &Перед/&После/&Вместо/&ИзменениеИКонтроль with distinct semantics; public BSL syntax. Verified against extension-operations/SKILL.md.
Q-TUL-070 | keep | Forbidden runtime form types (FormDataStructure/Collection/Tree) not serializable → error. Verified against form-dsl/SKILL.md (Запрещённые runtime-типы).
Q-TUL-071 | keep | Directive/context matching: ПриСозданииНаСервере→&НаСервере, ПриОткрытии/ПриЗакрытии→&НаКлиенте; basic BSL rule. Verified against form-dsl/SKILL.md (Команды и события).
Q-TUL-072 | rewrite | Original asked for a full 10-item enumeration of RoleRight values (forbidden form). Refocused on the operational nugget — PascalCase requirement and what happens with camelCase; example rights moved into the question. Verified against role-dsl/SKILL.md (Права enum RoleRight + Ловушки).
    OLD: Какие права доступа к объекту метаданных задаются в ролях 1С (перечисление RoleRight в Rights.xml), и в каком регистре (camelCase или PascalCase) их нужно указывать?
    NEW: В ролях 1С права доступа к объектам задаются значениями перечисления RoleRight (Read, Insert, Update, Delete, View и т.д.). В каком регистре их нужно указывать в Rights.xml и что произойдёт, если указать право в camelCase (например `read`, `insert`)?
Q-TUL-073 | keep | CommandInterface.xml reference formats (CommonCommand.X, StandardCommand.Create, Command.X, Report...Command); systematic syntax, "which syntax for X" form. Verified against subsystem-interface/SKILL.md (Формат ссылок на команды).
Q-TUL-074 | keep | EPF Designer directory tree (root XML, ObjectModule.bsl, Forms/.../Form.xml, Templates/.../Template.xml); real export structure, grading lenient on exact subfolder names. Verified against epf-full/SKILL.md §2.
Q-TUL-075 | keep | ERF vs EPF difference (ExternalReport vs ExternalDataProcessor, identical internal structure). Verified against epf-full/references/epf-base.md.
Q-TUL-076 | keep | BSP assignable (require Назначение) vs global external-processing kinds, via СведенияОВнешнейОбработке(). Verified against epf-full/references/epf-bsp.md.
Q-TUL-077 | keep | DESIGNER /LoadExternalDataProcessorOrReportFromFiles + exit code meaning; "linter weaker than compiler". Verified against epf-full/references/learned-patterns.md.
Q-TUL-078 | keep | <Template> registration in ChildObjects, Report MainDataCompositionSchema, template types (closed set an expert knows). Verified against epf-full/references/templates.md.
Q-TUL-079 | rewrite | Original led with a generic UI iterative cycle (measures nothing) as the primary point. Refocused on the meaningful distinction: what EPF/ERF validation checks beyond a single Form.xml. Verified against forms-toolkit/SKILL.md (form vs epf validate).
    OLD: Какой типичный итеративный цикл разработки управляемой формы в 1С описывается паттерном 'анализ структуры → редактирование → валидация → повторный анализ'? Почему для внешних обработок (EPF/ERF) обычно нужна отдельная процедура валидации, отличная от валидации одной формы?
    NEW: При работе с управляемой формой применяется цикл: изучить структуру → отредактировать → проверить (validate) → снова изучить структуру. Для внешней обработки/отчёта (EPF/ERF) используется отдельная валидация, а не валидация одной Form.xml. Что дополнительно проверяет валидация EPF/ERF по сравнению с проверкой отдельной формы?
Q-TUL-080 | keep | Form element → XML tag mapping (InputField/CheckBoxField/LabelDecoration/Table/Button/UsualGroup); real managed-form type names, "which tag for X" form. Verified against forms-toolkit/references/edit.md + info.md.
Q-TUL-081 | keep | Multilingual Title (v8:item/v8:lang/v8:content) + Title inheritance from requisite Synonym. Verified against forms-toolkit/references/element-mapping.md.

Q-TUL-082 | keep | EPF XML root structure (MetaDataObject/ExternalDataProcessor, InternalInfo, ChildObjects) verified against validate.md; tests meaning/purpose of the format, not a statistic.
Q-TUL-083 | keep | Reserved attribute names + FillFromFillingValue/FillValue/DataHistory only for InformationRegister — both verified in meta-operations SKILL.md "Инварианты компиляции"; asks "name several", not full list.
Q-TUL-084 | keep | Atomicity (all-or-nothing) of batch ops — general concept, verified in batch-patch.md.
Q-TUL-085 | keep | BSL macet API ПолучитьМакет/ПолучитьОбласть/Параметры + intersection via '|' — verified in mxl-dsl SKILL.md; strong operational question.
Q-TUL-086 | keep | Horizontal (span/colspan) vs vertical (rowspan) merge concept — verified in dsl-spec.md.
Q-TUL-087 | keep | Расшифровка (detail) mechanism paired with param — verified in dsl-spec.md / info-modes.md.
Q-TUL-088 | keep | Indexed palettes concept (shared style tables referenced by index, dedup) — verified in validate-classes.md §4 palette-refs; tests meaning/purpose, acceptable.
Q-TUL-089 | keep | SKD data set types (3) + field roles — verified in skd-dsl SKILL.md; core public platform knowledge.
Q-TUL-090 | keep | Variant structure (group/table/chart) + resource group-level formulas — verified in skd-dsl SKILL.md / info-modes.md.
Q-TUL-091 | rewrite | Original demanded exact internal XML type names (fieldTemplate/groupTemplate/groupHeaderTemplate) and drilldown XML internals (DetailsAreaTemplateParameter, mainAction=DrillDown) — узкоспециализированный internal format, expected нет. Reduced to conceptual binding targets + drilldown meaning; grading explicitly forgives unknown XML tag names.
    OLD: В макетах оформления СКД (шаблонах вывода) различают несколько типов привязки макета к элементам отчёта — к полю, к группировке (заголовок/подвал строк данных) и к заголовку самой группы. Как называются эти три типа? Также опишите, как в XML-шаблоне СКД реализуется расшифровка (drilldown) по клику.
    NEW: В отчёте на СКД макет вывода (шаблон) можно привязать к разным элементам отчёта. К чему может быть привязан макет (перечислите основные варианты привязки) и чем по смыслу отличается макет, привязанный к области данных группировки, от макета заголовка самой группировки? Как в общем виде задаётся расшифровка (drilldown) ячейки макета?
Q-TUL-092 | keep | read-modify-validate-write + idempotency of modify/remove — general practice, verified in skd-edit SKILL.md "Инварианты и контракт".
Q-TUL-093 | rewrite | Balance-role part is strong (balanceGroupName + balanceType Opening/Closing, verified in fields.md). Original also demanded full enumeration of 9 period components (>3, forbidden form). Reframed period part to "what does the period role specify" instead of listing all granularities.
    OLD: В схеме компоновки данных 1С поле может иметь роль 'балансовый показатель' (используется в бухгалтерских/оборотных отчётах для остатков) и роль 'период'. Какие дополнительные атрибуты обычно уточняют балансовую роль (различение начального и конечного остатка), и какие компоненты периода существуют для роли 'период'?
    NEW: В схеме компоновки данных 1С поле может иметь роль «балансовый показатель» (для остатков в бухгалтерских/оборотных отчётах). Как СКД различает начальный и конечный остаток одного и того же показателя — какие два уточнения балансовой роли для этого используются? И что задаёт полю-дате роль «период»?
Q-TUL-094 | keep | StandardPeriod @autoDates, use=Always, denyIncompleteValues — verified in parameters.md / skd-dsl SKILL.md.
Q-TUL-095 | keep | 1C query language (SQL-like Russian keywords) + patch-query @once assert-exactly-one — verified in query.md.
Q-TUL-096 | keep | Grouping properties stored separately from groupItems (selection/order/filter/conditionalAppearance/outputParameters) — verified in structure.md; operationally meaningful (why modify-structure preserves them).
Q-TUL-097 | keep | SKD aggregate functions (Сумма/Среднее/Количество/Минимум/Максимум) + totals on calculated fields — verified in totals.md; core domain, closed set of 5.
Q-TUL-098 | keep | Advisory vs blocking gate review distinction — verified in cross-provider-review SKILL.md "Режимы"; general orchestration concept.
Q-TUL-099 | keep | Read-only review flags: claude --permission-mode plan / --tools=Read,Grep,Glob,LS / --strict-mcp-config; codex --sandbox read-only — verified in SKILL.md "Safety".
Q-TUL-100 | keep | Traceability-matrix table (criterion→file:line→test→stdout→verdict) — verified in finalization-prompt.md §B; general RTM pattern.
Q-TUL-101 | keep | Anti-deception vectors — verified in finalization-prompt.md §C; concepts (test theater, scope shrinkage, cherry-picked logs...) are genuine reviewer reasoning patterns, grading accepts partial (4-5 of 8).
Q-TUL-102 | keep | Review-prompt structure Task/Artifact/Criteria/Context + "paths not content" — verified in review-prompt.md.
Q-TUL-103 | keep | Severity order BLOCK>WARN>INFO, stable IDs F-01.., round-3 restriction — verified in review-prompt.md Finding/Iteration Protocol.
Q-TUL-104 | keep | claude headless flags -p, --output-format stream-json, --include-partial-messages, --resume — verified in claude_opus_review.py run_claude (lines 392-410).
Q-TUL-105 | keep | codex exec / resume <session_id> / --sandbox read-only / --json / -c model_reasoning_effort — verified in codex_review.py run_codex (lines 642-679).
Q-TUL-106 | keep | Hardlink materialization (os.link, ~0 disk, read-only safe, fallback to copy) — verified in both scripts' hardlink_path/materialize_path.
Q-TUL-107 | keep | /Execute opens form (not object-module export method), ПараметрЗапуска() reads /C, /DisableUnsafeActionProtection suppresses safety dialog — all verified in v8-runner SKILL.md.
Q-TUL-108 | keep | 1cv8 test keys /N /P, /TESTMANAGER, /TESTCLIENT -TPort, /DisableStartupDialogs — verified in v8-runner SKILL.md (port numbers allowed per rubric).

## Counts
keep=25 rewrite=2 drop=0

Q-TUL-109 | keep | License-паттерны (лиценз/License/HASP/nethasp) как симптом hard-stop + дефолтные кандидаты логина Администратор/Admin — операционное «что означает вывод → что делать»; verified against auth-guard.md.
Q-TUL-110 | keep | «С какой версии ibcmd поддерживается» — версия 8.3.20 критична для выбора backend; verified bootstrap.md.
Q-TUL-111 | keep | Формы строки подключения File= / Srvr=;Ref= — базовое 1С-знание, «какой синтаксис для X»; verified bootstrap.md.
Q-TUL-112 | keep | Существование ключей Конфигуратора /CheckConfig, /CheckModules — public 1C batch-режим, «какой ключ для проверки».
Q-TUL-113 | keep | Два backend'а (Designer batch / ibcmd) и ограничение ibcmd — verified config-and-backends.md.
Q-TUL-114 | keep | /DumpConfigToFiles, /LoadConfigFromFiles, /UpdateDBCfg — общеизвестные public-ключи batch-режима Конфигуратора.
Q-TUL-115 | keep | Partial dump требует --object; ibcmd-partial деградирует до incremental — «что произойдёт»; verified file-and-artifact-workflows.md.
Q-TUL-116 | keep | Auto-detect транспорта коротким TCP-probe с fallback — общий инженерный паттерн; ~200ms из источника.
Q-TUL-117 | drop | Внутренний недокументированный формат /C-payload WS-сопряжения + порядок clap-флагов конкретного форка; expected_in_weights=нет, public-знание не может ответить.
Q-TUL-118 | drop | Уникальный внутренний инцидент (race condition idle-handler, DRIVE 2026-05-11) — не может быть в весах, измеряет только галлюцинацию.
Q-TUL-119 | keep | YaXUnit как open-source фреймворк, сценарии «все тесты / один модуль» — реальное тестовое знание.
Q-TUL-120 | keep | Различение ошибок окружения vs исходников и триггеры full-rebuild — операционная диагностика; verified troubleshooting.md.
Q-TUL-121 | keep | Устройство MCP-агрегатора/gateway — конкретный узнаваемый паттерн (агрегация витрины, роутинг, очередь, reconnect).
Q-TUL-122 | keep | Persistent tool-cache как решение нестабильной обработки notifications/tools/list_changed — реальный MCP-паттерн с конкретным ответом.
Q-TUL-123 | drop | Открытый эссе-вопрос про многослойную архитектуру + «типично ли раздельное версионирование» — не операционно, любая модель ответит правдоподобно независимо от знаний.
Q-TUL-124 | drop | Наводящий agreement-bait (один обязательный параметр + systemd + Prometheus) + внутренняя деталь workPath; операционной ценности нет.
Q-TUL-125 | rewrite | Убрана наводящая governance-часть (human-approval — проектная политика); оставлена операционная конвенция именования namespace для устранения коллизий; verified sessions-and-tools.md «Имя tool на витрине».
    OLD: Какая общая governance-практика требует явного одобрения человека перед добавлением/изменением публичного API-метода (tool) в расширяемой системе, и является ли конвенция именования <namespace>__<method> общепринятой для устранения коллизий имён между несколькими поставщиками одного интерфейса?
    NEW: Когда несколько независимых расширений (поставщиков) публикуют свои инструменты на единой витрине MCP-агрегатора и двое из них экспортируют метод с одинаковым коротким именем, но со своей схемой — какая конвенция именования применяется, чтобы имена инструментов не сталкивались на общей витрине?
Q-TUL-126 | keep | Роутинг round-robin vs session_id, FIFO per-session, soft-reconnect grace — конкретные фальсифицируемые паттерны stateful-сессий; verified sessions-and-tools.md.
Q-TUL-127 | keep | Диагностика Address already in use через ss -lntp / lsof -i — операционное «какая связка утилит для X»; verified troubleshooting.md.
Q-TUL-128 | keep | schema_conflict-guard + различие JSON-RPC -32601 (method not found) и tool-level «недоступен» — реальное public MCP/JSON-RPC знание; verified.
Q-TUL-129 | keep | Анатомия .feature-файла VA (директивы language/encoding, Функциональность/Контекст/Сценарий) — структурное авторское знание; verified vanessa-authoring SKILL.md.
Q-TUL-130 | keep | Взаимозаменяемость Дано/Когда/Тогда через matching по regex/тексту — реальное BDD-знание, «почему это работает».
Q-TUL-131 | keep | Русский аналог Scenario Outline/Examples = Структура сценария/Примеры — реальное VA-знание.
Q-TUL-132 | keep | Тег @tree (Turbo Gherkin, вложенность табами) — «что делает тег Z», реальная фича VA; знаемо VA-экспертами.
Q-TUL-133 | keep | Тег @exportscenarios (переиспользуемый подсценарий) — реальный часто используемый VA-тег.
Q-TUL-134 | drop | Обязательная внутренняя MCP-последовательность исследования формы + framework-обвязка (connect_test_client/... через v8-client-session-manager); проектная политика, не в весах.
Q-TUL-135 | rewrite | Обобщён до общего QA/BDD-принципа least-privilege (убраны «этого фреймворка» и специфичный `AgentAI`); ядро — setup под техпользователем, верификация под реальным бизнес-пользователем без техролей.
    OLD: Согласно практике этого фреймворка, feature-файл Vanessa логически делится на две сессии: setup и бизнес-флоу. Под какими пользователями должна выполняться каждая часть и почему нельзя выдавать бизнес-пользователю технические роли ради прохождения шага?
    NEW: В UI/BDD-сценарии, где подготовка тестовых данных (фикстуры, инфраструктурные шаги) совмещена с проверкой бизнес-поведения, под пользователями какого типа логично выполнять каждую из двух частей — и почему выдача бизнес-пользователю дополнительных технических ролей ради прохождения падающего шага считается антипаттерном?

## Totals
keep=20, rewrite=2, drop=5 (total 27)

Q-TUL-136 | keep | Symptom→meaning: red dashed underline for mandatory unfilled fields. Verified against learned-patterns.md. Expert-recallable platform behavior.
Q-TUL-137 | keep | Meaning/symptom: "*" in title + save dialog Да/Нет/Отмена. Verified. Good operational question.
Q-TUL-138 | keep | Operational "which step to use" for Switcher checkbox; project-empirical but verified against source, not a forbidden form.
Q-TUL-139 | keep | Meaning: Vanessa searches by Title not Name; verified. Good operational/gotcha question.
Q-TUL-140 | keep | Operational: name vs title search, prefer name. Verified against steps-cheatsheet.md.
Q-TUL-141 | drop | Enumeration-of-examples across 3 catalog categories; question leaks the categories and asks to reproduce catalog entries — encyclopedic recall an expert would not do from memory.
Q-TUL-142 | keep | Report/log formats (va-status.json, execution log, JUnit XML, Cucumber JSON). BDD-expert recallable formats. Verified line 16.
Q-TUL-143 | keep | Operational reasoning: why va-status.log is more reliable completion signal than va-status.json. Verified lines 28-31; meaningful lesson.
Q-TUL-144 | rewrite | Original demanded full enumeration of 7 error classes (forbidden form). Reworked into a symptom→class mapping (GOOD FORM).
    OLD: Какие классы ошибок использует этот фреймворк для классификации падений BDD/UI-прогонов Vanessa Automation при диагностике?
    NEW: При диагностике упавшего прогона Vanessa Automation по этому фреймворку встречаются два случая: (а) нужный шаг сценария не найден или не резолвится; (б) форма открылась, шаги выполнились, но ожидаемая проверка результата не совпала. К какому классу ошибки диагностика относит каждый из этих двух случаев?
Q-TUL-145 | keep | Rich operational "how to run headless 1C client + why GUI still needed" (Xvfb, xwininfo/xprop/xdotool). Verified. Toolchain enumeration is operationally justified.
Q-TUL-146 | keep | General graceful-degradation/fallback practice; answerable by experienced engineer, not a forbidden form.
Q-TUL-147 | keep | OneScript module structure (3 sections + order). Structural knowledge, verified lines 12-16.
Q-TUL-148 | rewrite | Original demanded enumerating >=4 manifest properties (forbidden). Reworked to file name + location + syntax style (the discriminating knowledge).
    OLD: Как называется файл-манифест пакета OneScript и какие у него основные свойства (перечислите минимум 4)?
    NEW: Как называется файл-манифест пакета OneScript, где он располагается в проекте и в каком виде (каким синтаксисом) в нём описывается пакет?
Q-TUL-149 | keep | "Which command does X" (opm build . / opm push .ospx). GOOD FORM, verified lines 138-139.
Q-TUL-150 | keep | Two #Использовать syntaxes (by name / by path). Verified lines 129-130. Operational.
Q-TUL-151 | keep | Niche but operational: format spec for fractional seconds in OneScript ДФ. Verified reference.md lines 9-16.
Q-TUL-152 | keep | Language-difference meaning (constructor result in expression: OneScript vs 1C). Verified.
Q-TUL-153 | keep | Operational "which file/procedure to customize library loading" (package-loader.os). Verified line 53.
Q-TUL-154 | keep | Existence + meaning of Autumn DI + &Желудь. Verified. Guarded against test-framework confusion.
Q-TUL-155 | keep | "Which method to get a component" (Осень.НайтиЖелудь). Verified line 29.
Q-TUL-156 | keep | Operational: &Дуб + &Завязь for legacy migration. Verified lines 40-42.
Q-TUL-157 | keep | Operational: declaring autumn-cli command + subcommands. Verified.
Q-TUL-158 | keep | Question asks for the type-annotation pattern (&Т-prefix) + &Обязательный with examples ("и т.п."), not exhaustive count; grading accepts partial. Verified line 70-72.
Q-TUL-159 | keep | Winow existence + default port 3333 + &Контроллер/&ТочкаМаршрута. Ports allowed for experts. Verified.
Q-TUL-160 | drop | Pure internal meta-documentation trivia (README capability-name→real-tool mapping). No operational use for using a tool; public knowledge cannot answer; grading notes themselves say it only tests file-reading.
Q-TUL-161 | rewrite | Original leaked the field list in a parenthetical hint. Removed the leak; made icons optional to match reality (v8-runner openai.yaml has no icon fields).
    OLD: Существует ли у OpenAI Codex CLI формат манифеста скилла agents/openai.yaml с блоком `interface`, и какие поля этот блок содержит (отображаемое имя, краткое описание, иконки, дефолтный промпт)?
    NEW: Существует ли у OpenAI Codex CLI формат манифеста скилла `agents/openai.yaml` с блоком `interface`? Если да — какие поля описывают представление скилла внутри этого блока?
Q-TUL-162 | keep | Core meaningful test = Apache 2.0 NOTICE attribution contents (general expert knowledge); repo-existence wrapper handled by grading. Verified against NOTICE.txt.

