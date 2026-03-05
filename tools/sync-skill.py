#!/usr/bin/env python3
"""
sync-skill.py — синхронизатор RU→EN для framework/ → framework_eng/

Использование:
  python3 tools/sync-skill.py [файл1 файл2 ...]   # синхронизировать конкретные файлы
  python3 tools/sync-skill.py --check              # показать статусы без перевода
  python3 tools/sync-skill.py --all                # синхронизировать все pending/dirty файлы
  python3 tools/sync-skill.py --init-all           # первичная синхронизация всего framework/

Вызывается из pre-commit хука автоматически.
"""

import argparse
import hashlib
import json
import os
import subprocess
import sys
import textwrap
from pathlib import Path

# ─── Пути ─────────────────────────────────────────────────────────────────────
SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
STATE_FILE = REPO_ROOT / ".skills-sync-state.json"
FRAMEWORK_DIR = REPO_ROOT / "framework"
MIRROR_DIR = REPO_ROOT / "framework_eng"
EXCLUDE_NAMES = {"README.md"}

CODEX_PROFILE = "cx_gpt-5-codex-mini"
CODEX_DONE_MARKER = "OK"
CODEX_POLL_INTERVAL = 3   # секунды между проверками файла -o
CODEX_TIMEOUT = 600       # максимальное время ожидания в секундах (10 мин для больших файлов)


def is_codex_custom_mode() -> bool:
    """
    Возвращает True если включён кастомный режим Codex с профилями.
    Проверяет переменную окружения CUSTOM_CODEX_ENABLED=1.
    """
    return os.environ.get("CUSTOM_CODEX_ENABLED", "0") == "1"


def make_translate_prompt(ru_rel: str, en_rel: str) -> str:
    """Промпт для Codex — работает с файлами проекта напрямую."""
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


# ─── Перевод через Codex CLI ──────────────────────────────────────────────────

def translate_file(ru_path: Path, en_path: Path) -> bool:
    """
    Запускает Codex CLI для перевода ru_path → en_path.
    Codex работает в контексте проекта и сам читает/пишет файлы.
    Появление файла -o со словом "OK" = задание выполнено.
    """
    import time

    ru_rel = relative(ru_path)
    en_rel = relative(en_path)
    prompt = make_translate_prompt(ru_rel, en_rel)

    ts = int(time.time())
    done_file = Path(f"/tmp/sync-skill-done-{ts}.txt")

    # Создаём директорию зеркала заранее
    en_path.parent.mkdir(parents=True, exist_ok=True)

    print(f"  → Translating {ru_rel} ...", end="", flush=True)

    # Формируем аргументы в зависимости от режима Codex
    if is_codex_custom_mode():
        cmd = [
            "codex", "exec",
            "-p", CODEX_PROFILE,
            "--sandbox", "workspace-write",
            "--ephemeral",
            "-o", str(done_file),
            prompt,
        ]
    else:
        cmd = [
            "codex", "exec",
            "-m", "gpt-5.1-codex-mini",
            "--sandbox", "workspace-write",
            "--ephemeral",
            "-o", str(done_file),
            prompt,
        ]

    try:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            cwd=str(REPO_ROOT),
        )
    except FileNotFoundError:
        print(" ERROR")
        print("    ✗ `codex` CLI not found. Install: npm install -g @openai/codex")
        return False

    # Polling: ждём появления done_file (атомарно создаётся Codex при завершении)
    elapsed = 0
    while elapsed < CODEX_TIMEOUT:
        time.sleep(CODEX_POLL_INTERVAL)
        elapsed += CODEX_POLL_INTERVAL

        if done_file.exists():
            marker = done_file.read_text(encoding="utf-8").strip()
            done_file.unlink(missing_ok=True)
            proc.wait()

            # Проверяем что EN-файл реально создан
            if not en_path.exists() or en_path.stat().st_size == 0:
                print(" ERROR")
                print(f"    ✗ Codex finished (marker='{marker}') but {en_rel} was not created")
                return False

            print(" OK")
            return True

        # Процесс завершился раньше файла — что-то пошло не так
        if proc.poll() is not None and not done_file.exists():
            print(" ERROR")
            print(f"    ✗ Codex exited (code {proc.returncode}) without creating done marker")
            return False

    # Таймаут
    proc.terminate()
    done_file.unlink(missing_ok=True)
    print(" TIMEOUT")
    print(f"    ✗ Translation timed out after {CODEX_TIMEOUT}s")
    return False


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


