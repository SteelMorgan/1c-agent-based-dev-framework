"""Контракт конкурентной записи runtime-состояния адаптеров (дефект Х-15).

Три адаптера (`claude_opus_review`, `codex_review`, `kimi_review`) сохраняют
`runtime.json` и `review.json` через ФИКСИРОВАННОЕ имя временного файла
``path.with_name(path.name + ".tmp")``.  Heartbeat пишет `runtime.json` порядка
раза в секунду, а участников роя в каталоге несколько, каждый — отдельный
процесс.  Два писателя используют одно и то же имя `.tmp`: первый переименовал
его в целевой файл, второй уже не находит свой временный файл —

    FileNotFoundError: '.review-sandboxes/<id>/runtime.json.tmp' -> runtime.json

Второй режим отказа мягче и опаснее: писатель A делает ``os.replace`` ровно в
тот момент, когда писатель B успел усечь общий `.tmp` под свою запись, но ещё
не дописал его.  Целевой файл остаётся, но становится битым/обрезанным JSON.

`RUNTIME_LOCK` внутри адаптеров — `threading.RLock`, он сериализует только
потоки одного процесса и на межпроцессную гонку не влияет; `save_meta` не
защищён и им.

Тест НЕ проверяет форму (наличие импорта `runtime_store` или строки в коде): он
прогоняет реальную конкурентную запись через production-функции адаптеров и
падает по факту исключения, битого JSON или потери целостности содержимого.
Зелёным он станет только после того, как запись пойдёт через атомарный
`runtime_store.write_json_atomically` (уникальное имя временного файла на
писателя).

Внешних зависимостей нет: только `tempfile`/`multiprocessing`, ни сети, ни CLI
моделей, ни сервера, ни БД.
"""
from __future__ import annotations

import importlib.util
import json
import multiprocessing
import os
import sys
import time
from pathlib import Path

import pytest

from tests.conftest import ADAPTER_PATHS


ALL_FAMILIES = ("claude", "codex", "kimi")

# Целевые файлы и производственные функции их записи (по 2 места в адаптере).
SAVERS = (
    ("save_runtime", "runtime.json"),
    ("save_meta", "review.json"),
)

WRITERS = 6            # участников роя, одновременно пишущих один каталог
ITERATIONS = 40        # ходов записи на писателя
FILLER_ITEMS = 1500    # объём полезной нагрузки: расширяет окно гонки
READER_DEADLINE_SEC = 60.0


