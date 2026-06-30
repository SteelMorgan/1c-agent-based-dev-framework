#!/usr/bin/env python3
"""
sync-skill.py — синхронизатор RU→EN для framework/ → framework_eng/

Использование:
  python3 tools/sync-skill.py [файл1 файл2 ...]   # синхронизировать конкретные файлы
  python3 tools/sync-skill.py --check              # показать статусы без перевода
  python3 tools/sync-skill.py --all                # синхронизировать все pending/dirty файлы
  python3 tools/sync-skill.py --init-all           # первичная синхронизация всего framework/
  python3 tools/sync-skill.py --sync-structure     # синхронизировать структуру каталогов
  python3 tools/sync-skill.py --sync-structure --clean  # + удалить осиротевшие каталоги в framework_eng/

Вызывается из pre-commit хука автоматически.
"""

import argparse
import difflib
import hashlib
import json
import os
import re
import signal
import subprocess
import sys
import tempfile
import textwrap
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

# ─── Пути ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
STATE_FILE = REPO_ROOT / ".skills-sync-state.json"
FRAMEWORK_DIR = REPO_ROOT / "framework"
MIRROR_DIR = REPO_ROOT / "framework_eng"
EXCLUDE_NAMES = {"README.md"}

# Расширения файлов, которые переводятся через Codex
TRANSLATABLE_EXTS = {".md", ".mdc"}
# Всё остальное копируется as-is (бинарники, скрипты, JSON, YAML и т.д.)

CODEX_PROFILE = "cx_gpt-5-codex-mini"
CODEX_DONE_MARKER = "OK"
CODEX_TIMEOUT = int(os.environ.get("SYNC_SKILL_CODEX_TIMEOUT", "420"))
DEFAULT_WORKERS = 8       # параллельных переводов по умолчанию
DIFF_CONTEXT_LINES = 3
DIFF_MAX_CHANGED_LINES = 240
DIFF_MAX_CHUNKS = 12
CHUNK_TRANSLATE_MIN_FILE_CHARS = 12000
CHUNK_TRANSLATE_MAX_CHARS = 3500


def is_codex_custom_mode() -> bool:
    """
    Возвращает True если включён кастомный режим Codex с профилями.
    Проверяет переменную окружения CUSTOM_CODEX_ENABLED=1.
    """
    return os.environ.get("CUSTOM_CODEX_ENABLED", "0") == "1"


def make_translate_prompt(ru_rel: str, en_rel: str) -> str:
    """Промпт для Codex — переводит RU-файл → EN-файл."""
    return textwrap.dedent(f"""\
        Translate the file `{ru_rel}` from Russian to English.
        Write the translated result to `{en_rel}`.

        Translation rules:
        1. Translate ALL Russian text to English. Do not leave any Russian words.
        2. Do NOT translate 1C-specific terms — keep them exactly as-is:
           - Platform and product names: 1С, 1С:Предприятие, 1C:Enterprise, 1C
           - Configuration abbreviations: БСП, УТ, УПП, ERP, БУХ, ЗУП, КА, УНФ, РЗ etc.
           - Built-in language identifiers (PascalCase/camelCase): НайтиПоРеквизиту,
             ВыполнитьЗапрос, РегистрыСведений, Справочники, Документы, etc.
           - Metadata type names used as technical references: Справочник, Документ,
             РегистрНакопления, РегистрСведений, ПланВидовХарактеристик, etc.
             (translate only when used as common nouns in explanation, not as type names)
           - IDE and tool names: EDT, YaxUnit, BSL, MDClasses, OneScript, vanessa-automation
           - Any PascalCase/camelCase identifier that looks like code — leave as-is
           - Code inside code blocks — never translate, leave completely unchanged
        3. Keep ALL markdown formatting exactly identical (headers, tables, lists, bold, etc.)
        4. Keep ALL YAML frontmatter keys unchanged — only translate VALUES if they are Russian.
           Exception: keep 'name:' value unchanged always.
        5. Keep ALL code blocks (``` ... ```) completely unchanged.
        6. Keep relative file paths and URLs unchanged.
        7. Write ONLY the translated document to `{en_rel}`. No explanations, no extra output.

        When the file has been written successfully, write the single word "OK" (nothing else)
        as your final response — this signals that the task is complete.
    """)