def cmd_sync(file_args: list[str], *, check_hashes: bool = True) -> int:
    """
    Синхронизировать указанные файлы (пути относительно корня репо).
    Если file_args пустой и check_hashes=True — синхронизировать все dirty/pending.
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

    errors = []
    added_files = []

    for ru_path in targets:
        rel = relative(ru_path)

        # Пересчитываем хэш текущего файла
        if not ru_path.exists():
            print(f"  ✗ File not found: {rel}")
            errors.append(rel)
            continue

        current_hash = sha256_file(ru_path)

        # Если хэш не изменился и EN существует и его хэш совпадает — пропускаем
        if check_hashes and rel in state["files"]:
            info = state["files"][rel]
            en_path_check = ru_to_en_path(ru_path)
            en_actual_hash = sha256_file(en_path_check) if en_path_check.exists() else None
            if (info.get("ru_hash") == current_hash
                    and info.get("status") == "synced"
                    and en_actual_hash is not None
                    and info.get("en_hash") == en_actual_hash):
                print(f"  ✓ {rel} — up to date, skipping")
                continue

        en_path = ru_to_en_path(ru_path)
        ok = translate_file(ru_path, en_path)

        if ok:
            en_hash = sha256_file(en_path)
            now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            state["files"][rel] = {
                "ru_hash": current_hash,
                "en_hash": en_hash,
                "synced_at": now,
                "status": "synced",
            }
            added_files.append(str(en_path))
        else:
            state["files"].setdefault(rel, {}).update({
                "ru_hash": current_hash,
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

    return 0


def cmd_init_all() -> int:
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
    return cmd_sync(targets, check_hashes=False)


# ─── Обновление реестра для новых/удалённых файлов ────────────────────────────

def sync_registry_with_disk() -> None:
    """Добавить в state новые файлы, пометить удалённые."""
    state = load_state()
    known = set(state["files"].keys())

    # Новые файлы
    for ru_path in sorted(FRAMEWORK_DIR.rglob("*")):
        if ru_path.is_file() and not is_excluded(ru_path):
            rel = relative(ru_path)
            if rel not in known:
                current_hash = sha256_file(ru_path)
                state["files"][rel] = {
                    "ru_hash": current_hash,
                    "en_hash": None,
                    "synced_at": None,
                    "status": "pending",
                }
                print(f"  + Registered new file: {rel}")

    save_state(state)


# ─── main ─────────────────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(
        description="Sync framework/ RU skills → framework_eng/ EN mirror"
    )
    parser.add_argument("files", nargs="*", help="Specific files to sync (relative to repo root)")
    parser.add_argument("--check", action="store_true", help="Show sync status table, exit 1 if any dirty")
    parser.add_argument("--all", action="store_true", help="Sync all pending/dirty files")
    parser.add_argument("--init-all", action="store_true", help="Initial sync: translate everything missing")

    args = parser.parse_args()

    # Всегда обновляем реестр на случай новых файлов
    sync_registry_with_disk()

    if args.check:
        return cmd_check()
    elif args.init_all:
        return cmd_init_all()
    elif args.all:
        return cmd_sync([], check_hashes=True)
    elif args.files:
        return cmd_sync(args.files, check_hashes=True)
    else:
        parser.print_help()
        return 0


if __name__ == "__main__":
    sys.exit(main())
