# Learned Patterns — v8-runner

## UI MCP via the platform test client requires two client roles

```
status: candidate
класс: Смешение управляющего MCP-клиента и тестируемого приложения при UI-автоматизации 1С
приём: Для клиентских MCP-tools запускать управляющий 1С-клиент с WS-сопряжением и /TESTMANAGER, отдельно запускать тестируемое приложение с /TESTCLIENT -TPort и теми же /N /P, затем подключать его через test_client_start и проверять connected=true
антиприём: Не запускать управляющий клиент без /TESTMANAGER и не считать процесс /TESTCLIENT подходящим, если он стартовал без учётных данных или завис на входе в ИБ
почему: Без /TESTMANAGER недоступны платформенные типы тестирования, а /TESTCLIENT без корректного входа в ИБ не считается подходящим клиентом; proxied MCP-вызовы зависают или возвращают ошибки подключения
шаги: session_list -> live kind=1c-client -> infobase_info -> запуск /TESTCLIENT с /N /P -> test_client_start(port) -> open_form/click/get_value с session_id
источник: UI MCP-прогон формы 1С через session-manager: сначала ошибки режима клиента и подключения /TESTCLIENT, затем успешная цепочка /TESTMANAGER + /TESTCLIENT с учётными данными
```
