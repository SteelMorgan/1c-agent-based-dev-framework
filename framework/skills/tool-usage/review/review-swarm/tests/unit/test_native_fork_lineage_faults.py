"""Fault/reducer contract for native child lineage and clarification resume."""
from __future__ import annotations

import threading

import pytest

import adapter_contract as ac
import swarm


def _session():
    return {
        "session_id": "s1", "execution": {"execution_id": "e1"},
        "shards": {"shard-a": {"shard_id": "shard-a", "snapshot_id": "snap-1",
                                "execution_id": "e1",
                                "root_task_id": "root-1", "fork_group_id": "fork-1",
                                "provider_checkpoint_id": "provider-parent-a",
                                "child_review_id": "child-a",
                                "task_ids": ["task-a"]},
                   "shard-b": {"shard_id": "shard-b", "snapshot_id": "snap-1",
                                "execution_id": "e1",
                                "root_task_id": "root-1", "fork_group_id": "fork-1",
                                "provider_checkpoint_id": "provider-parent-b",
                                "child_review_id": "child-b",
                                "task_ids": ["task-b"]}},
        "tasks": {"task-a": {"task_id": "task-a", "shard_id": "shard-a",
                                      "kind": "clarification", "status": "PENDING"},
                  "task-b": {"task_id": "task-b", "shard_id": "shard-b",
                                      "kind": "clarification", "status": "PENDING"}},
        "turns": {"turn-a": {"turn_id": "turn-a", "task_id": "task-a",
                               "shard_id": "shard-a", "provider_turn_id": "provider-turn-77"}},
        "lineage": [],
    }


def test_full_fr09_lineage_and_reverse_provider_turn_lookup(tmp_path):
    session = _session()
    event = {"event": "turn_completed", "execution_id": "e1",
             "root_task_id": "root-1", "fork_group_id": "fork-1",
             "snapshot_id": "snap-1", "shard_id": "shard-a",
             "shard_task_id": "task-a", "task_id": "task-a",
             "turn_id": "turn-a", "provider_turn_id": "provider-turn-77"}
    swarm.append_lineage_event(tmp_path, session, event)
    found = swarm.query_lineage_by_provider_turn(tmp_path, "provider-turn-77")
    assert all(found[key] == value for key, value in event.items())
    assert found["provider_checkpoint_id"] == "provider-parent-a"
    assert swarm.query_lineage_by_provider_turn(tmp_path, "absent") is None


def test_clarification_resumes_same_child_exactly_once_and_rejects_cross_shard(monkeypatch):
    session = _session()
    calls = []
    monkeypatch.setattr(ac, "ask_participant", lambda adapter, review_id, question, cwd, **kw:
                        calls.append(review_id) or ac.InvocationResult(ok=True, kind="ok",
                        review_id=review_id, session_id="provider-turn-2", text="answer"))
    result = swarm.resume_shard_clarification(
        session, "task-a", "clar-op-1", "prompt", {"shard-a": "adapter.py"},
    )
    assert result["shard_id"] == "shard-a"
    assert calls == ["child-a"]
    replay = swarm.resume_shard_clarification(
        session, "task-a", "clar-op-1", "prompt", {"shard-a": "adapter.py"},
    )
    assert replay == result and calls == ["child-a"]
    with pytest.raises(ValueError, match="cross-shard|shard"):
        swarm.resume_shard_clarification(
            session, "task-b", "clar-op-2", "prompt", {"shard-a": "adapter.py"},
        )


def test_concurrent_reducer_preserves_all_turns(monkeypatch):
    session = _session()
    lock = threading.Lock()
    monkeypatch.setattr(ac, "ask_participant", lambda *a, **k:
                        ac.InvocationResult(ok=True, kind="ok", session_id="pt", text="ok"))
    errors = []
    def reduce(index):
        try:
            swarm.project_native_child_turn(
                session, "shard-a", "task-a", f"turn-{index}", f"provider-{index}",
                {"verdict": "upheld"}, lock=lock,
            )
        except Exception as exc:
            errors.append(exc)
    threads = [threading.Thread(target=reduce, args=(i,)) for i in range(24)]
    for thread in threads: thread.start()
    for thread in threads: thread.join()
    assert not errors
    assert len(session["turns"]) == 25  # исходный turn-a + 24 concurrent projections
    assert len({turn["provider_turn_id"] for turn in session["turns"].values()}) == 25


