#!/usr/bin/env python3
"""
Блокирует прямую правку 1С metadata XML и MXL файлов в обход xml-gen CLI.

Режимы:
  1) Claude Code PreToolUse hook — читает JSON со stdin, exit 2 при блокировке
     (stderr попадает в контекст модели как причина блока).
  2) CLI --check <path> [--tool <Name>] — direct invocation. exit 0 = allow,
     exit 2 = block (сообщение в stderr). Подходит для Codex-обёрток и
     ручной проверки.

Логика блокировки:
  - Любой *.mxl — блок (двоичный формат, только xml-gen).
  - Любой *.xml внутри 1С-конфигурации:
      * Configuration.xml в корне
      * **/Ext/{Form,Rights,Template,Help,CommandInterface,Module,...}.xml
      * **/{Catalogs,Documents,*Registers,Reports,DataProcessors,Enums,
        CommonModules,Constants,Roles,Subsystems,ChartsOf*,...}/**.xml
  - Исключения (НЕ блокируется):
      * Сборочные дескрипторы (pom.xml, *.gradle*, settings*.xml в Maven layout)
      * CI/CD конфиги (.github/, .gitlab*, .gitea/)
      * Тестовые fixtures (пути с /test/, /tests/, /fixtures/, /__fixtures__/)
      * Документация и примеры (docs/, examples/) — если только не реальная
        1С-конфигурация внутри
  - Скрипт не блокирует Read/Bash — только запись (Edit/Write/MultiEdit/
    NotebookEdit).
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import PurePosixPath

WRITE_TOOLS = {"Edit", "Write", "MultiEdit", "NotebookEdit"}

ONEC_ROOT_DIRS = (
    "Catalogs",
    "Documents",
    "DocumentJournals",
    "InformationRegisters",
    "AccumulationRegisters",
    "AccountingRegisters",
    "CalculationRegisters",
    "Reports",
    "DataProcessors",
    "Enums",
    "ChartsOfCharacteristicTypes",
    "ChartsOfAccounts",
    "ChartsOfCalculationTypes",
    "CommonModules",
    "CommonForms",
    "CommonTemplates",
    "CommonCommands",
    "CommonAttributes",
    "CommonPictures",
    "Constants",
    "ExchangePlans",
    "Tasks",
    "BusinessProcesses",
    "HTTPServices",
    "WebServices",
    "Subsystems",
    "DefinedTypes",
    "EventSubscriptions",
    "ScheduledJobs",
    "FilterCriteria",
    "FunctionalOptions",
    "FunctionalOptionsParameters",
    "Roles",
    "SessionParameters",
    "SettingsStorages",
    "StyleItems",
    "Styles",
    "Languages",
    "XDTOPackages",
    "WSReferences",
    "Bots",
)

ROOT_FILES = {"Configuration.xml", "ParentConfigurations.bin"}

EXCLUDE_SUBSTRINGS = (
    "/test/",
    "/tests/",
    "/__tests__/",
    "/fixtures/",
    "/__fixtures__/",
    "/testdata/",
    "/test-resources/",
    "/.github/",
    "/.gitlab/",
    "/.gitea/",
    "/.idea/",
    "/.vscode/",
    "/node_modules/",
    "/.git/",
    "/build/",
    "/target/",
    "/out/",
)

EXCLUDE_BASENAMES = {
    "pom.xml",
    "build.xml",
    "ivy.xml",
    "nuget.config",
    "packages.config",
    "web.config",
    "app.config",
}

EXCLUDE_BASENAME_PATTERNS = (
    re.compile(r"^.*\.gradle$"),
    re.compile(r"^.*\.gradle\.kts$"),
    re.compile(r"^settings\.gradle.*$"),
    re.compile(r"^build\.gradle.*$"),
)

HINT_MESSAGE = (
    "BLOCKED: прямая правка 1С metadata XML/MXL запрещена.\n"
    "Используй xml-gen CLI (skill: xml-generation) — он гарантирует canonical schema,\n"
    "сохраняет BOM/CRLF/LF, валидирует и откатывает изменения при ошибке.\n"
    "\n"
    "Подбери команду по типу файла:\n"
    "  Form.xml          → xml-gen form add-element|add-attribute|edit|info|validate\n"
    "  Rights.xml        → xml-gen role add-object|add-right|info|validate\n"
    "  Schema.xml (СКД)  → xml-gen skd add-parameter|add-field|patch-query|info|validate\n"
    "  Template.xml MXL  → xml-gen mxl compile|decompile|info|validate\n"
    "  *.mxl             → xml-gen mxl compile|decompile (двоичный формат, никогда не Edit)\n"
    "  Configuration.xml → xml-gen config init|edit|info|validate\n"
    "  CommandInterface  → xml-gen interface edit|validate\n"
    "  Subsystem         → xml-gen subsystem compile|edit|info|validate\n"
    "  EPF/ERF root      → xml-gen epf init|add-form|add-template|bsp-init|bsp-add-command\n"
    "  Meta (Catalog,    → xml-gen meta compile|edit|info|validate|remove\n"
    "    Document, ...)\n"
    "  Extension (CFE)   → xml-gen extension init|borrow|diff|patch-method|validate\n"
    "\n"
    "Универсально:\n"
    "  - Точечная замена текста с сохранением line endings: xml-gen edit replace-text\n"
    "  - Регистрация формы/макета/справки:                   xml-gen form|template|help add\n"
    "  - Структурная + семантическая валидация:              xml-gen validate --type <kind>\n"
    "\n"
    "Skill: framework/skills/tool-usage/platform-data/xml-generation/SKILL.md\n"
    "Правило: framework/rules/no-manual-xml-edit.md\n"
    "\n"
    "Если xml-gen реально не поддерживает нужную операцию — следуй процедуре\n"
    "«ДОПУСТИМО (исключение)» из no-manual-xml-edit.md: залогируй MANUAL_XML_EDIT\n"
    "в orchestrator-context.md и заведи задачу на расширение xml-gen.\n"
)


def _normalize(path: str) -> str:
    if not path:
        return ""
    # Превращаем backslash в forward slash для единообразия проверок
    p = path.replace("\\", "/")
    return p


def is_onec_metadata_path(path: str) -> tuple[bool, str]:
    """Возвращает (нужно_блокировать, причина_или_тип)."""
    if not path:
        return False, ""

    p = _normalize(path)
    pp = PurePosixPath(p)
    name = pp.name
    lower = p.lower()

    # 0. Исключения по подстроке пути
    for sub in EXCLUDE_SUBSTRINGS:
        if sub in lower:
            return False, "excluded-path"

    # 1. Исключения по имени (build descriptors)
    if name in EXCLUDE_BASENAMES:
        return False, "excluded-basename"
    for pat in EXCLUDE_BASENAME_PATTERNS:
        if pat.match(name):
            return False, "excluded-basename-pattern"

    # 2. MXL — всегда блок
    if name.lower().endswith(".mxl"):
        return True, "mxl"

    # 3. Дальше — только .xml
    if not name.lower().endswith(".xml"):
        return False, "not-xml"

    # 4. Configuration.xml / другие root-файлы 1С
    if name in ROOT_FILES:
        return True, "configuration-root"

    parts = pp.parts

    # 5. Любой /Ext/ сегмент — типичные 1С артефакты (Form.xml, Rights.xml,
    #    Template.xml, Help.xml, CommandInterface.xml, Module.bsl и т.п.)
    if "Ext" in parts:
        return True, "ext-dir"

    # 6. Файл лежит внутри одной из 1С root-папок (Catalogs/<Name>/...)
    for d in ONEC_ROOT_DIRS:
        if d in parts:
            return True, f"onec-root:{d}"

    # 7. CommandInterface.xml без Ext/ контекста (на уровне Subsystem)
    if name == "CommandInterface.xml":
        return True, "command-interface"

    return False, "generic-xml"


def check_file(path: str, tool: str | None) -> int:
    """Возвращает exit code (0 = allow, 2 = block)."""
    if tool is not None and tool not in WRITE_TOOLS:
        return 0
    blocked, reason = is_onec_metadata_path(path)
    if not blocked:
        return 0
    sys.stderr.write(
        f"\n[xml-gen guard] tool={tool or '?'} file={path} reason={reason}\n\n"
    )
    sys.stderr.write(HINT_MESSAGE)
    return 2


def run_claude_code() -> int:
    """Читает PreToolUse JSON со stdin и решает allow/block."""
    try:
        raw = sys.stdin.read()
        if not raw.strip():
            return 0
        payload = json.loads(raw)
    except Exception as exc:  # noqa: BLE001
        # Не блокируем при ошибке парсинга — fail-open для надёжности.
        sys.stderr.write(f"[xml-gen guard] hook parse error: {exc}\n")
        return 0

    tool = payload.get("tool_name") or payload.get("tool") or ""
    if tool not in WRITE_TOOLS:
        return 0

    tool_input = payload.get("tool_input") or payload.get("input") or {}

    paths: list[str] = []
    if isinstance(tool_input, dict):
        # Edit / Write: file_path
        fp = tool_input.get("file_path") or tool_input.get("path")
        if isinstance(fp, str):
            paths.append(fp)
        # MultiEdit: file_path (одна цель, много edits)
        # NotebookEdit: notebook_path
        nbp = tool_input.get("notebook_path")
        if isinstance(nbp, str):
            paths.append(nbp)

    for p in paths:
        code = check_file(p, tool)
        if code != 0:
            return code
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        prog="block-direct-xml-edit",
        description="Блокировка прямой правки 1С XML/MXL в обход xml-gen.",
    )
    parser.add_argument(
        "--check",
        metavar="PATH",
        help="Проверить путь напрямую (для Codex/ручных обёрток).",
    )
    parser.add_argument(
        "--tool",
        metavar="NAME",
        default=None,
        help="Имя tool (Edit/Write/MultiEdit/NotebookEdit). Если не задано "
        "— проверяется как write-операция.",
    )
    parser.add_argument(
        "--claude-code",
        action="store_true",
        help="Явный режим Claude Code PreToolUse (читает JSON со stdin). "
        "По умолчанию активируется автоматически если есть stdin.",
    )
    args = parser.parse_args(argv)

    if args.check:
        return check_file(args.check, args.tool or "Edit")

    if args.claude_code or not sys.stdin.isatty():
        return run_claude_code()

    parser.print_help()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
