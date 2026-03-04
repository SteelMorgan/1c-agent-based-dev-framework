"""
Lightweight TUI primitives for 1c-ai-agent-cli.
No external dependencies — uses ANSI escape codes + raw terminal input.
Python 3.7+, cross-platform (Linux / macOS / Windows).
"""

import os
import subprocess
import sys
import time
from pathlib import Path
from typing import Callable, Dict, List, Optional, Set, Tuple, Union

# Sentinel for "go back to previous step" (used in select_many)
BACK = object()


# ─── Cross-platform keypress reader ──────────────────────────────────────────

def is_tui_available() -> bool:
    """Can we run TUI? Needs a real terminal (not pipe/redirect)."""
    try:
        return hasattr(sys.stdin, "fileno") and os.isatty(sys.stdin.fileno())
    except Exception:
        return False


if sys.platform == "win32":
    def _getch_impl() -> str:
        import msvcrt
        ch = msvcrt.getwch()
        if ch in ("\x00", "\xe0"):
            ch2 = msvcrt.getwch()
            return {"H": "UP", "P": "DOWN", "K": "LEFT", "M": "RIGHT"}.get(ch2, "")
        if ch == "\r":
            return "ENTER"
        if ch == "\x1b":
            return "ESC"
        if ch == " ":
            return "SPACE"
        if ch in ("w", "W", "ц", "Ц"):  # ц = w на русской раскладке
            return "UP"
        if ch in ("s", "S", "ы", "Ы"):  # ы = s на русской раскладке
            return "DOWN"
        if ch == "\x03":
            raise KeyboardInterrupt
        return ch
else:
    def _getch_impl() -> str:
        import termios
        import tty
        fd = sys.stdin.fileno()
        old = termios.tcgetattr(fd)
        try:
            tty.setraw(fd)
            ch = sys.stdin.read(1)
            if ch == "\x1b":
                ch2 = sys.stdin.read(1)
                if ch2 == "[":
                    ch3 = sys.stdin.read(1)
                    return {"A": "UP", "B": "DOWN", "C": "RIGHT", "D": "LEFT"}.get(ch3, "")
                return "ESC"
            if ch in ("\r", "\n"):
                return "ENTER"
            if ch == " ":
                return "SPACE"
            if ch in ("w", "W", "ц", "Ц"):  # ц = w на русской раскладке
                return "UP"
            if ch in ("s", "S", "ы", "Ы"):  # ы = s на русской раскладке
                return "DOWN"
            if ch == "\x03":
                raise KeyboardInterrupt
            return ch
        finally:
            termios.tcsetattr(fd, termios.TCSADRAIN, old)


def getch() -> str:
    """Read single keypress. Returns: UP/DOWN/LEFT/RIGHT/ENTER/SPACE/ESC or char."""
    return _getch_impl()


# ─── ANSI helpers ─────────────────────────────────────────────────────────────

def _detect_color() -> bool:
    if sys.platform == "win32":
        try:
            import ctypes
            kernel32 = ctypes.windll.kernel32
            kernel32.SetConsoleMode(kernel32.GetStdHandle(-11), 7)
            return True
        except Exception:
            return False
    return sys.stdout.isatty()


_COLOR = _detect_color()


def _ansi(code: str) -> str:
    return f"\033[{code}" if _COLOR else ""


def hide_cursor():
    sys.stdout.write(_ansi("?25l"))
    sys.stdout.flush()


def show_cursor():
    sys.stdout.write(_ansi("?25h"))
    sys.stdout.flush()


def clear_screen():
    sys.stdout.write(_ansi("2J") + _ansi("H"))
    sys.stdout.flush()


def _c(text: str, code: str) -> str:
    return f"\033[{code}m{text}\033[0m" if _COLOR else text


def bold(t: str) -> str:
    return _c(t, "1")


def green(t: str) -> str:
    return _c(t, "32")


def yellow(t: str) -> str:
    return _c(t, "33")


def cyan(t: str) -> str:
    return _c(t, "36")


def dim(t: str) -> str:
    return _c(t, "2")


def red(t: str) -> str:
    return _c(t, "31")


def inverse(t: str) -> str:
    return _c(t, "7")


def key(t: str) -> str:
    """Выделяет управляющий символ/клавишу в подсказках."""
    return bold(cyan(t))


