#!/usr/bin/env python3
"""
1C BSL Agent Framework — Интерактивный установщик

Кроссплатформенный скрипт (Windows / Linux / macOS) для установки компонентов
фреймворка в директорию проекта с учётом целевой IDE.

Использование:
    python install.py                         # Интерактивный режим
    python install.py --ide cursor --list     # Показать дерево компонентов
    python install.py --ide cursor --all      # Установить всё
    python install.py --ide cursor --include agent/developer workflow/full-cycle
    python install.py --relink                # Пересоздать симлинки

Требования: Python 3.7+, без внешних зависимостей.
"""

import argparse
import json
import os
import platform
import re
import shutil
import sys
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

# TUI — опционально, fallback на текстовый ввод
try:
    from tui import (
        is_tui_available, select_one, select_many, select_models_tui,
        ChecklistItem, bold, green, yellow, cyan, dim, red, clear_screen, show_cursor,
    )
    _HAS_TUI = True
except ImportError:
    _HAS_TUI = False


# ─── Конфигурация IDE ────────────────────────────────────────────────────────

IDE_CONFIGS = {
    "cursor": {
        "name": "Cursor",
        "rules_dir": ".cursor/rules",
        "skills_dir": ".cursor/skills",
        "description": "Cursor IDE — правила в .cursor/rules/, навыки в .cursor/skills/",
    },
    "claude-code": {
        "name": "Claude Code",
        "rules_dir": ".claude",
        "skills_dir": ".claude/skills",
        "description": "Claude Code — AGENTS.md + .claude/",
    },
    "windsurf": {
        "name": "Windsurf",
        "rules_dir": ".windsurf/rules",
        "skills_dir": ".windsurf/skills",
        "description": "Windsurf — .windsurfrules + .windsurf/",
    },
    "vscode-continue": {
        "name": "VS Code + Continue",
        "rules_dir": ".continue/rules",
        "skills_dir": ".continue/skills",
        "description": "VS Code с расширением Continue",
    },
    "generic": {
        "name": "Generic (копия в framework/)",
        "rules_dir": "framework/rules",
        "skills_dir": "framework/skills",
        "description": "Без привязки к IDE — копирование в framework/",
    },
}

# Маппинг type → целевая директория внутри IDE
TYPE_TO_DIR = {
    "skill": "skills_dir",
    "rule": "rules_dir",
    "agent": "rules_dir",       # Агенты — это правила для IDE
    "workflow": "rules_dir",    # Воркфлоу — это правила для IDE
}


# ─── Маппинг моделей ─────────────────────────────────────────────────────────

