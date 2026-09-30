---
name: resource-spike-watch
description: "Используй, когда нужно зафиксировать CPU или memory spike внутри agent/dev контейнера с владельцами процессов и ограниченными хвостами логов."
---

# Resource Spike Watch

## Назначение

Используй этот навык, когда нужно понять, какой процесс внутри контейнера съел CPU или память, особенно если IDE/Cursor/терминал зависли уже после пика.

Инструмент фреймворка:

`/workspaces/work/repos/1C Framework/1c-agent-based-dev-framework/tools/resource-spike-watch/resource-spike-watch.py`

Он работает внутри контейнера и читает:

- `/sys/fs/cgroup/cpu.stat` для суммарной CPU-нагрузки контейнера;
- `/sys/fs/cgroup/memory.current`, `memory.events` для памяти/OOM;
- `/proc/*` для PID, команд, CPU-дельт и RSS;
- ограниченные хвосты диагностических логов.

## Запуск

Базовый запуск в текущем контейнере:

```bash
nohup python3 "/workspaces/work/repos/1C Framework/1c-agent-based-dev-framework/tools/resource-spike-watch/resource-spike-watch.py" \
  --output-dir /tmp/resource-spike-captures \
  >/tmp/resource-spike-watch.log 2>&1 &
```

Порог по умолчанию:

- CPU: больше `4.0` ядер за интервал сэмплирования;
- память: больше `6G`;
- рост памяти: больше `768M` за `10` секунд;
- cooldown между полными снимками: `30` секунд;
- хранится до `50` директорий снимков.

Для контейнера с другим лимитом памяти явно укажи пороги:

```bash
python3 ".../resource-spike-watch.py" --cpu-cores 4 --mem-high 6g --mem-growth 768m
```

## Где искать результат

По умолчанию снимки пишутся в:

`/tmp/resource-spike-captures/`

Лог самого монитора, если запускали через `nohup` как выше:

`/tmp/resource-spike-watch.log`

Каждый сработавший триггер создает директорию вида:

`/tmp/resource-spike-captures/2026-07-07T09-31-12Z-cpu-4.82-cores/`

Внутри:

- `summary.txt` - причина срабатывания, CPU cores, memory.current, пороги;
- `proc-cpu-delta.tsv` - процессы по CPU за последний интервал, это главный файл для CPU spike;
- `proc-memory.tsv` - процессы по RSS;
- `recent-samples.csv` - кольцевой буфер последних легких сэмплов;
- `cgroup.txt` - `cpu.stat`, `memory.current`, `memory.events`, `memory.peak`;
- `pressure.txt` - `/proc/pressure/{cpu,memory,io}`;
- `recent-files.txt` - свежие файлы в `/tmp` и `/var/log/onec`;
- `logs/` - ограниченные хвосты Cursor, Vanessa/MCP, Xvfb/x11vnc и v8sm логов.

## Как читать

1. Начни с `summary.txt`.
2. Для CPU смотри `proc-cpu-delta.tsv`, а не `ps-top.txt`: он показывает дельту CPU за интервал, а не среднее с момента старта процесса.
3. Для памяти смотри `proc-memory.tsv` и `cgroup.txt`.
4. Если был OOM, проверь рост счетчиков `oom` / `oom_kill` в `cgroup.txt`.
5. Для связи с 1C/VA/MCP смотри `logs/manifest.txt`, затем хвосты `/tmp/web-transport.log`, `/tmp/va-mcp-*.log`, `/tmp/mcp-client.log`.

## Ограничения

- Если процесс мгновенно съел память и умер между сэмплами, его `/proc` может не попасть в снимок. Тогда используй `recent-samples.csv`, `memory.events` и хвосты логов.
- Инструмент не включает техжурнал 1С и не меняет конфигурацию базы.
- Хвосты логов ограничены по размеру, чтобы сам монитор не создавал значимую нагрузку.
