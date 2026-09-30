# Универсальные команды xml-gen — справочник

## validate

```bash
xml-gen validate [--type form|role|skd|mxl|epf] [--format designer|edt] \
                 [--level structure|semantic] [--output text|json] <file> [file2 ...]
xml-gen config validate <configPath>
xml-gen subsystem validate <subsystemPath>
xml-gen interface validate <ciPath>
xml-gen meta validate <objectPath>
xml-gen extension validate <extensionPath>
```

Exit codes: `0` = ok, `1` = errors, `2` = warnings (можно продолжать).

## Универсальные add-операции (любой объект метаданных)

```bash
xml-gen form add <objectPath> <formName>
xml-gen form remove <objectPath> <formName>
xml-gen template add <objectPath> <name> --type spreadsheet|html|text|dcs|binary
xml-gen template remove <objectPath> <name>
xml-gen help add <objectPath>
```

## Побайтовая замена текста (edit replace-text)

Безопасная замена в XML без нормализации line endings. Сохраняет bare LF (0x0A) внутри `<v8:content>`, CRLF между тегами, UTF-8 BOM.

**Используй вместо Claude Code Edit tool** когда файл содержит мультилайн в `<v8:content>` (тултипы, описания), а также для надёжной точечной правки любого XML-фрагмента.

```bash
xml-gen edit replace-text <file> --old "<old>" --new "<new>" \
       [--all] [--dry-run] [--backup] [--validate] [--encoding utf-8-sig|utf-8]
```

| Флаг | Описание |
|------|----------|
| `--old` / `--new` | Пара для замены. Можно несколько: `--old A --new B --old C --new D` |
| `--all` | Заменить все вхождения (по умолчанию — только первое) |
| `--dry-run` | Показать результат без записи |
| `--backup` | Создать `.bak` перед записью |
| `--validate` | Проверить XML well-formedness после замены |
| `--encoding` | `utf-8-sig` (default, сохраняет BOM) или `utf-8` (без BOM) |

Exit codes: `0` = замена выполнена, `1` = текст не найден, `2` = ошибка.

Вывод (stdout): JSON `{"file": "...", "replacements": N, "bytes_before": N, "bytes_after": N}`.

```bash
# Замена Type на TypeSet
xml-gen edit replace-text src/xml/Documents/биг_Операция.xml \
  --old '<v8:Type>cfg:DocumentRef.big_Order_OKX</v8:Type>' \
  --new '<v8:TypeSet>cfg:DefinedType.биг_ОрдерБиржи</v8:TypeSet>'

# Множественная замена во всех вхождениях с dry-run
xml-gen edit replace-text Form.xml \
  --old 'cfg:DefinedType.биг_ДокументыПозиций' \
  --new 'cfg:DefinedType.биг_ПозицияБиржи' \
  --all --dry-run
```
