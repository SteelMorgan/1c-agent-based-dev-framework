---
name: va-visual-check
description: "Vanessa/VA MCP: визуальная проверка форм 1С и скриншоты"
---

# VA Visual Check

Используй этот навык для визуальной проверки 1С-форм через Vanessa Automation / TestClient и VA MCP. Это профильный маршрут для UI/UX-скриншотов управляемых форм 1С.

## Основной маршрут

1. Если VA MCP manager-сессия ещё не поднята, подними её строго по навыку `v8-runner`; здесь проверяй только live-сессию `kind=vanessa_test_client` в `session_list`.
2. После запуска VA/клиента дай процессу штатно подняться: 10-20 секунд до первой проверки — нормальный интервал. Если live-сессия уже видна, но VA tools ещё не появились в витрине, жди регистрацию tools периодическим чеком до 120 секунд; переходи дальше сразу после появления нужных tools.
3. Подключи тест-клиент через `connect_test_client` с профилем из настроек VA, не угадывай имя профиля.
4. Убедись, что подключён реальный test-client. Не полагайся только на текстовый ответ `connect_test_client`: проверь выбранный VAParams-профиль, лог/состояние VA, живой процесс `/TESTCLIENT -TPort ...` или `get_window_list_os`. Если PID равен `0`/пустой, сначала проверяй активный `tests.va.params_path`, строку профиля в `ДанныеКлиентовТестирования`, занятые/зависшие порты и старые TestClient-процессы.
5. Открой нужную форму через VA/TestClient tools.
6. Получи структурное состояние формы (`get_form_analysis`, `get_window_list_testclient`, чтение элементов/таблиц).
7. Получи список OS-окон через `get_window_list_os`.
8. Критично: операции снятия скриншотов через VA MCP выполняй строго синхронно. Не запускай несколько `get_window_screenshot_os` параллельно и не используй для них `multi_tool_use.parallel`: отправь один запрос, дождись полного ответа и убедись через `session_list`, что сессия жива и `inflight=0`; только после этого отправляй следующий запрос.
9. Сними PNG через `get_window_screenshot_os` по заголовку OS-окна, возвращённому `get_window_list_os`. В текущем контракте VA MCP `connect_test_client(profileName)` выбирает активный TestClient, а `window_title` выбирает одно OS-окно из списка окон этого подключённого TestClient-процесса:

```text
get_window_screenshot_os {
  "window_title": "<заголовок из get_window_list_os>",
  "file_name": "<путь>.png",
  "color_mode": "color"
}
```

`window_id`/PID из `get_window_list_os` используй только для технической проверки, что окно принадлежит TestClient. Не выбирай окно глобальным поиском по заголовку вне VA: у VA manager и TestClient могут быть одинаковые заголовки. Если PNG чёрный, одноцветный или VA manager отключается во время скриншота, сначала докажи, что установлена текущая сборка VanessaExt и вызов идёт через маршрут подключённого TestClient; только потом переходи к диагностике ниже.

10. Проверь PNG: файл создан, размер ожидаемый, изображение не пустое, не одноцветное и не чёрное.

## MCP-исследование и scenario run-loop

Не смешивай две разные схемы выполнения VA в одном цикле проверки:

- **MCP-исследование / интерактивная проверка формы**: test-client уже запущен и подключён к VA MCP manager. Используй `connect_test_client`, `execute_feature_step`, `get_form_analysis`, `manage_form_elements`, скриншоты и инструменты сохранения табличного документа для этого живого клиента.
- **Scenario run-loop**: VA выполняет feature через `run_scenario` / `v8-runner test va`, а шаги вроде `Я подключаю клиент тестирования с параметрами` создают и ведут собственную сессию TestClient.

