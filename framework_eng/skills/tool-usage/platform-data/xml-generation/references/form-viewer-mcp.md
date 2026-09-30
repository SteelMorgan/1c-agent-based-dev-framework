# MCP `1c-form-viewer` — visual preview of forms, layouts, and СКД

Third-party MCP server `1c-form-viewer` from the BslEdit project (MIT license). It renders a managed form (`Ext/Form.xml`), a spreadsheet layout (`Template.xml`), or a data composition schema in a hidden headless browser and returns a PNG to the agent. 1С and building the database are not required. Its origin, license, and what is carried over from it into `xml-gen` are described in [../../../../../docs/third-party-tools.md](../../../../../docs/third-party-tools.md).

## Role in the process

| What | With what | Status |
|-----|-----|--------|
| Editing form, layout, or СКД XML | only `xml-gen` | the only path (`no-manual-xml-edit`) |
| Structural validation | `xml-gen validate` | primary check |
| Second opinion on form structure | `validate_form` | supplementary, non-blocking |
| Initial visual assessment without a build | `open_preview` + `capture_preview` | quick, rough check |
| Checking in the actual 1С client | `va-visual-check` / thin client | final; preview does not replace it |

## Triggers

1. **After generating or editing a form** (`xml-gen form compile/edit/add-element`, `epf add-form`) and after `xml-gen validate --type form`:
   - `open_preview` with `path` = absolute path to `Ext/Form.xml` and `audience="agent"`;
   - `capture_preview` — take a screenshot; check the order and nesting of groups and pages, visibility, titles, and presence of buttons and columns. The evaluation checklist is `form-visual-requirements`;
   - `validate_form` — a second opinion alongside `xml-gen validate`.
2. **After generating or editing a layout** (`xml-gen mxl compile`, `template add`) — `open_preview` + `capture_preview`. Also use `list_markup` (areas and parameters) and `validate_template` (dangling references to formats/fonts, cells outside the grid).
3. **After generating or editing СКД** (`xml-gen skd compile/edit`) — `open_preview` + `capture_preview`: datasets, fields, parameters, variant structure.
4. Find an element in the screenshot — `preview operation=inspect` (tree with id, XML line, and visible flag), then `preview operation=select/switch_tab/scroll` and `capture_preview scope=element`.

A reviewer can use the server to view a form or layout, including in version comparison mode (`open_preview` with `base_revision`).

## Tools (in view-only mode)

| Tool | Purpose |
|------------|------------|
| `open_preview` | open a form / layout / СКД preview; `base_revision` or `base_path` enables comparison mode |
| `preview` | actions on the open preview: `inspect`, `switch_tab`, `select`, `scroll`, `reload`, `url`, `close` |
| `capture_preview` | PNG of the open preview (`scope`: viewport or element) |
| `list_form_elements` | form element tree (name, type) |
| `validate_form` | structural form validation: duplicate IDs and names, companions, data paths, command references, namespace prefix declarations |
| `list_markup` | layout areas and parameters |
| `validate_template` | structural layout validation |
| `convert_xlsx_to_template` | **writes a file** (`Template.xml` from xlsx); cannot be disabled with server flags — block at the client level (see “Connection”) |

## Limitations

- **Not for editing.** Run the server only with `--no-form-edit-tools --no-template-edit-tools`; then `edit_form`/`edit_template` are not published. Editing through them puts nodes in a noncanonical order if there is no local property dictionary.
- **`validate_form` does not replace `xml-gen validate`.** It misses some XDTO defects that cause loading to fail: event name casing, the position of the root `<Title>`, a button without `<Type>`, container children without `<ChildItems>`, `CommandName` without the `Form.Command.` prefix, typos in property name casing, format version mismatch. `ok:true` does not prove that the form will load.
- **The preview is not a 1С client.** There is no data. Standard columns and commands are generated conditionally; search badges are rendered for tables. Complex forms may have overlapping layout. Do not conclude “it looks the same in the client” without checking with `va-visual-check`.
- Only `audience="agent"`: `audience="user"` tries to open the user's browser (`xdg-open`), which does not work in the container.
- Paths must be **absolute** and within the `--root` root. A relative path is resolved from the server's working directory; files outside the root are rejected. Do not use `--allow-any-path`.
- Speed: `open_preview` takes 1–2 s, `capture_preview` up to 1 s, `validate_form` runs without a browser in milliseconds.

## Connection

The server is built from BslEdit source at a pinned commit. Installation is described in `docs/tools-install/1c-form-viewer.md` in the framework repository. Launch parameters:

```text
command: <wrapper> -> node <BslEdit>/packages/1c-form-viewer/dist/mcp-server.js
args:    --stdio --root <project root> --no-form-edit-tools --no-template-edit-tools
env:     ONE_C_FORM_VIEWER_CHROMIUM=<path to system Chrome/Chromium>
```

- Claude Code: add an `1c-form-viewer` entry to the project’s `.mcp.json` and `permissions.deny: ["mcp__1c-form-viewer__convert_xlsx_to_template"]` to the settings.
- Codex: `[mcp_servers.1c-form-viewer]` with `startup_timeout_sec = 20`, `tool_timeout_sec = 120`, `disabled_tools = ["convert_xlsx_to_template"]`.
- If the server is unavailable, inform the user. Do not run `mcp-server.js` manually with editing flags or call it outside the connected MCP.
