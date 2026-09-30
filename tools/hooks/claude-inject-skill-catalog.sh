#!/usr/bin/env bash
# SessionStart hook (source=compact|startup|resume).
#
# ЗАЧЕМ: нативный skill_listing — одноразовое вложение и после компакта в контекст
# НЕ возвращается (sentSkillNames не сбрасывается). Каталог навыков (frontmatter-
# уровень) теряется на каждом компакте. Этот хук пересобирает реестр
# name+description+путь из всех SKILL.md и впрыскивает его как
# hookSpecificOutput.additionalContext → attachment hook_additional_context
# в пост-компакт сборку (compact.ts buildPostCompactMessages: ...result.hookResults).
# Самовосстанавливается: срабатывает после КАЖДОГО компакта (полный+частичный,
# авто+ручной) и на старте/resume сессии.
#
# Это указатель, НЕ тела навыков: каталог дёшев (~3k ток на 70+ навыков),
# 25k-бюджет invoked_skills здесь ни при чём (он про ТЕЛА вызванных навыков).
#
# Вывод ДОЛЖЕН быть JSON: для SessionStart plain-stdout в additionalContext
# не маппится — берётся только hookSpecificOutput.additionalContext (hooks.ts:626).

set -euo pipefail

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-/workspaces/work/repos/1C Projects/GBIG PAM}"
SKILLS_DIR="${PROJECT_DIR}/.claude/skills"

# Только для НЕ-сабагентных сессий. Канонический признак (utils/hooks.ts:316
# createBaseHookInput): поле agent_id во входе хука присутствует у сабагента и
# отсутствует у главного потока ("Hooks use agent_id presence to distinguish
# subagent calls from main-thread calls"; совпадает с isMainThread = !agentId).
# Hook-JSON читаем из stdin ДО python-heredoc (он займёт stdin под программу).
HOOK_INPUT="$(cat 2>/dev/null || true)"
AGENT_ID="$(printf '%s' "$HOOK_INPUT" | jq -r '.agent_id // empty' 2>/dev/null || true)"
# agent_id задан -> это сабагент -> каталог не подкидываем.
[ -n "$AGENT_ID" ] && exit 0

# Каталога нет — тихо выходим без вывода (хук не обязан ничего печатать).
[ -d "$SKILLS_DIR" ] || exit 0

python3 - "$SKILLS_DIR" <<'PY'
import json, os, sys, glob

skills_dir = sys.argv[1]

# -L-обход симлинков: glob открывает целевой файл по симлинку, прямые шаблоны ловят
# и плоские (.claude/skills/<n>/SKILL.md), и вложенные (.../xml-generation/<n>/SKILL.md).
paths = set()
for pat in ("*/SKILL.md", "*/*/SKILL.md"):
    paths.update(glob.glob(os.path.join(skills_dir, pat)))

def parse_frontmatter(text):
    """Минимальный парс верхнего ---...--- блока: name/description/capabilities.
    Без зависимостей (PyYAML может не быть). Значения в кавычках разворачиваются."""
    if not text.startswith("---"):
        return {}
    end = text.find("\n---", 3)
    if end == -1:
        return {}
    block = text[3:end]
    fields, key, buf = {}, None, []
    for line in block.splitlines():
        if line[:1] not in (" ", "\t") and ":" in line:
            if key:
                fields[key] = "\n".join(buf).strip()
            key, _, rest = line.partition(":")
            key = key.strip()
            buf = [rest.strip()]
        elif key:
            buf.append(line.strip())
    if key:
        fields[key] = "\n".join(buf).strip()
    for k, v in list(fields.items()):
        if len(v) >= 2 and v[0] in "\"'" and v[-1] == v[0]:
            fields[k] = v[1:-1]
    return fields

def short(desc, limit=220):
    desc = " ".join(desc.split())
    # первое предложение, если короткое; иначе жёсткое усечение
    for sep in (". ", "; "):
        i = desc.find(sep)
        if 0 < i <= limit:
            return desc[: i + 1]
    return desc if len(desc) <= limit else desc[: limit - 1].rstrip() + "…"

by_name = {}
for p in paths:
    try:
        with open(p, encoding="utf-8") as f:
            fm = parse_frontmatter(f.read())
    except Exception:
        continue
    name = fm.get("name") or os.path.basename(os.path.dirname(p))
    # путь от корня проекта (cwd модели): .claude/skills/.../SKILL.md
    project_root = os.path.dirname(os.path.dirname(skills_dir))
    rel = os.path.relpath(p, project_root)
    desc = short(fm.get("description", ""))
    # dedup по name; при коллизии берём более короткий путь (плоский симлинк предпочтительнее)
    prev = by_name.get(name)
    if prev is None or len(rel) < len(prev["path"]):
        by_name[name] = {"name": name, "desc": desc, "path": rel}

rows = sorted(by_name.values(), key=lambda r: r["name"])

header = (
    "## Каталог навыков проекта (восстановлен после компакта)\n"
    "Это УКАЗАТЕЛЬ frontmatter-уровня: тела навыков НЕ загружены в контекст. "
    "Когда задача попадает в область навыка — вызови его через инструмент Skill "
    "(а если навык не user-invocable — прочитай его SKILL.md по указанному пути) "
    "по мере необходимости. НЕ выполняй повторно навыки, уже применённые в этой сессии. "
    "Не пытайся удержать все тела сразу — подгружай точечно.\n"
)
lines = [header, f"Навыков: {len(rows)}\n"]
for r in rows:
    lines.append(f"- **{r['name']}** — {r['desc']} [{r['path']}]")
context = "\n".join(lines)

print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "SessionStart",
        "additionalContext": context,
    }
}, ensure_ascii=False))
PY
