# Watch-режим консилиума (CONS-05, вариант A)

Наблюдение за работающим консилиумом в реальном времени. Решение владельца —
вариант A: исполнение остаётся полностью headless (команды convene/round/… как
раньше), watch — отдельный read-only слой поверх файлов сессии.

## Границы слоя

- **Read-only**: watch только читает файлы; записей в состояние сессии нет
  (session.json/transcript/progress не изменяются никогда, в т.ч. при Ctrl+C).
- **Без сети и адаптеров**: никаких сетевых вызовов к моделям и subprocess'ов
  адаптеров; liveness читается напрямую из `runtime.json` sandbox'а.
- **Не гейт и не вход для ходов**: watch не участвует в протоколе, не влияет на
  state machine, стоп-условия и кворум; человеческие ходы через него не подаются.

## Что читает watch

| Источник | Что даёт |
| --- | --- |
| `.consilium-sessions/<id>/session.json` | фаза/раунд/волна, состав, state/invocations/retries, verdict_done, cleanup |
| `.consilium-sessions/<id>/transcript.jsonl` | ходы участников (excerpt в live-view, поток для --participant) |
| `.consilium-sessions/<id>/progress.jsonl` | чекпоинты границ фаз/раундов |
| `.consilium-sessions/<id>/anon_map.json` | anon_id участников фазы B |
| `.review-sandboxes/<review_id>/runtime.json` | живой прогресс инвокации: state/phase/elapsed_sec, счётчики `raw_events`/`tool_calls_total`/`last_event_type`, last_activity_at/last_heartbeat_at → liveness |

Во время волны файлы сессии пишутся только после блокирующего `run_wave`, поэтому
строка «адаптер: state=… events=… tools=…» из runtime.json — единственный живой
сигнал прогресса. Liveness-класс печатается только при `runtime.state=running`
(heartbeat тикает лишь во время инвокации); в паузе между волнами показывается
«инвокация не идёт» + возраст последней активности — голый `dead_watcher` в
исправной паузе не выводится.

Терминальное состояние определяется по первому из признаков: каталог сессии
удалён (close без --keep) → `cleanup.session_closed` → `verdict_done` →
последний чекпоинт ∈ {`verdict_ready`, `closed`}.

## Команды

```bash
SKILL={{runtime-ref:framework/skills/tool-usage/review/agent-consilium}}

# единый live-view по всем участникам (кадр перерисовывается каждые 3 с)
python3 $SKILL/scripts/consilium.py watch <session_id> [--interval 5] [--excerpt-lines 8]

# поток одного участника (отдельная панель)
python3 $SKILL/scripts/consilium.py watch <session_id> --participant <participant_id>

# один кадр/снимок без цикла (диагностика)
python3 $SKILL/scripts/consilium.py watch <session_id> --once
```

Реальные id и excerpt'ы ходов показываются намеренно: зритель watch — модератор/
владелец, анонимизация нужна только в выдаче участникам (bundles).

## Раскладка панелей herdr (opt-in, только по явному запросу)

Раскладка панелей НЕ создаётся по умолчанию и не является шагом протокола консилиума
(решение владельца 2026-08-04). Базовый способ наблюдения — `watch` в текущей панели;
helper запускается только когда пользователь явно просит многопанельное наблюдение.

`scripts/consilium-watch-herdr.sh <session_id> [--interval N]` — helper для
интерактивного наблюдения в herdr:

- проверяет `HERDR_ENV=1`, наличие `herdr` в PATH и каталог сессии;
- читает состав из `session.json`;
- создаёт широкую панель справа — общий live-view (`watch` без `--participant`);
- на каждого участника — панель вниз с потоком `watch --participant <pid>`
  (запуск через `python3 -u` — построчная буферизация stdout, иначе
  tail-семантика ломается в не-tty панели);
- потолок раскладки — 8 панелей: при большем составе усечение с явным
  предупреждением (остальные видны в панели общего статуса); отказ `pane split`
  не роняет скрипт на середине — оператор получает сообщение о неполной раскладке;
- фокус пользователя не меняется, чужие панели не закрываются; каждая панель
  завершается сама при терминальном состоянии сессии.

Запуск — из корня репозитория внутри herdr-панели:

```bash
$SKILL/scripts/consilium-watch-herdr.sh <session_id>
```
