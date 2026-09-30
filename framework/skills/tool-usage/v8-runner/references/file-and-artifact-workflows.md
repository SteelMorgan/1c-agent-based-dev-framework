# Сценарии работы с файлами и артефактами

Используй эти команды, когда задача про файлы, артефакты, публикацию или конвертацию формата исходников.

## Dump

`dump` синхронизирует текущее состояние ИБ обратно в файлы проекта.

```bash
git status --short
v8-runner dump --mode incremental
git diff
```

Поддерживаемые режимы:

```bash
v8-runner dump --mode full
v8-runner dump --mode incremental
v8-runner dump --mode partial --object <TYPE:NAME>
```

Полезные селекторы:

```bash
v8-runner dump --mode incremental --source-set <NAME>
v8-runner dump --mode incremental --extension <EXTENSION>
```

`partial` требует хотя бы одного `--object`. С `builder=IBCMD` объектно-ограниченный partial dump деградирует до incremental dump с предупреждением.

Для `format=EDT` dump использует внутренний Designer-снапшот в `workPath/designer/<sourceSetName>`, затем импортирует результат в EDT-цель.

## Convert

`convert` — репо-aware конвертация файлов между форматами исходников Designer и EDT.

```bash
v8-runner convert
v8-runner convert --source-set <NAME>
v8-runner convert --output <DIR>
```

Это не алиас для dump:

- не использует ИБ;
- не использует `builder`;
- направление выводится из настроенного `format`;
- без `--output` результаты публикуются в `workPath/convert/out/<sourceSetName>/<designer|edt>/`;
- `--output` — это корень цели, который зеркалирует `source-set.path` относительно `basePath`.

`convert` — это файловый CLI-сценарий и не работает через ИБ.

## Load

`load` применяет существующие артефакты `.cf` или `.cfe` к ИБ.

```bash
v8-runner load --path <FILE>
v8-runner load --path <FILE> --mode merge --settings <FILE>
v8-runner load --path <FILE> --extension <NAME>
```

Правила:

- поддерживается только для `format=DESIGNER`, `builder=DESIGNER`;
- `.cfe` требует `--extension`;
- `--mode merge` требует `--settings`;
- `load --mode update` отвергается текущим контрактом команды.

## Make и artifacts

`make` и `artifacts` — это один и тот же сценарий. В примерах предпочитай `make`, если пользователь сам не использует алиас.

```bash
v8-runner make --output <TARGET>
v8-runner make --output <TARGET> --source-set <NAME>
v8-runner make --output <TARGET> --extension <NAME>
```

Поведение:

- основная конфигурация экспортируется в `.cf`;
- экспорт расширения использует `.cfe`;
- внешние обработки и отчёты публикуются как `.epf` / `.erf` в каталог вывода;
- требуется `builder=DESIGNER`.

Полный dump и публикация package/external-артефактов используют поэтапную публикацию с семантикой backup/rollback. Incremental и partial dump — неатомарные режимы обновления.
