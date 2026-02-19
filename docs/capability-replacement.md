# Замена MCP tool / сервера

Фреймворк использует **capability** как абстракцию над MCP tool. Маппинг хранится в `framework/capabilities/registry.yaml`.

## Browser automation

По умолчанию используется **Playwright MCP** (Microsoft): `npx @playwright/mcp@latest`. IDE-агностичен, работает в Cursor, Claude, Windsurf и др. Альтернативы: browser-use (Python), cursor-ide-browser (только Cursor).

## Когда менять registry

- Хочешь использовать другой MCP-сервер для той же функциональности
- Хочешь заменить один tool на другой (другой сервер или другое имя tool)
- Добавляешь новую capability

## CLI

```bash
# Показать текущий registry
python tools/capability-registry.py list

# Заменить одну capability
python tools/capability-registry.py set navigate_symbol --server new-lsp --tool navigate_symbol

# Заменить весь сервер (все capabilities этого сервера переезжают на новый)
python tools/capability-registry.py set-server lsp-bridge --new-server new-lsp

# Заменить сервер с изменением имён tool (JSON: capability -> новый tool)
python tools/capability-registry.py set-server lsp-bridge --new-server new-lsp \
  --tool-map '{"navigate_symbol":"goto_definition","get_call_graph":"call_graph"}'
```

## Ручное редактирование

Открой `framework/capabilities/registry.yaml` и измени строки:

```yaml
# Было
navigate_symbol: { server: lsp-bridge, tool: navigate_symbol }

# Стало (новый сервер, тот же tool)
navigate_symbol: { server: new-lsp, tool: navigate_symbol }

# Или (новый сервер, другое имя tool)
navigate_symbol: { server: new-lsp, tool: goto_definition }
```

## Что не нужно менять

- **Навыки** (`framework/skills/tool-usage/`) — они ссылаются на capability по имени. Если имя capability не меняется, навыки не трогаем.
- **Правила** (`mandatory-tools.md` и др.) — то же самое.

## Если меняется имя capability

Если переименовываешь capability (например, `navigate_symbol` → `lsp_definition`):

1. Добавь новую строку в registry с новым именем
2. Удали старую (или оставь как alias, если нужно)
3. Обнови навыки и правила — замени старое имя на новое (grep по `framework/skills/` и `framework/rules/`)
