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
import hashlib
import json
import os
import subprocess
import sys
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
CODEX_POLL_INTERVAL = 3   # секунды между проверками файла -o
CODEX_TIMEOUT = 600       # максимальное время ожидания в секундах (10 мин для больших файлов)
DEFAULT_WORKERS = 8       # параллельных переводов по умолчанию


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
