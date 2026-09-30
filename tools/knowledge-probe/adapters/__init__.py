"""
Адаптеры моделей для knowledge-probe.

Каждый адаптер экспортирует:
    ask(model_id: str, prompt: str, timeout: int, retries: int) -> str
и бросает AdapterError с понятным сообщением при неустранимом сбое.

Общая утилита empty_cwd() даёт временный ПУСТОЙ каталог вне репозитория —
чтобы CLI-инструменты (claude, codex) НЕ подтянули проектный CLAUDE.md,
правила и скиллы, которые исказили бы «чистое» знание модели.
"""
import contextlib
import tempfile


class AdapterError(RuntimeError):
    """Неустранимая ошибка адаптера (после всех retry)."""


@contextlib.contextmanager
def empty_cwd():
    """Временный пустой каталог вне репозитория; удаляется по выходу."""
    with tempfile.TemporaryDirectory(prefix="kprobe-cwd-") as d:
        yield d


def get_adapter(name: str):
    """Возвращает модуль-адаптер по имени из config.MODELS."""
    if name == "claude_cli":
        from . import claude_cli
        return claude_cli
    if name == "codex_cli":
        from . import codex_cli
        return codex_cli
    if name == "deepseek_http":
        from . import deepseek_http
        return deepseek_http
    raise AdapterError(f"Неизвестный адаптер: {name}")