def test_provider_committed_turn_retries_projection_without_duplicate_ask(monkeypatch):
    session = _session()
    asks = []
    monkeypatch.setattr(ac, "ask_participant", lambda *a, **k: asks.append(a) or pytest.fail(
        "retry after provider commit must reconcile/project, not ask again"))
    first = swarm.project_native_child_turn(
        session, "shard-a", "task-a", "turn-crash-window", "provider-committed-1",
        {"verdict": "upheld"},
    )
    retry = swarm.project_native_child_turn(
        session, "shard-a", "task-a", "turn-crash-window", "provider-committed-1",
        {"verdict": "upheld"},
    )
    assert retry == first
    assert asks == []
    assert len([turn for turn in session["turns"].values()
                if turn["provider_turn_id"] == "provider-committed-1"]) == 1


@pytest.mark.parametrize("phase", ["tour2", "tour3", "tour4"])
def test_validation_phase_materializes_child_shard_and_projects_response(
    monkeypatch, tmp_path, phase,
):
    session = _session()
    session.update({
        "participants": [{"id": "claude-opus", "state": "active",
                          "review_id": "parent-review", "adapter_session_id": "parent-session",
                          "provider_checkpoint_id": "provider-head",
                          "native_fork": {"supported": True, "automation_safe": True}}],
        "parent_snapshots": {"snap-1": {
            "snapshot_id": "snap-1", "participant_id": "claude-opus",
            "parent_review_id": "parent-review", "parent_session_id": "parent-session",
            "provider_checkpoint_id": "provider-head",
            "snapshot_digest": "sha256:" + "c" * 64, "sealed": True,
        }},
        "findings": [{"finding_id": "F-001", "location": {"path": "src/a.py", "line": 1}}],
    })
    session["execution"].update({"root_task_id": "root-1", "fork_group_id": "fork-1",
                                 "state": "READY", "parent_sealed": True})
    session["shards"] = {}
    session["tasks"] = {}
    session["turns"] = {}
    sdir = tmp_path / ".swarm-sessions" / session["session_id"]
    (sdir / "prompts").mkdir(parents=True)
    (sdir / "session.json").write_text("{}", encoding="utf-8")
    participant = session["participants"][0]
    shard_ids = swarm._add_native_phase_tasks(
        sdir, session, phase, [(participant, "F-001", f"{phase} prompt")],
    )
    assert len(shard_ids) == 1
    shard = session["shards"][shard_ids[0]]
    task = session["tasks"][shard["task_ids"][0]]
    assert task["phase"] == phase and task["finding_id"] == "F-001"

    monkeypatch.setattr(ac, "fork_participant", lambda *a, **k: ac.InvocationResult(
        ok=True, kind="ok", review_id=a[2], session_id="child-session",
        parent_review_id="parent-review", parent_session_id="parent-session",
        operation_id=k["operation_id"], snapshot_digest=k["snapshot_digest"],
        provider_checkpoint_id="provider-head", provider_turn_id="child-turn",
        provider_fork_ref="native-child", prompt_consumed=True,
        text=f"parsed-{phase}-result",
    ))
    monkeypatch.setattr(ac, "close_participant", lambda *a, **k: True)
    swarm.run_native_fork_shards(
        session, tmp_path, {"claude-opus": "adapter.py"}, max_workers=1,
    )
    shard = session["shards"][shard_ids[0]]
    assert shard["child_review_id"], shard
    turn = next(iter(session["turns"].values()))
    assert turn["response"] == f"parsed-{phase}-result"
    assert turn["task_id"] == task["task_id"]