Эти схемы ненадёжно разделяют состояние клиента, привязку PID/profile, модальные окна и порты. Если идёт MCP-исследование, не вставляй в тот же feature `Я подключаю клиент тестирования...` как способ «перезапустить» клиент: это может оставить VA с PID `0`, устаревшим модальным состоянием или неподключённым клиентом. Если для MCP-исследования нужен свежий клиент, перезапусти его тем же MCP-рецептом и профилем, а затем снова вызови `connect_test_client`. Если нужен полноценный сценарный прогон, переключайся на `vanessa-run-loop` и веди его как отдельный прогон со своими артефактами.

## Linux headless X11/Xvfb без window-manager

Текущая VanessaExt должна обрабатывать Linux virtual X11/Xvfb без графического окружения/window-manager внутри `get_window_list_os` и `get_window_screenshot_os(window_title=...)`. Не добавляй ручные параметры выбора окна, PID-аргумент, `window_id`-аргумент или внешнюю подготовку окна в штатный маршрут снятия скриншота.

Штатный маршрут остаётся таким:

```text
connect_test_client { "profileName": "<профиль из VAParams>" }
get_window_list_os {}
get_window_screenshot_os {
  "window_title": "<заголовок из get_window_list_os>",
  "file_name": "<путь>.png",
  "color_mode": "color"
}
```

Если PNG чёрный/одноцветный в no-WM Xvfb, считай маршрут сломанным либо проверяй устаревшую компоненту, неверную привязку профиля/порта или выбор не того окна. Проверь:

- установленная компонента в пользовательском кэше 1С совпадает с ожидаемой сборкой VanessaExt;
- X11 root действительно без window-manager/client-list свойств (`_NET_SUPPORTING_WM_CHECK`, `_NET_CLIENT_LIST`, `_WIN_CLIENT_LIST`);
- `get_window_list_os` после `connect_test_client(profileName)` возвращает заголовок целевого окна TestClient;
- выбрано видимое окно формы, а не скрытое служебное окно `1cv8c`.

Не запускай внешние X11-команды управления окнами как часть процедуры скриншота. Не передавай ручные параметры выбора окна в `get_window_screenshot_os`. Если штатный маршрут не работает после проверок выше, исправляй причину в компоненте/профиле/портах/выборе окна или переходи к fallback-решению ниже с фиксацией отказа.

## Browser fallback

VA MCP — предпочтительный маршрут для обычных форм 1С, потому что он работает с реальным TestClient и даёт одновременно структуру формы и визуальный PNG.

Web/browser fallback допустим, когда:

- VA MCP недоступен или не проходит readiness;
- подключённый TestClient остаётся с PID `0`/пустым после проверки VAParams-профиля, портов, старых `/TESTCLIENT` процессов и `get_window_list_os`;
- `get_window_list_os` не видит нужное окно;
- `get_window_screenshot_os` остаётся чёрным/одноцветным после доказательства, что установлена текущая сборка VanessaExt, а проверки профиля/портов/выбора окна не выявили причину;
- проверяемое поведение относится к браузерному слою: DOM/CSS/HTML, console/network, web-auth/publication, viewport/pixel rendering, browser extension, browser-only upload/download/clipboard.

Перед fallback зафиксируй:

- какая VA capability не сработала;
- какие шаги VA-маршрута уже выполнены;
- почему browser/web-client даст достаточный сигнал для текущей задачи;
- остаточный риск: web-client может отличаться от тонкого/толстого клиента 1С.

Для browser fallback используй профильные browser-навыки (`web-test-1c`, `playwright`, `screenshot`) по их назначению. Не смешивай результат: если артефакт получен через web/browser fallback, так и называй его в отчёте.

## Что не делать

- Не заменяй VA MCP скриншот прямым X11/noVNC/OS-снимком без явной fallback-записи.
- Не выбирай окно только по заголовку в Xvfb: VA manager и test-client могут иметь одинаковые заголовки.
- Не считай `get_window_list_testclient` визуальным подтверждением: это структура внутренних окон, не PNG.
- Не продолжай по cached `tools/list`: нужна live-сессия нужного `kind`.

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md
  - framework/skills/tool-usage/v8-session-manager/SKILL.md
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
---
