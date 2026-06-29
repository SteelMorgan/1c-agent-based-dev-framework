---
name: va-visual-check
description: "Vanessa/VA MCP: визуальная проверка форм 1С и скриншоты"
---

# VA Visual Check

Используй этот навык для визуальной проверки 1С-форм через Vanessa Automation / TestClient и VA MCP. Это профильный маршрут для UI/UX-скриншотов управляемых форм 1С.

## Основной маршрут

1. Если VA MCP manager-сессия ещё не поднята, подними её строго по навыку `v8-runner`; здесь проверяй только live-сессию `kind=vanessa_test_client` в `session_list`.
2. Подключи тест-клиент через `connect_test_client` с профилем из настроек VA, не угадывай имя профиля.
3. Убедись, что подключён реальный test-client: профиль/лог/состояние VA содержит PID, не `0`.
4. Открой нужную форму через VA/TestClient tools.
5. Получи структурное состояние формы (`get_form_analysis`, `get_window_list_testclient`, чтение элементов/таблиц).
6. Получи список OS-окон через `get_window_list_os`.
7. Критично: операции снятия скриншотов через VA MCP выполняй строго синхронно. Не запускай несколько `get_window_screenshot_os` параллельно и не используй для них `multi_tool_use.parallel`: отправь один запрос, дождись полного ответа и убедись через `session_list`, что сессия жива и `inflight=0`; только после этого отправляй следующий запрос.
8. Сними PNG через `get_window_screenshot_os`:

```text
get_window_screenshot_os {
  "window_title": "<точный заголовок окна формы>",
  "file_name": "<путь>.png",
  "color_mode": "color"
}
```

9. Проверь PNG: файл создан, размер ожидаемый, изображение не пустое, не одноцветное и не чёрное.

## Linux headless X11/Xvfb без window-manager

Этот рецепт применим только для Linux на виртуальном X11/Xvfb-дисплее без графического окружения/window-manager. Он нужен, когда `get_window_list_os` видит окно формы, но `get_window_screenshot_os` возвращает чёрный или почти пустой PNG.

X11-команды используются только для экспонирования уже открытого окна. Приоритетный скриншот после этого всё равно делается через VA MCP.

1. Найди X11 id окна формы:

```bash
xwininfo -root -tree | sed -n '1,220p'
```

Если `wmctrl -l` или другие EWMH-инструменты отвечают `Cannot get client list properties` / `_NET_CLIENT_LIST or _WIN_CLIENT_LIST`, это ожидаемо для Xvfb без window-manager. Используй дерево `xwininfo`, а не список клиента window-manager.

2. Проверь, что найденное окно принадлежит test-client, а не VA manager:

```bash
xprop -id <window_id> _NET_WM_PID WM_NAME WM_CLASS
```

`_NET_WM_PID` должен совпадать с PID подключённого test-client. Если PID ещё не зафиксирован, получи его из VA-профиля/состояния подключения; заголовок окна используй только как дополнительный фильтр.

3. Перемести, увеличь и подними окно:

```bash
xdotool windowmove <window_id> 0 0 || true
xdotool windowsize <window_id> 1200 800 || true
xdotool windowraise <window_id> || true
xdotool windowactivate --sync <window_id> || true
xwininfo -id <window_id> | sed -n '1,60p'
```

В среде без window-manager `windowactivate` может упасть с сообщением про `_NET_ACTIVE_WINDOW`; это не blocker, если `xwininfo` показывает `Map State: IsViewable`.

4. Повтори штатный VA-снимок через `get_window_screenshot_os`.

5. Повтори проверку PNG. Если снимок всё ещё чёрный/одноцветный, переходи к fallback-решению ниже и явно зафиксируй причину.

## Browser fallback

VA MCP — предпочтительный маршрут для обычных форм 1С, потому что он работает с реальным TestClient и даёт одновременно структуру формы и визуальный PNG.

Web/browser fallback допустим, когда:

- VA MCP недоступен или не проходит readiness;
- `connect_test_client` не даёт реальный PID;
- `get_window_list_os` не видит нужное окно;
- `get_window_screenshot_os` остаётся чёрным/одноцветным после Linux/Xvfb-рецепта;
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