def _term_height() -> int:
    try:
        return os.get_terminal_size().lines
    except (ValueError, OSError):
        return 40


# ─── Banner ──────────────────────────────────────────────────────────────────

CLI_VERSION = "0.4.1"

BANNER = [
    "",
    bold("  ┌──────────────────────────────────────────────────┐"),
    bold("  │   1C BSL Agent Framework — Установщик            │"),
    bold(f"  │   Версия: {CLI_VERSION:<39}│"),
    bold("  └──────────────────────────────────────────────────┘"),
    "",
]


def _render(lines: List[str]):
    """Clear screen and draw lines."""
    clear_screen()
    sys.stdout.write("\n".join(lines) + "\n")
    sys.stdout.flush()


def _confirm_exit(lines: List[str], prompt: str = "Выйти? (y/n):") -> bool:
    """Показывает подтверждение выхода. Возвращает True если пользователь подтвердил."""
    extra = list(lines)
    extra.append("")
    extra.append(f"  {yellow(prompt)}")
    _render(extra)
    k = getch()
    return k in ("y", "Y", "н", "Н")  # н = y на русской раскладке


# ─── Component: Single-select menu ──────────────────────────────────────────

def select_one(
    title: str,
    items: List[Tuple[str, str]],
) -> int:
    """
    Arrow-key single-select menu.
    items: [(label, description), ...]
    Returns selected index, or -1 if cancelled.
    """
    cursor = 0
    n = len(items)

    hide_cursor()
    try:
        while True:
            lines = list(BANNER)
            lines.append(f"  {bold(title)}")
            lines.append("")

            for i, (label, desc) in enumerate(items):
                if i == cursor:
                    lines.append(f"   ► {inverse(f' {label:<28}')} {desc}")
                else:
                    lines.append(f"     {label:<28}  {dim(desc)}")

            lines.append("")
            lines.append(f"  {dim('выбор')} {key('↑')}{key('↓')}{key('W')}{key('S')}  {dim('подтвердить')} {key('Enter')}  {dim('выход')} {key('Q')}")

            _render(lines)

            k = getch()
            if k == "UP":
                cursor = (cursor - 1) % n
            elif k == "DOWN":
                cursor = (cursor + 1) % n
            elif k == "ENTER":
                return cursor
            elif k in ("q", "Q", "й", "Й", "ESC"):
                if _confirm_exit(lines):
                    return -1
    except KeyboardInterrupt:
        return -1
    finally:
        show_cursor()


# ─── Component: Multi-select checklist ───────────────────────────────────────

# (id, display_label, description, is_group_header)
ChecklistItem = Tuple[str, str, str, bool]


