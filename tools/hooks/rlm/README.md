# RLM hooks для Claude Code

Хуки, реализующие ритуалы `rlm-workflow` (см. `framework/rules/rlm-workflow.md`) для harness Claude Code. Без них правило остаётся «бумажным» — модель не получает триггеры на саммаризацию и не накапливает autocapture-буфер.

## Состав

| Файл | Hook event | Что делает |
|---|---|---|
| `auto-capture.sh` | `PostToolUse` | После каждого `Edit`/`Write`/значимого `Bash` молча пишет запись в `~/.claude/autocapture-buffer.jsonl`. Буфер потом обрабатывается ритуалом «summarize» (шаг 1) и очищается. |
| `context-monitor.sh` | `PostToolUse` | Читает текущий процент контекста из `/tmp/claude-ctx-state.json` (наполняется statusline-командой). На 70% — предупреждение; на 80% или >=300k токенов — инжектит CRIT-инструкцию «вызвать summarize», которая запускает шаги ритуала из `rlm-workflow.md`. |
| `pre-compact.sh` | `PreCompact` | Срабатывает перед авто-компактом. Stdout инжектится в контекст как system-message — содержит явное напоминание сохранить состояние в RLM перед тем, как компакт затрёт историю. |

## Требования

- `bash`, `jq` — иначе `auto-capture.sh` молча выходит без записи.
- MCP-сервер `rlm-toolkit` подключён в harness (иначе вызывать `rlm_*` будет некому).
- `statusLine`-команда, которая поддерживает файл `/tmp/claude-ctx-state.json` с полем `context_percent` (без этого `context-monitor.sh` не сможет считать порог). В DSSL DRIVE — `statusline-command.sh` в `~/.claude/`.

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

3. Перезапустить Claude Code (или начать новую сессию). Проверка: после нескольких `Edit`-операций должен появиться `~/.claude/autocapture-buffer.jsonl`.

## Что НЕ входит

- Сам MCP-сервер `rlm-toolkit` — он подключается отдельно через `.mcp.json` / `claude_desktop_config.json`.
- Statusline-команда — её источник проектный, в DRIVE devcontainer лежит на стороне окружения.
- Файл состояния `/tmp/claude-ctx-state.json` — пишется statusline'ом, читается `context-monitor.sh`.

## Связанная документация

- `framework/rules/rlm-workflow.md` — ритуалы «context» / «summarize» / «new task», уровни H-MEM (L0..L3), inline-writes, антипаттерны.
- MCP-tools: `mcp__rlm-toolkit__*` (см. tools/list харнеса при подключенном MCP-сервере).

## Обновление

Хуки развиваются вместе с правилом `rlm-workflow.md`. При изменении логики:

1. Поправить скрипт в этом каталоге.
2. Если меняется контракт с правилом — обновить `framework/rules/rlm-workflow.md` (и `framework_eng/rules/...`).
3. Пользователю — заново скопировать в `~/.claude/hooks/` (или подключить symlink'ом).
