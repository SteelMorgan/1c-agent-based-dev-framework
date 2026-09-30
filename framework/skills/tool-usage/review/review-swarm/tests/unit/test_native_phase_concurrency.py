"""Cross-family native phase concurrency, retry identity and tombstone cleanup."""
from __future__ import annotations

import json
import threading
import time

import pytest

import adapter_contract as ac
import swarm


def _phase_session():
    participants = [
        {"id": "claude-opus", "state": "active", "review_id": "parent-claude",
         "adapter_session_id": "session-claude", "provider_checkpoint_id": "head-claude",
         "native_fork": {"supported": True, "automation_safe": True}},
        {"id": "codex-gpt", "state": "active", "review_id": "parent-codex",
         "adapter_session_id": "session-codex", "provider_checkpoint_id": "head-codex",
         "native_fork": {"supported": True, "automation_safe": True}},
    ]
    snapshots = {
        f"snap-{p['id']}": {"snapshot_id": f"snap-{p['id']}", "participant_id": p["id"],
                            "parent_review_id": p["review_id"],
                            "parent_session_id": p["adapter_session_id"],
                            "provider_checkpoint_id": p["provider_checkpoint_id"],
                            "snapshot_digest": "sha256:" + ("a" if p["id"].startswith("claude") else "b") * 64,
                            "sealed": True}
        for p in participants
    }
    return {
        "session_id": "swarm-phase", "participants": participants,
        "execution": {"execution_id": "exec-phase", "root_task_id": "root-phase",
                      "fork_group_id": "fork-phase", "parent_sealed": True, "state": "READY"},
        "parent_snapshots": snapshots, "shards": {}, "tasks": {}, "turns": {},
        "findings": [{"finding_id": f"F-{i:03d}", "location": {"path": "src/a.py", "line": i}}
                     for i in range(1, 5)],
    }


def _work(session):
    p1, p2 = session["participants"]
    return [(p1, "F-001", "c1"), (p1, "F-002", "c2"),
            (p2, "F-003", "x1"), (p2, "F-004", "x2")]


def test_phase_retry_reuses_stable_shard_task_and_operation_ids(tmp_path):
    session = _phase_session()
    sdir = tmp_path / ".swarm-sessions" / session["session_id"]
    (sdir / "prompts").mkdir(parents=True)
    (sdir / "session.json").write_text("{}", encoding="utf-8")
    first = swarm._add_native_phase_tasks(sdir, session, "tour2", _work(session))
    identities = {sid: (tuple(session["shards"][sid]["task_ids"]),
                        session["shards"][sid].get("operation_id")) for sid in first}
    assert all(operation_id for _, operation_id in identities.values())
    second = swarm._add_native_phase_tasks(sdir, session, "tour2", _work(session))
    assert second == first
    assert len(session["shards"]) == 2
    assert {sid: (tuple(session["shards"][sid]["task_ids"]),
                  session["shards"][sid].get("operation_id")) for sid in second} == identities


def test_standard_phase_never_splits_one_participant_into_multiple_forks(tmp_path):
    session = _phase_session()
    participant = session["participants"][0]
    session["findings"].append(
        {"finding_id": "F-005", "location": {"path": "src/a.py", "line": 5}}
    )
    sdir = tmp_path / ".swarm-sessions" / session["session_id"]
    (sdir / "prompts").mkdir(parents=True)
    (sdir / "session.json").write_text("{}", encoding="utf-8")
    work = [(participant, f"F-{index:03d}", f"prompt-{index}") for index in range(1, 6)]
    shard_ids = swarm._add_native_phase_tasks(sdir, session, "tour2", work)
    assert len(shard_ids) == 1, "one participant owns one child fork with a serial queue"
    assert len(session["shards"][shard_ids[0]]["task_ids"]) == 5