def select_many(
    title: str,
    items: List[ChecklistItem],
    preselected: Optional[Set[str]] = None,
    on_open: Optional[Callable[[str], Optional[Path]]] = None,
    allow_back: bool = True,
    get_dependencies: Optional[Callable[[str], Set[str]]] = None,
    get_required_by: Optional[Callable[[str], Set[str]]] = None,
    get_mutual: Optional[Callable[[str], Set[str]]] = None,
    status_lines_provider: Optional[Callable[[Set[str]], List[str]]] = None,
) -> Union[Optional[Set[str]], object]:
    """
    Arrow-key multi-select checklist with group headers.
    items: [(id, label, description, is_header), ...]
    on_open: если задан, вызывается при нажатии 'o' — возвращает Path для просмотра или None.
    allow_back: если True, клавиша b/Backspace возвращает BACK (назад на предыдущий шаг).
    get_dependencies: при выборе — добавить эти id. get_required_by: кто зависит — нельзя снять.
    get_mutual: взаимозависимые — toggle вместе.
    Returns: set of selected ids, None if cancelled, or BACK if user pressed back.
    """
    selectable = [i for i, item in enumerate(items) if not item[3]]
    if not selectable:
        return set(preselected or set())

    valid_ids: Set[str] = {item[0] for item in items if not item[3]}
    manual_selected: Set[str] = set(preselected or set()) & valid_ids

    selected: Set[str] = set()
    locked_ids: Set[str] = set()

    cursor_pos = 0  # index into selectable[]
    page_h = _term_height() - 12  # room for banner + footer

    def _open_current():
        iid = items[cur_idx][0]
        if on_open:
            path = on_open(iid)
            if path and path.exists():
                show_cursor()
                try:
                    env = os.environ.copy()
                    env["LESS"] = (env.get("LESS", "") + " -P  q — вернуться к выбору").strip()
                    subprocess.run(
                        ["less", "-R", str(path)],
                        stdout=sys.stdout,
                        stderr=sys.stderr,
                        stdin=sys.stdin,
                        env=env,
                    )
                except FileNotFoundError:
                    try:
                        subprocess.run(["cat", str(path)])
                    except FileNotFoundError:
                        sys.stdout.write(path.read_text(encoding="utf-8", errors="replace"))
                        sys.stdout.write("\n\n  Enter — вернуться к выбору")
                        sys.stdout.flush()
                        input()
                finally:
                    hide_cursor()

    def _expand_manual_with_mutual(roots: Set[str]) -> Set[str]:
        expanded = set(roots)
        if not get_mutual:
            return expanded

        queue = list(roots)
        while queue:
            cid = queue.pop()
            for mid in get_mutual(cid):
                if mid in valid_ids and mid not in expanded:
                    expanded.add(mid)
                    queue.append(mid)
        return expanded

    def _recompute_state() -> Tuple[Set[str], Set[str]]:
        roots = _expand_manual_with_mutual(manual_selected)
        computed = set(roots)

        if get_dependencies:
            queue = list(roots)
            while queue:
                cid = queue.pop()
                for dep in get_dependencies(cid):
                    if dep in valid_ids and dep not in computed:
                        computed.add(dep)
                        queue.append(dep)

        # Блокируем только авто-выбранные зависимости. Ручные корни и mutual-корни остаются снимаемыми.
        locked = computed - roots
        return computed, locked

    selected, locked_ids = _recompute_state()

    hide_cursor()
    try:
        while True:
            cur_idx = selectable[cursor_pos]

            # Scrolling window
            start = 0
            if cur_idx > start + page_h - 3:
                start = max(0, cur_idx - page_h + 5)

            end = min(len(items), start + page_h)

            lines = list(BANNER)
            lines.append(f"  {bold(title)}")
            lines.append(f"  Выбрано: {green(str(len(selected)))}")
            if status_lines_provider:
                for status_line in status_lines_provider(selected):
                    lines.append(f"  {status_line}")
            lines.append("")

            for i in range(start, end):
                iid, label, desc, is_hdr = items[i]
                if is_hdr:
                    lines.append(f"  {bold(label)}")
                else:
                    is_cur = (i == cur_idx)
                    locked = iid in locked_ids
                    chk = green("[✓]") if iid in selected else "[ ]"
                    if locked:
                        chk = dim("[✓]")
                    desc_style = dim(desc) if not is_cur else desc
                    if is_cur:
                        lines.append(f"   ► {chk} {inverse(f' {iid:<36}')} {desc_style}")
                    else:
                        item_line = f"     {chk}  {iid:<36} {desc_style}"
                        if locked:
                            lines.append(dim(item_line))
                        else:
                            lines.append(item_line)

            if end < len(items):
                lines.append(f"     {dim(f'... ещё {len(items) - end} ...')}")

            lines.append("")
            hint_parts = [
                f"{dim('навигация')} {key('↑')}{key('↓')}{key('W')}{key('S')}",
                f"{dim('выбор')} {key('Space')}",
                f"{dim('всё')} {key('A')}",
                f"{dim('ничего')} {key('N')}",
                f"{dim('открыть')} {key('O')} {dim('(Q — вернуться)')}",
                f"{dim('готово')} {key('Enter')}",
            ]
            if allow_back:
                hint_parts.append(f"{dim('назад')} {key('B')}")
            hint_parts.append(f"{dim('выход')} {key('Q')}")
            lines.append("  " + "  ".join(hint_parts))

            _render(lines)

            k = getch()
            if allow_back and k in ("b", "B", "и", "И", "\x7f", "\x08"):  # b/и, Backspace
                return BACK
            if k == "UP":
                cursor_pos = (cursor_pos - 1) % len(selectable)
            elif k == "DOWN":
                cursor_pos = (cursor_pos + 1) % len(selectable)
            elif k == "SPACE":
                iid = items[cur_idx][0]

                # Автовыбранные зависимости снимать нельзя — информируем пользователя
                if iid in locked_ids:
                    blocked_lines = list(lines)
                    blocked_lines.append("")
                    blocked_lines.append(f"  {yellow('Заблокирован: от него зависят другие выбранные компоненты')}")
                    _render(blocked_lines)
                    time.sleep(1.5)
                    continue

                if iid in manual_selected:
                    # Снимаем текущий корень
                    to_drop_roots: Set[str] = {iid}

                    # Снимаем все ручные корни, которые зависят от него (транзитивно)
                    if get_required_by:
                        queue = [iid]
                        visited: Set[str] = set()
                        while queue:
                            current = queue.pop(0)
                            if current in visited:
                                continue
                            visited.add(current)
                            for dep_root in get_required_by(current):
                                if dep_root in valid_ids and dep_root not in to_drop_roots:
                                    to_drop_roots.add(dep_root)
                                    queue.append(dep_root)

                    # Взаимозависимые корни снимаются вместе
                    if get_mutual:
                        queue = list(to_drop_roots)
                        while queue:
                            current = queue.pop(0)
                            for mid in get_mutual(current):
                                if mid in valid_ids and mid not in to_drop_roots:
                                    to_drop_roots.add(mid)
                                    queue.append(mid)

                    manual_selected -= to_drop_roots
                else:
                    manual_selected.add(iid)
                    # Взаимозависимые корни выбираются вместе
                    if get_mutual:
                        for mid in get_mutual(iid):
                            if mid in valid_ids:
                                manual_selected.add(mid)
                    if cursor_pos < len(selectable) - 1:
                        cursor_pos += 1

                selected, locked_ids = _recompute_state()
            elif k in ("a", "A", "ф", "Ф"):
                manual_selected = set(valid_ids)
                selected, locked_ids = _recompute_state()
            elif k in ("n", "N", "т", "Т"):
                manual_selected.clear()
                selected, locked_ids = _recompute_state()
            elif k in ("o", "O", "щ", "Щ") and on_open:
                _open_current()
            elif k == "ENTER":
                return selected
            elif k in ("q", "Q", "й", "Й", "ESC"):
                if _confirm_exit(lines):
                    return None
                # иначе продолжаем цикл

    except KeyboardInterrupt:
        return None
    finally:
        show_cursor()


