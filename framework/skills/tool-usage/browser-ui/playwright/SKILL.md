---
name: "playwright"
description: "Browser UI automation: сценарии, формы, скриншоты"
---

# Playwright CLI Skill

CLI-first browser automation. Do not pivot to `@playwright/test` unless explicitly asked.

## 1C Boundary

For 1C:Enterprise UI, Playwright is not the preferred test or screenshot path for ordinary forms. First use the `va-visual-check` policy for Vanessa Automation/TestClient and VA MCP. Use Playwright/browser screenshots for 1C only as browser-layer work or as fallback under `va-visual-check`, recording the VA steps already attempted, why browser evidence is sufficient, and the residual risk.

## Prerequisite check

```bash
command -v npx >/dev/null 2>&1
```

If missing, ask user to install Node.js/npm first.

## Skill path (set once)

```bash
export CODEX_HOME="${CODEX_HOME:-$HOME/.codex}"
export PWCLI="$CODEX_HOME/skills/playwright/scripts/playwright_cli.sh"
```

## Quick start

```bash
"$PWCLI" open https://playwright.dev --headed
"$PWCLI" snapshot
"$PWCLI" click e15
"$PWCLI" type "Playwright"
"$PWCLI" press Enter
"$PWCLI" screenshot
```

## Core workflow

1. `open` the page.
2. `snapshot` to get stable element refs.
3. Interact using refs from the latest snapshot.
4. Re-snapshot after navigation or significant DOM changes.
5. Capture artifacts (screenshot, pdf, traces) when useful.

Re-snapshot after: navigation, UI-changing clicks, modals, tab switches, or stale-ref errors.

## Recommended patterns

### Form fill and submit

```bash
"$PWCLI" open https://example.com/form
"$PWCLI" snapshot
"$PWCLI" fill e1 "user@example.com"
"$PWCLI" fill e2 "password123"
"$PWCLI" click e3
"$PWCLI" snapshot
```

### Debug a UI flow with traces

```bash
"$PWCLI" open https://example.com --headed
"$PWCLI" tracing-start
# ...interactions...
"$PWCLI" tracing-stop
```

### Multi-tab work

```bash
"$PWCLI" tab-new https://example.com
"$PWCLI" tab-list
"$PWCLI" tab-select 0
"$PWCLI" snapshot
```

## References

- CLI command reference: `references/cli.md`
- Workflows and troubleshooting: `references/workflows.md`

## Guardrails

- Always snapshot before referencing element ids like `e12`.
- Re-snapshot when refs seem stale.
- Prefer explicit commands over `eval` and `run-code`.
- Use `--headed` when a visual check will help.
- Artifacts go to `output/playwright/`.
- Default to CLI commands, not Playwright test specs.
