# Конфиг и backends

Изучай `v8project.yaml` до диагностики поведения build, syntax, dump, test и launch.
Если рядом существует `v8project.local.yaml`, изучай и его — он переопределяет машинно-локальные
настройки до CLI-оверрайдов.

## Поля, которые проверяем в первую очередь

- `basePath`: корень исходников 1С; если не указан, по умолчанию — каталог, содержащий основной конфиг.
- `workPath`: расположение сгенерированного состояния, временных файлов и воркспейса.
- `format`: `DESIGNER` или `EDT`.
- `builder`: `DESIGNER` или `IBCMD`.
- `infobase.connection`: часто `File=build/ib` для локальной автоматизации.
- `source-set`: упорядоченные исходники конфигурации и расширений.
- `tools.platform.path` или `tools.platform.version`: подсказки для поиска платформы 1С.
- `tools.edt_cli.path`, `version` и `interactive-mode`: подсказки для поиска EDT CLI и режим выполнения.
- `tests.yaxunit` и `tests.va`: конфигурация запускалок тестов.
- `tools.client_mcp`, `tools.va` и `tools.enterprise`: подсказки для launch и интеграции клиентского MCP.
- `tools.client_mcp.extension`: опциональное tool-расширение, которое готовит `build`; это не source-set проекта.

## Правила формата и backend'а

- `format=DESIGNER`, `builder=DESIGNER`: поддерживает init, build, extensions, dump, синтаксические проверки Designer, тесты, сценарии make/load/artifact, если они настроены.
- `format=DESIGNER`, `builder=IBCMD`: поддерживает init, build, extensions, dump с ограниченным backend и только файловыми ИБ.
- `format=EDT`, `builder=DESIGNER`: поддерживает init, build через экспорт EDT в Designer-файлы, синтаксические проверки EDT, extensions и тесты.
- `format=EDT`, `builder=IBCMD`: поддерживает init и build через экспорт EDT в Designer-файлы с последующим IBCMD import/apply; требует файловую ИБ.
- `extensions` поддерживает Designer- и EDT-проекты, но действенны только записи `source-set` с расширениями.
- `syntax designer-config` и `syntax designer-modules` требуют формат Designer и backend Designer.
- `syntax edt` требует формат EDT с backend Designer.
- `dump --mode partial` с IBCMD деградирует до инкрементального dump, и об этом нужно сказать в пользовательских сводках.
- `convert` — только CLI, репо-aware, использует настроенные `source-set`, не использует `builder` и не требует ИБ.
- `load` поддерживает `.cf` и `.cfe` только для `format=DESIGNER`, `builder=DESIGNER`.
- `tools.client_mcp.extension.source` готовится во время `build`, пропускается, если ничего не изменилось, и обновляется через `build --full-rebuild`; `.artifact.path` должен указывать на `.cfe` и в текущей реализации требует `builder=DESIGNER`.
- `make` / `artifacts` требуют `builder=DESIGNER` и публикуют `.cf`, `.cfe`, `.epf` или `.erf` в зависимости от target/source-set.

## Заметки по source-set

`source-set.name` — стабильная идентичность для упорядочивания, диагностики, runtime-контекстов, сгенерированных каталогов и выбора команд.

Поддерживаемые значения `source-set.type`:

- `CONFIGURATION`
- `EXTENSION`
- `EXTERNAL_DATA_PROCESSORS`
- `EXTERNAL_REPORTS`

Предпочитай `--source-set <NAME>` для узких сценариев build, dump, convert и artifact, когда изменения пользователя ограничены одним настроенным source-set.

## Путь к конфигу

`v8project.yaml` — имя конфиг-файла по умолчанию. Используй `--config <path>` только когда активный конфиг проекта лежит не по дефолтному пути или пользователь явно просит такую форму команды.

`v8project.local.yaml` — это только автоматический локальный overlay. Он может переопределять только `workPath`,
`infobase.*`, `tools.*`, `tests.*` и `mcp.*`; он не должен задавать `source-set`, `format` или
`builder`, и его нельзя использовать как `--config`. `--workdir` имеет приоритет над обоими конфиг-файлами.

## Vanessa Automation в `v8project.yaml`

Конфигурация VA разделена на два уровня:

1. `v8project.yaml` / `v8project.local.yaml` указывает, какую внешнюю обработку VA запускать, какой JSON-шаблон параметров взять и какой профиль фич активен.
2. JSON из `tests.va.params_path` — это шаблон `VAParams`. В нём лежат настройки самой Vanessa Automation, включая таблицу профилей TestClient. `v8-runner` читает этот шаблон, создаёт runtime-копию в `workPath/temp/.../va-params.json`, накладывает выбранный профиль фич/теги/логи и передаёт runtime-копию в `/C` как `VAParams=<path>`. Не редактируй runtime-копию как источник правды.

