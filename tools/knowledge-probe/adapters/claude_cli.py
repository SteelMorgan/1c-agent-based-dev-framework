"""
Адаптер Anthropic Claude через headless CLI: `claude -p "<prompt>" --model <alias>`.

Минимальный проверенный набор флагов (проверено `claude --help` + реальные вызовы):
  -p / --print                       — неинтерактивный вывод (обязателен);
  --model <alias>                    — haiku|sonnet|opus;
  --dangerously-skip-permissions     — на случай, если что-то запросит доступ;
                                       в пустом cwd без инструментов не мешает.

Изоляция от проектного контекста достигается запуском с cwd = пустой временный
каталог вне репо (см. adapters.empty_cwd): auto-discovery CLAUDE.md идёт вверх
от cwd, а над /tmp проектного CLAUDE.md нет — значит проектные правила и скиллы
НЕ подтягиваются.

ВАЖНО: флаг --bare здесь НЕ используется намеренно. Он отключает CLAUDE.md,
но в этой среде также ломает OAuth-авторизацию ("Not logged in · Please run
/login"), т.к. --bare требует ANTHROPIC_API_KEY. Проверено вручную.

Смоук-вызовы (`claude -p "..." --model haiku` с пустым cwd) отвечали корректно.
"""
import subprocess

from . import AdapterError, empty_cwd


def _run_once(model_id: str, prompt: str, timeout: int) -> str:
    with empty_cwd() as cwd:
        cmd = [
            "claude",
            "-p", prompt,
            "--model", model_id,
            "--dangerously-skip-permissions",
        ]
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
            f"claude CLI rc={proc.returncode}: "
            f"{(proc.stderr or proc.stdout or '').strip()[:500]}"
        )
    out = (proc.stdout or "").strip()
    if not out:
        raise AdapterError("claude CLI вернул пустой ответ")
    return out


def ask(model_id: str, prompt: str, timeout: int, retries: int) -> str:
    last = None
    for attempt in range(retries + 1):
        try:
            return _run_once(model_id, prompt, timeout)
        except subprocess.TimeoutExpired:
            last = AdapterError(f"claude CLI таймаут ({timeout}s), модель {model_id}")
        except AdapterError as e:
            last = e
    raise last
