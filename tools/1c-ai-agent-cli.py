#!/usr/bin/env python3
"""
1C BSL Agent Framework — CLI (clone, install)

Кроссплатформенный скрипт (Windows / Linux / macOS):
  - clone: клонирование репозитория фреймворка
  - install: установка компонентов в проект с учётом целевой IDE

Использование:
    python tools/1c-ai-agent-cli.py clone                    # Клонировать репозиторий
    python tools/1c-ai-agent-cli.py clone -t ./fw --install  # Клонировать и установить
    python tools/1c-ai-agent-cli.py                          # Интерактивный режим установки
    python tools/1c-ai-agent-cli.py --ide cursor --list      # Показать дерево компонентов
    python tools/1c-ai-agent-cli.py --ide cursor --all       # Установить всё
    python tools/1c-ai-agent-cli.py --relink                 # Пересоздать симлинки

Требования: Python 3.7+, Git (для clone). Без внешних Python-зависимостей.
"""

import argparse
import json
import os
import platform
import re
import shutil
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

# Репозиторий фреймворка для команды clone
REPO_URL = "https://github.com/SteelMorgan/1c-agent-based-dev-framework"

# TUI — опционально, fallback на текстовый ввод
try:
    from tui import (
        is_tui_available, select_one, select_many, select_models_tui,
        ChecklistItem, bold, green, yellow, cyan, dim, red, clear_screen, show_cursor,
        BACK,
    )
    _HAS_TUI = True
except ImportError:
    _HAS_TUI = False
    BACK = None  # type: ignore


# ─── Конфигурация IDE ────────────────────────────────────────────────────────