def load_model_defaults(script_dir: Path) -> dict:
    """Загружает model-defaults.json из каталога скрипта."""
    defaults_file = script_dir / "model-defaults.json"
    if not defaults_file.exists():
        return {}
    try:
        return json.loads(defaults_file.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as e:
        print(yellow(f"  Предупреждение: не удалось загрузить {defaults_file}: {e}"))
        return {}


def get_ide_aliases(model_defaults: dict, ide_key: str) -> Dict[str, str]:
    """Извлекает aliases из model-defaults (поддерживает оба формата)."""
    ide_data = model_defaults.get(ide_key, {})
    if "aliases" in ide_data:
        return ide_data["aliases"]
    # Старый формат: {ide: {alias: model}} без ключа "aliases"
    return {k: v for k, v in ide_data.items() if k not in ("available", "_comment")}


def get_available_models(model_defaults: dict, ide_key: str) -> List[str]:
    """Извлекает список доступных моделей для IDE."""
    ide_data = model_defaults.get(ide_key, {})
    return ide_data.get("available", [])

# ─── Парсинг YAML frontmatter ────────────────────────────────────────────────

FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---", re.DOTALL)


def parse_frontmatter(filepath: Path) -> Optional[dict]:
    """Парсит YAML frontmatter из markdown-файла. Лёгкий парсер без PyYAML."""
    try:
        text = filepath.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return None

    match = FRONTMATTER_RE.match(text)
    if not match:
        return None

    data: dict = {}
    current_key = None
    current_list: list = []

    for line in match.group(1).splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue

        # Inline list: depends_on: [a, b, c]
        if ":" in stripped and not stripped.startswith("-"):
            if current_key and current_list:
                data[current_key] = current_list
                current_list = []

            key, _, val = stripped.partition(":")
            key = key.strip()
            val = val.strip()

            if val.startswith("[") and val.endswith("]"):
                items = [v.strip().strip("'\"") for v in val[1:-1].split(",") if v.strip()]
                data[key] = items
                current_key = None
            elif val:
                data[key] = val
                current_key = None
            else:
                current_key = key
                current_list = []
        elif stripped.startswith("- ") and current_key:
            current_list.append(stripped[2:].strip().strip("'\""))

    if current_key and current_list:
        data[current_key] = current_list

    return data


# ─── Граф компонентов ─────────────────────────────────────────────────────────

class Component:
    """Один компонент фреймворка (файл .md с frontmatter)."""

    def __init__(self, id: str, type: str, depends_on: List[str], filepath: Path):
        self.id = id
        self.type = type
        self.depends_on = depends_on
        self.filepath = filepath

    @property
    def display_name(self) -> str:
        """Человекочитаемое имя из первого заголовка файла или YAML name."""
        try:
            text = self.filepath.read_text(encoding="utf-8")
            for line in text.splitlines():
                if line.startswith("# "):
                    return line[2:].strip()
        except (OSError, UnicodeDecodeError):
            pass
        # Fallback: name из frontmatter (для агентов без # заголовка)
        fm = parse_frontmatter(self.filepath)
        if fm and "name" in fm:
            return fm["name"]
        return self.id

    def __repr__(self):
        return f"Component({self.id})"


class FrameworkGraph:
    """Граф зависимостей компонентов фреймворка."""

    def __init__(self, framework_dir: Path):
        self.framework_dir = framework_dir
        self.components: Dict[str, Component] = {}
        self._scan()

    def _infer_type_from_path(self, md_file: Path) -> Optional[str]:
        """Определяет тип компонента по его расположению в framework/."""
        try:
            rel = md_file.relative_to(self.framework_dir)
        except ValueError:
            return None
        parts = rel.parts
        if len(parts) >= 1:
            top = parts[0]
            type_map = {"agents": "agent", "rules": "rule", "skills": "skill", "workflows": "workflow"}
            return type_map.get(top)
        return None

    def _scan(self):
        """Сканирует framework/ на .md файлы с frontmatter."""
        for md_file in sorted(self.framework_dir.rglob("*.md")):
            if md_file.name.startswith("_"):
                continue  # Пропускаем шаблоны и служебные файлы

            fm = parse_frontmatter(md_file)
            if not fm:
                continue

            # Поддержка двух форматов frontmatter:
            # Old: id, type, depends_on (skills, rules, workflows)
            # New: name, description, model, readonly, skills (agents)
            if "id" in fm:
                comp_id = fm["id"]
                comp_type = fm.get("type", "unknown")
                depends = fm.get("depends_on", [])
            elif "name" in fm:
                name = fm["name"]
                comp_type = self._infer_type_from_path(md_file) or "unknown"
                comp_id = f"{comp_type}/{name}"
                # Convert skills list to depends_on format
                skills = fm.get("skills", [])
                depends = [f"skill/{s}" for s in skills] if isinstance(skills, list) else []
            else:
                continue

            if isinstance(depends, str):
                depends = [depends] if depends else []

            if comp_type == "template":
                continue

            self.components[comp_id] = Component(
                id=comp_id,
                type=comp_type,
                depends_on=depends,
                filepath=md_file,
            )

    def resolve_dependencies(self, selected_ids: Set[str]) -> Set[str]:
        """Рекурсивно резолвит все зависимости для выбранных компонентов."""
        resolved: Set[str] = set()
        queue = list(selected_ids)

        while queue:
            cid = queue.pop(0)
            if cid in resolved:
                continue
            resolved.add(cid)
            comp = self.components.get(cid)
            if comp:
                for dep in comp.depends_on:
                    if dep not in resolved and dep in self.components:
                        queue.append(dep)

        return resolved

    def get_by_type(self, comp_type: str) -> List[Component]:
        """Возвращает компоненты определённого типа, отсортированные по id."""
        return sorted(
            [c for c in self.components.values() if c.type == comp_type],
            key=lambda c: c.id,
        )

    def get_installable(self) -> List[Component]:
        """Возвращает все компоненты, которые можно установить (не шаблоны)."""
        return sorted(
            [c for c in self.components.values() if c.type != "template"],
            key=lambda c: (c.type, c.id),
        )


# ─── Интерактивный UI ────────────────────────────────────────────────────────

# ANSI-коды (отключаются на Windows без поддержки)
if sys.platform == "win32":
    try:
        import ctypes
        kernel32 = ctypes.windll.kernel32
        kernel32.SetConsoleMode(kernel32.GetStdHandle(-11), 7)
        USE_COLOR = True
    except Exception:
        USE_COLOR = False
else:
    USE_COLOR = sys.stdout.isatty()


def _c(text: str, code: str) -> str:
    if USE_COLOR:
        return f"\033[{code}m{text}\033[0m"
    return text


def bold(t): return _c(t, "1")
def green(t): return _c(t, "32")
def yellow(t): return _c(t, "33")
def cyan(t): return _c(t, "36")
def dim(t): return _c(t, "2")
def red(t): return _c(t, "31")


TYPE_LABELS = {
    "skill": "Навыки",
    "rule": "Правила",
    "agent": "Агенты",
    "workflow": "Воркфлоу",
}

TYPE_ICONS = {
    "skill": "📘",
    "rule": "📋",
    "agent": "🤖",
    "workflow": "🔄",
}


def print_tree(graph: FrameworkGraph, selected: Optional[Set[str]] = None):
    """Выводит дерево компонентов, сгруппированное по типу."""
    installable = graph.get_installable()
    types_seen: List[str] = []
    for c in installable:
        if c.type not in types_seen:
            types_seen.append(c.type)

    idx = 1
    idx_map: Dict[int, str] = {}

    for comp_type in types_seen:
        comps = [c for c in installable if c.type == comp_type]
        label = TYPE_LABELS.get(comp_type, comp_type)
        icon = TYPE_ICONS.get(comp_type, "📄")

        print(f"\n  {icon} {bold(label)}")

        for c in comps:
            marker = ""
            if selected is not None:
                if c.id in selected:
                    marker = green(" ✓")
                else:
                    marker = ""

            deps_str = ""
            if c.depends_on:
                dep_count = len(c.depends_on)
                deps_str = dim(f" ({dep_count} зав.)")

            print(f"    {cyan(str(idx).rjust(3))}  {c.id:<40} {c.display_name}{deps_str}{marker}")
            idx_map[idx] = c.id
            idx += 1

    return idx_map


def select_ide() -> str:
    """Интерактивный выбор IDE (TUI или текст)."""
    ide_list = list(IDE_CONFIGS.keys())

    # TUI mode
    if _HAS_TUI and is_tui_available():
        items = [(cfg["name"], cfg["description"]) for cfg in IDE_CONFIGS.values()]
        idx = select_one("Выберите IDE:", items)
        if idx < 0:
            sys.exit(0)
        return ide_list[idx]

    # Text fallback
    print(f"\n{bold('Выберите IDE:')}\n")
    for i, key in enumerate(ide_list, 1):
        cfg = IDE_CONFIGS[key]
        print(f"  {cyan(str(i))}  {cfg['name']:<25} {dim(cfg['description'])}")

    while True:
        try:
            choice = input(f"\n{bold('IDE')} [1-{len(ide_list)}]: ").strip()
            num = int(choice)
            if 1 <= num <= len(ide_list):
                return ide_list[num - 1]
        except (ValueError, EOFError):
            pass
        print(red("  Некорректный выбор. Повторите."))


def _build_checklist_items(graph: FrameworkGraph) -> List:
    """Строит список элементов для TUI-чеклиста с группировкой по типам."""
    items = []  # (id, label, description, is_header)
    installable = graph.get_installable()

    types_seen: List[str] = []
    for c in installable:
        if c.type not in types_seen:
            types_seen.append(c.type)

    for comp_type in types_seen:
        comps = [c for c in installable if c.type == comp_type]
        label = TYPE_LABELS.get(comp_type, comp_type)
        icon = TYPE_ICONS.get(comp_type, "📄")
        items.append(("", f"{icon} {label}", "", True))
        for c in comps:
            items.append((c.id, c.id, c.display_name, False))

    return items


def interactive_select(graph: FrameworkGraph) -> Set[str]:
    """Интерактивный выбор компонентов (TUI или текст)."""

    # TUI mode
    if _HAS_TUI and is_tui_available():
        items = _build_checklist_items(graph)
        result = select_many("Выберите компоненты:", items)
        if result is None:
            sys.exit(0)
        if not result:
            print(yellow("  Ничего не выбрано."))
            sys.exit(0)
        return result

    # Text fallback
    selected: Set[str] = set()

    while True:
        print(f"\n{'─' * 70}")
        print(bold("  Дерево компонентов фреймворка"))
        print(f"{'─' * 70}")

        idx_map = print_tree(graph, selected)

        print(f"\n{'─' * 70}")
        print(f"  Выбрано: {green(str(len(selected)))} компонентов")
        print(f"{'─' * 70}")
        print()
        print(f"  {bold('Команды:')}")
        print(f"    {cyan('1,3,5-8')}    — выбрать/снять компоненты")
        print(f"    {cyan('all')}        — выбрать всё")
        print(f"    {cyan('none')}       — снять всё")
        print(f"    {cyan('done')}       — завершить выбор")
        print(f"    {cyan('quit')}       — выйти")
        print()

        try:
            raw = input(f"  {bold('>')} ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            print()
            sys.exit(0)

        if raw in ("quit", "q"):
            sys.exit(0)
        if raw in ("done", "d"):
            if not selected:
                print(yellow("  Ничего не выбрано."))
                continue
            break
        if raw == "all":
            selected = {c.id for c in graph.get_installable()}
            continue
        if raw == "none":
            selected.clear()
            continue

        try:
            nums = _parse_numbers(raw)
            for n in nums:
                if n in idx_map:
                    cid = idx_map[n]
                    if cid in selected:
                        selected.discard(cid)
                    else:
                        selected.add(cid)
                else:
                    print(yellow(f"  Номер {n} вне диапазона."))
        except ValueError:
            print(red(f"  Не понял: '{raw}'"))

    return selected


def _text_project_dir(default_dir: Path, ide_cfg: dict) -> Path:
    """Текстовый fallback для выбора каталога проекта."""
    resolved = default_dir.resolve()

    print(f"\n{'─' * 70}")
    print(bold("  Каталог проекта"))
    print(f"{'─' * 70}")
    print(f"\n  IDE:     {bold(ide_cfg['name'])}")
    print(f"  Правила: {dim(ide_cfg['rules_dir'] + '/')}")
    print(f"  Навыки:  {dim(ide_cfg['skills_dir'] + '/')}")
    print(f"\n  Каталог: {cyan(str(resolved))}")
    print(f"\n  {dim('Enter — принять, или введите путь к проекту:')}")

    try:
        raw = input(f"  {bold('>')} ").strip()
    except (EOFError, KeyboardInterrupt):
        print()
        sys.exit(0)

    if raw:
        candidate = Path(raw).expanduser().resolve()
        if not candidate.is_dir():
            print(yellow(f"\n  Каталог не существует: {candidate}"))
            try:
                create = input(f"  Создать? [y/N]: ").strip().lower()
            except (EOFError, KeyboardInterrupt):
                print()
                sys.exit(0)
            if create in ("y", "yes", "д", "да"):
                candidate.mkdir(parents=True, exist_ok=True)
                print(green(f"  Создан: {candidate}"))
            else:
                print("  Отменено.")
                sys.exit(0)
        return candidate

    return resolved


def select_project_dir(default_dir: Path, ide_key: str) -> Path:
    """Интерактивный выбор каталога проекта (browser / text)."""
    resolved = default_dir.resolve()
    ide_cfg = IDE_CONFIGS[ide_key]

    # TUI — directory browser
    if _HAS_TUI and is_tui_available():
        from tui import browse_directory

        extra_info = [
            f"IDE:     {bold(ide_cfg['name'])}",
            f"Правила: {dim(ide_cfg['rules_dir'] + '/')}",
            f"Навыки:  {dim(ide_cfg['skills_dir'] + '/')}",
        ]
        result = browse_directory(
            start_dir=str(resolved),
            title="Каталог проекта",
            extra_info=extra_info,
        )

        if result is None:
            # cancelled
            sys.exit(0)
        elif result == "":
            # user pressed 't' — fallback to text input
            from tui import clear_screen as _cls, show_cursor as _sc
            _cls()
            _sc()
            return _text_project_dir(default_dir, ide_cfg)
        else:
            return Path(result)

    # Text fallback
    return _text_project_dir(default_dir, ide_cfg)


def _parse_numbers(s: str) -> List[int]:
    """Парсит строку вида '1,3,5-8,12' в список чисел."""
    result = []
    for part in s.replace(" ", "").split(","):
        if not part:
            continue
        if "-" in part:
            a, _, b = part.partition("-")
            result.extend(range(int(a), int(b) + 1))
        else:
            result.append(int(part))
    return result


# ─── Выбор и запись моделей ───────────────────────────────────────────────────

def get_agent_models(graph: FrameworkGraph, selected_ids: Set[str]) -> Dict[str, str]:
    """Возвращает {agent_id: текущий_алиас_модели} для выбранных агентов."""
    result: Dict[str, str] = {}
    for cid in sorted(selected_ids):
        comp = graph.components.get(cid)
        if comp and comp.type == "agent":
            fm = parse_frontmatter(comp.filepath)
            if fm and "model" in fm:
                result[cid] = fm["model"]
    return result


def select_models_interactive(
    ide_key: str,
    model_defaults: dict,
    agent_models: Dict[str, str],
) -> Dict[str, str]:
    """
    Интерактивный выбор моделей. Возвращает {agent_id: конкретная_модель}.
    """
    ide_aliases = get_ide_aliases(model_defaults, ide_key)
    available = get_available_models(model_defaults, ide_key)

    if not agent_models:
        return {}

    # TUI mode — per-agent model picker с ←→
    if _HAS_TUI and is_tui_available() and available:
        agents_list = []
        for aid, alias in sorted(agent_models.items()):
            default = ide_aliases.get(alias, alias)
            agents_list.append((aid, alias, default))

        result = select_models_tui(agents_list, list(available))
        if result is None:
            sys.exit(0)
        return result

    # Text fallback
    print(f"\n{'─' * 70}")
    print(bold("  Настройка моделей для агентов"))
    print(f"{'─' * 70}")

    alias_agents: Dict[str, List[str]] = {}
    for aid, alias in sorted(agent_models.items()):
        alias_agents.setdefault(alias, []).append(aid.split("/")[-1])

    print(f"\n  Маппинг по умолчанию ({bold(IDE_CONFIGS[ide_key]['name'])}):\n")
    for alias in ("haiku", "sonnet", "opus"):
        concrete = ide_aliases.get(alias, alias)
        agents_list_str = ", ".join(alias_agents.get(alias, []))
        if agents_list_str:
            print(f"    {cyan(alias.ljust(8))}→ {bold(concrete.ljust(35))} ({dim(agents_list_str)})")

    print(f"\n  {bold('Команды:')}")
    print(f"    {cyan('[Enter]')}  принять")
    print(f"    {cyan('c')}        настроить по алиасам")
    print(f"    {cyan('a')}        настроить по агентам")
    print()

    try:
        choice = input(f"  {bold('>')} ").strip().lower()
    except (EOFError, KeyboardInterrupt):
        print()
        sys.exit(0)

    final: Dict[str, str] = {}

    if choice == "c":
        custom: Dict[str, str] = {}
        print(f"\n  {dim('Enter — оставить дефолт')}:\n")
        for alias in ("haiku", "sonnet", "opus"):
            default = ide_aliases.get(alias, alias)
            try:
                val = input(f"    {alias.ljust(8)} [{bold(default)}]: ").strip()
            except (EOFError, KeyboardInterrupt):
                print()
                sys.exit(0)
            custom[alias] = val if val else default
        for aid, alias in agent_models.items():
            final[aid] = custom.get(alias, ide_aliases.get(alias, alias))

    elif choice == "a":
        print(f"\n  {dim('Enter — оставить дефолт')}:\n")
        for aid, alias in sorted(agent_models.items()):
            default = ide_aliases.get(alias, alias)
            short = aid.split("/")[-1]
            try:
                val = input(f"    {short.ljust(12)} ({alias}) [{bold(default)}]: ").strip()
            except (EOFError, KeyboardInterrupt):
                print()
                sys.exit(0)
            final[aid] = val if val else default

    else:
        for aid, alias in agent_models.items():
            final[aid] = ide_aliases.get(alias, alias)

    return final


def apply_models_to_agents(
    graph: FrameworkGraph,
    model_map: Dict[str, str],
    dry_run: bool = False,
) -> int:
    """
    Записывает выбранные модели в framework/agents/*.md (оригиналы).
    Возвращает количество изменённых файлов.
    """
    changed = 0
    for agent_id, concrete_model in sorted(model_map.items()):
        comp = graph.components.get(agent_id)
        if not comp:
            continue

        filepath = comp.filepath
        text = filepath.read_text(encoding="utf-8")

        # Ищем `model: <что-то>` в frontmatter и заменяем на конкретную модель
        new_text = re.sub(
            r"^(model:\s*)\S+",
            rf"\g<1>{concrete_model}",
            text,
            count=1,
            flags=re.MULTILINE,
        )

        if new_text != text:
            if dry_run:
                short = agent_id.split("/")[-1]
                fm = parse_frontmatter(filepath)
                old_model = fm.get("model", "?") if fm else "?"
                print(f"    {short.ljust(12)} {old_model} → {bold(concrete_model)}")
            else:
                filepath.write_text(new_text, encoding="utf-8")
            changed += 1

    return changed


# ─── Установка ───────────────────────────────────────────────────────────────

def detect_symlink_support() -> bool:
    """Проверяет, поддерживаются ли симлинки на текущей ОС."""
    if sys.platform != "win32":
        return True  # Linux/macOS — всегда

    # Windows: попробуем создать тестовый симлинк
    test_dir = Path(os.environ.get("TEMP", ".")) / "_fw_symlink_test"
    test_target = test_dir / "target.txt"
    test_link = test_dir / "link.txt"
    try:
        test_dir.mkdir(exist_ok=True)
        test_target.write_text("test", encoding="utf-8")
        test_link.symlink_to(test_target)
        return True
    except (OSError, NotImplementedError):
        return False
    finally:
        shutil.rmtree(test_dir, ignore_errors=True)


def install_components(
    graph: FrameworkGraph,
    selected_ids: Set[str],
    ide_key: str,
    project_dir: Path,
    use_symlinks: bool,
    dry_run: bool = False,
) -> Tuple[int, int]:
    """
    Устанавливает выбранные компоненты в директорию проекта.
    Возвращает (установлено, пропущено).
    """
    ide_cfg = IDE_CONFIGS[ide_key]

    # Резолвим зависимости
    all_ids = graph.resolve_dependencies(selected_ids)
    extra_deps = all_ids - selected_ids

    if extra_deps:
        print(f"\n  {bold('Добавлены зависимости')} ({len(extra_deps)} шт.):")
        for dep_id in sorted(extra_deps):
            comp = graph.components.get(dep_id)
            name = comp.display_name if comp else dep_id
            print(f"    + {dep_id:<40} {dim(name)}")

    print(f"\n  Итого к установке: {bold(str(len(all_ids)))} компонентов")
    print(f"  Метод: {bold('симлинки' if use_symlinks else 'копирование файлов')}")
    print(f"  IDE: {bold(ide_cfg['name'])}")
    print(f"  Каталог проекта: {bold(str(project_dir))}")

    if not dry_run:
        try:
            confirm = input(f"\n  {bold('Продолжить?')} [Y/n]: ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            print()
            sys.exit(0)
        if confirm and confirm not in ("y", "yes", "д", "да", ""):
            print("  Отменено.")
            return 0, 0

    installed = 0
    skipped = 0

    for comp_id in sorted(all_ids):
        comp = graph.components.get(comp_id)
        if not comp:
            print(yellow(f"  ⚠ Компонент {comp_id} не найден в графе. Пропуск."))
            skipped += 1
            continue

        # Определяем целевую директорию
        dir_key = TYPE_TO_DIR.get(comp.type, "rules_dir")
        target_base = project_dir / ide_cfg[dir_key]

        # Имя файла: id → путь (skill/coding-standards → coding-standards.md)
        _, _, short_name = comp.id.rpartition("/")
        if not short_name:
            short_name = comp.id.replace("/", "-")
        target_file = target_base / f"{short_name}.md"

        source_file = comp.filepath.resolve()

        if dry_run:
            action = "→ (symlink)" if use_symlinks else "→ (copy)"
            print(f"    {action} {target_file}")
            installed += 1
            continue

        # Создаём директорию
        target_file.parent.mkdir(parents=True, exist_ok=True)

        # Удаляем старый файл/симлинк если есть
        if target_file.exists() or target_file.is_symlink():
            target_file.unlink()

        if use_symlinks:
            try:
                try:
                    rel_path = os.path.relpath(source_file, target_file.parent)
                    target_file.symlink_to(rel_path)
                except ValueError:
                    target_file.symlink_to(source_file)
                installed += 1
            except OSError as e:
                print(red(f"  ✗ Ошибка симлинка {comp.id}: {e}"))
                shutil.copy2(source_file, target_file)
                print(yellow(f"    → скопирован как fallback"))
                installed += 1
        else:
            shutil.copy2(source_file, target_file)
            installed += 1

    return installed, skipped


def relink(project_dir: Path):
    """Пересоздаёт все сломанные симлинки в директории проекта."""
    broken = 0
    fixed = 0
    for p in project_dir.rglob("*.md"):
        if p.is_symlink() and not p.exists():
            broken += 1
            print(yellow(f"  Сломан: {p} → {os.readlink(p)}"))
            # Пока только диагностика; реальный relink требует знания оригинала

    if broken == 0:
        print(green("  Все симлинки в порядке."))
    else:
        print(f"\n  Найдено {red(str(broken))} сломанных симлинков.")
        print(f"  Для исправления: переустановите фреймворк (python install.py)")


# ─── Точка входа ─────────────────────────────────────────────────────────────

def find_framework_dir() -> Path:
    """Ищет каталог framework/ относительно скрипта."""
    script_dir = Path(__file__).resolve().parent
    fw_dir = script_dir / "framework"
    if fw_dir.is_dir():
        return fw_dir
    # Может быть запущен из framework/
    parent_fw = script_dir.parent / "framework"
    if parent_fw.is_dir():
        return parent_fw
    print(red(f"Каталог framework/ не найден рядом с {script_dir}"))
    sys.exit(1)


def main():
    parser = argparse.ArgumentParser(
        description="1C BSL Agent Framework — Установщик компонентов",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Примеры:
  python install.py                                     # Интерактивный режим
  python install.py --ide cursor --list                 # Показать дерево
  python install.py --ide cursor --all                  # Установить всё
  python install.py --ide cursor --include agent/developer workflow/full-cycle
  python install.py --ide cursor --include agent/developer --dry-run
  python install.py --relink                            # Проверить симлинки
        """,
    )

    parser.add_argument("--ide", choices=list(IDE_CONFIGS.keys()),
                        help="Целевая IDE")
    parser.add_argument("--project-dir", type=Path, default=Path("."),
                        help="Каталог проекта (по умолчанию: текущий)")
    parser.add_argument("--include", nargs="+", metavar="ID",
                        help="ID компонентов для установки (зависимости подтянутся автоматически)")
    parser.add_argument("--all", action="store_true",
                        help="Установить все компоненты")
    parser.add_argument("--list", action="store_true",
                        help="Показать дерево компонентов без установки")
    parser.add_argument("--copy", action="store_true",
                        help="Принудительно копировать файлы (не симлинки)")
    parser.add_argument("--dry-run", action="store_true",
                        help="Показать что будет сделано, без реальных изменений")
    parser.add_argument("--relink", action="store_true",
                        help="Проверить и пересоздать сломанные симлинки")

    args = parser.parse_args()

    # Banner
    print()
    print(bold("  ┌──────────────────────────────────────────────────┐"))
    print(bold("  │   1C BSL Agent Framework — Установщик            │"))
    print(bold("  │   Версия: 0.2                                    │"))
    print(bold("  └──────────────────────────────────────────────────┘"))

    system = platform.system()
    print(f"  Система: {system} ({platform.machine()})")
    print(f"  Python: {sys.version.split()[0]}")

    # Relink mode
    if args.relink:
        project_dir = args.project_dir.resolve()
        print(f"\n  Проверка симлинков в {project_dir}...")
        relink(project_dir)
        return

    # Сканируем framework
    framework_dir = find_framework_dir()
    print(f"  Framework: {framework_dir}")

    graph = FrameworkGraph(framework_dir)
    print(f"  Найдено компонентов: {len(graph.components)}")

    # List mode
    if args.list:
        ide_key = args.ide
        if not ide_key:
            ide_key = select_ide()
        print(f"\n  IDE: {bold(IDE_CONFIGS[ide_key]['name'])}")
        print_tree(graph)
        return

    # Выбор IDE
    ide_key = args.ide
    if not ide_key:
        ide_key = select_ide()

    # Выбор каталога проекта (интерактивно, если не задан явно через CLI)
    is_interactive = not (args.all or args.include)
    if is_interactive and args.project_dir == Path("."):
        project_dir = select_project_dir(args.project_dir, ide_key)
    else:
        project_dir = args.project_dir.resolve()

    # Выбор компонентов
    if args.all:
        selected = {c.id for c in graph.get_installable()}
    elif args.include:
        selected = set(args.include)
        # Проверяем что все ID валидны
        invalid = selected - set(graph.components.keys())
        if invalid:
            print(red(f"\n  Неизвестные компоненты: {', '.join(sorted(invalid))}"))
            print(f"  Используйте --list для просмотра доступных ID.")
            sys.exit(1)
    else:
        selected = interactive_select(graph)

    # ── Шаг: Настройка моделей для агентов ──
    script_dir = Path(__file__).resolve().parent
    model_defaults = load_model_defaults(script_dir)

    # Резолвим зависимости, чтобы знать полный набор агентов
    all_selected = graph.resolve_dependencies(selected)
    agent_models = get_agent_models(graph, all_selected)

    if agent_models:
        ide_aliases = get_ide_aliases(model_defaults, ide_key)
        if args.dry_run:
            # В dry-run показываем что будет изменено
            model_map = {}
            for aid, alias in agent_models.items():
                model_map[aid] = ide_aliases.get(alias, alias)
            has_changes = any(
                model_map.get(aid) != agent_models.get(aid)
                for aid in model_map
            )
            if has_changes:
                print(f"\n  {bold('Маппинг моделей (дефолты):')}")
                apply_models_to_agents(graph, model_map, dry_run=True)
            else:
                print(f"\n  {dim('Модели агентов уже соответствуют дефолтам — изменений не требуется.')}")
        else:
            model_map = select_models_interactive(ide_key, model_defaults, agent_models)
            if model_map:
                changed = apply_models_to_agents(graph, model_map, dry_run=False)
                if changed:
                    print(green(f"\n  ✓ Модели обновлены в {changed} agent-файлах (framework/agents/)"))
                    graph = FrameworkGraph(framework_dir)

    # Очищаем экран TUI перед выводом результатов
    if _HAS_TUI and is_tui_available():
        clear_screen()
        show_cursor()
        print(bold("  1C BSL Agent Framework — Установщик\n"))

    # Определяем метод установки
    if args.copy:
        use_symlinks = False
    else:
        use_symlinks = detect_symlink_support()
        if not use_symlinks:
            print(yellow("\n  ⚠ Симлинки недоступны (Windows без Developer Mode)."))
            print(yellow("    Файлы будут скопированы. При обновлении фреймворка — перезапустите install."))

    installed, skipped = install_components(
        graph=graph,
        selected_ids=selected,
        ide_key=ide_key,
        project_dir=project_dir,
        use_symlinks=use_symlinks,
        dry_run=args.dry_run,
    )

    print()
    if args.dry_run:
        print(f"  {bold('[DRY RUN]')} Было бы установлено: {installed}, пропущено: {skipped}")
    else:
        print(green(f"  ✓ Установлено: {installed} компонентов"))
        if skipped:
            print(yellow(f"  ⚠ Пропущено: {skipped}"))

    # Подсказка по проектным навыкам
    ide_cfg = IDE_CONFIGS[ide_key]
    print(f"\n  {bold('Проектные навыки')}: размещайте в {cyan(ide_cfg['skills_dir'] + '/')} вашего проекта")

    if use_symlinks:
        print(f"\n  {dim('Симлинки привязаны к расположению фреймворка.')}")
        print(f"  {dim('Если переместите framework/ — запустите: python install.py --relink')}")

    print()


if __name__ == "__main__":
    main()
