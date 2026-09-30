# Third-Party Tools and Borrowed Work

Registry of external projects that the framework uses as tools or from which it borrows ideas and code. The goal is to preserve provenance and comply with license terms.

## Rules

1. **Tool** (run as is; code is not copied). Record the repository link, license, pinned commit, usage mode, and limitations.
2. **Borrowed idea** (a validation rule or algorithm rewritten in your own words and language). Record the source: file, function, and commit. Copyright notices are not copied into the code, but a link in this registry is required.
3. **Code transfer** (a literal or near-literal translation of a fragment):
   - **MIT**: copy the copyright notice and MIT permission text from the source `LICENSE` into the file that receives the fragment. If the source itself was ported from another MIT project, preserve **both** notices, as in the source;
   - in a comment next to the fragment, specify the repository, path, commit, and function name;
   - add a row to the borrowings table below.
4. Projects with a license that does not permit transfers (no license, copyleft, or incompatible with the framework's license) may only be used as a tool or source of ideas. Code from them must not be transferred.
5. When updating a tool's pinned commit, recheck the license and update the commit here.

## Registry

### BslEdit / `1c-form-viewer`

| Field | Value |
|------|----------|
| Repository | https://github.com/alonehobo/BslEdit |
| License | MIT, `Copyright (c) 2026 1c-form-viewer contributors` (file `LICENSE`) |
| Pinned commit | `d24f9089dd1d483c7562ffdf9337079ed792073c` (26.09.2026), package `packages/1c-form-viewer` v0.2.4 |
| Nested provenance | `web/form-validate.js` — port of `form-validate` v1.19 from https://github.com/Nikolay-Shirokov/cc-1c-skills, MIT, `Copyright (c) 2025-2026 Nick Shirokov`. When transferring code from this file, preserve **both** notices |
| How we use it | Tool: MCP server **for viewing only** forms, layouts, and СКД. Run with flags `--no-form-edit-tools --no-template-edit-tools`; `convert_xlsx_to_template` is prohibited at the client level. Its place in the process is [../skills/tool-usage/platform-data/xml-generation/references/form-viewer-mcp.md](../skills/tool-usage/platform-data/xml-generation/references/form-viewer-mcp.md). Installation: `docs/tools-install/1c-form-viewer.md` at the repository root |
| What we do not do | Do not edit XML with it (editing is done only with `xml-gen`). Do not use its `validate_form` as a gate |

**Borrowings in `xml-gen` (ideas and validation logic)** — to be filled in as they are transferred:

| What | Source (path @ commit) | Type | Transferred to | Status |
|-----|--------------------------|-----|-----------------|--------|
| Check for namespace prefix declarations in `v8:Type` / `v8:TypeSet` / `xsi:type` (code `namespacePrefix`) | `web/form-validate.js` (≈стр. 520–530) @ `d24f9089` | idea | FormValidator `xml-gen` | planned |
| Uniqueness of element names, attribute column names and IDs, command names, attributes, and parameters (`duplicateElementName` and adjacent checks) | `web/form-validate.js` (≈стр. 290–310) @ `d24f9089` | idea | FormValidator | planned |
| Removing `[N]` indexes and `~` before resolving the path, the `Items.*` chain, full list of binding tags (`BINDING_TAGS`, `stripPath`) | `web/form-validate.js` (стр. 84, 406–440) @ `d24f9089` | idea | FormValidator (data path rule) | planned |
| Element nesting rules (`canContain`: Page only in Pages, ColumnGroup only in Table, only buttons in command panels, etc.) | `web/form-edit.js` (pp. 87–100) @ `d24f9089` | idea | FormValidator / FormEditor | planned |
| Map of required companion nodes by element type (ContextMenu, ExtendedTooltip, AutoCommandBar, SearchString…) | `web/form-validate.js` @ `d24f9089` | idea | FormValidator (warning) | planned |
| Refusal to delete an element, attribute, or command when live references exist (`CommandName Form.Item.*`, `DataPath`, conditional formatting fields) | `web/form-edit.js` @ `d24f9089` | idea | FormEditor | planned |

When a row transitions to “transferred,” specify the `xml-gen` commit and the type of transfer. If code was transferred rather than an idea, apply rule 3.
