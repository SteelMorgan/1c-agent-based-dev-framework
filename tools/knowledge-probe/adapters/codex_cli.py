"""
Адаптер OpenAI GPT через Codex CLI: `codex exec -m <model_id> "<prompt>"`.

Синтаксис проверен по `codex exec --help` и реальным вызовом (v0.142.4):
  codex exec [OPTIONS] [PROMPT]
    -m, --model <MODEL>          — id модели (gpt-5.5, gpt-5.4-mini, ...);
    -s, --sandbox read-only      — модель не пишет на диск (нам нужен только текст);
    --skip-git-repo-check        — разрешить запуск вне git-репозитория (у нас пустой cwd);
    --ignore-rules               — не грузить project/user execpolicy .rules;
    -o, --output-last-message <F>— записать ТОЛЬКО финальный ответ в файл (чистый вывод);
    -C, --cd <DIR>               — рабочий каталог (задаём пустой временный).

Изоляция: cwd = пустой временный каталог вне репо (см. adapters.empty_cwd),
чтобы codex не подтянул проектные AGENTS.md / .rules / контекст репозитория.
Модель и auth берутся из ~/.codex/config.toml (CODEX_HOME) — user config НЕ
игнорируем, т.к. там лежит авторизация; конкретный id всегда навязываем через -m.

ВАЖНО: `codex exec` читает stdin («Reading additional input from stdin...»),
поэтому вызываем со stdin=DEVNULL, иначе процесс может зависнуть.

Смоук-вызов `codex exec --skip-git-repo-check -s read-only -m gpt-5.5 ...`
реально ответил корректно и записал финальный ответ в -o файл.
"""
import os
import subprocess
import tempfile

from . import AdapterError, empty_cwd


def _run_once(model_id: str, prompt: str, timeout: int) -> str:
    # model_id может нести усилие рассуждения суффиксом: "gpt-5.5:high" ->
    # -m gpt-5.5 -c model_reasoning_effort="high" (low|medium|high|xhigh).
    effort = None
    if ":" in model_id:
        model_id, effort = model_id.split(":", 1)
    with empty_cwd() as cwd:
        out_fd, out_path = tempfile.mkstemp(prefix="kprobe-codex-", suffix=".txt")
        os.close(out_fd)
        try:
            cmd = [
                "codex", "exec",
                "-m", model_id,
                "-s", "read-only",
                "--skip-git-repo-check",
                "--ignore-rules",
                "-C", cwd,
                "-o", out_path,
            ]
            if effort:
                cmd += ["-c", f'model_reasoning_effort="{effort}"']
            cmd.append(prompt)
            proc = subprocess.run(
                cmd,
                cwd=cwd,
                capture_output=True,
                text=True,
                timeout=timeout,
                stdin=subprocess.DEVNULL,
            )
            if proc.returncode != 0:
                raise AdapterError(
                    f"codex exec rc={proc.returncode} (model {model_id}): "
                    f"{(proc.stderr or proc.stdout or '').strip()[:500]}"
                )
            with open(out_path, "r", encoding="utf-8") as f:
                out = f.read().strip()
        finally:
            try:
                os.unlink(out_path)
            except OSError:
                pass
    if not out:
        raise AdapterError("codex exec вернул пустой финальный ответ")
    return out


def ask(model_id: str, prompt: str, timeout: int, retries: int) -> str:
    last = None
    for attempt in range(retries + 1):
        try:
            return _run_once(model_id, prompt, timeout)
        except subprocess.TimeoutExpired:
            last = AdapterError(f"codex exec таймаут ({timeout}s), модель {model_id}")
        except AdapterError as e:
            last = e
    raise last
