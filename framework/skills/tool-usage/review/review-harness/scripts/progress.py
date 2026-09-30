"""Семантический прогресс сессий: progress.jsonl (RVSW-01, FR-13, AC-13; TD §5.6, §9.3).

Ядро инструмента пишет чекпоинты на границах этапов работы — не поток
сознания (FR-13). Файл append-only; вызывающая модель читает по желанию;
`status` инструмента дублирует последний чекпоинт (через last_checkpoint).

Правило границы (FR-01): harness НЕ знает имена чекпоинтов инструментов —
допустимое множество (`allowed_checkpoints`) передаёт вызывающая сторона;
harness только валидирует принадлежность fail-closed. Схема строки (TD §5.6):

{"ts": ISO-8601, "session_id": str, "tool": "swarm | consilium",
 "checkpoint": str ∈ allowed_checkpoints, "summary": непустая строка,
 "counters": {str: неотрицательный int}}  # снимок состояния на границе
"""
from __future__ import annotations

import json
from datetime import UTC, datetime
from pathlib import Path

PROGRESS_FILENAME = "progress.jsonl"
TOOLS = ("swarm", "consilium")


def _validate_counters(counters) -> dict:
    """Снимок счётчиков на границе: плоский dict неотрицательных int (fail-closed)."""
    if not isinstance(counters, dict):
        raise ValueError(f"counters: ожидается dict, получено {type(counters).__name__}")
    for key, value in counters.items():
        if not isinstance(key, str):
            raise ValueError(f"counters: ключ не строка: {key!r}")
        if not isinstance(value, int) or isinstance(value, bool) or value < 0:
            raise ValueError(f"counters[{key!r}]: ожидается неотрицательный int, получено {value!r}")
    return dict(counters)


def append_checkpoint(session_dir: Path, *, tool: str, session_id: str, checkpoint: str,
                      summary: str, counters: dict, allowed_checkpoints,
                      ts: str | None = None) -> dict:
    """Добавить чекпоинт в progress.jsonl сессии (append-only).

    Fail-closed: неизвестный tool/checkpoint, пустой summary, битые counters —
    ValueError, файл не трогается. Возвращает записанную запись.
    """
    if tool not in TOOLS:
        raise ValueError(f"tool вне enum {list(TOOLS)}: {tool!r}")
    if checkpoint not in allowed_checkpoints:
        raise ValueError(f"checkpoint вне допустимого множества инструмента: {checkpoint!r}")
    if not isinstance(summary, str) or not summary.strip():
        raise ValueError("summary: ожидается непустая строка (чекпоинт границы, не поток)")
    counters = _validate_counters(counters)
    record = {
        "ts": ts or datetime.now(UTC).isoformat(),
        "session_id": session_id,
        "tool": tool,
        "checkpoint": checkpoint,
        "summary": summary,
        "counters": counters,
    }
    session_dir = Path(session_dir)
    session_dir.mkdir(parents=True, exist_ok=True)
    with (session_dir / PROGRESS_FILENAME).open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(record, ensure_ascii=False) + "\n")
    return record


def read_checkpoints(session_dir: Path) -> list[dict]:
    """Все чекпоинты сессии в порядке записи; нет файла → []."""
    path = Path(session_dir) / PROGRESS_FILENAME
    if not path.exists():
        return []
    records = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            records.append(json.loads(line))
    return records


def last_checkpoint(session_dir: Path) -> dict | None:
    """Последний чекпоинт (его дублирует `status` инструмента, TD §9.3)."""
    records = read_checkpoints(session_dir)
    return records[-1] if records else None
