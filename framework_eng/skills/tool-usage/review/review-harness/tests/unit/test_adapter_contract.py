"""HU-C — новые обязательные элементы контракта адаптеров harness (RVSW-01, T-03).

FR-01: `sync` — обязательный элемент контракта (sync_participant); материализация
diff файлом внутри sandbox участника (materialize_diff, FR-01 п.к / FR-04).
FR-13: канонический доступ к полям активности last_activity_at/last_heartbeat_at —
новый таймстамп НЕ вводится.
TD §6.4: PARALLEL_CAP = 8 — потолок параллелизма волн; run_wave принимает cap.

Процессные проверки subcommand sync / heartbeat-каденса — contract-слой T-04
(HC-10..HC-12); здесь — unit-уровень без процессов.
"""
from __future__ import annotations

import json
import subprocess
import sys
import threading
import time
from pathlib import Path

import pytest

import adapter_contract
import invocation_lock


def _make_sandbox(cwd: Path, review_id: str) -> Path:
    """Минимальный sandbox участника: review.json с workspace_path + workspace."""
    review_dir = cwd / adapter_contract.REVIEW_ROOT / review_id
    workspace = review_dir / "workspace"
    workspace.mkdir(parents=True)
    (review_dir / "review.json").write_text(
        json.dumps({"review_id": review_id, "workspace_path": str(workspace)}),
        encoding="utf-8",
    )
    return workspace


@pytest.mark.parametrize("review_id", ["", ".", "..", "../../x", "/tmp/x", "a/b", "a\\b", "bad\nvalue"])
def test_start_rejects_unsafe_review_id_before_subprocess(monkeypatch, tmp_path, review_id):
    """Harness validation precedes every adapter process and filesystem mutation."""
    called = False

    def forbidden_run(*args, **kwargs):
        nonlocal called
        called = True
        raise AssertionError("adapter subprocess must not start")

    monkeypatch.setattr(adapter_contract, "_run_adapter", forbidden_run)
    with pytest.raises(ValueError, match="review_id"):
        adapter_contract.start_participant(
            "adapter.py", "q", [], tmp_path, review_id=review_id)
    assert not called
    assert not (tmp_path / adapter_contract.REVIEW_ROOT).exists()


def test_review_dir_for_id_rejects_symlink_root_and_child(tmp_path):
    outside = tmp_path / "outside"
    outside.mkdir()
    root = tmp_path / ".review-sandboxes"
    root.symlink_to(outside, target_is_directory=True)
    with pytest.raises(ValueError, match="root"):
        invocation_lock.review_dir_for_id(root, "review-abc123")
    root.unlink()
    root.mkdir()
    (root / "review-abc123").symlink_to(outside, target_is_directory=True)
    with pytest.raises(ValueError, match="directory"):
        invocation_lock.review_dir_for_id(root, "review-abc123")


def test_swarm_style_generated_review_id_is_safe(tmp_path):
    review_id = "review-0123456789ab"
    assert invocation_lock.review_dir_for_id(
        tmp_path / ".review-sandboxes", review_id
    ) == tmp_path / ".review-sandboxes" / review_id


# ---------- sync_participant (FR-01 п.б) ----------

def test_sync_participant_command(monkeypatch, tmp_path):
    """sync_participant — обёртка обязательного subcommand sync: argv, код возврата."""
    calls = []

    def fake_run(cmd, **kwargs):
        calls.append(cmd)
        return subprocess.CompletedProcess(cmd, 0, "", "")

    monkeypatch.setattr(subprocess, "run", fake_run)
    assert adapter_contract.sync_participant("adapter.py", "rid-1", tmp_path) is True
    assert calls == [[sys.executable, "adapter.py", "sync", "rid-1"]]

    def failing_run(cmd, **kwargs):
        return subprocess.CompletedProcess(cmd, 1, "", "boom")

    monkeypatch.setattr(subprocess, "run", failing_run)
    assert adapter_contract.sync_participant("adapter.py", "rid-1", tmp_path) is False


# ---------- materialize_diff (FR-01 п.к, FR-04) ----------

def test_materialize_diff_writes_into_workspace(tmp_path):
    """Diff материализуется файлом внутри sandbox участника; путь возвращается."""
    workspace = _make_sandbox(tmp_path, "rid-1")
    diff = "diff --git a/x.py b/x.py\n+line\n"
    path = adapter_contract.materialize_diff(tmp_path, "rid-1", diff)
    assert path == workspace / "review.diff"
    assert path.read_text(encoding="utf-8") == diff
    # sandbox-файл доступен как обычный файл относительно workspace (FR-04)
    assert path.parent == workspace