def test_duplicate_participant_shards_fail_closed_before_provider_call(tmp_path):
    session = _phase_session()
    sdir = tmp_path / ".swarm-sessions" / session["session_id"]
    (sdir / "prompts").mkdir(parents=True)
    (sdir / "session.json").write_text("{}", encoding="utf-8")
    shard_ids = swarm._add_native_phase_tasks(sdir, session, "tour2", _work(session))
    original = session["shards"][shard_ids[0]]
    duplicate_id = original["shard_id"] + "-duplicate"
    session["shards"][duplicate_id] = {
        **original, "shard_id": duplicate_id, "operation_id": "duplicate-operation",
    }

    with pytest.raises(ValueError, match="one ready shard per participant"):
        swarm.run_native_fork_shards(
            session, tmp_path,
            {"claude-opus": "claude.py", "codex-gpt": "codex.py"},
        )


def test_two_participants_parallel_one_fork_each_and_serial_tasks(monkeypatch, tmp_path):
    session = _phase_session()
    sdir = tmp_path / ".swarm-sessions" / session["session_id"]
    (sdir / "prompts").mkdir(parents=True)
    (sdir / "session.json").write_text("{}", encoding="utf-8")
    shard_ids = swarm._add_native_phase_tasks(sdir, session, "tour2", _work(session))
    assert len(shard_ids) == 2
    assert {session["shards"][sid]["participant_id"] for sid in shard_ids} == {
        "claude-opus", "codex-gpt",
    }
    forks = []
    active_parents = set()
    overlapped = threading.Event()
    lock = threading.Lock()
    asks_by_child = {}

    def fork(adapter, parent, child, cwd, **kwargs):
        with lock:
            assert parent not in active_parents, "same parent forked concurrently"
            active_parents.add(parent)
            forks.append((parent, child, kwargs["operation_id"]))
            if len(active_parents) == 2:
                overlapped.set()
        time.sleep(0.03)
        with lock:
            active_parents.remove(parent)
        participant = next(p for p in session["participants"] if p["review_id"] == parent)
        return ac.InvocationResult(ok=True, kind="ok", review_id=child,
            session_id=f"session-{child}", parent_review_id=parent,
            parent_session_id=participant["adapter_session_id"],
            operation_id=kwargs["operation_id"], snapshot_digest=kwargs["snapshot_digest"],
            provider_checkpoint_id=kwargs["provider_checkpoint_id"],
            provider_turn_id=f"turn-{child}-1", provider_fork_ref=f"native-{child}",
            prompt_consumed=True, text="first")

    def ask(adapter, child, prompt, cwd, **kwargs):
        asks_by_child.setdefault(child, []).append(prompt)
        return ac.InvocationResult(ok=True, kind="ok", review_id=child,
            session_id=f"session-{child}", provider_turn_id=f"turn-{child}-{len(asks_by_child[child])+1}",
            text="next")

    monkeypatch.setattr(ac, "fork_participant", fork)
    monkeypatch.setattr(ac, "ask_participant", ask)
    monkeypatch.setattr(ac, "close_participant", lambda *a, **k: True)
    swarm.run_native_fork_shards(
        session, tmp_path, {"claude-opus": "claude.py", "codex-gpt": "codex.py"},
        max_workers=2,
    )
    assert overlapped.is_set()
    assert len(forks) == 2
    assert len({parent for parent, _, _ in forks}) == 2
    assert all(len(session["shards"][sid]["task_ids"]) == 2 for sid in shard_ids)
    assert all(len(asks_by_child.get(session["shards"][sid]["child_review_id"], [])) == 1
               for sid in shard_ids)


def test_operator_close_recognizes_lock_only_tombstone(tmp_path):
    review = tmp_path / ac.REVIEW_ROOT / "child"
    review.mkdir(parents=True)
    (review / "invocation.lock").write_text("", encoding="utf-8")
    status, ok = swarm._close_participant_idempotent(
        "adapter.py", "child", tmp_path, keep_sandbox=False,
    )
    assert ok
    assert "already" in status.lower() or "tombstone" in status.lower()
    assert [path.name for path in review.iterdir()] == ["invocation.lock"]
