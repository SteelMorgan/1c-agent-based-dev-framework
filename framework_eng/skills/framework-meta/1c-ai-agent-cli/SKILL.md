---
name: 1c-ai-agent-cli
description: CLI 1C BSL Agent Framework — tools/1c-ai-agent-cli.py (clone, install). Use when cloning the repository, installing components in a project, configuring IDEs (Cursor, Claude Code, Windsurf, VS Code+Continue).
---

# 1c-ai-agent CLI — 1c-ai-agent-cli.py

Guidelines for working with the CLI 1C BSL Agent Framework. Commands: **clone** (fetch the repository) and **install** (set up components in a project).

---

## Command clone — obtaining the framework

Repository: **https://github.com/SteelMorgan/1c-agent-based-dev-framework**

**Requirement:** Git must be installed.

### Basic cloning

```bash
python tools/1c-ai-agent-cli.py clone
```

Clones into `./1c-agent-based-dev-framework` (current directory).

### Specifying a target directory

```bash
python tools/1c-ai-agent-cli.py clone -t ./my-framework
python tools/1c-ai-agent-cli.py clone --target /path/to/dir
```

### Shallow clone (faster, only the latest commit)

```bash
python tools/1c-ai-agent-cli.py clone --depth 1
```

### Specific branch

```bash
python tools/1c-ai-agent-cli.py clone -b agent-framework-bootstrap-20260211
```

### Clone + install

```bash
python tools/1c-ai-agent-cli.py clone -t ./fw --install
```

Installation runs interactively immediately after cloning.

### Clone help

```bash
python tools/1c-ai-agent-cli.py clone --help
```

---

## Alternative: manual cloning

If the CLI is unavailable (for example, first time running):

```bash
git clone https://github.com/SteelMorgan/1c-agent-based-dev-framework.git
cd 1c-agent-based-dev-framework
python tools/1c-ai-agent-cli.py
```

ZIP (without git): download the archive from GitHub and unpack it.

---

## Command install — installing components

Interactive installer for framework components into a project, taking the target IDE into account.

**Launch:** from the framework repository root (or after `clone`):

```bash
python tools/1c-ai-agent-cli.py [options]
python tools/1c-ai-agent-cli.py install [options]   # explicitly
```

### Basic invocation

| Command | Description |
|---------|-------------|
| `python tools/1c-ai-agent-cli.py` | Interactive mode — choose IDE, project, components |
| `python tools/1c-ai-agent-cli.py --ide cursor --list` | Show the component tree without installing |
| `python tools/1c-ai-agent-cli.py --ide cursor --all` | Install all components |
| `python tools/1c-ai-agent-cli.py --ide cursor --include agent/developer workflow/full-cycle` | Install the specified components (dependencies are pulled automatically) |
| `python tools/1c-ai-agent-cli.py --ide cursor --include agent/developer --dry-run` | Show what would be done without making changes |
| `python tools/1c-ai-agent-cli.py --relink` | Check and recreate broken symlinks |
| `python tools/1c-ai-agent-cli.py --ide cursor --all --sync` | Synchronize: remove symlinks for components that were uninstalled |

### Supported IDEs

`--ide` accepts one or multiple values separated by spaces (install to several IDEs at once):

```bash
python tools/1c-ai-agent-cli.py --ide claude-code codex --all
```

| `--ide` | Description |
|---------|-------------|
| `cursor` | Cursor — rules in `.cursor/rules/` (auto), skills in `.cursor/skills/` |
| `claude-code` | Claude Code — `CLAUDE.md` + `.claude/rules/` ⚠ requires `@import` in CLAUDE.md |
| `windsurf` | Windsurf — `.windsurf/rules/` (auto), skills in `.windsurf/skills/` |
| `vscode-continue` | VS Code + Continue — `.continue/rules/` |
| `roocode` | RooCode — `.roo/rules/` (auto, all files), skills in `.roo/skills/` |
| `kilocode` | Kilo Code — `AGENTS.md` + `.kilocode/rules/` ⚠ requires links in AGENTS.md |
| `kiro` | Kiro — `.kiro/steering/` (auto), skills in `.kiro/skills/` |
| `codex` | Codex CLI (OpenAI) — `AGENTS.md` + `.codex/rules/` ⚠ requires links in AGENTS.md |
| `antigravity` | Antigravity — `.agents/rules/`, skills in `.agents/skills/` (skill.md) |
| `generic` | IDE-agnostic — copy into `framework/` |

> ⚠ **IDEs with manual rule import** (claude-code, codex, kilocode): after installation the installer
> will print reminders with the exact lines to add to `CLAUDE.md` / `AGENTS.md`.

### Flags

| Flag | Description |
|------|-------------|
| `--ide <IDE> [IDE ...]` | Target IDE (multiple values separated by spaces are allowed) |
| `--project-dir <path>` | Project directory (default: current directory) |
| `--include ID [ID ...]` | IDs of components to install |
| `--all` | Install all components |
| `--list` | Show component tree without installing |
| `--copy` | Force copying files (no symlinks) |
| `--dry-run` | Show what would be done without making actual changes |
| `--relink` | Check and recreate broken symlinks |
| `--sync` | Synchronize installation: remove symlinks for removed components |

### Requirements

- Python 3.7+
- No external dependencies (standard library only)

---

## When to apply

| Trigger | Action |
|---------|--------|
| User wants to obtain the framework | `python tools/1c-ai-agent-cli.py clone` or `git clone <URL>` |
| User wants to install the framework into a project | `python tools/1c-ai-agent-cli.py clone -t ./fw --install` or clone + install |
| Need to check available components | `python tools/1c-ai-agent-cli.py --ide cursor --list` |
| User asks how to install the framework | Provide `python tools/1c-ai-agent-cli.py clone` and `python tools/1c-ai-agent-cli.py` |
| User asks to install specific agents/rules | `python tools/1c-ai-agent-cli.py --ide cursor --include <id1> <id2>` |
| Symlinks broke after moving framework/ | `python tools/1c-ai-agent-cli.py --relink` |
| Need to verify what will be installed | `python tools/1c-ai-agent-cli.py --ide cursor --include ... --dry-run` |
| Windows without Developer Mode (symlinks unavailable) | `python tools/1c-ai-agent-cli.py --ide cursor --copy --include ...` |

---

## Scenarios

### Scenario 1: First framework launch

**Option A (Git available, from scratch):**
```bash
git clone https://github.com/SteelMorgan/1c-agent-based-dev-framework.git
cd 1c-agent-based-dev-framework
python tools/1c-ai-agent-cli.py   # interactively, or python tools/1c-ai-agent-cli.py --ide cursor --all
```

**Option B (repository already exists, cloning into another directory):**
```bash
cd 1c-agent-based-dev-framework
python tools/1c-ai-agent-cli.py clone -t ../other-project/fw --install
```

**Option C (installing into the current project):**
```bash
python tools/1c-ai-agent-cli.py --ide cursor --all
```

### Scenario 2: Installing into an existing project

1. Clone the framework into a separate folder (or reuse an existing one).
2. Specify the target project: `python tools/1c-ai-agent-cli.py --ide cursor --project-dir /path/to/project --include agent/developer workflow/full-cycle`
3. If necessary: use `--dry-run` to check.

### Scenario 3: Restoring symlinks

After moving the framework to a different location:

```bash
cd /path/to/project
python /path/to/framework/tools/1c-ai-agent-cli.py --relink
```

---

## See also

- [agent-development](../agent-development/) — creating agents
- [skill-creator](../skill-creator/) — creating skills