def test_materialize_diff_custom_name_and_no_meta(tmp_path):
    """Имя файла — параметром; без meta участника — fail-closed."""
    workspace = _make_sandbox(tmp_path, "rid-2")
    path = adapter_contract.materialize_diff(tmp_path, "rid-2", "d", filename="fix.diff")
    assert path == workspace / "fix.diff"
    with pytest.raises(RuntimeError, match="workspace"):
        adapter_contract.materialize_diff(tmp_path, "absent-rid", "d")


# ---------- Канонические поля активности (FR-13) ----------

def test_activity_fields_canonical_names():
    """Ровно два канонических поля: last_activity_at/last_heartbeat_at.
    Пятый таймстамп не вводится (FR-13)."""
    assert adapter_contract.ACTIVITY_FIELDS == ("last_activity_at", "last_heartbeat_at")


def test_read_participant_activity(tmp_path):
    """Канонический доступ: оба поля из runtime.json; отсутствующий файл → None."""
    review_dir = tmp_path / adapter_contract.REVIEW_ROOT / "rid-1"
    review_dir.mkdir(parents=True)
    (review_dir / "runtime.json").write_text(
        json.dumps({
            "phase": "ask",
            "last_activity_at": "2026-07-29T10:00:01+00:00",
            "last_heartbeat_at": "2026-07-29T10:00:02+00:00",
        }),
        encoding="utf-8",
    )
    activity = adapter_contract.read_participant_activity(tmp_path, "rid-1")
    assert activity == {
        "last_activity_at": "2026-07-29T10:00:01+00:00",
        "last_heartbeat_at": "2026-07-29T10:00:02+00:00",
    }
    missing = adapter_contract.read_participant_activity(tmp_path, "absent")
    assert missing == {"last_activity_at": None, "last_heartbeat_at": None}


# ---------- Потолок параллелизма волн (TD §6.4) ----------

def test_parallel_cap_and_wave_workers():
    """PARALLEL_CAP = 8 (TD §6.4); run_wave с cap исполняет все задачи."""
    assert adapter_contract.PARALLEL_CAP == 8
    results = adapter_contract.run_wave([lambda i=i: i for i in range(5)], max_workers=2)
    assert sorted(results) == list(range(5))
    # дефолт без cap — поведение как раньше (волна = все задачи разом)
    results = adapter_contract.run_wave([lambda i=i: i * 2 for i in range(3)])
    assert sorted(results) == [0, 2, 4]


def test_run_wave_rejects_task_keys_not_aligned_with_tasks():
    """Keyed scheduling fail-closed: ключ требуется ровно для каждой task."""
    with pytest.raises(ValueError, match="task_keys"):
        adapter_contract.run_wave([lambda: "only"], task_keys=[])


def test_keyed_wave_slots_bound_accounts_for_cap_and_lane_depth():
    """Bound учитывает и группы запуска сверх cap, и same-key FIFO tail."""
    assert adapter_contract.keyed_wave_slots_bound([], max_workers=8) == 0
    assert adapter_contract.keyed_wave_slots_bound(list(range(8)), 8) == 1
    assert adapter_contract.keyed_wave_slots_bound(list(range(9)), 8) == 2
    assert adapter_contract.keyed_wave_slots_bound(["a", "a", "b"], 8) == 2
    with pytest.raises(ValueError, match="max_workers"):
        adapter_contract.keyed_wave_slots_bound(["a"], 0)


