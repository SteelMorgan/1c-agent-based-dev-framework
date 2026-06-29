---
name: gui-control
description: "Разблокировка зависших окон 1С, диалогов и тестов"
---

# Управление GUI 1С через X11

X11-управление — action, не диагностика. Использовать только когда детектирован GUI-диалог, блокирующий нормальное завершение базы. Диагностику причин — через ЖР (`event-log-analysis`).

Для UI/UX-приёмки обычных 1C-форм не используй `gui-control` как основной маршрут. Сначала применяй `va-visual-check`; X11-клавиши и прямое GUI-управление допустимы как fallback/action только с фиксацией причины и остаточного риска.

Для `Предупреждение безопасности` метаданные X11-окон могут быть неполными. Ориентируйся на связку: ЖР → визуальный артефакт по `va-visual-check` → действие с клавиатурой при необходимости.

## Когда применять

| Триггер | Действие |
|---------|----------|
| В ЖР нет событий после `test_start_time` | Проверить — не завис ли GUI-диалог |
| Заголовок окна: «Ошибка» / «Предупреждение» | VA MCP-скриншот → закрыть диалог только если VA MCP принципиально не умеет нужное действие → анализ ЖР |
| База не завершается после тестов | Закрыть через Escape + Enter только если VA MCP принципиально не умеет закрыть блокирующее окно |
| В ЖР `Предупреждение безопасности` на EPF | Визуальная проверка, не действовать вслепую по заголовкам |

## Настройка окружения

```python
import os
os.environ['DISPLAY'] = ':99'  # до импорта Xlib и PIL
```

## Алгоритм работы

### 1. Детектировать диалог ошибки

```python
import os
os.environ['DISPLAY'] = ':99'
from Xlib import display

d = display.Display()
root = d.screen().root

error_windows = []
for win in root.query_tree().children:
    name = win.get_wm_name()
    wm_class = win.get_wm_class()
    if wm_class and '1cv8' in wm_class:
        if name and any(kw in name for kw in ['Ошибка', 'Предупреждение', 'Error']):
            error_windows.append({'id': win.id, 'name': name})

print(error_windows)
```

- Пустой + есть окна 1С → база работает нормально
- Пустой + нет окон → база завершилась
- Не пустой → диалог ошибки → шаг 2

### 2. Закрыть диалог и завершить базу

Сначала проверь, есть ли в VA MCP инструмент для закрытия/подтверждения нужного окна. Если используешь X11-клавиши как fallback/action, зафиксируй причину. Последовательность: Enter (закрыть диалог) → Escape (закрытие) → Enter (подтвердить). После — ждать 2–3 сек и проверить через шаг 1.

```python
import os, time
os.environ['DISPLAY'] = ':99'
from Xlib import display, X
from Xlib.ext.xtest import fake_input

def send_key(d, keycode, delay=0.3):
    fake_input(d, X.KeyPress, keycode)
    d.flush()
    time.sleep(delay)
    fake_input(d, X.KeyRelease, keycode)
    d.flush()
    time.sleep(delay)

d = display.Display()
ENTER  = d.keysym_to_keycode(0xFF0D)
ESCAPE = d.keysym_to_keycode(0xFF1B)

send_key(d, ENTER)
time.sleep(1)
send_key(d, ESCAPE)
time.sleep(1)
send_key(d, ENTER)
```

### 3. Скриншот для лога (обязательно через VA MCP, перед шагом 2)

Скриншот для 1C UI получай по `va-visual-check`: VA MCP PNG, Linux/Xvfb рецепт и fallback-правила.

```json
{"name":"get_window_screenshot_os","arguments":{"window_title":"<title-from-get_window_list_os>","file_name":"<path>.png","color_mode":"color"}}
```

## Пайплайн: тесты завершились, база не закрылась

```
search_event_log(from=test_start_time, limit=20)
  ├── есть события, нет Error → ждать
  ├── есть Error → VA MCP-скриншот → закрыть при отсутствующей VA capability → анализ ЖР
  └── нет событий → детектировать окна
        ├── окно с ошибкой → VA MCP-скриншот → закрыть при отсутствующей VA capability
        └── нет окон → база не запустилась
```

## Безопасность

- **Только Xvfb** — не применять на продуктивных серверах с реальным дисплеем
- **Только навигационные клавиши** (Enter/Escape) — не вводить данные в поля
- **VA MCP-скриншоты — в /tmp/** — могут содержать персональные данные

## Типичные ошибки

| Ошибка | Обходной путь |
|--------|---------------|
| `DISPLAY` не установлен | `os.environ['DISPLAY'] = ':99'` до импортов |
| `python-xlib` не установлен | `pip install python-xlib` |
| Окна не найдены, но процесс есть | GUI ещё не отрисован — ждать 2–3 сек |
| VA MCP-скриншот Xvfb чёрный/одноцветный | Действовать по `va-visual-check`: Linux/Xvfb-рецепт, повтор VA-снимка, затем fallback при необходимости |
| XTEST недоступна | Xvfb с флагом `-extensions XTEST` |

## Capabilities

| Capability | Назначение |
|------------|------------|
| `python-xlib` | Чтение метаданных окон, симуляция ввода |
| `get_window_screenshot_os` | VA MCP-скриншот окна тест-клиента |

---
depends_on: []
---
