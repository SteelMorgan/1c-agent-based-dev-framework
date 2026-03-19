---
name: gui-control
description: Управление GUI 1С через X11. Навык учит агента обнаруживать окна 1С (включая диалоги ошибок), делать скриншоты и симулировать ввод (Enter, Escape) для управления интерфейсом без участия человека.
---

# Управление GUI 1С через X11

X11-управление — action, не диагностика. Использовать только когда детектирован GUI-диалог, блокирующий нормальное завершение базы. Диагностику причин — через ЖР (`event-log-analysis`).

Для `Предупреждение безопасности` метаданные X11-окон могут быть неполными. Ориентируйся на связку: ЖР → скриншот → действия с клавиатурой.

## Когда применять

| Триггер | Действие |
|---------|----------|
| В ЖР нет событий после `test_start_time` | Проверить — не завис ли GUI-диалог |
| Заголовок окна: «Ошибка» / «Предупреждение» | Скриншот → закрыть диалог → анализ ЖР |
| База не завершается после тестов | Закрыть через Escape + Enter |
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

Последовательность: Enter (закрыть диалог) → Escape (закрытие) → Enter (подтвердить). После — ждать 2–3 сек и проверить через шаг 1.

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

### 3. Скриншот для лога (опционально, перед шагом 2)

```python
import os
os.environ['DISPLAY'] = ':99'
from PIL import ImageGrab
from Xlib import display

d = display.Display()
root = d.screen().root

for win in root.query_tree().children:
    name = win.get_wm_name()
    wm_class = win.get_wm_class()
    if wm_class and '1cv8' in wm_class:
        geom = win.get_geometry()
        img = ImageGrab.grab(bbox=(geom.x, geom.y, geom.x + geom.width, geom.y + geom.height))
        path = f'/tmp/onec_{win.id}.png'
        img.save(path)
        print(f'Скриншот сохранён: {path}')
```

## Пайплайн: тесты завершились, база не закрылась

```
search_event_log(from=test_start_time, limit=20)
  ├── есть события, нет Error → ждать
  ├── есть Error → скриншот → закрыть → анализ ЖР
  └── нет событий → детектировать окна
        ├── окно с ошибкой → скриншот → закрыть
        └── нет окон → база не запустилась
```

## Безопасность

- **Только Xvfb** — не применять на продуктивных серверах с реальным дисплеем
- **Только навигационные клавиши** (Enter/Escape) — не вводить данные в поля
- **Скриншоты — в /tmp/** — могут содержать персональные данные

## Типичные ошибки

| Ошибка | Обходной путь |
|--------|---------------|
| `DISPLAY` не установлен | `os.environ['DISPLAY'] = ':99'` до импортов |
| `python-xlib` не установлен | `pip install python-xlib` |
| `PIL.ImageGrab` не работает | `pip install Pillow` |
| Окна не найдены, но процесс есть | GUI ещё не отрисован — ждать 2–3 сек |
| XTEST недоступна | Xvfb с флагом `-extensions XTEST` |

## Capabilities

| Capability | Назначение |
|------------|------------|
| `python-xlib` | Чтение метаданных окон, симуляция ввода |
| `PIL ImageGrab` | Скриншот фреймбуфера или окна |

---
depends_on: []
---
