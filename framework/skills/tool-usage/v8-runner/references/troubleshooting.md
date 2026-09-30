# Диагностика

Отделяй сбои исходников проекта от сбоев локального окружения или установки runner'а.

## Начальные проверки

```bash
git status --short
test -f v8project.yaml
```

Изучай поля `v8project.yaml`, влияющие на падающую команду:

- `format`
- `builder`
- `connection`
- `basePath`
- `workPath`
- `source-set`
- `tools.platform`
- `tools.edt_cli`
- `tests`

## Типовые ситуации

Отсутствие платформы 1С, EDT CLI, IBCMD или утилит запускалок тестов — это проблемы окружения/установки. Сообщай об отсутствующей утилите и о полях конфига, по которым выполняется её поиск.

Устаревшее инкрементальное состояние после переключения веток, rebase или больших перемещений исходников обычно требует:

```bash
v8-runner build --full-rebuild
```

Partial dump с IBCMD деградирует до инкрементального dump. Упомяни это в сводке и проверь итоговый Git diff.

Не очищай каталоги упавших прогонов, пока диагностика не завершена. Артефакты падений должны оставаться в:

```text
workPath/temp/<runner-id>/runs/<run-id>/
```

## Runtime-каталоги

Полезные расположения внутри `workPath`:

- `workPath/hash-storages/`: персистентное состояние change-detection.
- `workPath/edt-workspace/`: общий EDT-воркспейс для `init`.
- `workPath/convert/edt-workspace/`: отдельный EDT-воркспейс для `convert`.
- `workPath/designer/<sourceSetName>/`: сгенерированное Designer-представление, особенно для EDT-сценариев.
- `workPath/logs/platform/`: логи платформы.
- `workPath/temp/`: временные артефакты прогонов и диагностика.
