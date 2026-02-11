"""
Lightweight TUI primitives for install.py.
No external dependencies — uses ANSI escape codes + raw terminal input.
Python 3.7+, cross-platform (Linux / macOS / Windows).
"""

import os
import sys
from typing import Dict, List, Optional, Set, Tuple


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


def _term_height() -> int:
    try:
        return os.get_terminal_size().lines
    except (ValueError, OSError):
        return 40


# ─── Banner ──────────────────────────────────────────────────────────────────

BANNER = [
    "",
    bold("  ┌──────────────────────────────────────────────────┐"),
    bold("  │   1C BSL Agent Framework — Установщик            │"),
    bold("  │   Версия: 0.3                                    │"),
    bold("  └──────────────────────────────────────────────────┘"),
    "",
]


def _render(lines: List[str]):
    """Clear screen and draw lines."""
    clear_screen()
    sys.stdout.write("\n".join(lines) + "\n")
    sys.stdout.flush()


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
            lines.append(f"  {dim('↑↓ выбор  Enter подтвердить  q выход')}")

            _render(lines)

            key = getch()
            if key == "UP":
                cursor = (cursor - 1) % n
            elif key == "DOWN":
                cursor = (cursor + 1) % n
            elif key == "ENTER":
                return cursor
            elif key in ("q", "Q", "ESC"):
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
) -> Optional[Set[str]]:
    """
    Arrow-key multi-select checklist with group headers.
    items: [(id, label, description, is_header), ...]
    Returns set of selected ids, or None if cancelled.
    """
    selected: Set[str] = set(preselected or set())
    selectable = [i for i, item in enumerate(items) if not item[3]]
    if not selectable:
        return selected

    cursor_pos = 0  # index into selectable[]
    page_h = _term_height() - 12  # room for banner + footer

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
            lines.append("")

            for i in range(start, end):
                iid, label, desc, is_hdr = items[i]
                if is_hdr:
                    lines.append(f"  {bold(label)}")
                else:
                    is_cur = (i == cur_idx)
                    chk = green("[✓]") if iid in selected else "[ ]"
                    if is_cur:
                        lines.append(f"   ► {chk} {inverse(f' {iid:<36}')} {desc}")
                    else:
                        lines.append(f"     {chk}  {iid:<36} {dim(desc)}")

            if end < len(items):
                lines.append(f"     {dim(f'... ещё {len(items) - end} ...')}")

            lines.append("")
            lines.append(
                f"  {dim('↑↓ навигация  Space выбор  a всё  n ничего  Enter готово  q отмена')}"
            )

            _render(lines)

            key = getch()
            if key == "UP":
                cursor_pos = (cursor_pos - 1) % len(selectable)
            elif key == "DOWN":
                cursor_pos = (cursor_pos + 1) % len(selectable)
            elif key == "SPACE":
                iid = items[cur_idx][0]
                if iid in selected:
                    selected.discard(iid)
                else:
                    selected.add(iid)
                # Move to next item for convenience
                if cursor_pos < len(selectable) - 1:
                    cursor_pos += 1
            elif key in ("a", "A"):
                selected = {items[i][0] for i in selectable}
            elif key in ("n", "N"):
                selected.clear()
            elif key == "ENTER":
                return selected
            elif key in ("q", "Q", "ESC"):
                return None

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
            lines.append(f"  {dim('↑↓ агент  ←→ модель  Enter подтвердить  q отмена')}")
            lines.append("")

            # Show available models
            mlist = "  ".join(available_models[:8])
            lines.append(f"  {dim('Доступные:')} {dim(mlist)}")

            _render(lines)

            key = getch()
            if key == "UP":
                cursor = (cursor - 1) % n
            elif key == "DOWN":
                cursor = (cursor + 1) % n
            elif key == "LEFT":
                model_idx[cursor] = (model_idx[cursor] - 1) % n_models
            elif key == "RIGHT":
                model_idx[cursor] = (model_idx[cursor] + 1) % n_models
            elif key == "ENTER":
                result = {}
                for i, (aid, _, _) in enumerate(agents):
                    result[aid] = available_models[model_idx[i]]
                return result
            elif key in ("q", "Q", "ESC"):
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
                f"  {dim('↑↓ навигация  Enter войти  Space выбрать текущий каталог')}"
            )
            lines.append(
                f"  {dim('Backspace назад  t ввести путь  h скрытые  q отмена')}"
            )

            _render(lines)

            key = getch()
            if key == "UP":
                cursor = (cursor - 1) % len(items)
            elif key == "DOWN":
                cursor = (cursor + 1) % len(items)
            elif key == "ENTER":
                _, target, _ = items[cursor]
                if target and target.is_dir():
                    current = target
                    cursor = 0
            elif key == "SPACE":
                return str(current)
            elif key in ("\x7f", "\x08", "LEFT"):  # Backspace / Left
                if current.parent != current:
                    current = current.parent
                    cursor = 0
            elif key in ("h", "H"):
                show_hidden = not show_hidden
            elif key in ("t", "T"):
                return ""  # signal: switch to text input
            elif key in ("q", "Q", "ESC"):
                return None

    except KeyboardInterrupt:
        return None
    finally:
        show_cursor()
