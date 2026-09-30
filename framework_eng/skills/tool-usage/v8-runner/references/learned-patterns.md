# Learned Patterns — v8-runner

## UI MCP via the platform test client requires two client roles

```
status: candidate
класс: Mixing the controlling MCP client and the test application during 1С UI automation
приём: For client MCP-tools, start the controlling 1С client with WS binding and /TESTMANAGER, separately start the test application with /TESTCLIENT -TPort and the same /N /P, then connect to it through test_client_start and verify connected=true
антиприём: Do not start the controlling client without /TESTMANAGER and do not treat a /TESTCLIENT process as suitable if it started without credentials or got stuck at the infobase login
почему: Without /TESTMANAGER the platform testing types are unavailable, and /TESTCLIENT without a correct infobase login is not considered a suitable client; proxied MCP calls hang or return connection errors
шаги: session_list -> live kind=1c-client -> infobase_info -> запуск /TESTCLIENT с /N /P -> test_client_start(port) -> open_form/click/get_value с session_id
источник: UI MCP run of a 1С form through session-manager: first errors in client mode and /TESTCLIENT connection, then a successful /TESTMANAGER + /TESTCLIENT chain with credentials
```

## Common launch helpers require a matrix of entry-point tests

```
status: candidate
класс: Changing a shared launch-helper without locking down all consuming commands
приём: When extending a helper that builds launch keys or payloads for multiple commands, immediately find all call sites and add or update tests for each entry point
антиприём: Do not cover only the command that prompted the change if the actual helper is used by other launch modes
почему: A new key or overlay becomes part of the contract for all helper consumers; without tests, a regression or unexpected change in behavior of another command will go unnoticed
шаги: rg over helper/import -> list of consuming commands -> separate CLI/unit checks for the new contract and the absence of duplicates for each consumer
источник: Refinement of `launch mcp va`: `/DisableUnsafeActionProtection` was added through the shared `vanessa_enterprise_launch_keys`, and after review the `test va` contract also had to be locked down
```