# ─── Component: Per-agent model picker ───────────────────────────────────────

def select_models_tui(
    agents: List[Tuple[str, str, str]],  # (agent_id, alias, default_model)
    available_models: List[str],
) -> Optional[Dict[str, str]]:
    """
    Per-agent model picker. ←→ cycles through available models.
    Returns {agent_id: chosen_model} or None if cancelled.
    """
    if not agents or not available_models:
        return None

    n = len(agents)
    n_models = len(available_models)

    # Track selected model index per agent
    model_idx: List[int] = []
    for _, _, default in agents:
        if default in available_models:
            model_idx.append(available_models.index(default))
        else:
            available_models.append(default)
            model_idx.append(len(available_models) - 1)

    cursor = 0

    hide_cursor()
    try:
        while True:
            lines = list(BANNER)
            lines.append(f"  {bold('Настройка моделей для агентов')}")
            lines.append("")

            for i, (aid, alias, _) in enumerate(agents):
                short = aid.split("/")[-1]
                model = available_models[model_idx[i]]
                alias_s = dim(f"({alias})")
                is_cur = i == cursor

                if is_cur:
                    arrow_l = cyan("◄")
                    arrow_r = cyan("►")
                    lines.append(
                        f"   ► {short:<12}{alias_s:<12} {arrow_l} {inverse(f' {model:<33}')} {arrow_r}"
                    )
                else:
                    lines.append(
                        f"     {short:<12}{alias_s:<12}   {model:<33}"
                    )

            lines.append("")
            lines.append(
                f"  {dim('агент')} {key('↑')}{key('↓')}{key('W')}{key('S')}  "
                f"{dim('модель')} {key('←')}{key('→')}  "
                f"{dim('подтвердить')} {key('Enter')}  {dim('выход')} {key('Q')}"
            )
            lines.append("")

            # Show available models
            mlist = "  ".join(available_models[:8])
            lines.append(f"  {dim('Доступные:')} {dim(mlist)}")

            _render(lines)

            k = getch()
            if k == "UP":
                cursor = (cursor - 1) % n
            elif k == "DOWN":
                cursor = (cursor + 1) % n
            elif k == "LEFT":
                model_idx[cursor] = (model_idx[cursor] - 1) % n_models
            elif k == "RIGHT":
                model_idx[cursor] = (model_idx[cursor] + 1) % n_models
            elif k == "ENTER":
                result = {}
                for i, (aid, _, _) in enumerate(agents):
                    result[aid] = available_models[model_idx[i]]
                return result
            elif k in ("q", "Q", "й", "Й", "ESC"):
                if _confirm_exit(lines):
                    return None

    except KeyboardInterrupt:
        return None
    finally:
        show_cursor()