def _load_adapter(family: str):
    """Импорт production-модуля адаптера напрямую (тот же код, что и в бою)."""
    path = ADAPTER_PATHS[family]
    adapters_dir = str(path.parent)
    if adapters_dir not in sys.path:
        sys.path.insert(0, adapters_dir)
    spec = importlib.util.spec_from_file_location(path.stem, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules.setdefault(path.stem, module)
    spec.loader.exec_module(module)
    return module


def _payload(writer_index: int, seq: int) -> dict:
    """Состояние целиком: маркеры писателя + наполнитель фиксированной длины."""
    return {
        "writer": writer_index,
        "seq": seq,
        "state": "running",
        "filler": [f"w{writer_index}-s{seq}-{item}" for item in range(FILLER_ITEMS)],
    }


def _payload_is_whole(value: object) -> bool:
    """Записанное состояние — одно целиком, а не смесь двух писателей."""
    if not isinstance(value, dict):
        return False
    if set(value) != {"writer", "seq", "state", "filler"}:
        return False
    filler = value.get("filler")
    if not isinstance(filler, list) or len(filler) != FILLER_ITEMS:
        return False
    expected_prefix = f"w{value['writer']}-s{value['seq']}-"
    return all(
        isinstance(item, str) and item == f"{expected_prefix}{index}"
        for index, item in enumerate(filler)
    )


def _writer_process(family: str, saver_name: str, review_dir: str, writer_index: int, failures) -> None:
    """Один участник роя: бьёт по production-функции сохранения в цикле."""
    try:
        module = _load_adapter(family)
        save = getattr(module, saver_name)
    except Exception as exc:  # pragma: no cover — диагностика окружения
        failures.put(f"writer {writer_index}: не удалось импортировать {family}.{saver_name}: {exc!r}")
        return
    target_dir = Path(review_dir)
    for seq in range(ITERATIONS):
        try:
            save(target_dir, _payload(writer_index, seq))
        except Exception as exc:
            failures.put(
                f"writer {writer_index} seq {seq}: {type(exc).__name__}: {exc}"
            )
            return


def _reader_process(target: str, stop_flag, failures) -> None:
    """Наблюдатель: целевой файл обязан всегда быть целым JSON-состоянием."""
    target_path = Path(target)
    deadline = time.monotonic() + READER_DEADLINE_SEC
    while not stop_flag.is_set() and time.monotonic() < deadline:
        try:
            raw = target_path.read_text(encoding="utf-8")
        except FileNotFoundError:
            continue
        except OSError as exc:  # pragma: no cover — диагностика окружения
            failures.put(f"reader: OSError при чтении цели: {exc}")
            return
        if not raw:
            failures.put("reader: целевой файл пуст — виден незавершённый временный файл")
            return
        try:
            value = json.loads(raw)
        except json.JSONDecodeError as exc:
            failures.put(
                f"reader: битый JSON в целевом файле ({exc}); первые 120 байт: {raw[:120]!r}"
            )
            return
        if not _payload_is_whole(value):
            failures.put(
                "reader: в целевом файле не цельное состояние одного писателя: "
                f"ключи={sorted(value) if isinstance(value, dict) else type(value).__name__}"
            )
            return


@pytest.mark.parametrize("family", ALL_FAMILIES)
@pytest.mark.parametrize("saver_name,target_name", SAVERS)
def test_concurrent_writers_do_not_corrupt_runtime_state(
    tmp_path, family, saver_name, target_name
):
    """HX15: N процессов пишут один файл через функцию адаптера — без потерь.

    Красный на фиксированном `.tmp`: писатели ловят FileNotFoundError на
    ``tmp_path.replace(path)`` и/или наблюдатель видит обрезанный JSON.
    """
    review_dir = tmp_path / "20260804-000000-hx15"
    review_dir.mkdir()
    target = review_dir / target_name

    ctx = multiprocessing.get_context("fork")
    failures = ctx.Queue()
    stop_flag = ctx.Event()

    reader = ctx.Process(target=_reader_process, args=(str(target), stop_flag, failures))
    reader.start()

    writers = [
        ctx.Process(
            target=_writer_process,
            args=(family, saver_name, str(review_dir), index, failures),
        )
        for index in range(WRITERS)
    ]
    for process in writers:
        process.start()
    for process in writers:
        process.join(timeout=READER_DEADLINE_SEC)
    stop_flag.set()
    reader.join(timeout=10)
    if reader.is_alive():  # pragma: no cover — страховка от зависшего наблюдателя
        reader.terminate()
        reader.join(timeout=5)

    collected: list[str] = []
    while not failures.empty():
        collected.append(failures.get())

    assert not collected, (
        f"{family}.{saver_name}: конкурентная запись {target_name} разрушена "
        f"({len(collected)} отказов). Первые: " + " | ".join(collected[:5])
    )

    assert target.exists(), f"{family}.{saver_name}: целевой файл {target_name} не создан"
    raw = target.read_text(encoding="utf-8")
    try:
        final_state = json.loads(raw)
    except json.JSONDecodeError as exc:
        pytest.fail(
            f"{family}.{saver_name}: итоговый {target_name} — не JSON ({exc}); "
            f"первые 120 байт: {raw[:120]!r}"
        )
    assert _payload_is_whole(final_state), (
        f"{family}.{saver_name}: итоговый {target_name} — смесь состояний, "
        "а не одно целиком записанное"
    )

    leftovers = sorted(
        entry.name for entry in review_dir.iterdir() if entry.name.endswith(".tmp")
    )
    assert not leftovers, (
        f"{family}.{saver_name}: остались временные файлы после записи: {leftovers}"
    )
