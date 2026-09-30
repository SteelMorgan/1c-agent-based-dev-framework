"""RED-контракт native fork и параллельного shard runner.

Фиксирует утверждённые границы: только provider-native fork, отдельный child
session, параллелизм между shards и последовательность задач внутри shard.
Production-реализация намеренно появится после этих тестов.
"""
from __future__ import annotations

import subprocess
import sys
import threading
import time

import pytest

import adapter_contract as ac


def test_fork_participant_builds_native_fork_command(monkeypatch, tmp_path):
    calls: list[list[str]] = []
    parent_dir = tmp_path / ac.REVIEW_ROOT / "parent-1"
    parent_dir.mkdir(parents=True)
    (parent_dir / "review.json").write_text(
        '{"review_id":"parent-1","session_id":"parent-session",'
        '"provider_checkpoint_id":"provider-parent-turn"}',
        encoding="utf-8",
    )

    def fake_run(cmd, **kwargs):
        calls.append(cmd)
        return subprocess.CompletedProcess(
            cmd, 0,
            "review_id: child-1\nsession_id: child-session\n"
            "parent_review_id: parent-1\nparent_session_id: parent-session\n"
            "operation_id: exec-1:shard-1\n"
            "snapshot_digest: sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n"
            "provider_checkpoint_id: provider-parent-turn\nprovider_turn_id: provider-child-turn\n"
            "workspace: /tmp/child\nprompt_consumed: false\n",
            "",
        )

    monkeypatch.setattr(subprocess, "run", fake_run)
    result = ac.fork_participant(
        "adapter.py", "parent-1", "child-1", tmp_path,
        operation_id="exec-1:shard-1",
        snapshot_digest="sha256:" + "a" * 64,
        provider_checkpoint_id="provider-parent-turn", timeout_sec=17,
    )

    assert result.ok
    assert result.review_id == "child-1"
    assert result.session_id == "child-session"
    assert result.prompt_consumed is False
    assert calls == [[
        sys.executable, "adapter.py", "fork", "parent-1",
        "--child-review-id", "child-1",
        "--operation-id", "exec-1:shard-1",
        "--provider-checkpoint-id", "provider-parent-turn",
        "--snapshot-digest", "sha256:" + "a" * 64,
        "--timeout-sec", "17",
    ]]


def test_fork_participant_rejects_missing_or_reused_child_session(monkeypatch, tmp_path):
    """Fork без доказанного нового provider session id обязан fail-closed."""
    parent_session = "provider-parent-session"

    def fake_meta(cwd, review_id):
        return {"session_id": parent_session, "provider_checkpoint_id": "parent-turn"}

    monkeypatch.setattr(ac, "_read_meta", fake_meta)
    monkeypatch.setattr(
        subprocess,
        "run",
        lambda cmd, **kwargs: subprocess.CompletedProcess(
            cmd, 0,
            f"review_id: child-1\nsession_id: {parent_session}\n"
            "parent_review_id: parent-1\n"
            f"parent_session_id: {parent_session}\nworkspace: /tmp/child\n"
            "operation_id: exec-1:shard-1\n"
            "snapshot_digest: sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n"
            "provider_checkpoint_id: parent-turn\nprovider_turn_id: child-turn\n"
            "prompt_consumed: false\n",
            "",
        ),
    )

    result = ac.fork_participant(
        "adapter.py", "parent-1", "child-1", tmp_path, timeout_sec=17,
        operation_id="exec-1:shard-1", snapshot_digest="sha256:" + "a" * 64,
        provider_checkpoint_id="parent-turn",
    )
    assert not result.ok
    assert result.kind == "error"
    assert "session" in (result.error or "").lower()


@pytest.mark.parametrize("marker", [None, "yes", "1", "FALSE"])
def test_fork_participant_requires_literal_prompt_consumed_marker(
    monkeypatch, tmp_path, marker,
):
    parent_dir = tmp_path / ac.REVIEW_ROOT / "parent-1"
    parent_dir.mkdir(parents=True)
    (parent_dir / "review.json").write_text(
        '{"review_id":"parent-1","session_id":"parent-session",'
        '"provider_checkpoint_id":"parent-turn"}', encoding="utf-8",
    )
    marker_line = "" if marker is None else f"prompt_consumed: {marker}\n"
    stdout = (
        "review_id: child-1\nsession_id: child-session\n"
        "parent_review_id: parent-1\nparent_session_id: parent-session\n"
        "operation_id: exec-1:shard-1\n"
        "snapshot_digest: sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n"
        "provider_checkpoint_id: parent-turn\nprovider_turn_id: child-turn\n"
        f"workspace: /tmp/child\n{marker_line}"
    )
    monkeypatch.setattr(
        subprocess, "run",
        lambda cmd, **kwargs: subprocess.CompletedProcess(cmd, 0, stdout, ""),
    )
    result = ac.fork_participant(
        "adapter.py", "parent-1", "child-1", tmp_path, timeout_sec=17,
        operation_id="exec-1:shard-1", snapshot_digest="sha256:" + "a" * 64,
        provider_checkpoint_id="parent-turn",
    )
    assert not result.ok
    assert result.kind == "error"
    assert "prompt_consumed" in (result.error or "")


def test_run_shards_overlaps_shards_but_not_tasks_inside_a_shard():
    lock = threading.Lock()
    running_by_shard = {"A": 0, "B": 0}
    max_by_shard = {"A": 0, "B": 0}
    running_shards: set[str] = set()
    cross_shard_overlap = threading.Event()

    def shard(shard_id: str):
        completed = []
        for task_id in ("1", "2", "3"):
            with lock:
                running_by_shard[shard_id] += 1
                max_by_shard[shard_id] = max(
                    max_by_shard[shard_id], running_by_shard[shard_id]
                )
                running_shards.add(shard_id)
                if len(running_shards) == 2:
                    cross_shard_overlap.set()
            time.sleep(0.03)
            with lock:
                running_by_shard[shard_id] -= 1
                if running_by_shard[shard_id] == 0:
                    running_shards.discard(shard_id)
            completed.append(f"{shard_id}-{task_id}")
        return completed

    results = ac.run_shards(
        [lambda: shard("A"), lambda: shard("B")], max_workers=2
    )

    assert results == [
        ["A-1", "A-2", "A-3"],
        ["B-1", "B-2", "B-3"],
    ]
    assert cross_shard_overlap.is_set(), "разные shards должны реально перекрываться"
    assert max_by_shard == {"A": 1, "B": 1}


def test_run_shards_propagates_failure_fail_closed():
    def broken_shard():
        raise RuntimeError("native fork unavailable")

    with pytest.raises(RuntimeError, match="native fork unavailable"):
        ac.run_shards([broken_shard], max_workers=1)


@pytest.mark.parametrize("max_workers", [0, -1])
def test_run_shards_rejects_invalid_worker_budget(max_workers):
    with pytest.raises(ValueError, match="max_workers"):
        ac.run_shards([lambda: None], max_workers=max_workers)
