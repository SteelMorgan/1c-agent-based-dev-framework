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