def test_run_wave_keyed_scheduling_serializes_one_session_without_blocking_others():
    """Keyed wave: один key занимает ровно один invocation slot.

    `task_keys` — минимальный общий контракт планировщика: отдельная
    последовательность hashable ключей, выровненная с ``tasks``. Он не знает
    ни о tour, ни о finding; caller сопоставляет ключ participant/review_id.
    Пока первый ход ключа ``participant-a/review-1`` не завершён,
    второй callable этого же ключа не вызывается и, следовательно, не начинает
    свой invocation timeout. Независимый ключ должен стартовать одновременно,
    а итог сохраняет входной порядок, даже если завершился раньше.
    """
    first_started = threading.Event()
    release_first = threading.Event()
    first_finished = threading.Event()
    independent_started = threading.Event()
    queued_invocation_started = threading.Event()
    outcome = {}

    def first_invocation():
        first_started.set()
        assert release_first.wait(timeout=2), "test did not release the first slot"
        first_finished.set()
        return "first"

    def queued_invocation():
        # Вход в callable означает запуск CLI и его invocation-timeout budget.
        # До освобождения lane этот код не должен быть вызван.
        queued_invocation_started.set()
        assert first_finished.is_set(), "same keyed invocation overlapped its predecessor"
        return "queued"

    def independent_invocation():
        independent_started.set()
        return "independent"

    tasks = [
        first_invocation,
        queued_invocation,
        independent_invocation,
    ]
    task_keys = [
        ("participant-a", "review-1"),
        ("participant-a", "review-1"),
        ("participant-b", "review-2"),
    ]

    def invoke_wave():
        try:
            outcome["results"] = adapter_contract.run_wave(
                tasks, task_keys=task_keys, max_workers=2,
            )
        except BaseException as exc:  # проверяется в потоке самого теста
            outcome["error"] = exc

    runner = threading.Thread(target=invoke_wave)
    runner.start()
    try:
        # Ошибка scheduler/API должна быть видна прямо, а не маскироваться
        # последующей start-timeout assertion.
        runner.join(timeout=0.05)
        assert "error" not in outcome, outcome.get("error")
        assert first_started.wait(timeout=1), "first keyed invocation did not start"
        assert independent_started.wait(timeout=1), "independent key was starved"
        assert not queued_invocation_started.wait(timeout=0.1), (
            "queued keyed invocation started before its session slot was free"
        )
    finally:
        release_first.set()
        runner.join(timeout=3)

    assert not runner.is_alive(), "keyed wave did not finish after releasing its slot"
    assert "error" not in outcome, outcome.get("error")
    assert outcome["results"] == ["first", "queued", "independent"]


def test_run_wave_timeout_returns_only_after_running_tasks_are_quiescent():
    """Deadline не обещает невозможное thread-kill и не оставляет мутаций.

    После наблюдаемого TimeoutError уже стартовавшийся callable завершён, а
    хвост той же lane не запускался. Значит caller может сразу удалять session
    artifacts без гонки с фоновым worker.
    """
    running_finished = threading.Event()
    queued_started = threading.Event()

    def overrunning_invocation():
        time.sleep(0.12)
        running_finished.set()
        return "late"

    def queued_invocation():
        queued_started.set()
        return "must-not-run"

    started_at = time.monotonic()
    with pytest.raises(TimeoutError, match="после quiescence"):
        adapter_contract.run_wave(
            [overrunning_invocation, queued_invocation],
            wave_timeout_sec=0.03,
            max_workers=1,
            task_keys=["same-review", "same-review"],
        )
    elapsed = time.monotonic() - started_at

    assert elapsed >= 0.10, "TimeoutError вернулся до quiescence running callable"
    assert running_finished.is_set()
    assert not queued_started.is_set(), "scheduler запустил lane tail после deadline"


def test_run_wave_does_not_submit_lane_tail_when_deadline_crosses_after_completion(
        monkeypatch):
    """Deadline повторно проверяется между наблюдением head и admission tail."""
    clock = iter([0.0, 0.0, 2.0])
    monkeypatch.setattr(adapter_contract, "_monotonic", lambda: next(clock))
    tail_started = threading.Event()

    def tail():
        tail_started.set()

    with pytest.raises(TimeoutError, match="deadline.*после quiescence"):
        adapter_contract.run_wave(
            [lambda: "head", tail],
            wave_timeout_sec=1.0,
            max_workers=1,
            task_keys=["same-review", "same-review"],
        )

    assert not tail_started.is_set()


def test_run_wave_timeout_waits_for_all_started_keys_before_returning():
    """Quiescence включает все одновременно стартовавшие независимые keys."""
    finished = [threading.Event(), threading.Event()]

    def task(index: int):
        time.sleep(0.08 + index * 0.03)
        finished[index].set()

    with pytest.raises(TimeoutError, match="deadline"):
        adapter_contract.run_wave(
            [lambda: task(0), lambda: task(1)],
            wave_timeout_sec=0.02,
            max_workers=2,
            task_keys=["review-a", "review-b"],
        )

    assert all(event.is_set() for event in finished)
