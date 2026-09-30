#!/usr/bin/env python3
"""
Блокирует прямую правку 1С metadata XML и MXL файлов в обход xml-gen CLI.

Режимы:
  1) Claude Code PreToolUse hook — читает JSON со stdin, exit 2 при блокировке
     (stderr попадает в контекст модели как причина блока).
  2) CLI --check <path> [--tool <Name>] — direct invocation. exit 0 = allow,
     exit 2 = block (сообщение в stderr). Подходит для Codex-обёрток и
     ручной проверки.
  3) CLI --check-bash "<command>" — проверка одной Bash-команды. Используется
     pre-commit guard и ручными обёртками; exit 0 = allow, exit 2 = block.

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
  - Для Edit/Write/MultiEdit/NotebookEdit блокируется запись по file_path /
    notebook_path.
  - Для Bash блокируется команда, которая упоминает путь к 1С metadata XML/MXL
    и содержит маркеры записи (in-place sed/awk/perl, shell redirect >/>>,
    tee, inline Python с open(..., 'w*') / .write / .replace). Команды,
    содержащие "xml-gen" в любой подкоманде, считаются доверенными.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import PurePosixPath

WRITE_TOOLS = {"Edit", "Write", "MultiEdit", "NotebookEdit"}

# Tools, для которых нужно проверять Bash command на инлайн-правку XML/MXL.
BASH_TOOLS = {"Bash"}

# Маркеры записи в Bash. Регистр игнорируется не везде — sed/awk сами
# case-sensitive, поэтому матчим как есть.
_BASH_WRITE_MARKERS = (
    # in-place редакторы
    re.compile(r"(?<![\w-])sed\s+(?:-[^\s]*[iI][^\s]*|--in-place)"),
    re.compile(r"(?<![\w-])(?:g?awk)\s+(?:-[^\s]*i[^\s]*\s+)?(?:'[^']*'|\"[^\"]*\")?\s*-i\s+inplace"),
    re.compile(r"(?<![\w-])perl\s+(?:-[^\s]*i[^\s]*|-i(?:\.[\w-]+)?)"),
    re.compile(r"(?<![\w-])(?:ed|ex|vim)\s+-s\b"),
    # shell-редиректы (любой > или >>, кроме 2>, 2>>, &> — их исключаем
    # вокруг проверки целевого пути ниже)
    re.compile(r"(?<![\w&\d])(?:>>?)\s*['\"]?[^\s'\"|;&]+\.(xml|mxl)\b", re.IGNORECASE),
    re.compile(r"(?<![\w-])tee\s+(?:-[a]\s+)?['\"]?[^\s'\";|&]+\.(xml|mxl)\b", re.IGNORECASE),
    # inline Python write-патерны: open(..., 'w*'/'a*'), f.write, .replace
    # (последнее — типичная байтовая замена), os.replace/shutil
    re.compile(r"open\(\s*[^)]*\.(xml|mxl)[^)]*['\"](w|wb|a|ab|r\+|rb\+)['\"]", re.IGNORECASE),
    re.compile(r"\.write\s*\(\s*[^)]*(xml|mxl)", re.IGNORECASE),
    re.compile(r"\.replace\s*\(.*\.(xml|mxl)", re.IGNORECASE),
    re.compile(r"(?:os\.replace|shutil\.move|shutil\.copy(?:2|file)?)\s*\(\s*[^)]*\.(xml|mxl)", re.IGNORECASE),
    # cp/mv в .xml/.mxl как цель
    re.compile(r"(?<![\w-])(?:cp|mv|install)\s+(?:-[^\s]+\s+)*\S+\s+['\"]?[^\s'\";|&]+\.(xml|mxl)\b", re.IGNORECASE),
)

# Доверенные команды — если встретились в строке (отдельной подкомандой),
# проверка пропускается. xml-gen — единственный канонический путь правки.
_BASH_TRUSTED = re.compile(r"(?<![\w-])(?:xml-gen|xml_gen|xmlgen)\b")

# Регулярка для извлечения путей с расширением .xml/.mxl из bash-команды.
# Берём токены, отделённые пробелом/кавычками/служебными символами.
_BASH_XML_PATH = re.compile(
    r"""(?P<quote>['"])?(?P<path>(?:[^\s'"<>|&;]+)\.(?:xml|mxl))(?(quote)(?P=quote))""",
    re.IGNORECASE,
)

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
    "Правило: framework/rules/no-manual-xml-edit/SKILL.md\n"
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


def _split_bash_subcommands(command: str) -> list[str]:
    """Грубо разбивает bash-строку по ; && || | на подкоманды.

    Внутри одинарных и двойных кавычек разделители игнорируем — это даёт
    нормальную точность для типичных однострочников и heredoc-баз.
    """
    parts: list[str] = []
    buf: list[str] = []
    i = 0
    n = len(command)
    in_single = False
    in_double = False
    while i < n:
        c = command[i]
        if c == "\\" and i + 1 < n:
            buf.append(c)
            buf.append(command[i + 1])
            i += 2
            continue
        if c == "'" and not in_double:
            in_single = not in_single
            buf.append(c)
            i += 1
            continue
        if c == '"' and not in_single:
            in_double = not in_double
            buf.append(c)
            i += 1
            continue
        if not in_single and not in_double:
            # ; — разделитель; && — пара; || — пара; | (одиночный) — pipe.
            if c == ";":
                parts.append("".join(buf))
                buf = []
                i += 1
                continue
            if c in ("&", "|") and i + 1 < n and command[i + 1] == c:
                parts.append("".join(buf))
                buf = []
                i += 2
                continue
            if c == "|":
                parts.append("".join(buf))
                buf = []
                i += 1
                continue
        buf.append(c)
        i += 1
    tail = "".join(buf).strip()
    if tail:
        parts.append(tail)
    return [p.strip() for p in parts if p.strip()]


def _extract_metadata_paths(command: str) -> list[tuple[str, str]]:
    """Возвращает список (path, reason) для всех .xml/.mxl токенов, попадающих
    под is_onec_metadata_path. Read-only пути отфильтрованы."""
    found: list[tuple[str, str]] = []
    for m in _BASH_XML_PATH.finditer(command):
        path = m.group("path")
        blocked, reason = is_onec_metadata_path(path)
        if blocked:
            found.append((path, reason))
    return found


def check_bash_command(command: str) -> int:
    """Анализирует Bash-команду на признаки прямой правки 1С XML/MXL.

    Возвращает 0 если ничего подозрительного, 2 если найден риск.
    Heuristic-based — false positives возможны (тогда переписать через
    xml-gen или вынести команду из единой строки).
    """
    if not command or not command.strip():
        return 0

    # Если в команде вообще нет токенов вида *.xml / *.mxl среди metadata —
    # выходим сразу, чтобы не блокировать обычные pyhton/sed/awk операции.
    metadata_paths = _extract_metadata_paths(command)
    if not metadata_paths:
        return 0

    # Доверяем xml-gen.
    if _BASH_TRUSTED.search(command):
        return 0

    # Проверяем по подкомандам — write-маркер должен совпасть с подкомандой,
    # содержащей metadata путь. Это снижает шум: например, `cat foo.xml |
    # python3 -c "..."` (read+pipe) не должно блокироваться, если python3
    # ничего не пишет в xml.
    subcmds = _split_bash_subcommands(command)
    if not subcmds:
        subcmds = [command]

    blocked_paths: list[tuple[str, str, str]] = []  # (subcmd, path, marker)
    for sub in subcmds:
        paths_here = _extract_metadata_paths(sub)
        if not paths_here:
            continue
        for marker in _BASH_WRITE_MARKERS:
            mm = marker.search(sub)
            if mm:
                for path, _reason in paths_here:
                    blocked_paths.append((sub, path, mm.group(0)[:80]))
                break

    if not blocked_paths:
        return 0

    sys.stderr.write("\n[xml-gen guard] tool=Bash — подозрение на прямую правку 1С XML/MXL.\n")
    for sub, path, marker in blocked_paths:
        sys.stderr.write(
            f"  path={path}\n"
            f"  marker={marker!r}\n"
            f"  subcommand={sub[:200]}{'...' if len(sub) > 200 else ''}\n\n"
        )
    sys.stderr.write(HINT_MESSAGE)
    sys.stderr.write(
        "\nЕсли это ложное срабатывание (например, проверочный скрипт, который\n"
        "ничего не пишет в файл) — измени команду так, чтобы целевой .xml/.mxl\n"
        "не совпадал с маркером записи в той же подкоманде, или используй\n"
        "промежуточный файл вне 1С metadata путей.\n"
    )
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
    if tool not in WRITE_TOOLS and tool not in BASH_TOOLS:
        return 0

    tool_input = payload.get("tool_input") or payload.get("input") or {}

    if tool in BASH_TOOLS:
        if isinstance(tool_input, dict):
            command = tool_input.get("command") or ""
            if isinstance(command, str):
                return check_bash_command(command)
        return 0

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
        "--check-bash",
        metavar="COMMAND",
        help="Проверить Bash-команду на признаки прямой правки 1С XML/MXL.",
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

    if args.check_bash is not None:
        return check_bash_command(args.check_bash)

    if args.claude_code or not sys.stdin.isatty():
        return run_claude_code()

    parser.print_help()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
