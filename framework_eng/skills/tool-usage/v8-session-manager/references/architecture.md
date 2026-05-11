# Layer Architecture

An AI agent request to business logic inside 1C passes through five layers. The manager skill concerns layer L3; the others are points where the agent may end up during debugging or when developing a new tool.

## Layers

| Layer | Name | Where the code lives | Responsibility |
|---|---|---|---|
| L0 | External transport component add-in (`session_y8`) | C++/Rust addin for 1C, loaded into the client | TCP/WS socket outward: one 1C process ↔ one WS channel. Startup, reconnection, low-level frame |
| L1 | DevKit BSL (add-in ↔ BSL adapter) | common module/extension in the project | Wrapper around `session_y8`: frame read/write, events, JSON-RPC serialization |
| L2 | `client_mcp` (BSL) | extension `exts/client_mcp/` | JSON-RPC implementation on top of devkit: `session.register`, `tools/publish`, handling `tools/call`, lifecycle. Does not contain domain tools |
| L2.5 | Application extensions (`test_client`, `VAExtension`, `yaxunit_runner`, …) | `exts/<extension>/` | Register MCP tools through `client_mcp`. This is where business logic lives - form descriptions, test execution, etc. |
| L3 | `v8-session-manager` (this product) | `src/` Rust binary | WS server on `:4000/sessions` for clients; HTTP MCP on `:4001/mcp` for the AI agent; session registry, FIFO, soft reconnect, deduplication of proxied tools |
| L4 | AI agent | Claude Code, IDE plugin, … | Calls `tools/list`/`tools/call` through MCP HTTP, builds scenarios |

## Flow of the Call

```
AI-агент (L4)
  └─ POST /mcp (tools/call <kind>__<tool>, args+session_id)
       ↓
     v8-session-manager (L3)
       └─ resolve по (kind, tool_name) → session_id (из аргументов или единственный кандидат)
       └─ enqueue в FIFO нужной сессии
            ↓
          WS :4000/sessions ──► 1С-клиент
                                  └─ client_mcp BSL (L2) принимает JSON-RPC
                                       └─ диспетчер расширения (L2.5) выполняет tool
                                       └─ результат ──► обратно тем же путём
```

## What Lives Where

- **Rust** (this repo): transport + registry + MCP server. Tested separately, versioned separately.
- **BSL** (1C project repo): `client_mcp` + application extensions. This is where the developer works when adding new tools.
- **Transport** (`session_y8`): a separate add-in, versioned separately. On the 1C side, loaded through `client_mcp`.

## What Changes in Which Repository

| Change | Repo | Skill |
|---|---|---|
| New MCP tool in `test_client` / `VAExtension` / etc. | 1C project | this skill (`extending-tools.md`) + the skill of the corresponding extension |
| Add a parameter to `v8project.yaml`, bring up the manager | 1C project (config) | this skill (`bootstrap.md`) |
| Start the 1C client connected to the manager | 1C project | `v8-runner` skill (options `--mcp-transport=ws --manager-url=...`) |
| Change `session_y8` transport, manager registry, protocol | manager/add-in upstream repo | out of scope: agree on a separate task |
