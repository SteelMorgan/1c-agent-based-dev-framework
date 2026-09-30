# Установка MCP `1c-form-viewer` (BslEdit) — только просмотр

Визуальный просмотр управляемых форм, макетов и СКД для агента: [framework/skills/tool-usage/platform-data/xml-generation/references/form-viewer-mcp.md](../../framework/skills/tool-usage/platform-data/xml-generation/references/form-viewer-mcp.md). Лицензия и происхождение: [framework/docs/third-party-tools.md](../../framework/docs/third-party-tools.md).

## Требования

- Node.js 20+ и npm.
- Системный Chrome или Chromium (скачивать браузер через playwright не нужно). Работает headless, `DISPLAY` не нужен.
- git в `PATH` (для сравнения версий через `base_revision`).

## Сборка (на зафиксированном коммите)

```bash
TOOLS=<каталог инструментов>          # например /workspaces/work/tools
git clone https://github.com/alonehobo/BslEdit "$TOOLS/bsledit"
cd "$TOOLS/bsledit" && git checkout d24f9089dd1d483c7562ffdf9337079ed792073c
npm install
cd packages/1c-form-viewer && npm run build:assets && npm run build:node
# результат: packages/1c-form-viewer/dist/mcp-server.js
```

Коммит обновлять осознанно: перепроверить лицензию и обновить его в `framework/docs/third-party-tools.md`.

## Обёртка запуска

```bash
#!/usr/bin/env bash
# bsledit-mcp.sh [<корень проекта>] — только просмотр
set -euo pipefail
ROOT="${1:?корень проекта}"
export ONE_C_FORM_VIEWER_CHROMIUM="${ONE_C_FORM_VIEWER_CHROMIUM:-/opt/google/chrome/chrome}"
exec node "$TOOLS/bsledit/packages/1c-form-viewer/dist/mcp-server.js" \
  --stdio --root "$ROOT" --no-form-edit-tools --no-template-edit-tools
```

Флаги `--no-form-edit-tools --no-template-edit-tools` **обязательны**. `--allow-any-path` не использовать.

## Подключение

Claude Code (`.mcp.json` проекта):

```json
"1c-form-viewer": {
  "type": "stdio",
  "command": "<TOOLS>/bsledit-mcp.sh",
  "args": ["<корень проекта>"],
  "env": { "ONE_C_FORM_VIEWER_CHROMIUM": "/opt/google/chrome/chrome" }
}
```

Флагами сервера не отключается `convert_xlsx_to_template`, который пишет файл. Его нужно запретить в настройках Claude Code: `"permissions": { "deny": ["mcp__1c-form-viewer__convert_xlsx_to_template"] }`.

Codex (`~/.codex/config.toml`):

```toml
[mcp_servers.1c-form-viewer]
command = "<TOOLS>/bsledit-mcp.sh"
args = ["<корень проекта>"]
env = { ONE_C_FORM_VIEWER_CHROMIUM = "/opt/google/chrome/chrome" }
startup_timeout_sec = 20
tool_timeout_sec = 120
disabled_tools = ["convert_xlsx_to_template"]
```

## Проверка

1. `tools/list` — нет `edit_form` и `edit_template`. Ожидаемый набор: `open_preview`, `preview`, `capture_preview`, `list_form_elements`, `validate_form`, `list_markup`, `validate_template` и `convert_xlsx_to_template`, который запрещён на стороне клиента.
2. `open_preview` (абсолютный путь к любому `Ext/Form.xml` внутри корня, `audience="agent"`), затем `capture_preview` — PNG не пустой.
3. `validate_form` на той же форме возвращает JSON с `ok` и `summary`.
