#!/usr/bin/env python3
"""
1C BSL Agent Framework — CLI для capability registry.

Редактирование framework/capabilities/registry.yaml без внешних зависимостей.

Использование:
    python capability-registry.py list
    python capability-registry.py set navigate_symbol --server new-lsp --tool navigate_symbol
    python capability-registry.py set-server lsp-bridge --new-server new-lsp
"""

import argparse
import re
import sys
from pathlib import Path


def _repo_root() -> Path:
    """Корень репозитория (tools/ -> родитель)."""
    return Path(__file__).resolve().parent.parent


def _registry_path() -> Path:
    return _repo_root() / "framework" / "capabilities" / "registry.yaml"


def _parse_line(line: str) -> tuple[str | None, str | None, str | None]:
    """Парсит строку вида 'key: { server: X, tool: Y }'. Возвращает (key, server, tool) или (None, None, None)."""
    m = re.match(r"^(\w+):\s*\{\s*server:\s*([^,}]+),\s*tool:\s*([^}]+)\s*\}", line.strip())
    if m:
        return m.group(1).strip(), m.group(2).strip(), m.group(3).strip()
    return None, None, None


def _format_entry(capability: str, server: str, tool: str) -> str:
    return f"{capability}: {{ server: {server}, tool: {tool} }}\n"


def list_cmd() -> int:
    """Вывести текущий registry."""
    path = _registry_path()
    if not path.exists():
        print(f"Registry не найден: {path}", file=sys.stderr)
        return 1
    text = path.read_text(encoding="utf-8")
    print(text)
    return 0


def set_cmd(capability: str, server: str, tool: str) -> int:
    """Обновить одну capability."""
    path = _registry_path()
    if not path.exists():
        print(f"Registry не найден: {path}", file=sys.stderr)
        return 1

    lines = path.read_text(encoding="utf-8").splitlines()
    found = False
    new_lines = []

    for line in lines:
        key, _, _ = _parse_line(line)
        if key == capability:
            new_lines.append(_format_entry(capability, server, tool).rstrip())
            found = True
        else:
            new_lines.append(line)

    if not found:
        # Добавить в конец (после последней непустой некомментарной строки)
        new_lines.append(_format_entry(capability, server, tool).rstrip())

    path.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
    print(f"Обновлено: {capability} -> {server}/{tool}")
    return 0


def set_server_cmd(old_server: str, new_server: str, tool_map: dict[str, str] | None) -> int:
    """Обновить все capabilities для сервера."""
    path = _registry_path()
    if not path.exists():
        print(f"Registry не найден: {path}", file=sys.stderr)
        return 1

    lines = path.read_text(encoding="utf-8").splitlines()
    new_lines = []
    count = 0

    for line in lines:
        key, srv, t = _parse_line(line)
        if key and srv == old_server:
            new_tool = (tool_map or {}).get(key, t)
            new_lines.append(_format_entry(key, new_server, new_tool).rstrip())
            count += 1
        else:
            new_lines.append(line)

    path.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
    print(f"Обновлено {count} capabilities: {old_server} -> {new_server}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Capability registry CLI")
    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list", help="Показать registry")

    p_set = sub.add_parser("set", help="Установить capability")
    p_set.add_argument("capability", help="Имя capability")
    p_set.add_argument("--server", required=True, help="MCP server")
    p_set.add_argument("--tool", required=True, help="Tool name")

    p_server = sub.add_parser("set-server", help="Заменить сервер для всех его capabilities")
    p_server.add_argument("old_server", help="Текущий server")
    p_server.add_argument("--new-server", required=True, help="Новый server")
    p_server.add_argument("--tool-map", help="JSON: маппинг capability -> tool (если имена tool меняются)")

    args = parser.parse_args()

    if args.cmd == "list":
        return list_cmd()
    if args.cmd == "set":
        return set_cmd(args.capability, args.server, args.tool)
    if args.cmd == "set-server":
        tool_map = None
        if args.tool_map:
            import json
            tool_map = json.loads(args.tool_map)
        return set_server_cmd(args.old_server, args.new_server, tool_map)
    return 1


if __name__ == "__main__":
    sys.exit(main())