Минимальный универсальный блок в `v8project.yaml`:

```yaml
tools:
  va:
    epf_path: '<path-to-vanessa-automation.epf>'

tests:
  va:
    params_path: '<path-to-va-params-template.json>'
    profile: '<default-feature-profile>'
    fail_fast: false
    profiles:
      <default-feature-profile>:
        feature_path: '<feature-file-or-directory>'
        # опционально:
        # features_to_run: ['feature-name.feature']
        # filter_tags: ['tag-without-or-with-leading-at']
        # ignore_tags: ['wip']
        # scenario_filter: ['scenario name fragment']
```

Смысл полей:

- `tools.va.epf_path` — путь к внешней обработке Vanessa Automation. Legacy-поле `tests.va.epf_path` не поддерживается.
- `tests.va.params_path` — путь к JSON-шаблону VAParams. Это не generated-файл, а стабильный шаблон проекта или локального окружения.
- `tests.va.profile` — имя активного профиля фич; должно существовать в `tests.va.profiles`.
- `tests.va.profiles.<name>.feature_path` — файл или каталог `.feature`, который будет записан в runtime VAParams как `КаталогФич`.
- `filter_tags` и `ignore_tags` можно писать с `@` или без него; runner удалит один ведущий `@` перед записью в `СписокТеговОтбор` / `СписокТеговИсключение`.

`v8project.local.yaml` используй для машинно-локальных путей и секретов: например, если `epf_path`, `params_path`, пользователь/пароль TestClient или путь к локальной ИБ отличаются на машине агента. Не храни реальные секреты в общем `v8project.yaml`; лучше вынеси их в локальный VAParams-шаблон и укажи его через `tests.va.params_path` в `v8project.local.yaml`.

### Профиль TestClient внутри VAParams

Для `launch mcp va` и UI/UX-проверки через VA MCP профиль тест-клиента задаётся не отдельным полем `v8project.yaml`, а таблицей `ДанныеКлиентовТестирования` в JSON-шаблоне VAParams. Именно имя этой строки потом передаётся в MCP-вызов `connect_test_client {"profileName":"<имя-профиля>"}`.

Минимальная структура:

```json
{
  "ИспользоватьКомпонентуVanessaExt": "Истина",
  "ИспользоватьВнешнююКомпонентуДляСкриншотов": "Истина",
  "ДиапазонПортовTestclient": "<fixed-port>-<fixed-port>",
  "ОпределятьРеальныйПортНаКоторомЗапустилсяКлиентТестирования": "Истина",
  "ДанныеКлиентовТестирования": [
    {
      "Имя": "<stable-profile-name>",
      "Синоним": "<stable-profile-name>",
      "ПутьКИнфобазе": "<same-infobase-connection-as-tested-app>",
      "ПортЗапускаТестКлиента": <fixed-port>,
      "ДопПараметры": "/N<user> /P<password> /DisableStartupDialogs /DisableUnsafeActionProtection",
      "ТипКлиента": "Тонкий",
      "ИмяКомпьютера": "localhost"
    }
  ]
}
```

Почему поля именно такие:

- `Имя` — стабильный ключ профиля, который агент передаёт в `connect_test_client`; имя должно быть независимым от конкретной задачи.
- `Синоним` — человекочитаемый алиас; если отдельный алиас не нужен, держи равным `Имя`, чтобы не плодить неоднозначность.
- `ПутьКИнфобазе` — строка подключения тестируемого приложения. VA manager запускается отдельно и должен знать, какую ИБ открыть как `/TESTCLIENT`.
- `ПортЗапускаТестКлиента` и `ДиапазонПортовTestclient` — фиксируют порт, чтобы агент мог гарантированно подключиться к ожидаемому клиенту и не зависеть от старых открытых TestClient-процессов. Перед запуском закрывай старые test-client'ы или выбирай свободный зарезервированный порт.
- `ДопПараметры` — всё, что не должно останавливать запуск на диалогах: пользователь/пароль или другой способ авторизации, `/DisableStartupDialogs`, `/DisableUnsafeActionProtection`, при необходимости `/UC <код>`. Если строка содержит секреты, шаблон должен быть локальным.
- `ТипКлиента` — тип клиента, который VA должна запустить. Для автоматизации обычно выбирают тонкий клиент, если проект не требует толстый или обычный.
- `ИмяКомпьютера` — машина, где VA ищет/запускает TestClient. Для локального manager + test-client это `localhost`.
- `ИспользоватьКомпонентуVanessaExt` и `ИспользоватьВнешнююКомпонентуДляСкриншотов` включай, когда профиль VA должен работать с OS-окнами и реальным PID тест-клиента.
- `ОпределятьРеальныйПортНаКоторомЗапустилсяКлиентТестирования` оставляй включённым: VA должна сверить фактический процесс/порт, а не считать запуск успешным по одному профилю.