IDE_CONFIGS = {
    "cursor": {
        "name": "Cursor",
        "rules_dir": ".cursor/rules",
        "skills_dir": ".cursor/skills",
        "agents_dir": ".cursor/agents",
        "description": "Cursor IDE — правила в .cursor/rules/, навыки в .cursor/skills/, агенты в .cursor/agents/",
    },
    "claude-code": {
        "name": "Claude Code",
        "rules_dir": ".claude",
        "skills_dir": ".claude/skills",
        "agents_dir": ".claude/agents",
        "description": "Claude Code — AGENTS.md + .claude/, subagents в .claude/agents/",
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
    "roocode": {
        "name": "RooCode",
        "rules_dir": ".roo/rules",
        "skills_dir": ".roo/skills",
        "agents_dir": ".roo/agents",
        "description": "RooCode — .roo/rules/, навыки в .roo/skills/, агенты в .roo/agents/",
    },
    "kilocode": {
        "name": "Kilo Code",
        "rules_dir": ".kilocode/rules",
        "skills_dir": ".kilocode/skills",
        "agents_dir": ".kilocode/agents",
        "description": "Kilo Code — AGENTS.md + .kilocode/, агенты в .kilocode/agents/",
    },
    "cilocode": {
        "name": "Cilo Code (alias Kilo Code)",
        "rules_dir": ".kilocode/rules",
        "skills_dir": ".kilocode/skills",
        "agents_dir": ".kilocode/agents",
        "description": "Alias для Kilo Code: AGENTS.md + .kilocode/, агенты в .kilocode/agents/",
    },
    "kiro": {
        "name": "Kiro",
        "rules_dir": ".kiro/steering",
        "skills_dir": ".kiro/skills",
        "agents_dir": ".kiro/agents",
        "description": "Kiro — steering в .kiro/steering/, hooks в .kiro/hooks/, агенты в .kiro/agents/",
    },
    "codex": {
        "name": "Codex",
        "rules_dir": ".codex/rules",
        "skills_dir": ".codex/skills",
        "agents_dir": ".codex/agents",
        "description": "Codex — AGENTS.md + .codex/, агенты в .codex/agents/",
    },
    "antigravity": {
        "name": "Antigravity",
        "rules_dir": ".agents/rules",
        "skills_dir": ".agents/skills",
        "agents_dir": ".agents/workflows",
        "workflows_dir": ".agents/workflows",
        "skill_filename": "skill.md",
        "description": "Antigravity — .agents/rules/, навыки в .agents/skills/ (skill.md), воркфлоу в .agents/workflows/",
    },
    "generic": {
        "name": "Generic (копия в framework/)",
        "rules_dir": "framework/rules",
        "skills_dir": "framework/skills",
        "agents_dir": "framework/subagents",
        "description": "Без привязки к IDE — копирование в framework/",
    },
}

# Маппинг type → ключ целевой директории внутри IDE
TYPE_TO_DIR = {
    "skill": "skills_dir",
    "rule": "rules_dir",
    "agent": "agents_dir",
    "subagent": "agents_dir",
    "workflow": "workflows_dir",
}


def _dir_key_for_component(comp_type: str, ide_cfg: dict) -> str:
    """Определяет целевую директорию для типа компонента с fallback на rules_dir."""
    preferred = TYPE_TO_DIR.get(comp_type, "rules_dir")
    if preferred in ide_cfg:
        return preferred
    return "rules_dir"


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

FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*(?:\n|$)", re.DOTALL)
BACKMATTER_RE = re.compile(r"\n---\s*\n(.*?)\n---\s*$", re.DOTALL)


def _parse_simple_yaml_block(block: str) -> dict:
    """Парсит простой YAML-блок: key: value, key: [..], key:
- ..."""
    data: dict = {}
    current_key = None
    current_list: list = []

    for line in block.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue

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


def parse_frontmatter(filepath: Path) -> Optional[dict]:
    """Парсит frontmatter и поддерживает технический backmatter в конце файла."""
    try:
        text = filepath.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return None

    front = FRONTMATTER_RE.match(text)
    if not front:
        return None

    data = _parse_simple_yaml_block(front.group(1))

    # Технический backmatter (например depends_on) в конце файла переопределяет frontmatter.
    back = BACKMATTER_RE.search(text)
    if back:
        data.update(_parse_simple_yaml_block(back.group(1)))

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

    def short_description(self) -> str:
        """Первое предложение (до первой точки) из поля description."""
        fm = parse_frontmatter(self.filepath)
        desc = fm.get("description") if fm else ""
        if not desc:
            return self.display_name
        # Многострочный YAML — склеиваем в одну строку
        text = " ".join(str(desc).split())
        # Ищем ". " (граница предложения), не "4.0" и т.п.
        dot_pos = text.find(". ")
        if dot_pos >= 0:
            return (text[: dot_pos + 1]).strip()
        if text.endswith("."):
            return text.strip()
        return text.strip() or self.display_name

    def __repr__(self):
        return f"Component({self.id})"


class FrameworkGraph:
    """Граф зависимостей компонентов фреймворка."""

    def __init__(self, framework_dir: Path):
        self.framework_dir = framework_dir
        self.components: Dict[str, Component] = {}
        self._reverse_deps: Dict[str, Set[str]] = {}  # Кэш обратных зависимостей
        self._scan()
        self._build_reverse_deps()

    def _infer_type_from_path(self, md_file: Path) -> Optional[str]:
        """Определяет тип компонента по его расположению в framework/."""
        try:
            rel = md_file.relative_to(self.framework_dir)
        except ValueError:
            return None
        parts = rel.parts
        if len(parts) >= 1:
            top = parts[0]
            type_map = {"agents": "agent", "subagents": "subagent", "rules": "rule", "skills": "skill", "workflows": "workflow"}
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

            # Извлекаем name из frontmatter, тип определяем по корневой папке
            name = fm.get("name")
            comp_type = self._infer_type_from_path(md_file) or "unknown"
            
            # Если name не указан, пропускаем
            if not name:
                continue
            
            # Формируем comp_id как type/name
            comp_id = f"{comp_type}/{name}"
            
            # Обрабатываем зависимости
            depends = fm.get("depends_on", [])
            
            # Для агентов/сабагентов: конвертируем skills список в пути
            if comp_type in ("agent", "subagent") and "skills" in fm:
                skills = fm.get("skills", [])
                if isinstance(skills, list):
                    # Конвертируем имена навыков в пути (нужно найти файл по имени)
                    skill_paths = []
                    for skill_name in skills:
                        # Ищем навык по имени в уже отсканированных или по структуре
                        skill_path = self._find_skill_path(skill_name)
                        if skill_path:
                            skill_paths.append(skill_path)
                    depends.extend(skill_paths)
            
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
    
    def _find_skill_path(self, skill_name: str) -> str:
        """Находит путь к навыку по его имени."""
        # Ищем SKILL.md файлы с соответствующим name в frontmatter
        for skill_file in self.framework_dir.glob("skills/**/SKILL.md"):
            fm = parse_frontmatter(skill_file)
            if fm and fm.get("name") == skill_name:
                return str(skill_file.relative_to(self.framework_dir.parent))
        return ""

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
                    # dep теперь путь к файлу, нужно найти comp_id
                    dep_id = self._path_to_comp_id(dep)
                    if dep_id and dep_id not in resolved and dep_id in self.components:
                        queue.append(dep_id)

        return resolved
    
    def _path_to_comp_id(self, path: str) -> str:
        """Конвертирует путь к файлу в comp_id (type/name)."""
        path_norm = path.replace("\\", "/").strip()
        for comp_id, comp in self.components.items():
            try:
                rel_path = str(comp.filepath.relative_to(self.framework_dir.parent)).replace("\\", "/")
            except ValueError:
                continue
            if rel_path == path_norm:
                return comp_id
        return ""

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

    def get_installable_for_user(self) -> List[Component]:
        """Компоненты для установки в проект (исключая framework-meta)."""
        return [c for c in self.get_installable() if not self.is_framework_meta_skill(c.id)]
    
    def is_framework_meta_skill(self, comp_id: str) -> bool:
        """Проверяет, является ли навык служебным (framework-meta)."""
        comp = self.components.get(comp_id)
        if not comp or comp.type != "skill":
            return False
        # Проверяем, что путь содержит framework-meta
        rel_path = str(comp.filepath.relative_to(self.framework_dir.parent))
        return "framework-meta" in rel_path

    def load_mutual_groups(self) -> List[List[str]]:
        """Загружает группы взаимозависимостей из framework-cycles.json."""
        cycles_file = self.framework_dir.parent / "tools" / "framework-cycles.json"
        if not cycles_file.exists():
            return []
        try:
            data = json.loads(cycles_file.read_text(encoding="utf-8"))
            return [c["artifacts"] for c in data.get("cycles", [])]
        except (json.JSONDecodeError, KeyError, OSError):
            return []

    def get_linked_components(self, comp_id: str) -> List[str]:
        """Возвращает comp_id компонентов, связанных взаимозависимостью."""
        comp = self.components.get(comp_id)
        if not comp:
            return []
        
        rel_path = str(comp.filepath.relative_to(self.framework_dir.parent))
        groups = self.load_mutual_groups()
        
        linked = []
        for group in groups:
            if rel_path in group:
                for artifact in group:
                    if artifact != rel_path:
                        # Конвертируем путь обратно в comp_id
                        linked_id = self._path_to_comp_id(artifact)
                        if linked_id:
                            linked.append(linked_id)
        return linked

    def get_dependencies(self, comp_id: str) -> Set[str]:
        """Прямые зависимости comp_id (comp_ids)."""
        comp = self.components.get(comp_id)
        if not comp:
            return set()
        result: Set[str] = set()
        for dep in comp.depends_on:
            dep_id = self._path_to_comp_id(dep)
            if dep_id and dep_id in self.components:
                result.add(dep_id)
        return result

    def get_all_dependencies(self, comp_id: str) -> Set[str]:
        """Рекурсивно все зависимости (включая транзитивные)."""
        return self.resolve_dependencies({comp_id})

    def get_required_by(self, comp_id: str) -> Set[str]:
        """Компоненты, которые зависят от comp_id (обратная карта). O(1) благодаря кэшу."""
        return self._reverse_deps.get(comp_id, set()).copy()

    def _build_reverse_deps(self):
        """Строит обратную карту зависимостей один раз при инициализации."""
        self._reverse_deps.clear()
        for cid, comp in self.components.items():
            for dep in comp.depends_on:
                dep_id = self._path_to_comp_id(dep)
                if dep_id and dep_id in self.components:
                    self._reverse_deps.setdefault(dep_id, set()).add(cid)


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

# Подкатегории навыков (папки в framework/skills/)
SKILL_CATEGORY_LABELS = {
    "bsl-practices": "BSL-практики",
    "tool-usage": "Использование инструментов",
    "tool-usage/xml-generation": "XML-генерация",
    "spec-writing": "Спецификации",
    "framework-meta": "Framework-meta",
}


def print_tree(graph: FrameworkGraph, selected: Optional[Set[str]] = None):
    """Выводит дерево компонентов, сгруппированное по типу и папкам навыков."""
    installable = graph.get_installable_for_user()
    fw_dir = graph.framework_dir

    idx = 1
    idx_map: Dict[int, str] = {}

    for comp_type in ["workflow", "agent", "rule", "skill"]:
        comps = [c for c in installable if c.type == comp_type]
        if not comps:
            continue

        label = TYPE_LABELS.get(comp_type, comp_type)
        icon = TYPE_ICONS.get(comp_type, "📄")
        print(f"\n  {icon} {bold(label)}")

        if comp_type == "skill":
            by_category: Dict[str, List] = {}
            for c in comps:
                cat = _skill_category(c, fw_dir)
                by_category.setdefault(cat, []).append(c)
            for cat in sorted(by_category.keys()):
                cat_comps = sorted(by_category[cat], key=lambda x: x.id)
                cat_label = SKILL_CATEGORY_LABELS.get(cat, cat) if cat else "Прочие"
                print(f"\n    {dim(cat_label)}")
                for c in cat_comps:
                    marker = green(" ✓") if selected and c.id in selected else ""
                    deps_str = dim(f" ({len(c.depends_on)} зав.)") if c.depends_on else ""
                    meta_note = ""
                    linked = graph.get_linked_components(c.id)
                    link_str = cyan(f" ↔ {', '.join(lid.split('/')[-1] for lid in linked)}") if linked else ""
                    print(f"    {cyan(str(idx).rjust(3))}  {c.id:<40} {c.display_name}{deps_str}{meta_note}{link_str}{marker}")
                    idx_map[idx] = c.id
                    idx += 1
        else:
            for c in comps:
                marker = green(" ✓") if selected and c.id in selected else ""
                deps_str = dim(f" ({len(c.depends_on)} зав.)") if c.depends_on else ""
                meta_note = ""
                linked = graph.get_linked_components(c.id)
                link_str = cyan(f" ↔ {', '.join(lid.split('/')[-1] for lid in linked)}") if linked else ""
                print(f"    {cyan(str(idx).rjust(3))}  {c.id:<40} {c.display_name}{deps_str}{meta_note}{link_str}{marker}")
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


def _skill_category(comp: "Component", framework_dir: Path) -> str:
    """Возвращает подкатегорию навыка (папка под framework/skills/)."""
    if comp.type != "skill":
        return ""
    try:
        rel = comp.filepath.relative_to(framework_dir)
        parts = rel.parts
        if len(parts) >= 2 and parts[0] == "skills":
            if len(parts) >= 3 and parts[1] == "tool-usage" and parts[2] == "xml-generation":
                return "tool-usage/xml-generation"
            return parts[1]
    except ValueError:
        pass
    return ""


def _build_checklist_items(graph: FrameworkGraph) -> List:
    """Строит список элементов для TUI-чеклиста с группировкой по типам и папкам навыков."""
    items = []  # (id, label, description, is_header)
    installable = graph.get_installable_for_user()
    fw_dir = graph.framework_dir

    types_order = ["workflow", "agent", "rule", "skill"]
    for comp_type in types_order:
        comps = [c for c in installable if c.type == comp_type]
        if not comps:
            continue

        label = TYPE_LABELS.get(comp_type, comp_type)
        icon = TYPE_ICONS.get(comp_type, "📄")
        items.append(("", f"{icon} {label}", "", True))

        if comp_type == "skill":
            # Группируем навыки по папкам (framework/skills/<category>/)
            by_category: Dict[str, List] = {}
            for c in comps:
                cat = _skill_category(c, fw_dir)
                by_category.setdefault(cat, []).append(c)
            for cat in sorted(by_category.keys()):
                cat_comps = by_category[cat]
                cat_label = SKILL_CATEGORY_LABELS.get(cat, cat) if cat else "Прочие"
                items.append(("", f"    {cat_label}", "", True))
                for c in sorted(cat_comps, key=lambda x: x.id):
                    items.append((c.id, c.id, c.short_description(), False))
        else:
            for c in comps:
                items.append((c.id, c.id, c.short_description(), False))

    return items


def interactive_select(
    graph: FrameworkGraph, preselected: Optional[Set[str]] = None
) -> Optional[Set[str]]:
    """Интерактивный выбор компонентов (TUI или текст).
    Возвращает: Set[str] — выбранные ID, None — отмена, BACK — назад к предыдущему шагу.
    """
    # TUI mode
    if _HAS_TUI and is_tui_available():
        items = _build_checklist_items(graph)

        def _get_component_path(comp_id: str) -> Optional[Path]:
            comp = graph.components.get(comp_id)
            return comp.filepath if comp else None

        result = select_many(
            "Выберите компоненты:",
            items,
            preselected=preselected,
            on_open=_get_component_path,
            get_dependencies=graph.get_all_dependencies,
            get_required_by=graph.get_required_by,
            get_mutual=lambda cid: graph.get_linked_components(cid),
        )
        return result  # None | BACK | Set[str]

    # Text fallback
    selected: Set[str] = set(preselected or set())

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
            selected = {c.id for c in graph.get_installable_for_user()}
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
                        # Отключаем — вместе со связанными
                        selected.discard(cid)
                        linked = graph.get_linked_components(cid)
                        for lid in linked:
                            if lid in selected:
                                selected.discard(lid)
                                print(cyan(f"  ↔ Автоматически снят связанный: {lid}"))
                    else:
                        # Включаем — вместе со связанными
                        selected.add(cid)
                        linked = graph.get_linked_components(cid)
                        for lid in linked:
                            if lid not in selected and lid in {c.id for c in graph.get_installable_for_user()}:
                                selected.add(lid)
                                print(cyan(f"  ↔ Автоматически выбран связанный: {lid}"))
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
    if 'agents_dir' in ide_cfg:
        print(f"  Агенты:  {dim(ide_cfg['agents_dir'] + '/')}")
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
        if 'agents_dir' in ide_cfg:
            extra_info.append(f"Агенты:  {dim(ide_cfg['agents_dir'] + '/')}")
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
    Записывает выбранные модели в framework/subagents/*.md (оригиналы).
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


# ─── Логирование сессии (для верификации установки) ───────────────────────────

def write_session_log(
    project_dir: Path,
    ide_key: str,
    selected: Set[str],
    model_map: Dict[str, str],
    use_symlinks: bool,
    installed: int,
    skipped: int,
    removed: int,
) -> None:
    """Записывает лог выбора в .1c-ai-agent-cli-session.json для верификации."""
    log_path = project_dir / ".1c-ai-agent-cli-session.json"
    data = {
        "timestamp": datetime.now().isoformat(),
        "ide": ide_key,
        "project_dir": str(project_dir.resolve()),
        "selected_components": sorted(selected),
        "model_map": dict(sorted(model_map.items())) if model_map else {},
        "use_symlinks": use_symlinks,
        "result": {"installed": installed, "skipped": skipped, "removed": removed},
    }
    try:
        log_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        print(dim(f"  Лог сессии: {log_path}"))
    except OSError as e:
        print(yellow(f"  Не удалось записать лог: {e}"))


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


def _component_source_paths(comp: Component) -> Tuple[Path, Path, str]:
    """
    Возвращает (source_file, source_path_for_link, short_name).
    source_path_for_link:
      - skill -> директория навыка
      - остальное -> файл компонента
    """
    source_file = comp.filepath.resolve()

    if comp.type == "skill" and source_file.name == "SKILL.md":
        source_path = source_file.parent
        short_name = source_file.parent.name
    else:
        source_path = source_file
        _, _, short_name = comp.id.rpartition("/")
        if not short_name:
            short_name = comp.id.replace("/", "-")

    return source_file, source_path, short_name


def _component_target_file(
    comp: Component, ide_key: str, project_dir: Path, use_symlinks: bool = True
) -> Path:
    """Строит целевой путь компонента в каталоге проекта.
    Для навыков: симлинк и копия — short_name (как у исходной папки).
    Симлинк: target_base/short_name → каталог. Копия: target_base/short_name/SKILL.md.
    Antigravity: skill.md (lowercase) вместо SKILL.md.
    """
    ide_cfg = IDE_CONFIGS[ide_key]
    dir_key = _dir_key_for_component(comp.type, ide_cfg)
    target_base = project_dir / ide_cfg[dir_key]
    _, _, short_name = _component_source_paths(comp)
    if comp.type == "skill":
        if use_symlinks:
            return target_base / short_name  # симлинк на каталог
        skill_fn = ide_cfg.get("skill_filename", "SKILL.md")
        return target_base / short_name / skill_fn
    return target_base / f"{short_name}.md"


def _norm_path(path: Path) -> str:
    """Нормализует путь для безопасного сравнения."""
    return str(path.resolve(strict=False))


def detect_existing_component_symlinks(
    graph: FrameworkGraph,
    ide_key: str,
    project_dir: Path,
) -> Set[str]:
    """
    Определяет уже установленные компоненты по существующим симлинкам проекта.
    Возвращает набор comp_id, для которых симлинк указывает на ожидаемый источник.
    """
    detected: Set[str] = set()

    for comp in graph.get_installable_for_user():
        # Навыки: проверяем оба варианта (short_name и short_name.md) для совместимости
        target_candidates = [
            _component_target_file(comp, ide_key, project_dir, use_symlinks=True),
            _component_target_file(comp, ide_key, project_dir, use_symlinks=False),
        ]
        target_file = next((t for t in target_candidates if t.is_symlink()), None)
        if not target_file:
            continue

        _, expected_source, _ = _component_source_paths(comp)
        try:
            raw_link = os.readlink(target_file)
        except OSError:
            continue

        link_target = Path(raw_link)
        if not link_target.is_absolute():
            link_target = target_file.parent / link_target

        if _norm_path(link_target) == _norm_path(expected_source):
            detected.add(comp.id)

    return detected


def install_components(
    graph: FrameworkGraph,
    selected_ids: Set[str],
    existing_symlink_ids: Set[str],
    ide_key: str,
    project_dir: Path,
    use_symlinks: bool,
    dry_run: bool = False,
) -> Tuple[int, int, int]:
    """
    Устанавливает выбранные компоненты в директорию проекта.
    Возвращает (установлено/обновлено, пропущено, удалено).
    """
    ide_cfg = IDE_CONFIGS[ide_key]

    # Резолвим зависимости
    all_ids = graph.resolve_dependencies(selected_ids)
    extra_deps = all_ids - selected_ids
    to_remove = existing_symlink_ids - all_ids

    if extra_deps:
        print(f"\n  {bold('Добавлены зависимости')} ({len(extra_deps)} шт.):")
        for dep_id in sorted(extra_deps):
            comp = graph.components.get(dep_id)
            name = comp.display_name if comp else dep_id
            print(f"    + {dep_id:<40} {dim(name)}")

    if to_remove:
        print(f"\n  {bold('Будут удалены снятые компоненты')} ({len(to_remove)} шт.):")
        for comp_id in sorted(to_remove):
            comp = graph.components.get(comp_id)
            name = comp.display_name if comp else comp_id
            print(f"    - {comp_id:<40} {dim(name)}")

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
            return 0, 0, 0

    installed = 0
    skipped = 0
    removed = 0

    for comp_id in sorted(to_remove):
        comp = graph.components.get(comp_id)
        if not comp:
            continue

        for use_sl in (True, False):
            target_file = _component_target_file(comp, ide_key, project_dir, use_symlinks=use_sl)
            if target_file.is_symlink():
                if dry_run:
                    print(f"    ← remove symlink {target_file}")
                    removed += 1
                else:
                    target_file.unlink()
                    removed += 1
                break

    for comp_id in sorted(all_ids):
        comp = graph.components.get(comp_id)
        if not comp:
            print(yellow(f"  ⚠ Компонент {comp_id} не найден в графе. Пропуск."))
            skipped += 1
            continue
        
        # Блокируем установку framework-meta навыков
        if graph.is_framework_meta_skill(comp_id):
            print(yellow(f"  ⚠ Навык {comp_id} предназначен только для изменения фреймворка."))
            print(yellow(f"    Откройте каталог фреймворка как проект в IDE для его использования."))
            skipped += 1
            continue

        source_file, source_path, _ = _component_source_paths(comp)
        target_file = _component_target_file(comp, ide_key, project_dir, use_symlinks=use_symlinks)

        # IDE с кастомным именем файла навыка (skill.md вместо SKILL.md)
        # При симлинках: создаём каталог навыка и симлинк на файл.
        skill_fn = ide_cfg.get("skill_filename")
        use_skill_file_link = (
            comp.type == "skill" and skill_fn and skill_fn != "SKILL.md" and use_symlinks
        )
        if use_skill_file_link:
            target_file = _component_target_file(comp, ide_key, project_dir, use_symlinks=False)

        if dry_run:
            if use_skill_file_link:
                action = f"→ (symlink file, {skill_fn})"
            else:
                action = "→ (symlink)" if use_symlinks else "→ (copy)"
            print(f"    {action} {target_file}")
            installed += 1
            continue

        # Сначала удаляем старый файл/симлинк (до mkdir, чтобы не создавать каталог через симлинк)
        if comp.type == "skill":
            symlink_target = _component_target_file(comp, ide_key, project_dir, use_symlinks=True)
            _, _, short_name = _component_source_paths(comp)
            target_base = project_dir / ide_cfg[_dir_key_for_component(comp.type, ide_cfg)]
            old_md = target_base / f"{short_name}.md"
            # 1) Сначала убираем симлинк — иначе удаление short_name/SKILL.md затронет исходник
            if symlink_target.is_symlink():
                symlink_target.unlink()
            # 2) Старый формат short_name.md
            if old_md.exists():
                old_md.unlink()
            # 3) short_name/SKILL.md — только если реальный файл (не через симлинк)
            if target_file.exists() and not target_file.parent.is_symlink():
                target_file.unlink()
        else:
            for old_path in {
                target_file,
                _component_target_file(comp, ide_key, project_dir, use_symlinks=not use_symlinks),
            }:
                if old_path.exists() or old_path.is_symlink():
                    old_path.unlink()

        target_file.parent.mkdir(parents=True, exist_ok=True)

        if use_skill_file_link:
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
        elif use_symlinks:
            try:
                try:
                    rel_path = os.path.relpath(source_path, target_file.parent)
                    target_file.symlink_to(rel_path)
                except ValueError:
                    target_file.symlink_to(source_path)
                installed += 1
            except OSError as e:
                print(red(f"  ✗ Ошибка симлинка {comp.id}: {e}"))
                # Fallback: копируем файл (для навыков — SKILL.md в short_name.md)
                copy_target = (
                    _component_target_file(comp, ide_key, project_dir, use_symlinks=False)
                    if comp.type == "skill"
                    else target_file
                )
                if comp.type == "skill":
                    shutil.copy2(source_file, copy_target)
                else:
                    shutil.copy2(source_path, copy_target)
                print(yellow(f"    → скопирован как fallback"))
                installed += 1
        else:
            # При копировании: для навыков копируем SKILL.md, для остальных — сам файл
            if comp.type == "skill":
                shutil.copy2(source_file, target_file)
            else:
                shutil.copy2(source_path, target_file)
            installed += 1

    return installed, skipped, removed


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
        print(f"  Для исправления: переустановите фреймворк (python tools/1c-ai-agent-cli.py)")


# ─── Команда clone ────────────────────────────────────────────────────────────

def cmd_clone(args: argparse.Namespace) -> int:
    """Клонирует репозиторий фреймворка в целевую директорию."""
    target = args.target.resolve()
    if target.exists() and any(target.iterdir()):
        print(red(f"  Директория {target} не пуста. Укажите пустую или несуществующую."))
        return 1

    target.parent.mkdir(parents=True, exist_ok=True)

    cmd = ["git", "clone"]
    if args.branch:
        cmd.extend(["--branch", args.branch])
    if args.depth > 0:
        cmd.extend(["--depth", str(args.depth)])
    cmd.extend([REPO_URL, str(target)])

    print(f"  Клонирование: {REPO_URL}")
    print(f"  В: {target}")
    if args.branch:
        print(f"  Ветка: {args.branch}")
    print()

    try:
        result = subprocess.run(cmd, check=False)
        if result.returncode != 0:
            return result.returncode
    except FileNotFoundError:
        print(red("  git не найден. Установите Git и повторите."))
        return 1

    print(green(f"  ✓ Успешно клонировано в {target}"))
    if args.install:
        print()
        print(bold("  Запуск установщика..."))
        install_script = target / "tools" / "1c-ai-agent-cli.py"
        if install_script.exists():
            subprocess.run([sys.executable, str(install_script)], cwd=str(target))
        else:
            print(yellow(f"  CLI не найден в {target}. Запустите вручную: python tools/1c-ai-agent-cli.py"))
    return 0


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
        prog="1c-ai-agent-cli",
        description="1C BSL Agent Framework — CLI (clone, install)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Примеры:
  python tools/1c-ai-agent-cli.py clone                       # Клонировать репозиторий
  python tools/1c-ai-agent-cli.py clone -t ./my-fw --install  # Клонировать и установить
  python tools/1c-ai-agent-cli.py install                     # Интерактивный режим
  python tools/1c-ai-agent-cli.py --ide cursor --list           # Показать дерево
  python tools/1c-ai-agent-cli.py --ide cursor --all          # Установить всё
  python tools/1c-ai-agent-cli.py --ide cursor --include agent/developer workflow/full-cycle
  python tools/1c-ai-agent-cli.py --relink                     # Проверить симлинки
        """,
    )

    subparsers = parser.add_subparsers(dest="command", help="Команда")
    subparsers.required = False
    subparsers.default = "install"

    # clone
    clone_parser = subparsers.add_parser("clone", help="Клонировать репозиторий фреймворка")
    clone_parser.add_argument("--target", "-t", type=Path, default=Path("1c-agent-based-dev-framework"),
                              help="Целевая директория (по умолчанию: 1c-agent-based-dev-framework)")
    clone_parser.add_argument("--branch", "-b", default="",
                              help="Ветка или тег (по умолчанию: ветка по умолчанию репозитория)")
    clone_parser.add_argument("--depth", type=int, default=0,
                              help="Глубина shallow clone, 0 = полный клон")
    clone_parser.add_argument("--install", action="store_true",
                              help="Запустить установщик после клонирования")

    subparsers.add_parser("install", help="Установить компоненты в проект")

    # Аргументы install (на main parser для обратной совместимости)
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
    parser.add_argument("--sync", action="store_true",
                        help="Синхронизировать установку: удалить снятые симлинки компонентов")

    args = parser.parse_args()

    # Banner
    print()
    print(bold("  ┌──────────────────────────────────────────────────┐"))
    print(bold("  │   1C BSL Agent Framework — CLI                   │"))
    print(bold("  │   Версия: 0.2                                    │"))
    print(bold("  └──────────────────────────────────────────────────┘"))

    system = platform.system()
    print(f"  Система: {system} ({platform.machine()})")
    print(f"  Python: {sys.version.split()[0]}")

    # Команда clone
    if args.command == "clone":
        sys.exit(cmd_clone(args))

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

    # Выбор каталога проекта и компонентов (интерактивно, если не задано через CLI)
    is_interactive = not (args.all or args.include)
    preselected: Set[str] = set()

    if args.all:
        project_dir = args.project_dir.resolve()
        selected = {c.id for c in graph.get_installable_for_user()}
    elif args.include:
        project_dir = args.project_dir.resolve()
        selected = set(args.include)
        invalid = selected - set(graph.components.keys())
        if invalid:
            print(red(f"\n  Неизвестные компоненты: {', '.join(sorted(invalid))}"))
            print(f"  Используйте --list для просмотра доступных ID.")
            sys.exit(1)
    else:
        # Интерактивный режим: каталог + компоненты, с возможностью «назад»
        while True:
            if is_interactive and args.project_dir == Path("."):
                project_dir = select_project_dir(args.project_dir, ide_key)
            else:
                project_dir = args.project_dir.resolve()

            preselected = detect_existing_component_symlinks(graph, ide_key, project_dir)
            if preselected:
                print(f"\n  Найдено существующих симлинков компонентов: {bold(str(len(preselected)))}")
                print(f"  {dim('Флаги в списке предварительно расставлены по текущей установке.')}")
            selected = interactive_select(graph, preselected=preselected)

            if _HAS_TUI and selected is BACK:
                continue  # назад к выбору каталога
            if selected is None:
                sys.exit(0)
            if not selected:
                print(yellow("  Ничего не выбрано."))
                sys.exit(0)
            break

    # ── Шаг: Настройка моделей для агентов ──
    script_dir = Path(__file__).resolve().parent
    model_defaults = load_model_defaults(script_dir)
    model_map: Dict[str, str] = {}

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
                    print(green(f"\n  ✓ Модели обновлены в {changed} agent-файлах (framework/subagents/)"))
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

    if is_interactive and not (args.all or args.include):
        sync_source = preselected
    elif args.sync:
        sync_source = detect_existing_component_symlinks(graph, ide_key, project_dir)
        print(f"\n  Режим синхронизации (--sync): найдено текущих симлинков {bold(str(len(sync_source)))}")
    else:
        sync_source = set()

    installed, skipped, removed = install_components(
        graph=graph,
        selected_ids=selected,
        existing_symlink_ids=sync_source,
        ide_key=ide_key,
        project_dir=project_dir,
        use_symlinks=use_symlinks,
        dry_run=args.dry_run,
    )

    print()
    if args.dry_run:
        print(f"  {bold('[DRY RUN]')} Было бы установлено: {installed}, удалено: {removed}, пропущено: {skipped}")
    else:
        print(green(f"  ✓ Установлено: {installed} компонентов"))
        if removed:
            print(green(f"  ✓ Удалено симлинков: {removed}"))
        if skipped:
            print(yellow(f"  ⚠ Пропущено: {skipped}"))

    # Подсказка по проектным навыкам
    ide_cfg = IDE_CONFIGS[ide_key]
    print(f"\n  {bold('Проектные навыки')}: размещайте в {cyan(ide_cfg['skills_dir'] + '/')} вашего проекта")

    if use_symlinks:
        print(f"\n  {dim('Симлинки привязаны к расположению фреймворка.')}")
        print(f"  {dim('Если переместите framework/ — запустите: python tools/1c-ai-agent-cli.py --relink')}")

    # Лог сессии для верификации (не в dry-run)
    if not args.dry_run:
        write_session_log(
            project_dir=project_dir,
            ide_key=ide_key,
            selected=selected,
            model_map=model_map,
            use_symlinks=use_symlinks,
            installed=installed,
            skipped=skipped,
            removed=removed,
        )

    print()


if __name__ == "__main__":
    main()
