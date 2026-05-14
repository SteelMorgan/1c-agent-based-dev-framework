# RLM hooks для Claude Code

Хуки, реализующие ритуалы `rlm-workflow` (см. `framework/rules/rlm-workflow.md`) для harness Claude Code. Без них правило остаётся «бумажным» — модель не получает триггеры на саммаризацию и не накапливает autocapture-буфер.

## Состав

| Файл | Hook event | Что делает |
|---|---|---|
| `auto-capture.sh` | `PostToolUse` | После каждого `Edit`/`Write`/значимого `Bash` молча пишет запись в `~/.claude/autocapture-buffer.jsonl`. Буфер потом обрабатывается ритуалом «summarize» (шаг 1) и очищается. |
| `context-monitor.sh` | `PostToolUse` | Читает текущий процент контекста из `/tmp/claude-ctx-state.json` (наполняется statusline-командой — см. ниже). На 70% — предупреждение; на 80% или >=300k токенов — инжектит CRIT-инструкцию «вызвать summarize», которая запускает шаги ритуала из `rlm-workflow.md`. |
| `pre-compact.sh` | `PreCompact` | Срабатывает перед авто-компактом. Stdout инжектится в контекст как system-message — содержит явное напоминание сохранить состояние в RLM перед тем, как компакт затрёт историю. |
| `statusline-snippet.sh` | `statusLine.command` | Источник для `/tmp/claude-ctx-state.json` — без этого файла `context-monitor.sh` не получит данных и пороги 70%/80%/300k никогда не сработают. Это **обязательная** часть инфраструктуры, не опция. |

## Требования

- `bash`, `jq` — иначе `auto-capture.sh` молча выходит без записи.
- MCP-сервер `rlm-toolkit` подключён в harness (`claude mcp add rlm-toolkit -s user --type http --url http://rlm:8200/mcp` или аналог), иначе вызывать `rlm_*` будет некому.
- `statusLine`-команда, которая пишет `/tmp/claude-ctx-state.json` — см. `statusline-snippet.sh` и раздел «Statusline contract» ниже.

## Установка

1. Скопировать каталог в окружение пользователя:

   ```bash
   mkdir -p ~/.claude/hooks
   cp tools/hooks/rlm/*.sh ~/.claude/hooks/
   chmod +x ~/.claude/hooks/*.sh
   ```

2. Прописать события в `~/.claude/settings.json` (или `~/.claude/settings.local.json`):

   ```json
   {
     "hooks": {
       "PreCompact": [
         {
           "matcher": "",
           "hooks": [
             { "type": "command", "command": "bash \"/home/<user>/.claude/hooks/pre-compact.sh\"" }
           ]
         }
       ],
       "PostToolUse": [
         {
           "matcher": "",
           "hooks": [
             { "type": "command", "command": "bash \"/home/<user>/.claude/hooks/auto-capture.sh\"" },
             { "type": "command", "command": "bash \"/home/<user>/.claude/hooks/context-monitor.sh\"" }
           ]
         }
       ]
     }
   }
   ```

   Порядок в `PostToolUse` имеет значение: сначала `auto-capture` (быстрый, тихий), затем `context-monitor` (может инжектить системное сообщение).

3. Подключить statusline-блок — см. следующий раздел.

4. Перезапустить Claude Code (или начать новую сессию). Проверка:
   - после нескольких `Edit`-операций появляется `~/.claude/autocapture-buffer.jsonl`;
   - после первого statusline-тика появляется `/tmp/claude-ctx-state.json` с тремя ключами `pct`/`tokens`/`limit`.

## Statusline contract

`context-monitor.sh` сам по себе слепой — он только читает файл-состояние. Источник этого файла — statusline-команда Claude Code, которая исполняется на каждом тике обновления статус-бара.

**Путь и схема (фиксированы для хука):**

```
file:   ${TMPDIR:-/tmp}/claude-ctx-state.json
schema: {"pct":<int>, "tokens":<int>, "limit":<int>}
```

**Источник чисел в payload Claude Code 2.x:**

| Поле в файле | Откуда брать из `$input` |
|---|---|
| `pct` | `.context_window.used_percentage` (округлить до целого) |
| `tokens` | сумма `.context_window.current_usage.input_tokens` + `.cache_creation_input_tokens` + `.cache_read_input_tokens` |
| `limit` | `.context_window.context_window_size` (или 200000 fallback) |

**Критично**: брать сумму трёх счётчиков, а не только `input_tokens`. Cache_creation и cache_read тоже занимают окно; если игнорировать их, цифра недооценивается в 2–10 раз и пороги хука не срабатывают.

**Готовый блок-врезка**: `statusline-snippet.sh`. Вставить в существующий `~/.claude/statusline-command.sh` после `input=$(cat)`, между метками `>>> RLM context-state block — start` и `<<< RLM context-state block — end`. Блок не пишет stdout — только обновляет JSON-файл.

Если своего statusline нет — `statusline-snippet.sh` работает как минимальный stub целиком: прописать `statusLine.command` в `~/.claude/settings.json`:

```json
{
  "statusLine": {
    "type": "command",
    "command": "bash \"/home/<user>/.claude/statusline-command.sh\""
  }
}
```

## Что НЕ входит

- Сам MCP-сервер `rlm-toolkit` — поднимается отдельно (docker-группа `rlm-toolkit_default`: контейнеры `rlm-toolkit-rlm` + `rlm-toolkit-tei`), подключается через `claude mcp add` или `.mcp.json`.
- Полноценная statusline-команда с git-веткой, цветами, рендером prompt'а — это вкусовая часть окружения. В каталоге лежит только минимальный `statusline-snippet.sh`, содержащий обязательный для RLM блок.

## Связанная документация

- `framework/rules/rlm-workflow.md` — ритуалы «context» / «summarize» / «new task», уровни H-MEM (L0..L3), inline-writes, антипаттерны.
- MCP-tools: `mcp__rlm-toolkit__*` (см. tools/list харнеса при подключенном MCP-сервере).

## Обновление

Хуки развиваются вместе с правилом `rlm-workflow.md`. При изменении логики:

1. Поправить скрипт в этом каталоге.
2. Если меняется контракт с правилом — обновить `framework/rules/rlm-workflow.md` (и `framework_eng/rules/...`).
3. Пользователю — заново скопировать в `~/.claude/hooks/` (или подключить symlink'ом).
