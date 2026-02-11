# Capability Index (справочник для людей)

> Этот файл — **справочник для разработчиков фреймворка**, не для агентов.
> Агенты обнаруживают инструменты динамически через MCP (`tools/list`).
> Подробности использования — в навыках `tool-usage/*.md`.

## Capability → MCP-сервер → Навык

| Capability | MCP-сервер | Tool(s) | Навык |
|------------|------------|---------|-------|
| `navigate_symbol` | mcp-bsl-lsp-bridge | `symbol_explore`, `definition`, `hover` | code-navigation |
| `get_call_graph` | mcp-bsl-lsp-bridge | `call_hierarchy`, `call_graph` | code-navigation |
| `rename_symbol` | mcp-bsl-lsp-bridge | `prepare_rename`, `rename` | code-navigation |
| `get_diagnostics` | mcp-bsl-lsp-bridge | `document_diagnostics` | code-navigation, syntax-checking |
| `get_code_actions` | mcp-bsl-lsp-bridge | `code_actions` | code-navigation |
| `search_ssl_functions` | mcp-bsl-lsp-bridge | `symbol_explore` | search-before-write |
| `search_metadata` | RooLee10/1c-mcp-tools | `list_metadata_objects`, `get_metadata_structure` | metadata-discovery |
| `execute_query` | RooLee10/1c-mcp-tools | `execute_query` | metadata-discovery |
| `validate_query` | RooLee10/1c-mcp-tools | `validate_query` | metadata-discovery |
| `resolve_nav_link` | RooLee10/1c-mcp-tools | `parse_nav_link`, `get_nav_link` | metadata-discovery |
| `check_syntax` | alkoleft/mcp-onec-test-runner | `check_syntax_edt`, `check_syntax_designer_*` | syntax-checking |
| `run_tests` | alkoleft/mcp-onec-test-runner | `run_all_tests`, `run_module_tests` | test-execution |
| `build_project` | alkoleft/mcp-onec-test-runner | `build_project` | test-execution |
| `dump_config` | alkoleft/mcp-onec-test-runner | `dump_config` | metadata-discovery |
| `launch_app` | alkoleft/mcp-onec-test-runner | `launch_app` | — |
| `search_syntax_reference` | alkoleft/mcp-bsl-platform-context | `search` | search-before-write |
| `get_type_info` | alkoleft/mcp-bsl-platform-context | `info`, `getMembers`, `getConstructors` | search-before-write |
| `ask_ai_assistant` | SteelMorgan/spring-mcp-1c-copilot | `ask_1c_ai` | search-before-write |
| `check_code_quality` | SteelMorgan/spring-mcp-1c-copilot | `check_1c_code` | syntax-checking |
| `search_event_log` | SteelMorgan/1c-log-checker | `logc_get_event_log` | log-analysis |
| `search_tech_log` | SteelMorgan/1c-log-checker | `logc_get_tech_log` | log-analysis |
| `configure_tech_log` | SteelMorgan/1c-log-checker | `logc_configure_techlog`, `logc_save_techlog`, ... | log-analysis |

## MCP-серверы (полный список)

| Сервер | Описание |
|--------|----------|
| `mcp-bsl-lsp-bridge` | LSP-мост к BSL Language Server (навигация, диагностика, рефакторинг) |
| `RooLee10/1c-mcp-tools` | Метаданные конфигурации, запросы, навигационные ссылки |
| `alkoleft/mcp-onec-test-runner` | Синтаксис, тесты, сборка, выгрузка конфигурации |
| `alkoleft/mcp-bsl-platform-context` | Справка синтаксиса платформы 1С (типы, методы, свойства) |
| `SteelMorgan/spring-mcp-1c-copilot` | AI-ассистент 1С:Напарник (вопросы, объяснение синтаксиса, проверка кода) |
| `SteelMorgan/1c-log-checker` | Журнал регистрации и технологический журнал |
| `vladimir-kharin/1c-batch` | Утилита пакетных операций (альтернатива test-runner) |
