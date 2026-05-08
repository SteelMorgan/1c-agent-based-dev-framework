#!/usr/bin/env python3
"""
codex_image_gen.py — тонкая обёртка над `codex exec` для генерации/редактирования изображений.

Запускает Codex CLI с `--sandbox workspace-write -C <output_dir>`, чтобы Codex мог
записать сгенерированный файл в `tasks/<task-id>/assets/`. Промт автоматически
дополняется инструкцией о сохранении файла под нужным именем в CWD.

Возвращает в stdout JSON-блок одной строкой:
  {"status": "ok", "files": ["/abs/path/to/file.png", ...], "raw_log": "/abs/path/to/log"}
  {"status": "error", "reason": "...", "raw_log": "/abs/path/to/log"}

Скрипт намеренно НЕ имитирует session-lifecycle cross-provider-review (start/ask/debate/...).
Image generation — атомарная операция «один промт → один файл», и если результат не подходит,
проще вызвать wrapper заново с уточнённым промтом, чем держать sandbox.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shlex
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

DEFAULT_MODEL: str | None = None  # None → respect codex config.toml
DEFAULT_REASONING = "medium"
DEFAULT_TIMEOUT_SEC = 600
DEFAULT_SIZE_HINT = "Use 1024x1024 unless the user explicitly asked for another size."
ALLOWED_EXTS = {".png", ".jpg", ".jpeg", ".webp"}


def emit(payload: dict) -> None:
    """Печатает однострочный JSON в stdout — формат, ожидаемый вызывающим агентом."""
    sys.stdout.write(json.dumps(payload, ensure_ascii=False) + "\n")
    sys.stdout.flush()


def fail(reason: str, raw_log: Path | None = None) -> None:
    payload = {"status": "error", "reason": reason}
    if raw_log is not None:
        payload["raw_log"] = str(raw_log.resolve())
    emit(payload)
    sys.exit(1)


def find_repo_root(start: Path) -> Path:
    """
    Поиск корня репозитория по `.git` снизу вверх. Если не найден — возвращаем CWD.
    Нужно, чтобы --task-id корректно ложился в `<repo>/tasks/<id>/assets/`,
    даже если вызов идёт из подкаталога.
    """
    cur = start.resolve()
    for candidate in [cur, *cur.parents]:
        if (candidate / ".git").exists():
            return candidate
    return cur


def build_output_dir(task_id: str, override: Path | None) -> Path:
    if override is not None:
        return override.resolve()
    repo_root = find_repo_root(Path.cwd())
    return (repo_root / "tasks" / task_id / "assets").resolve()


def validate_filename(filename: str) -> None:
    if "/" in filename or "\\" in filename:
        fail(f"--filename must be a basename without path separators, got: {filename!r}")
    suffix = Path(filename).suffix.lower()
    if suffix not in ALLOWED_EXTS:
        fail(f"--filename must end with one of {sorted(ALLOWED_EXTS)}, got: {filename!r}")


def read_prompt(args: argparse.Namespace) -> str:
    if args.prompt and args.prompt_file:
        fail("--prompt and --prompt-file are mutually exclusive")
    if not args.prompt and not args.prompt_file:
        fail("either --prompt or --prompt-file is required")
    if args.prompt_file:
        path = Path(args.prompt_file)
        if not path.is_file():
            fail(f"--prompt-file does not exist: {path}")
        return path.read_text(encoding="utf-8").strip()
    return args.prompt.strip()


def collect_reference_images(refs: list[str], output_dir: Path) -> list[Path]:
    """
    Копируем reference-изображения в output_dir под фиксированными именами `ref_<i>.<ext>`,
    чтобы Codex видел их через относительные пути в CWD (sandbox = workspace-write,
    путь за пределы CWD — read-only, но копия внутри CWD доступна полностью).
    """
    copied: list[Path] = []
    for idx, ref in enumerate(refs):
        src = Path(ref).resolve()
        if not src.is_file():
            fail(f"--reference-image not found: {ref}")
        ext = src.suffix.lower() or ".png"
        dst = output_dir / f"ref_{idx}{ext}"
        dst.write_bytes(src.read_bytes())
        copied.append(dst)
    return copied


def assemble_full_prompt(user_prompt: str, filename: str, references: list[Path]) -> str:
    """
    Соединяем пользовательский промт с обязательной wrapper-инструкцией:
    1. Сохранить файл под точным именем.
    2. Дефолтный hint размера (если в промте нет явного override).
    3. Перечисление reference-файлов, если они есть.
    """
    parts = [user_prompt]

    if references:
        ref_lines = "\n".join(f"- {p.name}" for p in references)
        parts.append(
            "Reference images already present in the current working directory:\n"
            f"{ref_lines}\n"
            "Use them as the source/base for editing. Do not invent additional reference files."
        )

    parts.append(DEFAULT_SIZE_HINT)
    parts.append(
        f"Save the resulting image as `{filename}` in the current working directory. "
        "Do not save under any other name and do not write outside the current working directory."
    )
    return "\n\n".join(parts)


def run_codex(
    prompt: str,
    output_dir: Path,
    model: str | None,
    reasoning: str,
    timeout_sec: int,
    log_path: Path,
) -> int:
    cmd = ["codex", "exec"]
    if model:
        cmd += ["-m", model]
    cmd += [
        "-c",
        f'model_reasoning_effort="{reasoning}"',
        "--sandbox",
        "workspace-write",
        "--skip-git-repo-check",
        "--json",
        "-C",
        str(output_dir),
        "-",
    ]
    with log_path.open("w", encoding="utf-8") as log:
        log.write(f"# codex_image_gen.py invocation @ {datetime.now(timezone.utc).isoformat()}\n")
        log.write(f"# cmd: {' '.join(shlex.quote(c) for c in cmd)}\n")
        log.write(f"# output_dir: {output_dir}\n")
        log.write("# --- PROMPT START ---\n")
        log.write(prompt)
        log.write("\n# --- PROMPT END ---\n")
        log.flush()
        try:
            proc = subprocess.run(
                cmd,
                input=prompt,
                stdout=log,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=timeout_sec,
                cwd=str(output_dir),
                env=os.environ.copy(),
            )
        except FileNotFoundError:
            fail("`codex` CLI not found in PATH — install it before using this skill", log_path)
        except subprocess.TimeoutExpired:
            fail(f"codex exec timed out after {timeout_sec}s", log_path)
    return proc.returncode


def discover_new_files(output_dir: Path, snapshot_before: set[Path], expected: str) -> list[Path]:
    """
    Возвращает все новые файлы изображений в output_dir, появившиеся после запуска.
    Если ожидаемое имя присутствует — оно ставится первым в списке.
    """
    after = {p for p in output_dir.iterdir() if p.is_file()}
    new = [p for p in (after - snapshot_before) if p.suffix.lower() in ALLOWED_EXTS]
    expected_path = output_dir / expected
    if expected_path in new:
        new = [expected_path] + [p for p in new if p != expected_path]
    return sorted(new, key=lambda p: (p.name != expected, p.name))


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--task-id", required=True, help="Идентификатор задачи. Файл ляжет в tasks/<task-id>/assets/.")
    p.add_argument("--filename", required=True, help="Имя итогового файла, например ui-mockup-v1.png.")
    g = p.add_mutually_exclusive_group(required=False)
    g.add_argument("--prompt", help="Inline-промт.")
    g.add_argument("--prompt-file", help="Путь к файлу с промтом.")
    p.add_argument("--reference-image", action="append", default=[], help="Reference-изображение для редактирования. Можно повторять.")
    p.add_argument("--output-dir", help="Перекрыть путь tasks/<task-id>/assets/ (на всякий случай). По умолчанию вычисляется автоматически.")
    p.add_argument("--model", default=DEFAULT_MODEL)
    p.add_argument("--reasoning-effort", default=DEFAULT_REASONING, choices=["low", "medium", "high"])
    p.add_argument("--timeout-sec", type=int, default=DEFAULT_TIMEOUT_SEC)
    p.add_argument("--dry-run", action="store_true", help="Только напечатать финальный промт и команду; не запускать codex.")
    return p.parse_args()


def main() -> None:
    args = parse_args()
    validate_filename(args.filename)
    user_prompt = read_prompt(args)

    output_dir = build_output_dir(args.task_id, Path(args.output_dir) if args.output_dir else None)
    output_dir.mkdir(parents=True, exist_ok=True)

    references = collect_reference_images(args.reference_image, output_dir)
    full_prompt = assemble_full_prompt(user_prompt, args.filename, references)

    log_path = output_dir / f".codex_image_gen.{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}.log"

    if args.dry_run:
        emit({
            "status": "dry-run",
            "output_dir": str(output_dir),
            "filename": args.filename,
            "model": args.model,
            "reasoning_effort": args.reasoning_effort,
            "prompt_preview": full_prompt[:400] + ("…" if len(full_prompt) > 400 else ""),
        })
        return

    snapshot_before = {p for p in output_dir.iterdir() if p.is_file()}

    rc = run_codex(
        prompt=full_prompt,
        output_dir=output_dir,
        model=args.model,
        reasoning=args.reasoning_effort,
        timeout_sec=args.timeout_sec,
        log_path=log_path,
    )

    new_files = discover_new_files(output_dir, snapshot_before, args.filename)

    if rc != 0:
        fail(f"codex exec exited with code {rc}; see raw log", log_path)

    if not new_files:
        fail(
            "codex finished cleanly but no new image file appeared in the output directory; "
            "проверь raw log — обычно это значит что модель не вызвала image_generation tool",
            log_path,
        )

    emit({
        "status": "ok",
        "files": [str(p.resolve()) for p in new_files],
        "raw_log": str(log_path.resolve()),
    })


if __name__ == "__main__":
    main()