def make_translate_diff_prompt(ru_rel: str, chunks: list[dict]) -> str:
    """Промпт для Codex — переводит только изменённые RU-блоки."""
    payload = json.dumps(chunks, ensure_ascii=False, indent=2)
    return textwrap.dedent(f"""\
        Translate only the changed Russian markdown blocks from `{ru_rel}` into English.

        Input is a JSON array. For each item:
        - `old_ru` is the previous Russian block.
        - `new_ru` is the updated Russian block.
        - `old_en` is the current English translation of `old_ru` from the mirror file.

        Return ONLY a JSON array with objects:
        [
          {{"id": <same id>, "new_en": "<English translation of new_ru>"}}
        ]

        Translation rules:
        1. Translate Russian prose to English.
        2. Do NOT translate 1C-specific terms and identifiers: БСП, 1С, 1С:Предприятие,
           EDT, YaxUnit, BSL, MDClasses, OneScript, module names, metadata names,
           PascalCase/camelCase identifiers, file paths, URLs.
        3. Keep code blocks and inline code identifiers unchanged.
        4. Preserve markdown structure, table pipes, list markers, heading levels,
           blank-line intent, and frontmatter/backmatter keys.
        5. `new_en` must contain only the replacement block text, not explanations.

        JSON input:
        {payload}
    """)


def make_translate_chunk_prompt(ru_rel: str, chunk_no: int, total: int, chunk: str) -> str:
    """Промпт для Codex — переводит один markdown-фрагмент."""
    payload = json.dumps({
        "id": chunk_no,
        "total": total,
        "markdown": chunk,
    }, ensure_ascii=False, indent=2)
    return textwrap.dedent(f"""\
        Translate this Russian markdown fragment from `{ru_rel}` to English.
        Fragment {chunk_no} of {total}.

        Return ONLY JSON:
        {{"id": {chunk_no}, "markdown": "<translated markdown fragment>"}}

        Translation rules:
        1. Translate Russian prose to English.
        2. Do NOT translate 1C-specific terms and identifiers: БСП, 1С, 1С:Предприятие,
           EDT, YaxUnit, BSL, MDClasses, OneScript, module names, metadata names,
           PascalCase/camelCase identifiers, file paths, URLs.
        3. Keep code blocks completely unchanged.
        4. Preserve markdown structure exactly: table pipes, list markers, heading
           levels, blank lines, and frontmatter/backmatter keys.
        5. Do not summarize, omit, merge, or reorder lines. Translate the fragment
           at the same level of detail.

        JSON input:
        {payload}
    """)


# ─── Утилиты ──────────────────────────────────────────────────────────────────

def sha256_file(path: Path) -> str:
    content = path.read_bytes()
    return "sha256:" + hashlib.sha256(content).hexdigest()


def load_state() -> dict:
    if STATE_FILE.exists():
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    return {"version": 1, "rules": {}, "files": {}}


