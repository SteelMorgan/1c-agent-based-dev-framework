#!/usr/bin/env python3
"""
1C BSL Agent Framework — CLI (clone, install)

Кроссплатформенный скрипт (Windows / Linux / macOS):
  - clone: клонирование репозитория фреймворка
  - install: установка компонентов в проект с учётом целевой IDE

Использование:
    python tools/install.py clone                    # Клонировать репозиторий
    python tools/install.py clone -t ./fw --install  # Клонировать и установить
    python tools/install.py                          # Интерактивный режим установки
    python tools/install.py --ide cursor --list      # Показать дерево компонентов
    python tools/install.py --ide cursor --all       # Установить всё
    python tools/install.py --relink                 # Пересоздать симлинки

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

try:
    import tiktoken
    _TOKENIZER = tiktoken.get_encoding("cl100k_base")
except Exception:
    _TOKENIZER = None

# Версия CLI/инсталлятора
CLI_VERSION = "0.4.1"

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
        "rules_ext": ".mdc",
        "skills_dir": ".cursor/skills",
        "agents_dir": ".cursor/agents",
        "description": "Cursor IDE — правила в .cursor/rules/ (alwaysApply:true), навыки в .cursor/skills/",
        # Cursor auto-loads .mdc files from .cursor/rules/ with alwaysApply:true — no manual import
    },
    "claude-code": {
        "name": "Claude Code",
        # Официальная схема (docs.anthropic.com):
        #   CLAUDE.md — грузится автоматически при старте (в контекст)
        #   .claude/rules/ — официальное место для разбивки; импортируется через @path в CLAUDE.md
        # Наш installer кладёт правила в .claude/rules/ — правильное место,
        # но CLAUDE.md с @-импортами нужно создать вручную.
        "rules_dir": ".claude/rules",
        "rules_ext": ".md",
        "skills_dir": ".claude/skills",
        "agents_dir": ".claude/agents",
        "description": "Claude Code — CLAUDE.md + .claude/rules/, навыки в .claude/skills/, агенты в .claude/agents/",
        # .claude/rules/*.md не грузятся сами — нужен CLAUDE.md с @-импортами
        "rules_import_hint": {
            "file": "CLAUDE.md",
            "format": "@.claude/rules/{name}.md",
            "note": (
                "CLAUDE.md грузится автоматически при старте Claude Code.\n"
                "Файлы из .claude/rules/ — официальный механизм разбивки,\n"
                "но импортируются только через @-ссылки в CLAUDE.md.\n\n"
                "Создайте CLAUDE.md в корне проекта и добавьте импорты\n"
                "(рекомендуется держать CLAUDE.md до 200 строк — docs.anthropic.com):"
            ),
        },
    },
    "windsurf": {
        "name": "Windsurf",
        # Нативно Windsurf читает только .windsurfrules (один файл в корне).
        # .windsurf/rules/ — наша конвенция, нативно НЕ сканируется.
        "rules_dir": ".windsurf/rules",
        "skills_dir": ".windsurf/skills",
        "description": "Windsurf — .windsurf/rules/ + .windsurfrules в корне, навыки в .windsurf/skills/",
        # Windsurf нативно читает только .windsurfrules — нужно добавить ссылки туда
        "rules_import_hint": {
            "file": ".windsurfrules",
            "format": "# See .windsurf/rules/{name}.md",
            "note": (
                "Windsurf нативно читает только файл .windsurfrules в корне проекта.\n"
                "Директория .windsurf/rules/ НЕ сканируется автоматически.\n\n"
                "Добавьте в .windsurfrules упоминание установленных правил\n"
                "или скопируйте их содержимое напрямую. Минимальный вариант:"
            ),
        },
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
        "description": "RooCode — .roo/rules/ (все файлы авто), навыки в .roo/skills/, агенты в .roo/agents/",
        # RooCode auto-loads ALL files from .roo/rules/ — no manual import needed
    },
    "kilocode": {
        "name": "Kilo Code",
        "rules_dir": ".kilocode/rules",
        "skills_dir": ".kilocode/skills",
        "agents_dir": ".kilocode/agents",
        "description": "Kilo Code — .kilocode/rules/ (авто, RooCode-форк), агенты в .kilocode/agents/",
        # Kilo Code (RooCode fork) auto-loads ALL files from .kilocode/rules/ — no hint needed
    },
    "cilocode": {
        "name": "Cilo Code (alias Kilo Code)",
        "rules_dir": ".kilocode/rules",
        "skills_dir": ".kilocode/skills",
        "agents_dir": ".kilocode/agents",
        "description": "Alias для Kilo Code: .kilocode/rules/ (авто), агенты в .kilocode/agents/",
        # Same as kilocode — auto-loads all files from .kilocode/rules/
    },
    "kiro": {
        "name": "Kiro",
        "rules_dir": ".kiro/steering",
        "skills_dir": ".kiro/skills",
        "agents_dir": ".kiro/agents",
        "description": "Kiro — .kiro/steering/ (авто при inclusion:always), навыки в .kiro/skills/",
        # Kiro auto-loads steering docs with `inclusion: always` frontmatter
        "rules_import_hint": {
            "file": None,  # no entry-point file, but frontmatter required
            "format": "",
            "note": (
                "Kiro загружает steering-файлы из .kiro/steering/ только при наличии\n"
                "frontmatter-заголовка inclusion: always в каждом файле.\n\n"
                "Если правила не срабатывают, добавьте в начало каждого .md файла:\n\n"
                "  ---\n"
                "  inclusion: always\n"
                "  ---"
            ),
        },
    },
    "codex": {
        "name": "Codex CLI",
        "rules_dir": ".codex/rules",
        "skills_dir": ".codex/skills",
        "agents_dir": ".codex/agents",
        "description": "Codex CLI (OpenAI) — правила → навыки .codex/skills/<name>/ (dir symlink), агенты → .codex/agents/<name>.toml",
        # Codex-специфика (см. _codex_mode / _install_codex_component):
        #   rule/workflow → .codex/skills/<name>/ (симлинк на каталог с SKILL.md)
        #   agent → .codex/agents/<agent-name>.toml (конвертация: frontmatter name/description + тело → developer_instructions)
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
    """Парсит frontmatter и технические ---блоки--- в теле файла.

    Ищет блоки в двух позициях:
    1. Сразу после frontmatter (до первого заголовка #) — стиль full-cycle.
    2. В конце файла (backmatter) — стиль subagent/*.md.
    Из блоков берём только технические ключи (depends_on и др.).
    """
    try:
        text = filepath.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return None

    front = FRONTMATTER_RE.match(text)
    if not front:
        return None

    data = _parse_simple_yaml_block(front.group(1))

    TECHNICAL_KEYS = {"depends_on", "requires", "metadata", "category", "version"}
    body = text[front.end():]

    # Позиция 1: блок сразу после frontmatter (до первого заголовка)
    # Тело может начинаться сразу с --- (без ведущего \n)
    early_block_re = re.compile(r"^\s*---\s*\n(.*?)\n---\s*(?:\n|$)", re.DOTALL)
    m = early_block_re.match(body)
    if m:
        block_data = _parse_simple_yaml_block(m.group(1))
        for key in TECHNICAL_KEYS:
            if key in block_data:
                data[key] = block_data[key]

    # Позиция 2: backmatter — блок в самом конце файла
    # BACKMATTER_RE ищет \n---\n...\n---\n?$ — работает для subagent/*.md
    back = BACKMATTER_RE.search(text)
    if back:
        block_data = _parse_simple_yaml_block(back.group(1))
        for key in TECHNICAL_KEYS:
            if key in block_data:
                data[key] = block_data[key]

    return data


# ─── Граф компонентов ─────────────────────────────────────────────────────────

class Component:
    """Один компонент фреймворка (файл .md с frontmatter)."""

    def __init__(self, id: str, type: str, depends_on: List[str], filepath: Path,
                 requires: Optional[List[str]] = None,
                 always_apply: bool = False):
        self.id = id
        self.type = type
        self.depends_on = depends_on
        self.requires = requires or []
        self.filepath = filepath
        # alwaysApply: true в frontmatter → правило считается guardrail/триггером.
        # Физический канал установки зависит от IDE.
        self.always_apply = always_apply

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

    def __init__(self, framework_dir: Path, mirror_dir: Optional[Path] = None):
        self.framework_dir = framework_dir
        # mirror_dir — EN-зеркало (framework_eng/). Симлинки создаются на него.
        # Если не задан или не существует — симлинки на оригинальный framework_dir.
        self.mirror_dir = mirror_dir if (mirror_dir and mirror_dir.is_dir()) else None
        self.components: Dict[str, Component] = {}
        self._reverse_deps: Dict[str, Set[str]] = {}  # Кэш обратных зависимостей
        self._codex_prefixed_rules: Optional[Set[str]] = None  # Кэш правил с коллизией имён
        self._scan()
        self._build_reverse_deps()

    def codex_prefixed_rule_names(self) -> Set[str]:
        """short_name правил, имя которых совпадает с устанавливаемым навыком.

        В Codex правила разворачиваются в общий .codex/skills/. Если правило
        одноимённо навыку (coding-standards), каталоги коллидируют — такому
        правилу даём префикс rule_ (→ .codex/skills/rule_coding-standards/).
        """
        if self._codex_prefixed_rules is None:
            skill_names: Set[str] = set()
            rule_names: Set[str] = set()
            for c in self.get_installable_for_user():
                _, _, sn = _component_source_paths(c)
                if c.type == "skill":
                    skill_names.add(sn)
                elif c.type == "rule":
                    rule_names.add(sn)
            self._codex_prefixed_rules = rule_names & skill_names
        return self._codex_prefixed_rules

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
        """Сканирует framework/ на .md и .mdc файлы с frontmatter."""
        for md_file in sorted(self.framework_dir.rglob("*")):
            if md_file.suffix not in (".md", ".mdc"):
                continue
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

            # Для агентов/субагентов: если backmatter depends_on отсутствует —
            # строим зависимости из списка skills: в frontmatter.
            # Если depends_on уже есть (backmatter) — не дублируем.
            if comp_type in ("agent", "subagent") and "skills" in fm and not depends:
                skills = fm.get("skills", [])
                if isinstance(skills, list):
                    skill_paths = []
                    for skill_name in skills:
                        skill_path = self._find_skill_path(skill_name)
                        if skill_path:
                            skill_paths.append(skill_path)
                    depends = skill_paths
            
            if isinstance(depends, str):
                depends = [depends] if depends else []

            # Ресурсные зависимости (requires: [tools])
            requires = fm.get("requires", [])
            if isinstance(requires, str):
                requires = [requires] if requires else []

            # alwaysApply: true → правило-guardrail/триггер.
            # Без флага → on-demand правило.
            # Навыки (skill) флаг не используют — они всегда в skills_dir, не в rules_dir.
            always_apply = str(fm.get("alwaysApply", "")).strip().lower() == "true"

            if comp_type == "template":
                continue

            self.components[comp_id] = Component(
                id=comp_id,
                type=comp_type,
                depends_on=depends,
                filepath=md_file,
                requires=requires,
                always_apply=always_apply,
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

    def is_always_on_rule(self, comp_id: str) -> bool:
        """Возвращает True, если правило помечено alwaysApply: true.

        Только правила (type=rule) из always-on каталога IDE становятся guardrail/триггерами.
        Workflow-файлы и всё без флага — только component_map (on-demand).
        Навыки (skill) этот метод не касается — они всегда в skills_dir.
        """
        comp = self.components.get(comp_id)
        if not comp:
            return False
        return comp.always_apply
    
    def is_framework_meta_skill(self, comp_id: str) -> bool:
        """Проверяет, является ли навык служебным (framework-meta).

        Навыки с ``installable: true`` в frontmatter не считаются служебными
        и доступны для установки в проекты пользователей.
        """
        comp = self.components.get(comp_id)
        if not comp or comp.type != "skill":
            return False
        # installable: true — явное разрешение на установку в проект
        fm = parse_frontmatter(comp.filepath)
        if fm and str(fm.get("installable", "")).lower() == "true":
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
    "subagent": "Агенты",
    "workflow": "Воркфлоу",
}

TYPE_ICONS = {
    "skill": "📘",
    "rule": "📋",
    "subagent": "🤖",
    "workflow": "🔄",
}

# Подкатегории навыков (папки в framework/skills/)
SKILL_CATEGORY_LABELS = {
    "bsl-practices": "BSL-практики",
    "tool-usage": "Использование инструментов",
    "tool-usage/xml-generation": "XML-генерация",
    "spec-writing": "Спецификации",
    "framework-meta": "Framework-meta",
    "other": "Прочее",
}


def print_tree(graph: FrameworkGraph, selected: Optional[Set[str]] = None):
    """Выводит дерево компонентов, сгруппированное по типу и папкам навыков.

    Для секции «Правила»:
    - always-on правила (alwaysApply: true) выводятся со связанным навыком-парой
      (→ имя навыка), если такая пара существует по _build_trigger_skill_pairs.
    - on-demand правила (без alwaysApply или alwaysApply: false) выводятся в
      отдельной подгруппе «on-demand (component_map)» с явной пометкой — чтобы
      пользователь видел, что они НЕ попадут в always-on канал IDE.
    """
    installable = graph.get_installable_for_user()
    fw_dir = graph.framework_dir

    idx = 1
    idx_map: Dict[int, str] = {}

    for comp_type in ["workflow", "subagent", "rule", "skill"]:
        comps = [c for c in installable if c.type == comp_type]
        if not comps:
            continue

        label = TYPE_LABELS.get(comp_type, comp_type)
        icon = TYPE_ICONS.get(comp_type, "📄")
        print(f"\n  {icon} {bold(label)}")

        if comp_type == "rule":
            # Строим пары «правило-триггер ↔ навык» для always-on правил
            all_rule_ids = {c.id for c in comps}
            # Для пар нужны и навыки из installable — передаём все installable ids
            all_installable_ids = {c.id for c in installable}
            pairs_map: Dict[str, Optional[str]] = {
                r: s for r, s in _build_trigger_skill_pairs(graph, all_installable_ids)
            }

            always_on = [c for c in comps if c.always_apply]
            on_demand = [c for c in comps if not c.always_apply]

            # Подгруппа: always-on правила (попадают в rules_dir IDE)
            if always_on:
                print(f"\n    {dim('always-on (в rules-каталог IDE)')}")
                for c in sorted(always_on, key=lambda x: x.id):
                    marker = green(" ✓") if selected and c.id in selected else ""
                    linked = graph.get_linked_components(c.id)
                    link_str = cyan(f" ↔ {', '.join(lid.split('/')[-1] for lid in linked)}") if linked else ""
                    # Показываем связанный навык из пар (если есть)
                    paired_skill = pairs_map.get(c.id)
                    pair_str = dim(f" → {paired_skill.split('/')[-1]}") if paired_skill else ""
                    print(f"    {cyan(str(idx).rjust(3))}  {c.id:<40} {c.display_name}{pair_str}{link_str}{marker}")
                    idx_map[idx] = c.id
                    idx += 1

            # Подгруппа: on-demand правила (только component_map, НЕ в always-on канал)
            if on_demand:
                print(f"\n    {dim('on-demand (только component_map, НЕ в always-on канал IDE)')}")
                for c in sorted(on_demand, key=lambda x: x.id):
                    marker = green(" ✓") if selected and c.id in selected else ""
                    linked = graph.get_linked_components(c.id)
                    link_str = cyan(f" ↔ {', '.join(lid.split('/')[-1] for lid in linked)}") if linked else ""
                    demand_note = yellow(" (on-demand)")
                    print(f"    {cyan(str(idx).rjust(3))}  {c.id:<40} {c.display_name}{demand_note}{link_str}{marker}")
                    idx_map[idx] = c.id
                    idx += 1

        elif comp_type == "skill":
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
                linked = graph.get_linked_components(c.id)
                link_str = cyan(f" ↔ {', '.join(lid.split('/')[-1] for lid in linked)}") if linked else ""
                # Для субагентов: показываем оценку контекста (навыки + правила зависимостей)
                if comp_type in ("agent", "subagent") and c.depends_on:
                    deps = graph.resolve_dependencies({c.id})
                    dep_comps = [
                        graph.components[d] for d in deps
                        if d in graph.components and graph.components[d].type in ("rule", "skill")
                    ]
                    eng_text = "\n".join(_collect_component_text_en(d, graph.mirror_dir) for d in dep_comps)
                    eng_tokens = _estimate_tokens(eng_text)
                    ctx_str = dim(f" [~{eng_tokens} токенов]")
                else:
                    deps_count = len(c.depends_on)
                    ctx_str = dim(f" ({deps_count} зав.)") if deps_count else ""
                print(f"    {cyan(str(idx).rjust(3))}  {c.id:<40} {c.display_name}{ctx_str}{link_str}{marker}")
                idx_map[idx] = c.id
                idx += 1

    return idx_map


def select_ides() -> List[str]:
    """Интерактивный выбор одной или нескольких IDE (TUI или текст).

    Возвращает список выбранных ключей IDE.
    """
    ide_list = list(IDE_CONFIGS.keys())

    # TUI mode — select_many принимает List[Tuple[id, label, desc, is_header]]
    # и возвращает Set[str] (набор выбранных id)
    if _HAS_TUI and is_tui_available():
        try:
            # ChecklistItem = Tuple[str, str, str, bool]: (id, label, description, is_header)
            checklist: List = [
                (key, cfg["name"], cfg["description"], False)
                for key, cfg in IDE_CONFIGS.items()
            ]
            while True:
                result = select_many(
                    "Выберите IDE  [Пробел — отметить, Enter — подтвердить, нужна ≥1]:",
                    checklist,
                    allow_back=False,
                )
                if result is None:
                    sys.exit(0)
                # result — Set[str] с выбранными id (ключами IDE)
                # Сохраняем порядок из ide_list
                selected_keys = [k for k in ide_list if k in result]
                if selected_keys:
                    return selected_keys
                # Пустой выбор — показываем снова с подсказкой
        except Exception:
            pass
        # Fallback: одиночный выбор
        items = [(cfg["name"], cfg["description"]) for cfg in IDE_CONFIGS.values()]
        idx = select_one("Выберите IDE:", items)
        if idx < 0:
            sys.exit(0)
        return [ide_list[idx]]

    # Text fallback — с поддержкой множественного ввода
    print(f"\n{'─' * 70}")
    print(bold("  Выберите IDE"))
    print(f"{'─' * 70}")
    print(f"\n  {dim('Можно выбрать несколько: например, 1,3 или 1-3')}\n")
    for i, key in enumerate(ide_list, 1):
        cfg = IDE_CONFIGS[key]
        hint = ""
        if "rules_import_hint" in cfg:
            hint = yellow(" ⚠ требует ручной настройки")
        print(f"  {cyan(str(i).rjust(2))}  {cfg['name']:<25} {dim(cfg['description'])}{hint}")

    while True:
        try:
            choice = input(f"\n{bold('IDE')} [1-{len(ide_list)}]: ").strip()
            nums = _parse_numbers(choice)
            keys = []
            for n in nums:
                if 1 <= n <= len(ide_list):
                    key = ide_list[n - 1]
                    if key not in keys:
                        keys.append(key)
            if keys:
                return keys
        except (ValueError, EOFError):
            pass
        print(red("  Некорректный выбор. Повторите."))


# Обратная совместимость для кода который ещё вызывает select_ide()
def select_ide() -> str:
    return select_ides()[0]


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
    """Строит список элементов для TUI-чеклиста с группировкой по типам и папкам навыков.

    Для секции «Правила»:
    - always-on правила выводятся в подгруппе «always-on», со связанным навыком в описании
      (переиспользует _build_trigger_skill_pairs, не дублирует логику).
    - on-demand правила выводятся в подгруппе «on-demand (component_map)» с явной пометкой
      в описании — чтобы пользователь видел, что в always-on канал IDE они не попадут.
    """
    items = []  # (id, label, description, is_header)
    installable = graph.get_installable_for_user()
    fw_dir = graph.framework_dir

    types_order = ["workflow", "agent", "subagent", "rule", "skill"]
    for comp_type in types_order:
        comps = [c for c in installable if c.type == comp_type]
        if not comps:
            continue

        label = TYPE_LABELS.get(comp_type, comp_type)
        icon = TYPE_ICONS.get(comp_type, "📄")
        items.append(("", f"{icon} {label}", "", True))

        if comp_type == "rule":
            # Строим пары «правило ↔ навык» для обогащения описания
            all_installable_ids = {c.id for c in installable}
            pairs_map: Dict[str, Optional[str]] = {
                r: s for r, s in _build_trigger_skill_pairs(graph, all_installable_ids)
            }

            always_on = [c for c in comps if c.always_apply]
            on_demand = [c for c in comps if not c.always_apply]

            # Подгруппа: always-on (попадают в rules_dir IDE)
            if always_on:
                items.append(("", "    always-on (в rules-каталог IDE)", "", True))
                for c in sorted(always_on, key=lambda x: x.id):
                    paired_skill = pairs_map.get(c.id)
                    desc = c.short_description()
                    if paired_skill:
                        desc = f"{desc} → навык: {paired_skill.split('/')[-1]}"
                    items.append((c.id, c.id, desc, False))

            # Подгруппа: on-demand (только component_map, НЕ в always-on канал)
            if on_demand:
                items.append(("", "    on-demand (component_map, не в always-on канал IDE)", "", True))
                for c in sorted(on_demand, key=lambda x: x.id):
                    desc = f"[on-demand] {c.short_description()}"
                    items.append((c.id, c.id, desc, False))

        elif comp_type == "skill":
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
                # Для агентов/субагентов — описание с оценкой контекста навыков+правил
                if comp_type in ("agent", "subagent") and c.depends_on:
                    deps = graph.resolve_dependencies({c.id})
                    dep_comps = [
                        graph.components[d] for d in deps
                        if d in graph.components and graph.components[d].type in ("rule", "skill")
                    ]
                    eng_text = "\n".join(_collect_component_text_en(d, graph.mirror_dir) for d in dep_comps)
                    eng_tokens = _estimate_tokens(eng_text)
                    desc = f"{c.short_description()} [~{eng_tokens} токенов]"
                else:
                    desc = c.short_description()
                items.append((c.id, c.id, desc, False))

    return items


def interactive_select(
    graph: FrameworkGraph,
    preselected: Optional[Set[str]] = None,
    show_context_estimate: bool = False,
    show_english_estimate: bool = False,
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

        def _status_lines(selected_ids: Set[str]) -> List[str]:
            if not selected_ids:
                return []
            always_tokens, on_demand_tokens, total_tokens, english_total, savings = estimate_context_usage(
                graph,
                selected_ids,
            )
            savings_str = f" (экономия {savings})" if savings else ""
            return [
                f"Контекст: ~{total_tokens} токенов (всегда {always_tokens} + по треб. {on_demand_tokens})",
                f"English-only: ~{english_total}{savings_str}",
            ]

        result = select_many(
            "Выберите компоненты:",
            items,
            preselected=preselected,
            on_open=_get_component_path,
            get_dependencies=graph.get_all_dependencies,
            get_required_by=graph.get_required_by,
            get_mutual=lambda cid: graph.get_linked_components(cid),
            status_lines_provider=_status_lines,
        )
        return result  # None | BACK | Set[str]

    # Text fallback
    selected: Set[str] = set(preselected or set())
    # Отслеживаем, какие зависимости были добавлены автоматически и кем
    # auto_deps[dep_id] = set of comp_ids that caused this dep to be auto-added
    auto_deps: Dict[str, Set[str]] = {}

    # Инициализируем auto_deps для preselected (тихо, без вывода)
    for _pre_cid in list(preselected or []):
        for _dep in graph.get_all_dependencies(_pre_cid) - {_pre_cid}:
            auto_deps.setdefault(_dep, set()).add(_pre_cid)

    def _auto_add_deps(cid: str) -> None:
        """Авто-добавляет транзитивные зависимости компонента cid, помечая их как авто."""
        deps = graph.get_all_dependencies(cid) - {cid}
        newly_added = []
        for dep in deps:
            if dep not in selected:
                selected.add(dep)
                newly_added.append(dep)
            auto_deps.setdefault(dep, set()).add(cid)
        if newly_added:
            for dep in sorted(newly_added):
                comp = graph.components.get(dep)
                name = comp.display_name if comp else dep
                print(cyan(f"  + Авто-добавлена зависимость: {dep} ({name})"))

    def _auto_remove_orphan_deps(cid: str) -> None:
        """При снятии cid убирает его авто-зависимости, которые больше никем не нужны."""
        # Находим зависимости, которые cid добавил автоматически
        my_auto = {dep for dep, causes in auto_deps.items() if cid in causes}
        for dep in my_auto:
            auto_deps[dep].discard(cid)
        # Убираем те, у которых не осталось живых причин.
        # dep считается "явно пользовательским" только если его нет в auto_deps вообще.
        # Пустое множество causes означает, что все авто-причины сняты → dep-сирота.
        for dep in sorted(my_auto):
            # Живые причины: компоненты ещё в selected, которые требуют этот dep
            remaining_causes = auto_deps.get(dep, set()) & selected
            # Явно выбранный пользователем = dep вообще не попал в auto_deps
            # (пользователь выбрал его вручную до того, как была вызвана _auto_add_deps)
            is_user_explicit = dep not in auto_deps
            if not remaining_causes and not is_user_explicit and dep in selected:
                # Финальная проверка: не нужен ли dep другим выбранным компонентам
                still_needed = graph.get_required_by(dep) & selected
                if not still_needed:
                    selected.discard(dep)
                    auto_deps.pop(dep, None)
                    comp = graph.components.get(dep)
                    name = comp.display_name if comp else dep
                    print(cyan(f"  - Авто-снята сиротская зависимость: {dep} ({name})"))

    def _can_deselect(cid: str) -> Optional[str]:
        """Проверяет, можно ли снять cid. Возвращает None если можно, иначе — причину."""
        blockers = graph.get_required_by(cid) & selected - {cid}
        if blockers:
            blocker_names = ", ".join(sorted(b.split("/")[-1] for b in blockers))
            return f"требуется: {blocker_names}"
        return None

    while True:
        print(f"\n{'─' * 70}")
        print(bold("  Дерево компонентов фреймворка"))
        print(f"{'─' * 70}")

        idx_map = print_tree(graph, selected)

        print(f"\n{'─' * 70}")
        # Подсчёт токенов — всегда показываем в text-режиме
        if selected:
            always_tokens, on_demand_tokens, total_tokens, english_total, savings = estimate_context_usage(
                graph, selected
            )
            savings_str = f" (экономия {savings})" if savings else ""
            token_str = (
                f"~{total_tokens} токенов (всегда {always_tokens} + по треб. {on_demand_tokens})"
                f" | English-only ~{english_total}{savings_str}"
            )
            print(f"  Выбрано: {green(str(len(selected)))} компонентов  {dim(token_str)}")
        else:
            print(f"  Выбрано: {green('0')} компонентов")
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
            auto_deps.clear()
            continue
        if raw == "none":
            selected.clear()
            auto_deps.clear()
            continue

        try:
            nums = _parse_numbers(raw)
            for n in nums:
                if n in idx_map:
                    cid = idx_map[n]
                    if cid in selected:
                        # Проверяем блокировку: нельзя снять, если нужен другим выбранным
                        block_reason = _can_deselect(cid)
                        if block_reason:
                            print(red(f"  ✗ Нельзя снять {cid} — {block_reason}"))
                            print(red(f"    Сначала снимите зависящие компоненты."))
                            continue
                        # Снимаем — вместе со связанными и сиротскими авто-зависимостями
                        selected.discard(cid)
                        auto_deps.pop(cid, None)
                        _auto_remove_orphan_deps(cid)
                        linked = graph.get_linked_components(cid)
                        for lid in linked:
                            if lid in selected:
                                selected.discard(lid)
                                auto_deps.pop(lid, None)
                                _auto_remove_orphan_deps(lid)
                                print(cyan(f"  ↔ Автоматически снят связанный: {lid}"))
                    else:
                        # Включаем — авто-добавляем зависимости, вместе со связанными
                        selected.add(cid)
                        _auto_add_deps(cid)
                        linked = graph.get_linked_components(cid)
                        for lid in linked:
                            if lid not in selected and lid in {c.id for c in graph.get_installable_for_user()}:
                                selected.add(lid)
                                _auto_add_deps(lid)
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


def _estimate_tokens(text: str) -> int:
    """Оценка токенов. Предпочтительно cl100k_base (tiktoken), fallback len/4."""
    if not text:
        return 0
    if _TOKENIZER is not None:
        try:
            return len(_TOKENIZER.encode(text))
        except Exception:
            pass
    return (len(text) + 3) // 4


def _collect_component_text_en(comp: "Component", mirror_dir: Optional[Path]) -> str:
    """Возвращает только EN-текст компонента из зеркала. Без fallback на RU."""
    if not mirror_dir:
        return ""

    def _read(path: Path) -> str:
        try:
            return path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            return ""

    ru_path = comp.filepath
    fw_dir = ru_path.parent
    while fw_dir.name != "framework" and fw_dir.parent != fw_dir:
        fw_dir = fw_dir.parent
    try:
        rel = ru_path.relative_to(fw_dir)
        en_path = mirror_dir / rel
        if en_path.exists():
            return _read(en_path)
    except ValueError:
        pass
    return ""




def _collect_component_text(comp: "Component", mirror_dir: Optional[Path] = None) -> str:
    """Возвращает текст компонента для оценки контекста.
    Если mirror_dir задан — читает EN-версию из зеркала (реальный текст агента).
    Fallback на RU-файл если EN ещё не создан.
    """
    def _read(path: Path) -> str:
        try:
            return path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            return ""

    ru_path = comp.filepath

    if mirror_dir:
        # Вычисляем зеркальный путь
        fw_dir = ru_path.parent
        while fw_dir.name != "framework" and fw_dir.parent != fw_dir:
            fw_dir = fw_dir.parent
        try:
            rel = ru_path.relative_to(fw_dir)
            en_path = mirror_dir / rel
            if en_path.exists():
                return _read(en_path)
        except ValueError:
            pass

    # Fallback: читаем RU
    if ru_path.suffix in (".md", ".mdc"):
        return _read(ru_path)
    return ""


def _is_always_on_component(comp: "Component") -> bool:
    """Определяет, попадает ли компонент в always-on канал IDE.

    Правила: только если alwaysApply: true.
    Агенты/сабагенты/воркфлоу: always-on по типу.
    Навыки: всегда on-demand (skills_dir, не rules_dir).
    """
    if comp.type == "rule":
        return comp.always_apply
    return comp.type in {"agent", "subagent", "workflow"}


def estimate_context_usage(
    graph: "FrameworkGraph",
    selected_ids: Set[str],
) -> Tuple[int, int, int, int, int]:
    """Оценивает контекст: always, on-demand, total, english-only, savings.

    Подсчёт ведётся только по EN-зеркалу (framework_eng). Если EN-файл
    отсутствует — вклад компонента считается 0.

    Правила без alwaysApply: true идут в on-demand (не в always-on канал),
    даже если они типа rule.
    """
    resolved = graph.resolve_dependencies(selected_ids)
    always_en, on_demand_en = [], []

    for cid in sorted(resolved):
        comp = graph.components.get(cid)
        if not comp:
            continue
        en_text = _collect_component_text_en(comp, graph.mirror_dir)
        if _is_always_on_component(comp):
            always_en.append(en_text)
        else:
            on_demand_en.append(en_text)

    always_tokens = _estimate_tokens("\n".join(always_en))
    on_demand_tokens = _estimate_tokens("\n".join(on_demand_en))

    total_tokens = always_tokens + on_demand_tokens
    english_total = total_tokens
    savings = 0
    return always_tokens, on_demand_tokens, total_tokens, english_total, savings


def estimate_agent_contexts(
    graph: "FrameworkGraph",
    selected_ids: Set[str],
) -> List[Tuple[str, int, int, int]]:
    """Оценка контекста по агентам с учётом зависимых правил и навыков."""
    resolved = graph.resolve_dependencies(selected_ids)
    agent_ids = [
        cid
        for cid in sorted(resolved)
        if graph.components.get(cid) and graph.components[cid].type in {"agent", "subagent"}
    ]
    results: List[Tuple[str, int, int, int]] = []

    for agent_id in agent_ids:
        deps = graph.resolve_dependencies({agent_id})
        include_ids: List[str] = []
        for cid in sorted(deps | {agent_id}):
            comp = graph.components.get(cid)
            if not comp:
                continue
            if cid == agent_id or comp.type in {"rule", "skill"}:
                include_ids.append(cid)

        en_text = "\n".join(
            _collect_component_text_en(graph.components[cid], graph.mirror_dir)
            for cid in include_ids
            if cid in graph.components
        )
        english_total = _estimate_tokens(en_text)
        total_tokens = english_total
        savings = 0
        results.append((agent_id, total_tokens, english_total, savings))

    return results


def _has_complete_en_mirror(graph: "FrameworkGraph", selected_ids: Set[str]) -> bool:
    """True, если для всех selected+deps есть EN-файлы в mirror_dir."""
    if not graph.mirror_dir:
        return False
    resolved = graph.resolve_dependencies(selected_ids)
    for cid in sorted(resolved):
        comp = graph.components.get(cid)
        if not comp:
            continue
        if not _collect_component_text_en(comp, graph.mirror_dir):
            return False
    return True


def _has_complete_en_mirror_for_agents(graph: "FrameworkGraph", agent_contexts: List[Tuple[str, int, int, int]]) -> bool:
    """True, если для всех агентов из списка есть EN-тексты агента и его rule/skill deps."""
    if not graph.mirror_dir:
        return False
    for agent_id, _, _, _ in agent_contexts:
        deps = graph.resolve_dependencies({agent_id})
        include_ids = [
            cid for cid in sorted(deps | {agent_id})
            if cid in graph.components and (cid == agent_id or graph.components[cid].type in {"rule", "skill"})
        ]
        for cid in include_ids:
            if not _collect_component_text_en(graph.components[cid], graph.mirror_dir):
                return False
    return True


def format_agent_contexts(
    graph: "FrameworkGraph",
    agent_contexts: List[Tuple[str, int, int, int]],
    show_english: bool,
) -> List[str]:
    """Формирует блок строк с оценкой контекста по агентам."""
    if not agent_contexts:
        return []
    lines = ["  Контекст по агентам (агент + правила/навыки зависимостей):"]
    for agent_id, total_tokens, english_total, savings in agent_contexts:
        short = agent_id.split("/")[-1]
        label = short
        if graph.components.get(agent_id) and graph.components[agent_id].type == "subagent":
            label = f"{short} (sub)"
        if show_english:
            savings_str = f" ({savings})" if savings else ""
            lines.append(f"    {label:<14} {total_tokens} | English-only {english_total}{savings_str}")
        else:
            lines.append(f"    {label:<14} {total_tokens}")
    return lines


def format_context_estimate(
    always_tokens: int,
    on_demand_tokens: int,
    total_tokens: int,
    english_total: int,
    savings: int,
    show_english: bool,
    mirror_available: bool = False,
) -> List[str]:
    """Формирует блок строк для вывода оценки контекста."""
    en_note = "" if mirror_available else " (~оценка)"
    lines = [
        f"  {bold('Оценка контекста (токены EN):')}",
        f"    Всегда в контексте: {bold(str(always_tokens))}",
        f"    По требованию:       {bold(str(on_demand_tokens))}",
        f"    Итого{en_note}:            {bold(str(total_tokens))}",
    ]
    if savings > 0:
        lines.append(f"    Экономия vs RU:      ~{savings} токенов")
    return lines


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
    graph: Optional["FrameworkGraph"] = None,
) -> None:
    """Записывает лог выбора в .install-session.json для верификации."""
    log_path = project_dir / ".install-session.json"

    # Карта компонентов: comp_id → {type, ru_path, en_path}
    # Позволяет агенту, работающему в проекте, быстро найти RU-источник для редактирования.
    component_map: Dict[str, dict] = {}
    if graph:
        fw_dir = graph.framework_dir.resolve()
        mirror_dir = graph.mirror_dir.resolve() if graph.mirror_dir else None
        resolved_ids = graph.resolve_dependencies(selected)
        for comp_id in sorted(resolved_ids):
            comp = graph.components.get(comp_id)
            if not comp:
                continue
            ru_path = str(comp.filepath.resolve())
            entry: dict = {"type": comp.type, "ru_path": ru_path}
            # alwaysApply сохраняется как метаданные: физическое размещение зависит от IDE.
            if comp.type == "rule":
                entry["always_apply"] = comp.always_apply
            # Строим EN-путь: framework/x/y → framework_eng/x/y
            if mirror_dir:
                try:
                    rel = comp.filepath.resolve().relative_to(fw_dir)
                    en_candidate = mirror_dir / rel
                    if en_candidate.exists():
                        entry["en_path"] = str(en_candidate)
                except ValueError:
                    pass
            component_map[comp_id] = entry

    data = {
        "timestamp": datetime.now().isoformat(),
        "ide": ide_key,
        "project_dir": str(project_dir.resolve()),
        "framework_dir": str(graph.framework_dir.resolve()) if graph else None,
        "sync_script": str((graph.framework_dir.parent / "tools" / "sync-skill.py").resolve()) if graph else None,
        "selected_components": sorted(selected),
        "model_map": dict(sorted(model_map.items())) if model_map else {},
        "use_symlinks": use_symlinks,
        "result": {"installed": installed, "skipped": skipped, "removed": removed},
        "component_map": component_map,
    }
    try:
        log_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        print(dim(f"  Лог сессии: {log_path}"))
    except OSError as e:
        print(yellow(f"  Не удалось записать лог: {e}"))


def print_rules_import_hint(ide_key: str, project_dir: Path, installed_rules: List[str]) -> None:
    """Выводит напоминание о ручной настройке entry-point файла для IDE,
    которые не подхватывают правила автоматически (CLAUDE.md, AGENTS.md, .windsurfrules и т.д.)."""
    ide_cfg = IDE_CONFIGS[ide_key]
    hint = ide_cfg.get("rules_import_hint")
    if not hint:
        return

    entry_file = hint.get("file")  # None для Kiro (нет единого entry-point)
    rules_dir = ide_cfg["rules_dir"]
    note = hint.get("note", "")

    print()
    print(yellow(f"  ⚠  {ide_cfg['name']}: правила требуют ручной настройки"))
    print(f"  {'─' * 66}")

    # Случай Kiro: нет entry-point файла, только frontmatter предупреждение
    if not entry_file:
        print(f"  Правила установлены в  {cyan(rules_dir + '/')}")
        print()
        for line in note.splitlines():
            print(f"  {dim(line)}" if line.startswith("  ") else f"  {line}")
        print(f"  {'─' * 66}")
        return

    entry_path = project_dir / entry_file

    print(f"  Файлы правил установлены в  {cyan(rules_dir + '/')}")
    print(f"  но НЕ подхватываются автоматически — нужен  {bold(entry_file)}")
    print()
    # Выводим note (описание что делать)
    for line in note.splitlines():
        print(f"  {line}")
    print()

    # Выводим конкретные строки для каждого установленного правила
    fmt = hint.get("format", "")
    if fmt and installed_rules:
        for rule_name in sorted(installed_rules):
            line = fmt.replace("{name}", rule_name).replace("{description}", rule_name)
            print(f"    {dim(line)}")
        print()

    if entry_path.exists():
        print(green(f"  ✓ {entry_file} уже существует — откройте и добавьте строки выше."))
    else:
        print(yellow(f"  Файл {entry_file} не найден — создайте его в корне проекта."))
    print(f"  {'─' * 66}")


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


def _component_source_paths(
    comp: Component,
    framework_dir: Optional[Path] = None,
    mirror_dir: Optional[Path] = None,
) -> Tuple[Path, Path, str]:
    """
    Возвращает (source_file, source_path_for_link, short_name).
    source_path_for_link:
      - skill/rule/workflow с SKILL.md -> директория навыка
      - остальное -> файл компонента

    Если mirror_dir задан (framework_eng/) — source_path_for_link указывает на EN-зеркало.
    Дерево и сканирование всегда по framework/ (RU-источник).
    Fallback на RU если EN-файл ещё не создан.
    """
    source_file = comp.filepath.resolve()

    def _mirror_of(ru_path: Path) -> Optional[Path]:
        """framework/x/y → framework_eng/x/y, если существует."""
        if not mirror_dir or not framework_dir:
            return None
        try:
            rel = ru_path.relative_to(framework_dir)
            m = mirror_dir / rel
            return m.resolve() if m.exists() else None
        except ValueError:
            return None

    if comp.type == "skill" and source_file.name == "SKILL.md":
        ru_dir = source_file.parent
        short_name = ru_dir.name
        source_path = _mirror_of(ru_dir) or ru_dir
    elif comp.type in ("rule", "workflow") and source_file.name == "SKILL.md":
        ru_dir = source_file.parent
        short_name = ru_dir.name
        source_path = _mirror_of(ru_dir) or ru_dir
        source_file = source_path / "SKILL.md"
    else:
        short_name = comp.id.rpartition("/")[2] or comp.id.replace("/", "-")
        mirrored = _mirror_of(source_file)
        if mirrored:
            source_file = mirrored
        source_path = source_file

    return source_file, source_path, short_name


# ─── Codex-специфичная установка ─────────────────────────────────────────────
# Codex CLI разворачивает компоненты иначе остальных IDE:
#   rule/workflow → навык: .codex/skills/<name>/  (симлинк на каталог с SKILL.md)
#   agent → профиль: .codex/agents/<agent-name>.toml      (конвертация из *.md)

def _rule_workflow_file_skill_mode(comp: Component, ide_key: str) -> bool:
    """Для не-Codex платформ rule/workflow устанавливаются как file-style skills."""
    return ide_key != "codex" and comp.type in ("rule", "workflow") and "skills_dir" in IDE_CONFIGS[ide_key]

def _codex_mode(comp: Component, ide_key: str) -> Optional[str]:
    """Режим codex-специфичной установки для компонента или None.

    'rule_skill' — правило/воркфлоу разворачивается как навык (skills/<name>/);
    'agent_toml' — агент конвертируется в TOML-профиль (agents/<name>.toml).
    """
    if ide_key != "codex":
        return None
    if comp.type in ("rule", "workflow"):
        return "rule_skill"
    if comp.type in ("agent", "subagent"):
        return "agent_toml"
    return None


def _read_body_after_frontmatter(path: Path) -> str:
    """Возвращает прозу агента: тело без ведущего frontmatter и без хвостового
    технического backmatter-блока (depends_on/skills) субагентов."""
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return ""
    m = FRONTMATTER_RE.match(text)
    body = text[m.end():] if m else text
    # Срезаем хвостовой технический ---блок--- (стиль subagent/*.md): он несёт
    # depends_on/skills, а не инструкции агенту.
    back = BACKMATTER_RE.search(body)
    if back:
        body = body[: back.start()]
    return body.strip()


def _frontmatter_scalar_folded(source_file: Path, key: str) -> str:
    """Извлекает скалярное поле frontmatter, склеивая многострочные (folded) значения.

    Общий `_parse_simple_yaml_block` берёт только первую строку folded-скаляра
    (description у агентов часто занимает 2-3 строки). Здесь собираем всё значение
    до следующего ключа того же уровня и склеиваем в одну строку.
    """
    try:
        text = source_file.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return ""
    m = FRONTMATTER_RE.match(text)
    if not m:
        return ""

    collected: List[str] = []
    capturing = False
    base_indent = 0
    for line in m.group(1).splitlines():
        if not capturing:
            stripped = line.strip()
            if stripped.startswith(f"{key}:"):
                val = stripped.split(":", 1)[1].strip()
                if val not in (">", "|", ">-", "|-", ">+", "|+"):  # folding-индикаторы
                    if val:
                        collected.append(val)
                base_indent = len(line) - len(line.lstrip())
                capturing = True
            continue
        # сбор continuation-строк (с отступом больше ключа)
        if not line.strip():
            continue
        if (len(line) - len(line.lstrip())) <= base_indent:
            break  # следующий ключ того же уровня
        collected.append(line.strip())

    return " ".join(" ".join(collected).split())


def _toml_basic_string(value: str) -> str:
    """Однострочное TOML basic-значение с экранированием спецсимволов."""
    escaped = (
        value.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\n", " ")
        .replace("\t", " ")
    )
    return f'"{escaped}"'


def generate_codex_agent_toml(source_file: Path, fallback_name: str) -> str:
    """Конвертирует агент-*.md в Codex TOML-профиль.

    Шаблон Codex:
        name = "frontend-builder"
        description = "..."
        developer_instructions = "..."

    name/description берутся из frontmatter, тело файла → developer_instructions.
    """
    fm = parse_frontmatter(source_file) or {}
    name = str(fm.get("name") or fallback_name).strip()
    # description часто folded на 2-3 строки — берём полное значение, а не первую строку
    description = _frontmatter_scalar_folded(source_file, "description")
    if not description:
        description = " ".join(str(fm.get("description") or "").split())
    body = _read_body_after_frontmatter(source_file)

    # developer_instructions — многострочное тело агента.
    # Предпочитаем TOML-литерал ('''…'''), он не требует экранирования.
    # Если тело содержит разделитель ''', падаем в экранированный basic-блок.
    if "'''" not in body:
        instr = "'''\n" + body + "\n'''"
    else:
        esc = body.replace("\\", "\\\\").replace('"""', '\\"\\"\\"')
        instr = '"""\n' + esc + '\n"""'

    return (
        f"name = {_toml_basic_string(name)}\n"
        f"description = {_toml_basic_string(description)}\n"
        f"developer_instructions = {instr}\n"
    )


def _install_codex_component(
    comp: Component,
    mode: str,
    source_file: Path,
    source_path: Path,
    target_path: Path,
    use_symlinks: bool,
    dry_run: bool,
) -> int:
    """Размещает codex-компонент. Возвращает 1 (установлен).

    rule_skill — симлинк skills/<name>/ → каталог с SKILL.md;
    agent_toml — генерирует .toml-профиль из тела агента.
    """
    if dry_run:
        action = (
            "→ (symlink skill dir)"
            if mode == "rule_skill" and use_symlinks
            else "→ (copy skill)"
            if mode == "rule_skill"
            else "→ (convert → .toml)"
        )
        print(f"    {action} {target_path}")
        return 1

    if mode == "rule_skill":
        if use_symlinks:
            if target_path.is_symlink() or target_path.is_file():
                target_path.unlink()
            elif target_path.exists():
                shutil.rmtree(target_path)
            target_path.parent.mkdir(parents=True, exist_ok=True)
            try:
                try:
                    rel_path = os.path.relpath(source_path, target_path.parent)
                    target_path.symlink_to(rel_path, target_is_directory=True)
                except ValueError:
                    target_path.symlink_to(source_path, target_is_directory=True)
            except OSError as e:
                print(red(f"  ✗ Ошибка симлинка {comp.id}: {e}"))
                target_path.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source_file, target_path / "SKILL.md")
                print(yellow(f"    → скопирован как fallback"))
        else:
            if target_path.is_symlink() or target_path.exists():
                target_path.unlink()
            target_path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source_file, target_path)
    else:  # agent_toml
        if target_path.is_symlink() or target_path.exists():
            target_path.unlink()
        target_path.parent.mkdir(parents=True, exist_ok=True)
        fallback_name = comp.id.rpartition("/")[2] or comp.id
        target_path.write_text(
            generate_codex_agent_toml(source_file, fallback_name), encoding="utf-8"
        )
    return 1


def _component_target_file(
    comp: Component, ide_key: str, project_dir: Path, use_symlinks: bool = True,
    graph: "Optional[FrameworkGraph]" = None,
) -> Path:
    """Строит целевой путь компонента в каталоге проекта.
    Для навыков: симлинк и копия — short_name (как у исходной папки).
    Симлинк: target_base/short_name → каталог. Копия: target_base/short_name/SKILL.md.
    Antigravity: skill.md (lowercase) вместо SKILL.md.
    """
    ide_cfg = IDE_CONFIGS[ide_key]
    dir_key = _dir_key_for_component(comp.type, ide_cfg)
    target_base = project_dir / ide_cfg[dir_key]
    source_file, _, short_name = _component_source_paths(comp)
    # Codex: правило/воркфлоу → навык skills/<name>/, агент → agents/<name>.toml
    codex_mode = _codex_mode(comp, ide_key)
    if codex_mode == "rule_skill":
        folder = short_name
        # Коллизия с одноимённым навыком → префикс rule_ (см. codex_prefixed_rule_names)
        if graph is not None and short_name in graph.codex_prefixed_rule_names():
            folder = f"rule_{short_name}"
        skill_dir = project_dir / ide_cfg["skills_dir"] / folder
        return skill_dir if use_symlinks else skill_dir / "SKILL.md"
    if codex_mode == "agent_toml":
        return project_dir / ide_cfg["agents_dir"] / f"{short_name}.toml"
    if _rule_workflow_file_skill_mode(comp, ide_key):
        return project_dir / ide_cfg["skills_dir"] / f"{short_name}.md"
    if comp.type == "skill":
        if use_symlinks:
            return target_base / short_name  # симлинк на каталог
        skill_fn = ide_cfg.get("skill_filename", "SKILL.md")
        return target_base / short_name / skill_fn
    # Для правил: IDE может задавать целевое расширение (напр. Cursor → .mdc)
    if comp.type == "rule" and "rules_ext" in ide_cfg:
        ext = ide_cfg["rules_ext"]
    else:
        ext = source_file.suffix if source_file.suffix in (".md", ".mdc") else ".md"
    return target_base / f"{short_name}{ext}"


def _codex_legacy_rule_workflow_target_file(
    comp: Component,
    ide_key: str,
    project_dir: Path,
    source_file: Path,
) -> Optional[Path]:
    """Старый путь Codex для rule/workflow до разворачивания их как skills."""
    if ide_key != "codex" or comp.type not in ("rule", "workflow"):
        return None
    ide_cfg = IDE_CONFIGS[ide_key]
    _, _, short_name = _component_source_paths(comp)
    ext = source_file.suffix if source_file.suffix in (".md", ".mdc") else ".md"
    return project_dir / ide_cfg["rules_dir"] / f"{short_name}{ext}"


def _legacy_rule_workflow_rules_target_file(
    comp: Component,
    ide_key: str,
    project_dir: Path,
    source_file: Path,
) -> Optional[Path]:
    """Старый путь rule/workflow в rules/workflows-каталоге IDE до file-style skills."""
    if not _rule_workflow_file_skill_mode(comp, ide_key):
        return None
    ide_cfg = IDE_CONFIGS[ide_key]
    dir_key = _dir_key_for_component(comp.type, ide_cfg)
    if dir_key not in ide_cfg:
        return None
    _, _, short_name = _component_source_paths(comp)
    if comp.type == "rule" and "rules_ext" in ide_cfg:
        ext = ide_cfg["rules_ext"]
    else:
        ext = source_file.suffix if source_file.suffix in (".md", ".mdc") else ".md"
    return project_dir / ide_cfg[dir_key] / f"{short_name}{ext}"


def _norm_path(path: Path) -> str:
    """Нормализует путь для безопасного сравнения."""
    return str(path.resolve(strict=False))


def _component_expected_symlink_source(
    comp: Component,
    ide_key: str,
    use_symlinks_target: bool,
    graph: FrameworkGraph,
) -> Path:
    """Ожидаемый источник symlink-а для конкретной формы target-а."""
    source_file, source_path, _ = _component_source_paths(
        comp, graph.framework_dir, graph.mirror_dir
    )
    if comp.type == "skill" and use_symlinks_target:
        return source_path
    if _codex_mode(comp, ide_key) == "rule_skill" and use_symlinks_target:
        return source_path
    if _rule_workflow_file_skill_mode(comp, ide_key):
        return source_file
    return source_file if not use_symlinks_target else source_path


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
            (
                _component_target_file(comp, ide_key, project_dir, use_symlinks=True, graph=graph),
                _component_expected_symlink_source(comp, ide_key, True, graph),
            ),
            (
                _component_target_file(comp, ide_key, project_dir, use_symlinks=False, graph=graph),
                _component_expected_symlink_source(comp, ide_key, False, graph),
            ),
        ]
        detected_pair = next(((t, expected) for t, expected in target_candidates if t.is_symlink()), None)
        if not detected_pair:
            continue
        target_file, expected_source = detected_pair
        if not target_file:
            continue

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


def _build_trigger_skill_pairs(
    graph: "FrameworkGraph",
    comp_ids: Set[str],
) -> List[Tuple[str, Optional[str]]]:
    """Строит пары (правило-триггер, навык) для человекочитаемого вывода установки.

    Пара создаётся когда правило типа rule с alwaysApply: true зависит от навыка
    (skill), или навык имеет совпадающее имя с правилом (через depends_on).
    Возвращает список пар: (rule_id, skill_id_or_None), отсортированных по rule_id.
    """
    pairs: List[Tuple[str, Optional[str]]] = []

    rules = [cid for cid in comp_ids if graph.components.get(cid) and graph.components[cid].type == "rule"]
    skill_ids = {cid for cid in comp_ids if graph.components.get(cid) and graph.components[cid].type == "skill"}

    for rule_id in sorted(rules):
        comp = graph.components[rule_id]
        # Ищем связанный навык: прямая зависимость (depends_on) или совпадение имени
        paired_skill: Optional[str] = None

        # Способ 1: зависимость указывает на навык
        for dep in comp.depends_on:
            dep_cid = graph._path_to_comp_id(dep)
            if dep_cid and dep_cid in skill_ids:
                paired_skill = dep_cid
                break

        # Способ 2: совпадение имени правила с именем навыка (одно слово)
        if paired_skill is None:
            rule_short = rule_id.split("/")[-1]  # напр. "xml-generation" из "rule/xml-generation"
            for sk_id in sorted(skill_ids):
                sk_short = sk_id.split("/")[-1]
                if sk_short == rule_short:
                    paired_skill = sk_id
                    break

        pairs.append((rule_id, paired_skill))

    return pairs


def print_trigger_skill_pairs(
    graph: "FrameworkGraph",
    comp_ids: Set[str],
) -> None:
    """Выводит пары «правило-триггер + навык» одной строкой.

    Показывает только правила с alwaysApply: true, у которых есть связанный навык.
    Остальные правила и навыки выводятся отдельно ниже.
    """
    pairs = _build_trigger_skill_pairs(graph, comp_ids)
    paired_skills: Set[str] = set()
    paired_rules: Set[str] = set()

    shown_pairs = [(r, s) for r, s in pairs if s is not None and graph.is_always_on_rule(r)]
    if not shown_pairs:
        return

    print(f"\n  {bold('Пары «триггер + навык»')} (правило → читается по требованию):")
    for rule_id, skill_id in shown_pairs:
        rule_short = rule_id.split("/")[-1]
        skill_short = skill_id.split("/")[-1] if skill_id else ""
        print(f"    {cyan(rule_short):<35} → {dim(skill_short)}")
        paired_rules.add(rule_id)
        if skill_id:
            paired_skills.add(skill_id)


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

    # Показываем пары «триггер + навык» для наглядности
    print_trigger_skill_pairs(graph, all_ids)

    # Подсчёт правил: сколько always-on, сколько only component_map
    always_on_rules = [cid for cid in all_ids
                       if graph.components.get(cid) and graph.components[cid].type == "rule"
                       and graph.components[cid].always_apply]
    lazy_rules = [cid for cid in all_ids
                  if graph.components.get(cid) and graph.components[cid].type == "rule"
                  and not graph.components[cid].always_apply]
    if always_on_rules or lazy_rules:
        print(f"\n  {bold('Правила')}: {green(str(len(always_on_rules)))} always-on"
              f" + {dim(str(len(lazy_rules)) + ' on-demand (alwaysApply отсутствует)')}")

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
            target_file = _component_target_file(comp, ide_key, project_dir, use_symlinks=use_sl, graph=graph)
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

        # Фильтр alwaysApply применим только к старому rules-каталогу IDE.
        # Когда rule/workflow устанавливается как skill, физически размещаем все правила.
        if (
            comp.type == "rule"
            and not comp.always_apply
            and ide_key != "codex"
            and not _rule_workflow_file_skill_mode(comp, ide_key)
        ):
            # Правило не-always-on: пропускаем физическое размещение, но включаем в component_map.
            # Вывод только в dry-run для наглядности.
            if dry_run:
                print(f"    → (component_map only, alwaysApply missing) {comp_id}")
            skipped += 1
            continue

        source_file, source_path, _ = _component_source_paths(comp, graph.framework_dir, graph.mirror_dir)
        target_file = _component_target_file(comp, ide_key, project_dir, use_symlinks=use_symlinks, graph=graph)

        # Codex: правило/воркфлоу → навык (symlink каталога), агент → TOML-профиль (конвертация).
        # Одноимённые навыку правила автоматически получают префикс rule_ в target_file
        # (см. _component_target_file + codex_prefixed_rule_names) — коллизии каталогов нет.
        codex_mode = _codex_mode(comp, ide_key)
        if codex_mode:
            legacy_target = _codex_legacy_rule_workflow_target_file(
                comp, ide_key, project_dir, source_file
            )
            if legacy_target and (legacy_target.exists() or legacy_target.is_symlink()):
                if dry_run:
                    print(f"    ← remove legacy {legacy_target}")
                else:
                    legacy_target.unlink()
            installed += _install_codex_component(
                comp, codex_mode, source_file, source_path, target_file, use_symlinks, dry_run
            )
            continue

        legacy_target = _legacy_rule_workflow_rules_target_file(
            comp, ide_key, project_dir, source_file
        )
        if legacy_target and (legacy_target.exists() or legacy_target.is_symlink()):
            if dry_run:
                print(f"    ← remove legacy {legacy_target}")
            else:
                legacy_target.unlink()

        # IDE с кастомным именем файла навыка (skill.md вместо SKILL.md)
        # При симлинках: создаём каталог навыка и симлинк на файл.
        skill_fn = ide_cfg.get("skill_filename")
        use_skill_file_link = (
            comp.type == "skill" and skill_fn and skill_fn != "SKILL.md" and use_symlinks
        )
        use_rule_workflow_file_link = _rule_workflow_file_skill_mode(comp, ide_key) and use_symlinks
        if use_skill_file_link:
            target_file = _component_target_file(comp, ide_key, project_dir, use_symlinks=False)

        if dry_run:
            if use_skill_file_link:
                action = f"→ (symlink file, {skill_fn})"
            elif use_rule_workflow_file_link:
                action = "→ (symlink file skill)"
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
            # Очистка старых симлинков с прежним расширением (напр. .md → .mdc при апгрейде)
            if comp.type == "rule" and "rules_ext" in ide_cfg:
                src_ext = source_file.suffix
                if src_ext != ide_cfg["rules_ext"]:
                    old_ext_path = target_file.with_suffix(src_ext)
                    if old_ext_path.exists() or old_ext_path.is_symlink():
                        old_ext_path.unlink()

        target_file.parent.mkdir(parents=True, exist_ok=True)

        if use_skill_file_link or use_rule_workflow_file_link:
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
                if comp.type == "skill" or _rule_workflow_file_skill_mode(comp, ide_key):
                    shutil.copy2(source_file, copy_target)
                else:
                    shutil.copy2(source_path, copy_target)
                print(yellow(f"    → скопирован как fallback"))
                installed += 1
        else:
            # При копировании: для навыков копируем SKILL.md, для остальных — сам файл
            if comp.type == "skill" or _rule_workflow_file_skill_mode(comp, ide_key):
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

    # Проверяем симлинки каталогов (capabilities/, tools/)
    for dir_name in ("capabilities", "tools"):
        dir_link = project_dir / dir_name
        if dir_link.is_symlink() and not dir_link.exists():
            broken += 1
            print(yellow(f"  Сломан: {dir_link} → {os.readlink(dir_link)}"))

    if broken == 0:
        print(green("  Все симлинки в порядке."))
    else:
        print(f"\n  Найдено {red(str(broken))} сломанных симлинков.")
        print(f"  Для исправления: переустановите фреймворк (python tools/install.py)")


# ─── xml-gen: сборка и установка ─────────────────────────────────────────────

XML_GEN_GROUP = "tool-usage/xml-generation"
XML_GEN_JAR_DIR = Path.home() / ".local" / "share" / "1c-xml-gen"
XML_GEN_JAR = XML_GEN_JAR_DIR / "xml-gen.jar"
XML_GEN_BIN = Path.home() / ".local" / "bin" / "xml-gen"

WRAPPER_SH = """\
#!/bin/sh
exec java -jar "{jar}" "$@"
"""

WRAPPER_BAT = """\
@echo off
java -jar "{jar}" %*
"""


def _xml_gen_selected(all_ids: Set[str]) -> bool:
    """Возвращает True, если среди выбранных есть хотя бы один навык xml-generation."""
    return any(XML_GEN_GROUP in comp_id for comp_id in all_ids)


def _tool_usage_selected(all_ids: Set[str], graph: "FrameworkGraph") -> bool:
    """Возвращает True, если среди выбранных есть хотя бы один навык из tool-usage."""
    for comp_id in all_ids:
        comp = graph.components.get(comp_id)
        if comp and comp.type == "skill":
            try:
                rel = comp.filepath.relative_to(graph.framework_dir)
                if rel.parts[:2] == ("skills", "tool-usage"):
                    return True
            except ValueError:
                pass
    return False


def _tools_required(all_ids: Set[str], graph: "FrameworkGraph") -> bool:
    """Возвращает True, если среди выбранных компонентов есть requires: [tools]."""
    for comp_id in all_ids:
        comp = graph.components.get(comp_id)
        if comp and "tools" in comp.requires:
            return True
    return False


def install_tools_symlink(
    framework_dir: Path,
    project_dir: Path,
    use_symlinks: bool = True,
    dry_run: bool = False,
) -> bool:
    """Создаёт tools/ в корне проекта → tools/ фреймворка.

    Возвращает True если создан/уже существует, False при ошибке или конфликте.
    """
    src = framework_dir.parent / "tools"
    if not src.exists():
        return False

    target = project_dir / "tools"

    if target.exists() or target.is_symlink():
        if target.is_symlink():
            if target.resolve() == src.resolve():
                return True  # уже корректный симлинк
            # Сломанный/неверный симлинк — пересоздаём
            if not dry_run:
                target.unlink()
        else:
            # Реальный каталог — НЕ трогаем, предупреждаем пользователя
            print(yellow(f"  ⚠ tools/ уже существует в проекте как каталог — симлинк не создан."))
            print(yellow(f"    Переименуйте или удалите его, затем перезапустите установку."))
            return False

    if dry_run:
        print(f"  → (symlink) {target}  →  {src}")
        return True

    try:
        if use_symlinks:
            target.symlink_to(src)
        else:
            shutil.copytree(str(src), str(target))
        return True
    except Exception as e:
        print(red(f"  ✗ tools/: {e}"))
        return False


def install_capabilities_symlink(
    framework_dir: Path,
    project_dir: Path,
    use_symlinks: bool = True,
    dry_run: bool = False,
    mirror_dir: Optional[Path] = None,
) -> bool:
    """Создаёт capabilities/ в корне проекта → framework_eng/capabilities/ (или framework/).

    Возвращает True если создан/уже существует, False при ошибке.
    """
    # Предпочитаем EN-зеркало если доступно
    src = (mirror_dir / "capabilities" if mirror_dir else None)
    if not src or not src.exists():
        src = framework_dir / "capabilities"
    if not src.exists():
        return False  # нечего линковать

    target = project_dir / "capabilities"

    if target.exists() or target.is_symlink():
        # Уже есть — проверяем корректность
        if target.is_symlink():
            if target.resolve() == src.resolve():
                return True  # всё хорошо
            # Сломанный/неверный симлинк — пересоздаём
            if not dry_run:
                target.unlink()
        else:
            # Реальный каталог — не трогаем
            print(yellow(f"  ⚠ capabilities/ уже существует как каталог — пропускаем"))
            return True

    if dry_run:
        print(f"  → (symlink) {target}  →  {src}")
        return True

    try:
        if use_symlinks:
            target.symlink_to(src)
        else:
            shutil.copytree(str(src), str(target))
        return True
    except Exception as e:
        print(red(f"  ✗ capabilities/: {e}"))
        return False


def _check_java() -> Optional[str]:
    """Возвращает путь к java если JDK 17+ доступен, иначе None."""
    java = shutil.which("java")
    if not java:
        return None
    try:
        out = subprocess.check_output(
            [java, "-version"], stderr=subprocess.STDOUT, text=True
        )
        # "openjdk version "21.0..." или "java version "17..."
        m = re.search(r'version "(\d+)', out)
        if m and int(m.group(1)) >= 17:
            return java
    except Exception:
        pass
    return None


def _find_gradlew(script_dir: Path) -> Optional[Path]:
    """Ищет gradlew рядом со скриптом (tools/xml-gen/gradlew)."""
    gw = script_dir / "xml-gen" / "gradlew"
    if gw.exists():
        return gw
    return None


def _install_java_ubuntu() -> bool:
    """Предлагает установить OpenJDK 21 через apt. Возвращает True если установлено."""
    print(yellow("  ⚠ JDK 17+ не найден — он необходим для сборки xml-gen."))
    print()
    print("  Для установки OpenJDK 21 выполните:")
    print(cyan("      sudo apt-get update && sudo apt-get install -y openjdk-21-jdk-headless"))
    print()
    try:
        ans = input("  Установить сейчас? [Y/n]: ").strip().lower()
    except (EOFError, KeyboardInterrupt):
        print()
        return False
    if ans and ans not in ("y", "yes", "д", "да", ""):
        return False
    print()
    ret = subprocess.run(
        ["sudo", "apt-get", "install", "-y", "openjdk-21-jdk-headless"],
        check=False,
    ).returncode
    if ret != 0:
        # Пробуем с update
        subprocess.run(["sudo", "apt-get", "update", "-q"], check=False)
        ret = subprocess.run(
            ["sudo", "apt-get", "install", "-y", "openjdk-21-jdk-headless"],
            check=False,
        ).returncode
    return ret == 0


def install_xml_gen(script_dir: Path, dry_run: bool = False) -> bool:
    """
    Собирает xml-gen JAR и устанавливает wrapper-скрипт в ~/.local/bin/xml-gen.

    Возвращает True если успешно (или уже установлено), False — если пропущено/ошибка.
    """
    print()
    print(bold("  ── xml-gen: установка CLI-инструмента ──────────────────────"))

    # 1. Проверяем JAR — если уже актуальный, пропускаем сборку
    gradlew = _find_gradlew(script_dir)
    if gradlew is None:
        print(red("  ✗ tools/xml-gen/gradlew не найден — сборка невозможна."))
        return False

    jar_src = gradlew.parent / "build" / "libs" / "xml-gen-0.1.1-SNAPSHOT.jar"
    need_build = not jar_src.exists()

    # Проверка устаревшего JAR: если любой .java в src новее JAR — пересобираем.
    # Иначе agent-изменения теряются в stale JAR (см. baseline regression OC-22444).
    if jar_src.exists():
        try:
            jar_mtime = jar_src.stat().st_mtime
            src_root = gradlew.parent / "src" / "main"
            latest_src_mtime = 0.0
            if src_root.exists():
                for p in src_root.rglob("*.java"):
                    m = p.stat().st_mtime
                    if m > latest_src_mtime:
                        latest_src_mtime = m
            if latest_src_mtime > jar_mtime:
                print(yellow(f"  ⚠ JAR устарел — исходники новее на "
                             f"{int(latest_src_mtime - jar_mtime)}s. Пересборка…"))
                need_build = True
        except OSError:
            pass  # если mtime недоступен — не рискуем, доверяем существованию JAR

    if not need_build:
        print(green(f"  ✓ JAR уже собран: {jar_src}"))
    else:
        # 2. Проверяем JDK
        java = _check_java()
        if java is None:
            system = platform.system()
            if system == "Linux":
                ok = _install_java_ubuntu()
                if not ok:
                    print(yellow("  ⚠ xml-gen не установлен — JDK недоступен."))
                    print(yellow("    Навыки xml-generation доступны, но запуск xml-gen потребует"))
                    print(yellow("    ручной установки JDK 17+ и сборки: cd tools/xml-gen && ./gradlew shadowJar"))
                    return False
                java = _check_java()
                if java is None:
                    print(red("  ✗ JDK установлен, но java не найдена в PATH. Перезапустите установщик."))
                    return False
            else:
                print(yellow("  ⚠ JDK 17+ не найден. Установите OpenJDK 17+ вручную,"))
                print(yellow("    затем запустите: cd tools/xml-gen && ./gradlew shadowJar"))
                print(yellow("    После сборки перезапустите: python tools/install.py --install-xml-gen"))
                return False

        # 3. Собираем JAR
        print(f"  Сборка xml-gen (gradlew shadowJar)...")
        print(f"  {dim('Это занимает ~10-30 секунд при первом запуске (скачивание Gradle).')}")
        if dry_run:
            print(f"  {bold('[DRY RUN]')} ./gradlew shadowJar")
        else:
            ret = subprocess.run(
                [str(gradlew), "shadowJar"],
                cwd=str(gradlew.parent),
                check=False,
            ).returncode
            if ret != 0:
                print(red("  ✗ Сборка завершилась с ошибкой. Проверьте вывод выше."))
                return False
            print(green(f"  ✓ JAR собран: {jar_src}"))

    # 4. Копируем JAR в ~/.local/share/1c-xml-gen/
    if dry_run:
        print(f"  {bold('[DRY RUN]')} cp {jar_src} → {XML_GEN_JAR}")
    else:
        XML_GEN_JAR_DIR.mkdir(parents=True, exist_ok=True)
        shutil.copy2(jar_src, XML_GEN_JAR)
        print(green(f"  ✓ JAR установлен: {XML_GEN_JAR}"))

    # 5. Создаём wrapper-скрипт
    system = platform.system()
    if system == "Windows":
        wrapper_path = XML_GEN_BIN.with_suffix(".bat")
        wrapper_content = WRAPPER_BAT.format(jar=XML_GEN_JAR)
    else:
        wrapper_path = XML_GEN_BIN
        wrapper_content = WRAPPER_SH.format(jar=XML_GEN_JAR)

    if dry_run:
        print(f"  {bold('[DRY RUN]')} write wrapper → {wrapper_path}")
    else:
        wrapper_path.parent.mkdir(parents=True, exist_ok=True)
        wrapper_path.write_text(wrapper_content, encoding="utf-8")
        if system != "Windows":
            wrapper_path.chmod(0o755)
        print(green(f"  ✓ Wrapper установлен: {wrapper_path}"))

    # 6. Проверяем PATH
    bin_dir = str(wrapper_path.parent)
    path_dirs = os.environ.get("PATH", "").split(os.pathsep)
    if bin_dir not in path_dirs:
        print()
        print(yellow(f"  ⚠ {bin_dir} не в $PATH."))
        if system != "Windows":
            print(yellow("    Добавьте в ~/.bashrc или ~/.zshrc:"))
            print(cyan(f'      export PATH="$PATH:{bin_dir}"'))
            print(yellow("    Или для текущей сессии:"))
            print(cyan(f'      export PATH="$PATH:{bin_dir}"'))
        print()
    else:
        print(green(f"  ✓ {bin_dir} уже в $PATH — команда xml-gen доступна сразу"))

    return True


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
        install_script = target / "tools" / "install.py"
        if install_script.exists():
            subprocess.run([sys.executable, str(install_script)], cwd=str(target))
        else:
            print(yellow(f"  CLI не найден в {target}. Запустите вручную: python tools/install.py"))
    return 0


# ─── Внешние инструменты: pre-built бинари из GitHub Releases ────────────────
#
# Скачивает Latest-релиз каждого инструмента и распаковывает в
# `tools/external/<name>/`. Версии НЕ пиннятся — каждый запуск установщика
# тянет актуальный Latest. .version-маркер хранит установленный тег для
# идемпотентности (повторный запуск с той же версией пропускает скачивание).
#
# Имена ассетов в релизах стабильны между версиями: меняется только тег.
# При смене схемы именования — обновить `assets` для соответствующего tool.

EXTERNAL_TOOLS_DIR_NAME = "external"

EXTERNAL_TOOLS = {
    "v8-runner": {
        "repo": "alkoleft/v8-runner-rust",
        # fork_repo — наш форк с PR-ами, которые upstream долго не принимает.
        # Берётся, если его Latest-релиз published_at СВЕЖЕЕ, чем у upstream,
        # И в нём есть ассет для текущей платформы. Иначе — upstream.
        "fork_repo": "SteelMorgan/v8-runner-rust",
        "binary": "v8-runner",
        # (platform.system(), нормализованная machine) → (asset name, kind)
        "assets": {
            ("Linux", "x86_64"):   ("v8-runner-linux-x86_64-musl.tar.gz", "tar.gz"),
            ("Darwin", "x86_64"):  ("v8-runner-macos-x86_64.tar.gz",      "tar.gz"),
            ("Darwin", "arm64"):   ("v8-runner-macos-aarch64.tar.gz",     "tar.gz"),
            ("Windows", "x86_64"): ("v8-runner-windows-x86_64.zip",       "zip"),
        },
    },
    "v8-session-manager": {
        "repo": "1c-neurofish/v8-session-manager",
        "binary": "v8-session-manager",
        "assets": {
            ("Linux", "x86_64"): ("v8-session-manager-linux-x86_64", "raw"),
        },
    },
}


def _external_tools_dir(script_dir: Path) -> Path:
    return script_dir / EXTERNAL_TOOLS_DIR_NAME


def _normalize_machine() -> str:
    """Нормализует `platform.machine()` к ключу из EXTERNAL_TOOLS[...]["assets"]."""
    m = platform.machine().lower()
    if m in ("x86_64", "amd64"):
        return "x86_64"
    if m in ("arm64", "aarch64"):
        return "arm64"
    return m


def _fetch_latest_release(repo: str) -> Optional[dict]:
    """GET https://api.github.com/repos/<repo>/releases/latest. Возвращает JSON или None."""
    import urllib.request, urllib.error
    url = f"https://api.github.com/repos/{repo}/releases/latest"
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        print(yellow(f"    GitHub API: HTTP {e.code} для {repo}"))
    except Exception as e:
        print(yellow(f"    GitHub API недоступен ({type(e).__name__}: {e})"))
    return None


def _download_to(url: str, dest: Path, timeout: int = 120) -> bool:
    import urllib.request, urllib.error
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            dest.write_bytes(resp.read())
        return True
    except Exception as e:
        print(red(f"    Ошибка скачивания {url}: {type(e).__name__}: {e}"))
        return False


def _sha256_of(path: Path) -> str:
    import hashlib
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def _extract_to(archive: Path, kind: str, dest_dir: Path, binary: Optional[str] = None) -> bool:
    """Распаковывает архив в dest_dir. Для kind=raw — копирует файл сразу под
    целевым именем бинаря (`binary`), если оно задано.

    Почему raw кладётся под именем `binary`, а не сохраняет имя ассета: raw-ассет
    САМ является бинарём, но его имя в релизе обычно платформо-суффиксное
    (например `v8-session-manager-linux-x86_64`). `_place_binary` ищет файл по
    ТОЧНОМУ имени `binary` (`rglob(binary)`), и суффиксное имя не матчилось →
    бинарь не клался, установка молча падала «не удалось найти бинарь»
    (инцидент 2026-06-20, v8-session-manager). Кладём сразу правильно."""
    import tarfile, zipfile
    dest_dir.mkdir(parents=True, exist_ok=True)
    try:
        if kind == "tar.gz":
            with tarfile.open(archive, "r:gz") as tf:
                tf.extractall(dest_dir)
        elif kind == "zip":
            with zipfile.ZipFile(archive) as zf:
                zf.extractall(dest_dir)
        elif kind == "raw":
            target_name = binary or archive.name
            shutil.copy2(archive, dest_dir / target_name)
        else:
            print(red(f"    Неизвестный тип архива: {kind}"))
            return False
        return True
    except Exception as e:
        print(red(f"    Ошибка распаковки {archive.name}: {type(e).__name__}: {e}"))
        return False


def _place_binary(tool_dir: Path, binary: str) -> Optional[Path]:
    """Находит бинарь внутри `tool_dir` (после распаковки) и переносит его
    на верхний уровень как `tool_dir/<binary>` (или `<binary>.exe` под Windows)."""
    candidates = list(tool_dir.rglob(binary)) + list(tool_dir.rglob(binary + ".exe"))
    candidates = [c for c in candidates if c.is_file()]
    if not candidates:
        return None
    src = candidates[0]
    target_name = binary + ".exe" if src.suffix.lower() == ".exe" else binary
    target = tool_dir / target_name
    if src != target:
        if target.exists():
            target.unlink()
        shutil.move(str(src), str(target))
    try:
        target.chmod(0o755)
    except OSError:
        pass  # Windows
    return target


def _read_version_marker(tool_dir: Path) -> Optional[str]:
    """Возвращает строку маркера в новом формате `<tag>@<source>`.

    Backward-compat: старые маркеры содержат только `<tag>` (без источника) —
    считаем их `<tag>@upstream`, чтобы не передёргивать ре-инсталл при апгрейде
    схемы маркеров.
    """
    marker = tool_dir / ".version"
    if not marker.is_file():
        return None
    raw = marker.read_text().strip()
    if not raw:
        return None
    if "@" not in raw:
        return f"{raw}@upstream"
    return raw


def _select_release_source(spec: dict, asset_name: str) -> Optional[tuple[str, str, dict]]:
    """Выбирает источник релиза: upstream или fork.

    Возвращает (source_label, repo, release_json) либо None если ни один
    источник недоступен. source_label — "upstream" или "fork", удобно
    для логов и хранения в .version.

    Логика выбора (см. EXTERNAL_TOOLS[*]["fork_repo"]):
      1. Тянем Latest у upstream и fork (если fork_repo задан).
      2. Форк — ОСНОВНОЙ источник: если у него есть ассет под текущую
         платформу — берём форк (вне зависимости от даты релиза upstream).
      3. Upstream — ЗАПАСНОЙ: только когда форк недоступен или в его релизе
         нет ассета для этой платформы.

    ВНИМАНИЕ: раньше здесь сравнивались published_at и форк брался лишь когда
    его релиз СТРОГО свежее upstream. Это приводило к молчаливой подмене: более
    поздний upstream-релиз (v0.5.1, 2026-05-25) затирал форк-сборку v0.5.0-sm.1
    (2026-05-22) с WS-режимом / unlock_code / --dynamic. Форк специально держит
    PR-ы, которые upstream не принимает, поэтому он приоритетен по определению,
    а не по дате (инцидент 2026-06-20).
    """
    upstream_repo = spec["repo"]
    fork_repo = spec.get("fork_repo")

    upstream = _fetch_latest_release(upstream_repo)

    if not fork_repo:
        return ("upstream", upstream_repo, upstream) if upstream else None

    fork = _fetch_latest_release(fork_repo)

    # Готовим asset-индекс для проверки наличия ассета в форке
    def _has_asset(release: Optional[dict]) -> bool:
        if not release:
            return False
        return any(a.get("name") == asset_name for a in release.get("assets", []))

    fork_has = _has_asset(fork)
    upstream_has = _has_asset(upstream)

    # Форк — основной источник: есть ассет под платформу → берём форк
    # независимо от даты upstream-релиза.
    if fork and fork_has:
        suffix = ""
        if upstream and upstream_has:
            up_tag = upstream.get("tag_name") or "?"
            suffix = f" (upstream {up_tag} проигнорирован — форк приоритетен)"
        print(dim(f"    Источник: fork ({fork_repo}) — релиз {fork.get('tag_name')}{suffix}"))
        return ("fork", fork_repo, fork)

    # Upstream — запасной: форк недоступен или без ассета под эту платформу.
    if upstream and upstream_has:
        reason = "у форка нет ассета для этой платформы" if fork else "форк недоступен"
        print(dim(f"    Источник: upstream ({upstream_repo}) — {reason}"))
        return ("upstream", upstream_repo, upstream)

    return None


def install_external_tool(
    name: str,
    spec: dict,
    base_dir: Path,
    dry_run: bool = False,
) -> bool:
    """Скачивает Latest-релиз указанного инструмента в base_dir/name.
    Возвращает True, если инструмент в актуальном состоянии после вызова."""
    tool_dir = base_dir / name
    upstream_repo = spec["repo"]
    fork_repo = spec.get("fork_repo")
    binary = spec["binary"]

    title = f"{upstream_repo}"
    if fork_repo:
        title += f" + fork {fork_repo}"
    print(f"\n  ── {bold(name)} ({title}) ──")

    # 1) Платформа поддерживается?
    plat_key = (platform.system(), _normalize_machine())
    asset_info = spec["assets"].get(plat_key)
    if asset_info is None:
        supported = ", ".join(f"{s}/{m}" for s, m in spec["assets"].keys())
        print(yellow(f"    Платформа {plat_key[0]}/{plat_key[1]} не поддерживается ({name} ассеты: {supported})."))
        print(yellow(f"    Установка пропущена."))
        return False
    asset_name, kind = asset_info

    # 2) Узнаём актуальную пару (источник, релиз).
    #    Источник — upstream или fork (если fork_repo задан и релиз свежее).
    installed = _read_version_marker(tool_dir)
    has_binary = (tool_dir / binary).exists() or (tool_dir / (binary + ".exe")).exists()

    selection = _select_release_source(spec, asset_name)
    if selection is None:
        if has_binary:
            print(yellow(f"    GitHub недоступен — используем уже установленный "
                         f"{installed or '(версия неизвестна)'}."))
            return True
        print(yellow(f"    GitHub недоступен и локальной копии нет — пропуск."))
        return False
    source, repo, release = selection
    tag = release.get("tag_name") or ""
    if not tag:
        print(yellow(f"    Выбранный релиз ({source}) без tag_name — пропуск."))
        return False

    # marker = "<tag>@<source>" — обновляемся, если изменился любой компонент
    version_id = f"{tag}@{source}"

    # 3) Уже актуален?
    if installed == version_id and has_binary:
        print(green(f"    Уже установлен {tag} ({source}) — пропуск."))
        return True

    if dry_run:
        print(f"    [dry-run] Скачал бы {asset_name} из {tag} ({source}) → {tool_dir / binary}")
        return True

    # 4) Находим asset URL и (опционально) sha256-sidecar
    assets = {a["name"]: a["browser_download_url"] for a in release.get("assets", [])}
    asset_url = assets.get(asset_name)
    if not asset_url:
        available = ", ".join(sorted(assets.keys())) or "(пусто)"
        print(yellow(f"    Ассет '{asset_name}' не найден в релизе {tag}. Доступно: {available}"))
        return False
    sha_url = assets.get(asset_name + ".sha256")

    # 5) Скачивание во временный каталог
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = Path(tmp)
        archive_path = tmp_dir / asset_name

        print(f"    Скачивание {asset_name} (релиз {tag})...")
        if not _download_to(asset_url, archive_path):
            return False

        if sha_url:
            sha_path = tmp_dir / (asset_name + ".sha256")
            if _download_to(sha_url, sha_path):
                expected = sha_path.read_text().strip().split()[0].lower()
                actual = _sha256_of(archive_path)
                if expected != actual:
                    print(red(f"    SHA256 не совпал (ожидали {expected[:12]}…, получили {actual[:12]}…)."))
                    return False
                print(green("    SHA256 OK"))
            else:
                print(yellow("    Sha256 sidecar недоступен — пропускаю верификацию."))
        else:
            print(yellow("    Sha256 sidecar в релизе отсутствует — пропускаю верификацию."))

        # 6) Чистим прошлую установку, распаковываем
        if tool_dir.exists():
            shutil.rmtree(tool_dir)
        tool_dir.mkdir(parents=True, exist_ok=True)

        if not _extract_to(archive_path, kind, tool_dir, binary):
            return False

        binary_path = _place_binary(tool_dir, binary)
        if binary_path is None:
            print(red(f"    Не удалось найти бинарь '{binary}' после распаковки."))
            return False

        (tool_dir / ".version").write_text(version_id + "\n")
        print(green(f"    ✓ {binary_path}  ({tag} from {source})"))

    return True


def install_all_external_tools(script_dir: Path, dry_run: bool = False) -> None:
    """Ставит все инструменты из `EXTERNAL_TOOLS` в `tools/external/`.
    Сетевые/платформенные ошибки — некритичны: пропускаем и идём дальше."""
    base = _external_tools_dir(script_dir)
    if not dry_run:
        base.mkdir(parents=True, exist_ok=True)

    print()
    print(bold("  ── Внешние инструменты (tools/external/) ──────────────────"))
    print(f"  {dim('Каждый запуск тянет Latest release. Релиз кешируется через .version-маркер.')}")

    for name, spec in EXTERNAL_TOOLS.items():
        install_external_tool(name, spec, base, dry_run=dry_run)


# ─── Точка входа ─────────────────────────────────────────────────────────────

def find_framework_dir() -> Path:
    """Ищет каталог framework/ относительно скрипта."""
    script_dir = Path(__file__).resolve().parent
    fw_dir = script_dir.parent / "framework"
    if fw_dir.is_dir():
        return fw_dir
    fw_dir = script_dir / "framework"
    if fw_dir.is_dir():
        return fw_dir
    print(red(f"Каталог framework/ не найден рядом с {script_dir}"))
    sys.exit(1)


def find_mirror_dir(framework_dir: Path) -> Optional[Path]:
    """Ищет каталог framework_eng/ — EN-зеркало рядом с framework/."""
    mirror = framework_dir.parent / "framework_eng"
    return mirror if mirror.is_dir() else None


def main():
    parser = argparse.ArgumentParser(
        prog="install",
        description="1C BSL Agent Framework — CLI (clone, install)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Примеры:
  python tools/install.py clone                       # Клонировать репозиторий
  python tools/install.py clone -t ./my-fw --install  # Клонировать и установить
  python tools/install.py install                     # Интерактивный режим
  python tools/install.py --ide cursor --list           # Показать дерево
  python tools/install.py --ide cursor --all          # Установить всё
  python tools/install.py --ide cursor --include agent/developer workflow/full-cycle
  python tools/install.py --relink                     # Проверить симлинки
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
    parser.add_argument("--ide", nargs="+", choices=list(IDE_CONFIGS.keys()),
                        help="Целевая IDE (можно указать несколько через пробел)")
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
    parser.add_argument("--install-xml-gen", action="store_true",
                        help="Собрать xml-gen JAR и установить wrapper ~/.local/bin/xml-gen (без полной установки)")
    parser.add_argument("--install-external-tools", action="store_true",
                        help=f"Скачать Latest-релизы внешних инструментов в tools/{EXTERNAL_TOOLS_DIR_NAME}/ (без полной установки)")
    parser.add_argument("--skip-external-tools", action="store_true",
                        help="Не ставить внешние инструменты автоматически при полной установке")
    parser.add_argument("--sync", action="store_true",
                        help="Синхронизировать установку: удалить снятые симлинки компонентов")
    parser.add_argument("--estimate-context", action="store_true",
                        help="Оценить потребление контекста для выбранных компонентов")
    parser.add_argument("--estimate-english-only", action="store_true",
                        help="Показать English-only оценку контекста")
    parser.add_argument("--estimate-context-only", action="store_true",
                        help="Только показать оценку контекста и выйти")

    args = parser.parse_args()

    # Banner
    print()
    print(bold("  ┌──────────────────────────────────────────────────┐"))
    print(bold("  │   1C BSL Agent Framework — CLI                   │"))
    print(bold(f"  │   Версия: {CLI_VERSION:<39}│"))
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

    # Install xml-gen only
    if args.install_xml_gen:
        install_xml_gen(script_dir=Path(__file__).resolve().parent, dry_run=args.dry_run)
        print()
        return

    # Install external tools only
    if args.install_external_tools:
        install_all_external_tools(script_dir=Path(__file__).resolve().parent, dry_run=args.dry_run)
        print()
        return

    # Сканируем framework
    framework_dir = find_framework_dir()
    mirror_dir = find_mirror_dir(framework_dir)
    print(f"  Framework (RU): {framework_dir}")
    if mirror_dir:
        print(f"  Mirror   (EN): {mirror_dir}  ← симлинки будут на EN-версии")
    else:
        print(yellow(f"  Mirror (EN): не найден — симлинки на RU-версии (запустите --init-all)"))

    graph = FrameworkGraph(framework_dir, mirror_dir)
    print(f"  Найдено компонентов: {len(graph.components)}")

    # List mode
    if args.list:
        ide_keys = args.ide
        if not ide_keys:
            ide_keys = select_ides()
        ide_key = ide_keys[0]
        print(f"\n  IDE: {bold(IDE_CONFIGS[ide_key]['name'])}")
        print_tree(graph)
        return

    # Выбор IDE (одна или несколько)
    ide_keys = args.ide
    if not ide_keys:
        ide_keys = select_ides()

    # Для обратной совместимости: одиночный ide_key для шагов где нужен один
    ide_key = ide_keys[0]

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
            selected = interactive_select(
                graph,
                preselected=preselected,
                show_context_estimate=args.estimate_context or args.estimate_context_only,
                show_english_estimate=args.estimate_english_only,
            )

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
        _default_sync_source = preselected
    elif args.sync:
        _default_sync_source = detect_existing_component_symlinks(graph, ide_key, project_dir)
        print(f"\n  Режим синхронизации (--sync): найдено текущих симлинков {bold(str(len(_default_sync_source)))}")
    else:
        _default_sync_source = set()

    if args.estimate_context or args.estimate_english_only or args.estimate_context_only:
        always_tokens, on_demand_tokens, total_tokens, english_total, savings = estimate_context_usage(
            graph,
            selected,
        )
        en_complete = _has_complete_en_mirror(graph, selected)
        print()
        for line in format_context_estimate(
            always_tokens,
            on_demand_tokens,
            total_tokens,
            english_total,
            savings,
            show_english=args.estimate_english_only,
            mirror_available=en_complete,
        ):
            print(line)

        agent_contexts = estimate_agent_contexts(graph, selected)
        for line in format_agent_contexts(
            graph,
            agent_contexts,
            show_english=args.estimate_english_only,
        ):
            print(line)

        if graph.mirror_dir and not en_complete:
            print(yellow("  ⚠ Для части компонентов EN-зеркало отсутствует — они учтены как 0 токенов."))

        if graph.mirror_dir and agent_contexts and not _has_complete_en_mirror_for_agents(graph, agent_contexts):
            print(yellow("  ⚠ Для части агентских зависимостей EN-зеркало отсутствует — оценка по агентам неполная."))

        if not graph.mirror_dir:
            print(yellow("  ⚠ EN-зеркало не найдено — оценка равна 0. Для корректного подсчёта используйте framework_eng/."))

        if args.estimate_context_only:
            return

    # ── Установка для каждой выбранной IDE ──
    all_resolved = graph.resolve_dependencies(selected)
    xml_gen_needed = _xml_gen_selected(all_resolved)

    # Если выбрано несколько IDE — показываем заголовок
    if len(ide_keys) > 1:
        ide_names = ", ".join(IDE_CONFIGS[k]["name"] for k in ide_keys)
        print(f"\n  {bold('Установка для IDE:')} {cyan(ide_names)}")

    total_installed = total_removed = total_skipped = 0

    for current_ide_key in ide_keys:
        if len(ide_keys) > 1:
            ide_cfg_cur = IDE_CONFIGS[current_ide_key]
            print(f"\n  {'─' * 66}")
            print(f"  {bold('→')} {bold(ide_cfg_cur['name'])}")

        # Для каждой IDE — свой sync_source
        if is_interactive and not (args.all or args.include):
            cur_sync_source = preselected if current_ide_key == ide_key else set()
        elif args.sync:
            cur_sync_source = detect_existing_component_symlinks(graph, current_ide_key, project_dir)
        else:
            cur_sync_source = set()

        installed, skipped, removed = install_components(
            graph=graph,
            selected_ids=selected,
            existing_symlink_ids=cur_sync_source,
            ide_key=current_ide_key,
            project_dir=project_dir,
            use_symlinks=use_symlinks,
            dry_run=args.dry_run,
        )
        total_installed += installed
        total_skipped += skipped
        total_removed += removed

        print()
        if args.dry_run:
            print(f"  {bold('[DRY RUN]')} {IDE_CONFIGS[current_ide_key]['name']}: было бы установлено: {installed}, удалено: {removed}, пропущено: {skipped}")
        else:
            print(green(f"  ✓ {IDE_CONFIGS[current_ide_key]['name']}: установлено {installed} компонентов"))
            if removed:
                print(green(f"  ✓ Удалено симлинков: {removed}"))
            if skipped:
                print(yellow(f"  ⚠ Пропущено: {skipped}"))

        # Лог сессии для верификации (не в dry-run)
        if not args.dry_run:
            write_session_log(
                project_dir=project_dir,
                ide_key=current_ide_key,
                selected=selected,
                model_map=model_map,
                use_symlinks=use_symlinks,
                installed=installed,
                skipped=skipped,
                removed=removed,
                graph=graph,
            )

    # Post-install: xml-gen CLI (один раз, если выбраны навыки xml-generation)
    if xml_gen_needed and not args.dry_run:
        install_xml_gen(script_dir=Path(__file__).resolve().parent, dry_run=False)
    elif xml_gen_needed and args.dry_run:
        install_xml_gen(script_dir=Path(__file__).resolve().parent, dry_run=True)

    # Post-install: внешние инструменты (всегда, если не указан --skip-external-tools)
    if not args.skip_external_tools:
        install_all_external_tools(
            script_dir=Path(__file__).resolve().parent,
            dry_run=args.dry_run,
        )

    # Post-install: capabilities/ симлинк (если выбран хотя бы один tool-usage навык)
    if _tool_usage_selected(all_resolved, graph):
        cap_ok = install_capabilities_symlink(
            framework_dir=graph.framework_dir,
            project_dir=project_dir,
            use_symlinks=use_symlinks,
            mirror_dir=graph.mirror_dir,
            dry_run=args.dry_run,
        )
        if cap_ok and not args.dry_run:
            print(green(f"  ✓ capabilities/ → framework/capabilities/"))

    # Post-install: tools/ симлинк (если есть компоненты с requires: [tools])
    if _tools_required(all_resolved, graph):
        tools_ok = install_tools_symlink(
            framework_dir=graph.framework_dir,
            project_dir=project_dir,
            use_symlinks=use_symlinks,
            dry_run=args.dry_run,
        )
        if tools_ok and not args.dry_run:
            print(green(f"  ✓ tools/ → {graph.framework_dir.parent / 'tools'}/"))

    # Подсказка по проектным навыкам (для первой IDE)
    ide_cfg = IDE_CONFIGS[ide_key]
    print(f"\n  {bold('Проектные навыки')}: размещайте в {cyan(ide_cfg['skills_dir'] + '/')} вашего проекта")

    if use_symlinks:
        print(f"\n  {dim('Симлинки привязаны к расположению фреймворка.')}")
        print(f"  {dim('Если переместите framework/ — запустите: python tools/install.py --relink')}")

    print()


if __name__ == "__main__":
    main()
