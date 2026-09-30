# Universal xml-gen commands — reference

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

Exit codes: `0` = ok, `1` = errors, `2` = warnings (can continue).

## Universal add operations (any metadata object)

```bash
xml-gen form add <objectPath> <formName>
xml-gen form remove <objectPath> <formName>
xml-gen template add <objectPath> <name> --type spreadsheet|html|text|dcs|binary
xml-gen template remove <objectPath> <name>
xml-gen help add <objectPath>
```

## Byte-level text replacement (edit replace-text)

Safe replacement in XML without normalizing line endings. Preserves bare LF (0x0A) inside `<v8:content>`, CRLF between tags, UTF-8 BOM.

**Use instead of the Claude Code Edit tool** when the file contains multiline content in `<v8:content>` (tooltips, descriptions), as well as for reliable point edits of any XML fragment.

```bash
xml-gen edit replace-text <file> --old "<old>" --new "<new>" \
       [--all] [--dry-run] [--backup] [--validate] [--encoding utf-8-sig|utf-8]
```

| Flag | Description |
|------|----------|
| `--old` / `--new` | Replacement pair. You can specify several: `--old A --new B --old C --new D` |
| `--all` | Replace all occurrences (by default, only the first one) |
| `--dry-run` | Show the result without writing |
| `--backup` | Create a `.bak` before writing |
| `--validate` | Check XML well-formedness after replacement |
| `--encoding` | `utf-8-sig` (default, preserves BOM) or `utf-8` (without BOM) |

Exit codes: `0` = replacement performed, `1` = text not found, `2` = error.

Output (stdout): JSON `{"file": "...", "replacements": N, "bytes_before": N, "bytes_after": N}`.

```bash
# Replace Type with TypeSet
xml-gen edit replace-text src/xml/Documents/биг_Операция.xml \
  --old '<v8:Type>cfg:DocumentRef.big_Order_OKX</v8:Type>' \
  --new '<v8:TypeSet>cfg:DefinedType.биг_ОрдерБиржи</v8:TypeSet>'

# Multiple replacement in all occurrences with dry-run
xml-gen edit replace-text Form.xml \
  --old 'cfg:DefinedType.биг_ДокументыПозиций' \
  --new 'cfg:DefinedType.биг_ПозицияБиржи' \
  --all --dry-run
```
