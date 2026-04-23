---
name: 1c-ai-agent-cli
description: "CLI 1C BSL Agent Framework — tools/install.py (clone, install). Use when cloning the repository, installing components into the project, or configuring IDEs (Cursor, Claude Code, Windsurf, VS Code+Continue)."
---

# 1c-ai-agent CLI — install.py

## clone command — obtaining the framework

Repository: `SteelMorgan/1c-agent-based-dev-framework`. Git must be installed.

```bash
python tools/install.py clone                              # в ./1c-agent-based-dev-framework
python tools/install.py clone -t ./my-framework            # target directory
python tools/install.py clone --depth 1                    # shallow clone
python tools/install.py clone -b <branch>                  # specific branch
python tools/install.py clone -t ./fw --install            # clone + интерактивная установка
```

Alternative (first run without CLI):
```bash
git clone https://github.com/SteelMorgan/1c-agent-based-dev-framework.git
cd 1c-agent-based-dev-framework
python tools/install.py
```

---

## install command — installing components

Run from the framework repository root:

```bash
python tools/install.py [options]
```

### Invocation examples

| Command | Description |
|---------|-------------|
| `python tools/install.py` | Interactive mode |
| `python tools/install.py --ide cursor --list` | Show the component tree |
| `python tools/install.py --ide cursor --all` | Install all components |
| `python tools/install.py --ide cursor --include agent/developer workflow/full-cycle` | Specific components (dependencies will be pulled in) |
| `python tools/install.py --ide cursor --include agent/developer --dry-run` | Show the plan without making changes |
| `python tools/install.py --relink` | Recreate broken symlinks |
| `python tools/install.py --ide cursor --all --sync` | Remove symlinks for uninstalled components |

### Supported IDEs

`--ide` accepts multiple values: `python tools/install.py --ide claude-code codex --all`

| `--ide` | Description |
|---------|-------------|
| `cursor` | `.cursor/rules/` (auto), skills in `.cursor/skills/` |
| `claude-code` | `CLAUDE.md` + `.claude/rules/` — requires `@import` in CLAUDE.md |
| `windsurf` | `.windsurf/rules/` (auto), skills in `.windsurf/skills/` |
| `vscode-continue` | `.continue/rules/` |
| `roocode` | `.roo/rules/` (auto), skills in `.roo/skills/` |
| `kilocode` | `AGENTS.md` + `.kilocode/rules/` — requires links in AGENTS.md |
| `kiro` | `.kiro/steering/` (auto), skills in `.kiro/skills/` |
| `codex` | `AGENTS.md` + `.codex/rules/` — requires links in AGENTS.md |
| `antigravity` | `.agents/rules/`, skills in `.agents/skills/` |
| `generic` | Copying into `framework/` |

IDEs that need manual imports (`claude-code`, `codex`, `kilocode`): the installer will output the lines to add to `CLAUDE.md` / `AGENTS.md`.

### Flags

| Flag | Description |
|------|-------------|
| `--ide <IDE> [IDE ...]` | Target IDE (multiple values separated by space) |
| `--project-dir <path>` | Project directory (default: current) |
| `--include ID [ID ...]` | IDs of components to install |
| `--all` | Install every component |
| `--list` | Show the tree without installing anything |
| `--copy` | Copy files instead of symlinks (Windows without Developer Mode) |
| `--dry-run` | Show the plan without making changes |
| `--relink` | Recreate broken symlinks |
| `--sync` | Remove symlinks for components that were removed |

Requirements: Python 3.7+, no external dependencies.

---

## When to apply

| Trigger | Action |
|---------|--------|
| Obtain the framework | `python tools/install.py clone` |
| Install into the project | `clone -t ./fw --install` or `--ide cursor --all` |
| Inspect components | `--ide cursor --list` or `--dry-run` |
| Install specific components | `--ide cursor --include <id1> <id2>` |
| Broken symlinks | `--relink` |
| Windows without symlinks | `--copy` |

---
depends_on: []
metadata:
  category: framework-meta
  version: "1.0"
---