def save_state(state: dict) -> None:
    STATE_FILE.write_text(
        json.dumps(state, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8"
    )


def ru_to_en_path(ru_path: Path) -> Path:
    """framework/skills/foo/SKILL.md → framework_eng/skills/foo/SKILL.md"""
    rel = ru_path.relative_to(REPO_ROOT / "framework")
    return REPO_ROOT / "framework_eng" / rel


def relative(path: Path) -> str:
    return str(path.relative_to(REPO_ROOT))


def is_excluded(path: Path) -> bool:
    return path.name in EXCLUDE_NAMES


def is_translatable(path: Path) -> bool:
    """True если файл нужно переводить; False — копировать as-is."""
    return path.suffix.lower() in TRANSLATABLE_EXTS


def copy_file(ru_path: Path, en_path: Path) -> bool:
    """Копирует файл as-is (для бинарников, скриптов, JSON, YAML и т.д.)."""
    import shutil
    en_path.parent.mkdir(parents=True, exist_ok=True)
    try:
        shutil.copy2(ru_path, en_path)
        print(f"  → Copied {relative(ru_path)} ... OK")
        return True
    except OSError as e:
        print(f"  → Copied {relative(ru_path)} ... ERROR")
        print(f"    ✗ {e}")
        return False


def _git_head_text(path: Path) -> str | None:
    """Вернуть содержимое файла из HEAD или None, если Git/baseline недоступны."""
    try:
        rel = relative(path)
    except ValueError:
        return None

    cmd = [
        "git",
        f"--git-dir={REPO_ROOT / '.git'}",
        f"--work-tree={REPO_ROOT}",
        "show",
        f"HEAD:{rel}",
    ]
    try:
        proc = subprocess.run(
            cmd,
            cwd=str(REPO_ROOT),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except OSError:
        return None

    if proc.returncode != 0:
        return None
    return proc.stdout


def _decode_json_response(text: str):
    """Достать JSON из ответа модели, включая случай ```json ... ```."""
    stripped = text.strip()
    if stripped.startswith("```"):
        stripped = re.sub(r"^```(?:json)?\s*", "", stripped)
        stripped = re.sub(r"\s*```$", "", stripped)
    return json.loads(stripped)


def _build_diff_chunks(old_ru: str, new_ru: str, old_en: str) -> list[dict] | None:
    """Построить line-based chunks для частичного перевода.

    Предположение diff-режима: EN-зеркало сохраняет построчную структуру markdown.
    Если это не так или изменение слишком крупное, возвращаем None и делаем полный
    перевод.
    """
    old_ru_lines = old_ru.splitlines(keepends=True)
    new_ru_lines = new_ru.splitlines(keepends=True)
    old_en_lines = old_en.splitlines(keepends=True)

    if len(old_en_lines) != len(old_ru_lines):
        return None

    matcher = difflib.SequenceMatcher(a=old_ru_lines, b=new_ru_lines, autojunk=False)
    groups = matcher.get_grouped_opcodes(DIFF_CONTEXT_LINES)
    chunks = []
    changed_lines = 0

    for group in groups:
        non_equal = [op for op in group if op[0] != "equal"]
        if not non_equal:
            continue

        old_start = min(i1 for _tag, i1, _i2, _j1, _j2 in non_equal)
        old_end = max(i2 for _tag, _i1, i2, _j1, _j2 in non_equal)
        new_start = min(j1 for _tag, _i1, _i2, j1, _j2 in non_equal)
        new_end = max(j2 for _tag, _i1, _i2, _j1, j2 in non_equal)
        changed_lines += max(old_end - old_start, new_end - new_start)

        chunks.append({
            "id": len(chunks) + 1,
            "old_start": old_start,
            "old_end": old_end,
            "new_start": new_start,
            "new_end": new_end,
            "old_ru": "".join(old_ru_lines[old_start:old_end]),
            "new_ru": "".join(new_ru_lines[new_start:new_end]),
            "old_en": "".join(old_en_lines[old_start:old_end]),
        })

    if not chunks:
        return []
    if len(chunks) > DIFF_MAX_CHUNKS or changed_lines > DIFF_MAX_CHANGED_LINES:
        return None
    return chunks


def _apply_translated_chunks(old_en: str, chunks: list[dict], translations: list[dict]) -> str:
    by_id = {item["id"]: item["new_en"] for item in translations}
    en_lines = old_en.splitlines(keepends=True)

    for chunk in sorted(chunks, key=lambda item: item["old_start"], reverse=True):
        if chunk["id"] not in by_id:
            raise ValueError(f"missing translated chunk id={chunk['id']}")
        replacement = by_id[chunk["id"]]
        replacement_lines = replacement.splitlines(keepends=True)
        if replacement and not replacement.endswith(("\n", "\r")):
            replacement_lines[-1] += "\n"
        en_lines[chunk["old_start"]:chunk["old_end"]] = replacement_lines

    return "".join(en_lines)


def _split_markdown_for_translation(text: str, max_chars: int = CHUNK_TRANSLATE_MAX_CHARS) -> list[str]:
    """Разбить markdown на переводимые фрагменты, не разрывая fenced code blocks."""
    lines = text.splitlines(keepends=True)
    chunks: list[str] = []
    current: list[str] = []
    current_len = 0
    in_fence = False

    def flush() -> None:
        nonlocal current, current_len
        if current:
            chunks.append("".join(current))
            current = []
            current_len = 0

    for line in lines:
        stripped = line.lstrip()
        starts_heading = stripped.startswith("#") and not in_fence
        starts_fence = stripped.startswith("```") or stripped.startswith("~~~")

        if starts_heading and current_len >= max_chars // 2:
            flush()
        elif not in_fence and current_len >= max_chars and not line.strip():
            current.append(line)
            flush()
            continue
        elif not in_fence and current_len + len(line) > max_chars and current:
            flush()

        current.append(line)
        current_len += len(line)

        if starts_fence:
            in_fence = not in_fence

    flush()
    return chunks


def _strip_wrapping_fence(text: str) -> str:
    stripped = text.strip()
    if stripped.startswith("```"):
        stripped = re.sub(r"^```(?:markdown|md)?\s*", "", stripped)
        stripped = re.sub(r"\s*```$", "", stripped)
        return stripped + ("\n" if text.endswith("\n") else "")
    return text


def _verify_chunk_translation(source: str, translated: str) -> tuple[bool, str]:
    source_headings = _count_headings(source)
    translated_headings = _count_headings(translated)
    if source_headings != translated_headings:
        return False, f"heading count differs (RU={source_headings}, EN={translated_headings})"
    if len(source.strip()) > 1000 and len(translated.strip()) < len(source.strip()) * MIN_EN_RU_LEN_RATIO:
        return False, "translated chunk is suspiciously shorter than source"
    return True, ""


# ─── Перевод через Codex CLI ──────────────────────────────────────────────────

BACKMATTER_SPLIT_RE = re.compile(r"\n---\s*\n((?:depends_on|requires|metadata|category|version)\s*:.*)\n---\s*$", re.DOTALL)


def _extract_backmatter(text: str) -> tuple[str, str]:
    """Отделяет backmatter (---\\ndepends_on:...\\n---) от текста.

    Возвращает (text_without_backmatter, backmatter_with_delimiters).
    Если backmatter не найден, возвращает (text, "").
    """
    m = BACKMATTER_SPLIT_RE.search(text)
    if not m:
        return text, ""
    body = text[:m.start()]
    backmatter = "\n---\n" + m.group(1) + "\n---\n"
    return body, backmatter


FRONTMATTER_RE = re.compile(r"^---\s*\n.*?\n---\s*\n", re.DOTALL)
HEADING_RE = re.compile(r"(?m)^#{1,6}\s")
# EN-тело короче этой доли RU-тела → подозрение на усечённый перевод
MIN_EN_RU_LEN_RATIO = 0.5


def _strip_frontmatter(text: str) -> str:
    """Убирает ведущий YAML-frontmatter (--- ... ---), оставляя тело."""
    return FRONTMATTER_RE.sub("", text, count=1)


def _count_headings(text: str) -> int:
    return len(HEADING_RE.findall(text))


def _verify_translation(
    ru_path: Path,
    en_path: Path,
    old_en_text: "str | None",
    *,
    allow_unchanged: bool = False,
) -> "tuple[bool, str]":
    """Проверяет, что перевод реально выполнен, а не отрапортован вхолостую.

    Codex может вернуть маркер DONE, фактически не перезаписав EN-файл
    (наблюдалось на параллельных прогонах: статус ставился `synced`, а контент
    оставался старым). Маркера + непустого файла недостаточно — сверяем содержимое.
    """
    ru_text = ru_path.read_text(encoding="utf-8")
    en_text = en_path.read_text(encoding="utf-8")

    # 1. No-op: EN байт-в-байт совпал с тем, что было до перевода.
    if old_en_text is not None and en_text == old_en_text and not allow_unchanged:
        return False, "EN-файл не изменился после перевода (Codex вернул DONE, перевод не выполнен)"

    ru_body, _ = _extract_backmatter(ru_text)
    en_body, _ = _extract_backmatter(en_text)

    # 2. Структурная чётность: перевод обязан сохранять разметку (число заголовков).
    ru_h = _count_headings(_strip_frontmatter(ru_body))
    en_h = _count_headings(_strip_frontmatter(en_body))
    if ru_h != en_h:
        return False, f"число заголовков расходится (RU={ru_h}, EN={en_h}) — перевод неполный"

    # 3. EN не должен быть аномально короче RU (грубое усечение).
    ru_len = len(ru_body.strip())
    en_len = len(en_body.strip())
    if ru_len > 200 and en_len < ru_len * MIN_EN_RU_LEN_RATIO:
        return False, f"EN заметно короче RU (EN={en_len}, RU={ru_len} симв.) — вероятно усечён"

    return True, ""


def run_codex_prompt(prompt: str) -> str:
    """Безопасно запускает Codex CLI и возвращает финальный текст из `-o`.

    На таймауте убивает всю process group, чтобы в контейнере не оставались
    дочерние процессы `codex`.
    """
    prompt_file = tempfile.NamedTemporaryFile(
        mode="w",
        suffix=".txt",
        prefix="codex-prompt-",
        delete=False,
        encoding="utf-8",
    )
    result_file = tempfile.NamedTemporaryFile(
        suffix=".txt",
        prefix="codex-result-",
        delete=False,
    )
    result_file.close()

    try:
        prompt_file.write(prompt)
        prompt_file.close()

        if is_codex_custom_mode():
            cmd = (
                f'codex exec -p {CODEX_PROFILE}'
                f' --dangerously-bypass-approvals-and-sandbox'
                f' --ephemeral'
                f' -o "{result_file.name}"'
                f' - < "{prompt_file.name}"'
            )
        else:
            cmd = (
                'codex exec -m gpt-5.4-mini'
                ' --dangerously-bypass-approvals-and-sandbox'
                ' --ephemeral'
                f' -o "{result_file.name}"'
                f' - < "{prompt_file.name}"'
            )

        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            cwd=str(REPO_ROOT),
            text=False,
            start_new_session=True,
            shell=True,
        )

        try:
            stdout, stderr = proc.communicate(timeout=CODEX_TIMEOUT)
        except subprocess.TimeoutExpired:
            try:
                os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
            except (ProcessLookupError, OSError):
                proc.kill()
            proc.wait()
            raise RuntimeError(f"Codex timed out after {CODEX_TIMEOUT}s")

        if proc.returncode != 0:
            err = stderr.decode("utf-8", errors="replace").strip()
            out = stdout.decode("utf-8", errors="replace").strip()
            details = (err or out or f"codex exited with code {proc.returncode}")[:500]
            raise RuntimeError(f"Codex failed rc={proc.returncode}: {details}")

        result_path = Path(result_file.name)
        if not result_path.exists() or result_path.stat().st_size == 0:
            raise RuntimeError("Codex produced empty output")

        return result_path.read_text(encoding="utf-8", errors="replace").strip()
    finally:
        Path(prompt_file.name).unlink(missing_ok=True)
        Path(result_file.name).unlink(missing_ok=True)


def translate_file_by_diff(ru_path: Path, en_path: Path, old_en_text: str) -> bool:
    """Перевести только изменённые RU-блоки и вставить их в EN-файл.

    Возвращает False, если diff-режим неприменим или не прошёл проверку. В этом
    случае вызывающий код должен выполнить полный перевод.
    """
    ru_rel = relative(ru_path)
    old_ru_text = _git_head_text(ru_path)
    if old_ru_text is None:
        return False

    new_ru_text = ru_path.read_text(encoding="utf-8")
    chunks = _build_diff_chunks(old_ru_text, new_ru_text, old_en_text)
    if chunks is None:
        print(" diff-skip(large/unaligned); fallback full ...", end="", flush=True)
        return False
    if not chunks:
        print(" diff-noop OK")
        return True

    prompt = make_translate_diff_prompt(ru_rel, [
        {
            "id": chunk["id"],
            "old_ru": chunk["old_ru"],
            "new_ru": chunk["new_ru"],
            "old_en": chunk["old_en"],
        }
        for chunk in chunks
    ])

    try:
        result = run_codex_prompt(prompt)
        translations = _decode_json_response(result)
        if not isinstance(translations, list):
            raise ValueError("JSON response is not an array")
        new_en_text = _apply_translated_chunks(old_en_text, chunks, translations)
    except (json.JSONDecodeError, KeyError, TypeError, ValueError, RuntimeError, OSError) as e:
        print(f" diff-fail({e}); fallback full ...", end="", flush=True)
        return False

    en_path.write_text(new_en_text, encoding="utf-8")
    verified, reason = _verify_translation(ru_path, en_path, old_en_text)
    if not verified:
        print(f" diff-verify-fail({reason}); fallback full ...", end="", flush=True)
        en_path.write_text(old_en_text, encoding="utf-8")
        return False

    print(f" diff OK ({len(chunks)} chunk(s))")
    return True


def translate_file_by_chunks(ru_path: Path, en_path: Path) -> bool:
    """Первичный перевод большого markdown-файла небольшими фрагментами."""
    ru_rel = relative(ru_path)
    ru_text = ru_path.read_text(encoding="utf-8")
    chunks = _split_markdown_for_translation(ru_text)
    if len(chunks) <= 1 and len(ru_text) < CHUNK_TRANSLATE_MIN_FILE_CHARS:
        return False

    translated_chunks: list[str] = []
    total = len(chunks)
    print(f" chunked({total})", end="", flush=True)

    for idx, chunk in enumerate(chunks, start=1):
        prompt = make_translate_chunk_prompt(ru_rel, idx, total, chunk)
        try:
            payload = _decode_json_response(run_codex_prompt(prompt))
            if payload.get("id") != idx:
                raise ValueError(f"unexpected chunk id: {payload.get('id')!r}")
            translated = payload["markdown"]
            if not isinstance(translated, str):
                raise ValueError("translated markdown is not a string")
            translated = _strip_wrapping_fence(translated)
            ok, reason = _verify_chunk_translation(chunk, translated)
            if not ok:
                raise ValueError(reason)
        except (json.JSONDecodeError, KeyError, TypeError, ValueError, RuntimeError, OSError) as e:
            print(f" chunk-{idx}-fail({e})", end="", flush=True)
            return False
        translated_chunks.append(translated)
        print(f" {idx}/{total}", end="", flush=True)

    en_path.write_text("".join(translated_chunks), encoding="utf-8")
    verified, reason = _verify_translation(ru_path, en_path, None, allow_unchanged=True)
    if not verified:
        print(f" chunk-verify-fail({reason})", end="", flush=True)
        return False

    print(" OK")
    return True


def translate_file(ru_path: Path, en_path: Path) -> bool:
    """
    Переводит ru_path → en_path через Codex CLI.

    Стратегия:
    1. Модель переводит RU → EN как раньше (проверенная схема)
    2. Python пост-обработкой гарантирует backmatter из RU-файла
    3. Если модель потеряла pre_backmatter — Python инжектит его
    """
    ru_rel = relative(ru_path)
    en_rel = relative(en_path)
    prompt = make_translate_prompt(ru_rel, en_rel)

    # Запоминаем backmatter из RU для пост-обработки
    ru_text = ru_path.read_text(encoding="utf-8")
    ru_body, ru_backmatter = _extract_backmatter(ru_text)

    # Содержимое EN до перевода — для проверки, что перевод реально выполнен
    old_en_text = en_path.read_text(encoding="utf-8") if en_path.exists() else None

    en_path.parent.mkdir(parents=True, exist_ok=True)

    print(f"  → Translating {ru_rel} ...", end="", flush=True)

    if old_en_text is not None and _git_head_text(ru_path) is None:
        verified, reason = _verify_translation(
            ru_path,
            en_path,
            old_en_text,
            allow_unchanged=True,
        )
        if verified:
            print(" existing OK")
            return True
        print(f" existing-invalid({reason}); ", end="", flush=True)

    if old_en_text is not None and translate_file_by_diff(ru_path, en_path, old_en_text):
        return True

    if translate_file_by_chunks(ru_path, en_path):
        return True

    try:
        result = run_codex_prompt(prompt)
    except FileNotFoundError:
        print(" ERROR")
        print("    ✗ `codex` CLI not found. Install: npm install -g @openai/codex")
        return False
    except RuntimeError as e:
        print(" ERROR")
        print(f"    ✗ {e}")
        return False

    if result != CODEX_DONE_MARKER:
        print(" ERROR")
        print(f"    ✗ Unexpected Codex completion marker: {result[:120]!r}")
        return False

    if not en_path.exists() or en_path.stat().st_size == 0:
        print(" ERROR")
        print(f"    ✗ Codex finished but {en_rel} was not created")
        return False

    # ── Пост-обработка: гарантируем backmatter и pre_backmatter ──
    en_text = en_path.read_text(encoding="utf-8")
    en_body, en_backmatter = _extract_backmatter(en_text)

    # Backmatter: всегда берём из RU (пути файлов, перевод не нужен)
    if ru_backmatter:
        en_text = en_body.rstrip() + "\n" + ru_backmatter
        en_path.write_text(en_text, encoding="utf-8")

    # ── Верификация: перевод действительно выполнен, а не отрапортован вхолостую ──
    verified, reason = _verify_translation(
        ru_path,
        en_path,
        old_en_text,
        allow_unchanged=(old_en_text is not None and _git_head_text(ru_path) is None),
    )
    if not verified:
        print(" FAIL")
        print(f"    ✗ Verification failed: {reason}")
        return False

    print(" OK")
    return True


# ─── Команды ──────────────────────────────────────────────────────────────────

def cmd_check() -> int:
    """Показать таблицу статусов всех файлов."""
    state = load_state()
    files = state.get("files", {})

    if not files:
        print("No files tracked in .skills-sync-state.json")
        return 0

    col_w = max(len(f) for f in files) + 2
    header = f"{'File':<{col_w}} {'Status':<10} {'Synced at'}"
    print(header)
    print("-" * len(header))

    counts = {"synced": 0, "pending": 0, "dirty": 0, "error": 0}
    for path, info in sorted(files.items()):
        status = info.get("status", "pending")
        synced_at = info.get("synced_at") or "-"
        counts[status] = counts.get(status, 0) + 1
        marker = {"synced": "✓", "pending": "○", "dirty": "✗", "error": "!"}.get(status, "?")
        print(f"  {marker} {path:<{col_w}} {status:<10} {synced_at}")

    print()
    print(f"Total: {len(files)} | " + " | ".join(f"{k}: {v}" for k, v in counts.items() if v))
    dirty = counts.get("dirty", 0) + counts.get("pending", 0)
    return 1 if dirty > 0 else 0


def _sync_one_file(ru_path: Path, check_hashes: bool, state_files: dict) -> dict:
    """
    Синхронизировать один файл. Возвращает dict с результатом.
    Вызывается из потока — не пишет в state напрямую.
    """
    rel = relative(ru_path)

    if not ru_path.exists():
        print(f"  ✗ File not found: {rel}")
        return {"rel": rel, "ok": False}

    current_hash = sha256_file(ru_path)

    # Если хэш не изменился и EN существует и его хэш совпадает — пропускаем
    if check_hashes and rel in state_files:
        info = state_files[rel]
        en_path_check = ru_to_en_path(ru_path)
        en_actual_hash = sha256_file(en_path_check) if en_path_check.exists() else None
        if (info.get("ru_hash") == current_hash
                and info.get("status") == "synced"
                and en_actual_hash is not None
                and info.get("en_hash") == en_actual_hash):
            print(f"  ✓ {rel} — up to date, skipping")
            return {"rel": rel, "ok": True, "skipped": True}

    en_path = ru_to_en_path(ru_path)
    if is_translatable(ru_path):
        ok = translate_file(ru_path, en_path)
    else:
        ok = copy_file(ru_path, en_path)

    return {
        "rel": rel,
        "ok": ok,
        "ru_hash": current_hash,
        "en_path": str(en_path),
    }


def cmd_sync(file_args: list[str], *, check_hashes: bool = True, workers: int = DEFAULT_WORKERS) -> int:
    """
    Синхронизировать указанные файлы (пути относительно корня репо).
    Если file_args пустой и check_hashes=True — синхронизировать все dirty/pending.
    workers — количество параллельных потоков перевода.
    """
    state = load_state()
    from datetime import datetime, timezone

    # Собираем список файлов для синхронизации
    if file_args:
        targets = []
        for f in file_args:
            p = Path(f)
            if not p.is_absolute():
                p = REPO_ROOT / p
            p = p.resolve()
            if is_excluded(p):
                continue
            if not str(p).startswith(str(REPO_ROOT / "framework")):
                print(f"  ⚠ Skipping {f} (not in framework/)")
                continue
            targets.append(p)
    else:
        # Все pending/dirty
        targets = []
        for rel_path, info in state["files"].items():
            if info.get("status") in ("pending", "dirty"):
                targets.append(REPO_ROOT / rel_path)

    if not targets:
        print("Nothing to sync.")
        return 0

    effective_workers = min(workers, len(targets))
    print(f"\nSyncing {len(targets)} file(s) with {effective_workers} parallel worker(s)...\n")

    errors = []
    added_files = []

    # Snapshot state_files для потоков (read-only)
    state_files_snapshot = dict(state.get("files", {}))

    with ThreadPoolExecutor(max_workers=effective_workers) as pool:
        futures = {
            pool.submit(_sync_one_file, ru_path, check_hashes, state_files_snapshot): ru_path
            for ru_path in targets
        }

        for future in as_completed(futures):
            result = future.result()
            rel = result["rel"]

            if result.get("skipped"):
                continue

            if result["ok"]:
                en_path = Path(result["en_path"])
                en_hash = sha256_file(en_path)
                now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
                state["files"][rel] = {
                    "ru_hash": result["ru_hash"],
                    "en_hash": en_hash,
                    "synced_at": now,
                    "status": "synced",
                }
                added_files.append(result["en_path"])
            else:
                state["files"].setdefault(rel, {}).update({
                    "ru_hash": result.get("ru_hash", ""),
                    "status": "dirty",
                })
                errors.append(rel)

    save_state(state)

    # Сообщаем пре-коммит хуку какие файлы добавить в индекс
    if added_files:
        added_files.append(str(STATE_FILE))
        print("\n__SYNC_ADD_FILES__")
        for f in added_files:
            print(f)

    if errors:
        print(f"\n✗ Failed to sync {len(errors)} file(s):")
        for e in errors:
            print(f"  - {e}")
        return 1

    print(f"\n✓ Synced {len(added_files) - 1} file(s) successfully.")
    return 0


def cmd_init_all(workers: int = DEFAULT_WORKERS) -> int:
    """Первичная синхронизация — перевести все файлы framework/ у которых нет EN-версии."""
    targets = []
    for ru_path in sorted(FRAMEWORK_DIR.rglob("*")):
        if ru_path.is_file() and not is_excluded(ru_path):
            en_path = ru_to_en_path(ru_path)
            if not en_path.exists():
                targets.append(str(ru_path.relative_to(REPO_ROOT)))

    if not targets:
        print("All files already have EN mirrors. Nothing to do.")
        return 0

    print(f"Found {len(targets)} files without EN mirror. Starting translation...\n")
    return cmd_sync(targets, check_hashes=False, workers=workers)


# ─── Обновление реестра для новых/удалённых файлов ────────────────────────────

def sync_registry_with_disk() -> None:
    """Добавить новые файлы, пометить изменённые как dirty, удалить записи для удалённых файлов."""
    state = load_state()
    known = set(state["files"].keys())

    # Собираем актуальные файлы на диске
    disk_files: set[str] = set()

    # Новые файлы и проверка хэшей существующих
    for ru_path in sorted(FRAMEWORK_DIR.rglob("*")):
        if ru_path.is_file() and not is_excluded(ru_path):
            rel = relative(ru_path)
            disk_files.add(rel)
            current_hash = sha256_file(ru_path)
            if rel not in known:
                state["files"][rel] = {
                    "ru_hash": current_hash,
                    "en_hash": None,
                    "synced_at": None,
                    "status": "pending",
                }
                print(f"  + Registered new file: {rel}")
            else:
                # Проверяем, изменился ли файл с момента последней синхронизации
                info = state["files"][rel]
                if info.get("status") == "synced" and info.get("ru_hash") != current_hash:
                    info["ru_hash"] = current_hash
                    info["status"] = "dirty"
                    print(f"  ~ Marked dirty: {rel}")

    # Удаляем записи для файлов, которых больше нет на диске
    stale = known - disk_files
    for rel in sorted(stale):
        del state["files"][rel]
        print(f"  - Pruned stale entry: {rel}")

    save_state(state)


# ─── Синхронизация структуры каталогов ─────────────────────────────────────────

def cmd_sync_structure(*, clean: bool = False) -> int:
    """
    Синхронизирует структуру каталогов framework/ → framework_eng/.

    - Создаёт отсутствующие каталоги в framework_eng/
    - Находит осиротевшие каталоги в framework_eng/ (нет соответствия в framework/)
    - При clean=True удаляет осиротевшие каталоги
    """
    import shutil

    ru_base = FRAMEWORK_DIR
    en_base = MIRROR_DIR

    if not ru_base.is_dir():
        print(f"  ✗ Source directory not found: {ru_base}")
        return 1

    if not en_base.is_dir():
        print(f"  Creating mirror root: {relative(en_base)}")
        en_base.mkdir(parents=True, exist_ok=True)

    # Collect all directories in framework/ (relative paths)
    ru_dirs = set()
    for p in sorted(ru_base.rglob("*")):
        if p.is_dir():
            ru_dirs.add(p.relative_to(ru_base))

    # Collect all directories in framework_eng/ (relative paths)
    en_dirs = set()
    for p in sorted(en_base.rglob("*")):
        if p.is_dir():
            en_dirs.add(p.relative_to(en_base))

    # Missing in EN (exist in RU but not in EN)
    missing_in_en = sorted(ru_dirs - en_dirs)
    # Orphaned in EN (exist in EN but not in RU)
    orphaned_in_en = sorted(en_dirs - ru_dirs)

    created = 0
    removed = 0
    issues = False

    print("\n┌─────────────────────────────────────────────────────────┐")
    print("│  Directory structure sync: framework/ → framework_eng/  │")
    print("└─────────────────────────────────────────────────────────┘\n")

    # Create missing directories
    if missing_in_en:
        print(f"  Missing directories in framework_eng/ ({len(missing_in_en)}):")
        for rel_dir in missing_in_en:
            en_dir = en_base / rel_dir
            en_dir.mkdir(parents=True, exist_ok=True)
            print(f"    + Created: framework_eng/{rel_dir}")
            created += 1
    else:
        print("  ✓ No missing directories in framework_eng/")

    # Report/remove orphaned directories
    if orphaned_in_en:
        print(f"\n  Orphaned directories in framework_eng/ ({len(orphaned_in_en)}):")
        for rel_dir in orphaned_in_en:
            en_dir = en_base / rel_dir
            if clean:
                if en_dir.exists():
                    shutil.rmtree(en_dir, ignore_errors=True)
                    print(f"    - Removed: framework_eng/{rel_dir}")
                    removed += 1
            else:
                print(f"    ! Orphan: framework_eng/{rel_dir}")
                issues = True
    else:
        print("  ✓ No orphaned directories in framework_eng/")

    # Summary
    print()
    parts = []
    if created:
        parts.append(f"created: {created}")
    if removed:
        parts.append(f"removed: {removed}")
    if issues:
        orphan_count = len(orphaned_in_en)
        parts.append(f"orphaned: {orphan_count} (use --clean to remove)")

    if parts:
        print(f"  Summary: {' | '.join(parts)}")
    else:
        print("  ✓ Directory structure is in sync.")

    return 1 if issues else 0


# ─── main ─────────────────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(
        description="Sync framework/ RU skills → framework_eng/ EN mirror"
    )
    parser.add_argument("files", nargs="*", help="Specific files to sync (relative to repo root)")
    parser.add_argument("--check", action="store_true", help="Show sync status table, exit 1 if any dirty")
    parser.add_argument("--all", action="store_true", help="Sync all pending/dirty files")
    parser.add_argument("--init-all", action="store_true", help="Initial sync: translate everything missing")
    parser.add_argument("--sync-structure", action="store_true",
                        help="Sync directory structure: create missing dirs in framework_eng/, report orphans")
    parser.add_argument("--clean", action="store_true",
                        help="With --sync-structure: remove orphaned directories in framework_eng/")
    parser.add_argument("--workers", "-j", type=int, default=DEFAULT_WORKERS,
                        help=f"Parallel translation workers (default: {DEFAULT_WORKERS})")

    args = parser.parse_args()

    # Всегда обновляем реестр на случай новых файлов
    sync_registry_with_disk()

    if args.sync_structure:
        return cmd_sync_structure(clean=args.clean)
    elif args.check:
        return cmd_check()
    elif args.init_all:
        return cmd_init_all(workers=args.workers)
    elif args.all:
        return cmd_sync([], check_hashes=True, workers=args.workers)
    elif args.files:
        return cmd_sync(args.files, check_hashes=True, workers=args.workers)
    else:
        parser.print_help()
        return 0


if __name__ == "__main__":
    sys.exit(main())
