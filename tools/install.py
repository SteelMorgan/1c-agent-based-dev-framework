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
import os
import platform
import re
import shutil
import sys
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple


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
    "config": "rules_dir",
    "provider": "rules_dir",
    "registry": "rules_dir",
}

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
        """Человекочитаемое имя из первого заголовка файла."""
        try:
            for line in self.filepath.read_text(encoding="utf-8").splitlines():
                if line.startswith("# "):
                    return line[2:].strip()
        except (OSError, UnicodeDecodeError):
            pass
        return self.id

    def __repr__(self):
        return f"Component({self.id})"


class FrameworkGraph:
    """Граф зависимостей компонентов фреймворка."""

    def __init__(self, framework_dir: Path):
        self.framework_dir = framework_dir
        self.components: Dict[str, Component] = {}
        self._scan()

    def _scan(self):
        """Сканирует framework/ на .md файлы с frontmatter."""
        for md_file in sorted(self.framework_dir.rglob("*.md")):
            fm = parse_frontmatter(md_file)
            if not fm or "id" not in fm:
                continue

            comp_id = fm["id"]
            comp_type = fm.get("type", "unknown")
            depends = fm.get("depends_on", [])
            if isinstance(depends, str):
                depends = [depends] if depends else []

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
    "config": "Конфигурация",
    "provider": "Провайдеры",
    "registry": "Реестр",
}

TYPE_ICONS = {
    "skill": "📘",
    "rule": "📋",
    "agent": "🤖",
    "workflow": "🔄",
    "config": "⚙️",
    "provider": "🔌",
    "registry": "📦",
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
    """Интерактивный выбор IDE."""
    print(f"\n{bold('Выберите IDE:')}\n")
    ide_list = list(IDE_CONFIGS.keys())
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


def interactive_select(graph: FrameworkGraph) -> Set[str]:
    """Интерактивный выбор компонентов с отображением зависимостей."""
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
        print(f"    {cyan('1,3,5-8')}    — выбрать/снять компоненты (номера через запятую, диапазоны)")
        print(f"    {cyan('all')}        — выбрать всё")
        print(f"    {cyan('none')}       — снять всё")
        print(f"    {cyan('agents')}     — выбрать всех агентов")
        print(f"    {cyan('skills')}     — выбрать все навыки")
        print(f"    {cyan('rules')}      — выбрать все правила")
        print(f"    {cyan('minimal')}    — минимальный набор (developer + quick-fix)")
        print(f"    {cyan('done')}       — завершить выбор и установить")
        print(f"    {cyan('quit')}       — выйти без установки")
        print()

        try:
            raw = input(f"  {bold('>')} ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            print()
            sys.exit(0)

        if raw == "quit" or raw == "q":
            sys.exit(0)

        if raw == "done" or raw == "d":
            if not selected:
                print(yellow("  Ничего не выбрано. Выберите компоненты или введите 'quit'."))
                continue
            break

        if raw == "all":
            selected = {c.id for c in graph.get_installable()}
            continue

        if raw == "none":
            selected.clear()
            continue

        if raw == "agents":
            for c in graph.get_by_type("agent"):
                selected.add(c.id)
            continue

        if raw == "skills":
            for c in graph.get_by_type("skill"):
                selected.add(c.id)
            continue

        if raw == "rules":
            for c in graph.get_by_type("rule"):
                selected.add(c.id)
            continue

        if raw == "minimal":
            selected = {"agent/developer", "workflow/quick-fix"}
            continue

        # Парсим номера: 1,3,5-8
        try:
            nums = _parse_numbers(raw)
            for n in nums:
                if n in idx_map:
                    cid = idx_map[n]
                    if cid in selected:
                        selected.discard(cid)  # toggle off
                    else:
                        selected.add(cid)
                else:
                    print(yellow(f"  Номер {n} вне диапазона."))
        except ValueError:
            print(red(f"  Не понял команду: '{raw}'"))

    return selected


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
                # Делаем относительный симлинк если возможно
                try:
                    rel_path = os.path.relpath(source_file, target_file.parent)
                    target_file.symlink_to(rel_path)
                except ValueError:
                    # На Windows: разные диски — абсолютный симлинк
                    target_file.symlink_to(source_file)
                installed += 1
            except OSError as e:
                print(red(f"  ✗ Ошибка симлинка {comp.id}: {e}"))
                # Fallback — копируем
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

    # Определяем метод установки
    if args.copy:
        use_symlinks = False
    else:
        use_symlinks = detect_symlink_support()
        if not use_symlinks:
            print(yellow("\n  ⚠ Симлинки недоступны (Windows без Developer Mode)."))
            print(yellow("    Файлы будут скопированы. При обновлении фреймворка — перезапустите install."))

    project_dir = args.project_dir.resolve()

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
    print(f"  {bold('Конфигурация')}: отредактируйте {cyan('framework/config.md')} под свой проект")

    if use_symlinks:
        print(f"\n  {dim('Симлинки привязаны к расположению фреймворка.')}")
        print(f"  {dim('Если переместите framework/ — запустите: python install.py --relink')}")

    print()


if __name__ == "__main__":
    main()