# ─── Component: Directory browser ───────────────────────────────────────────

def browse_directory(
    start_dir: str,
    title: str = "Выберите каталог проекта",
    extra_info: Optional[List[str]] = None,
) -> Optional[str]:
    """
    Interactive directory browser with arrow-key navigation.

    Returns:
      - path string — user selected a directory (Space)
      - ""          — user wants manual text input (t)
      - None        — cancelled (q / Esc)
    """
    from pathlib import Path as _Path

    current = _Path(start_dir).resolve()
    cursor = 0
    show_hidden = False

    hide_cursor()
    try:
        while True:
            # List subdirectories
            try:
                all_entries = sorted(current.iterdir(), key=lambda p: p.name.lower())
                dirs = [e for e in all_entries if e.is_dir()]
            except PermissionError:
                dirs = []

            # Split into visible and hidden
            visible = [d for d in dirs if not d.name.startswith(".")]
            hidden = [d for d in dirs if d.name.startswith(".")]

            # Build display list
            items: List[Tuple[str, Optional[_Path], bool]] = []  # (label, path, is_hidden)

            # Parent navigation
            if current.parent != current:
                items.append(("..  (на уровень выше)", current.parent, False))

            for d in visible:
                items.append((d.name + "/", d, False))

            if show_hidden:
                for d in hidden:
                    items.append((d.name + "/", d, True))
            elif hidden:
                items.append((f"  ({len(hidden)} скрытых — h показать)", None, True))

            if not items:
                items.append(("(пусто)", None, False))

            # Clamp cursor
            cursor = max(0, min(cursor, len(items) - 1))

            # Render
            page_h = _term_height() - 14
            lines = list(BANNER)
            lines.append(f"  {bold(title)}")
            if extra_info:
                for info_line in extra_info:
                    lines.append(f"  {info_line}")
            lines.append("")
            lines.append(f"  {cyan(str(current))}")
            lines.append("")

            # Scroll window
            scroll_start = max(0, cursor - page_h + 3)
            scroll_end = min(len(items), scroll_start + page_h)

            for i in range(scroll_start, scroll_end):
                label, _path, is_hid = items[i]
                is_cur = i == cursor
                display = f" {label:<55}"

                if is_cur:
                    lines.append(f"   ► {inverse(display)}")
                elif is_hid:
                    lines.append(f"     {dim(label)}")
                else:
                    lines.append(f"     {label}")

            if scroll_end < len(items):
                lines.append(f"     {dim(f'... ещё {len(items) - scroll_end} ...')}")

            lines.append("")
            lines.append(
                f"  {dim('навигация')} {key('↑')}{key('↓')}{key('W')}{key('S')}  "
                f"{dim('войти')} {key('Enter')}  {dim('выбрать')} {key('Space')}  "
                f"{dim('назад')} {key('Backspace')}  {dim('путь')} {key('T')}  "
                f"{dim('скрытые')} {key('H')}  {dim('выход')} {key('Q')}"
            )

            _render(lines)

            k = getch()
            if k == "UP":
                cursor = (cursor - 1) % len(items)
            elif k == "DOWN":
                cursor = (cursor + 1) % len(items)
            elif k == "ENTER":
                _, target, _ = items[cursor]
                if target and target.is_dir():
                    current = target
                    cursor = 0
            elif k == "SPACE":
                return str(current)
            elif k in ("\x7f", "\x08", "LEFT"):  # Backspace / Left
                if current.parent != current:
                    current = current.parent
                    cursor = 0
            elif k in ("h", "H", "р", "Р"):  # р = h на русской раскладке
                show_hidden = not show_hidden
            elif k in ("t", "T", "е", "Е"):  # е = t на русской раскладке
                return ""  # signal: switch to text input
            elif k in ("q", "Q", "й", "Й", "ESC"):
                if _confirm_exit(lines):
                    return None

    except KeyboardInterrupt:
        return None
    finally:
        show_cursor()
