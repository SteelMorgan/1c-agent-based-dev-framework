"""Operator-facing native fork restart/query/cleanup contracts."""
from __future__ import annotations

from argparse import Namespace

import pytest

import adapter_contract as ac
import swarm


def test_lineage_query_cli_is_operator_accessible():
    args = swarm.build_parser().parse_args([
        "lineage-query", "swarm-1", "--provider-turn-id", "provider-turn-7",
    ])
    assert args.cmd == "lineage-query"
    assert args.provider_turn_id == "provider-turn-7"
    assert args.func is swarm.cmd_lineage_query


def test_clarification_cli_has_durable_idempotency_identity(tmp_path):
    prompt = tmp_path / "clarification.md"
    prompt.write_text("clarify evidence", encoding="utf-8")
    args = swarm.build_parser().parse_args([
        "clarify", "swarm-1", "--shard-task-id", "shard-1-task-1",
        "--idempotency-key", "clar-op-77", "--prompt-file", str(prompt),
    ])
    assert args.func is swarm.cmd_clarify
    assert args.shard_task_id == "shard-1-task-1"
    assert args.idempotency_key == "clar-op-77"


def test_close_failure_is_durable_and_returns_cleanup_exit(monkeypatch, tmp_path):
    session = {
        "session_id": "swarm-1", "tier": "swarm", "state": "TOUR2",
        "registry": "registry.yaml",
        "participants": [{"id": "claude-opus", "review_id": "parent-review"}],
        "shards": {"shard-1": {"shard_id": "shard-1", "participant_id": "claude-opus",
                                      "child_review_id": "child-review", "status": "COMPLETED"}},
        "cleanup": {"participants_closed": {}, "status": "pending"},
    }
    saved = []
    monkeypatch.chdir(tmp_path)
    monkeypatch.setattr(swarm, "_load_session_checked", lambda sid: (tmp_path / sid, session))
    monkeypatch.setattr(swarm, "load_registry_checked", lambda path: {
        "participants": [{"id": "claude-opus", "adapter": "adapter.py"}],
    })
    monkeypatch.setattr(swarm, "save_session", lambda sdir, value: saved.append(value.copy()))
    monkeypatch.setattr(swarm, "_close_participant_idempotent",
                        lambda *a, **k: ("close failed: provider unavailable", False))
    code = swarm.cmd_close(Namespace(
        session_id="swarm-1", registry=None, keep=False, keep_reason=None,
    ))
    assert code == swarm.EXIT_CLEANUP_FAILED
    assert session["cleanup"]["status"] == "failed"
    assert "native-child:shard-1" in session["cleanup"]["failed_participants"]
    assert saved, "failed cleanup state must survive process restart"


def test_clarification_command_survives_reload_and_replays_idempotently(
    monkeypatch, tmp_path,
):
    sdir = tmp_path / ".swarm-sessions" / "swarm-1"
    sdir.mkdir(parents=True)
    session = {
        "session_id": "swarm-1", "registry": "registry.yaml",
        "shards": {"shard-1": {"shard_id": "shard-1", "participant_id": "claude-opus",
                                      "execution_id": "exec-1", "snapshot_id": "snap-1",
                                      "root_task_id": "root-1", "fork_group_id": "fork-1",
                                      "provider_checkpoint_id": "provider-parent",
                                      "child_review_id": "child-1", "task_ids": ["task-1"]}},
        "tasks": {"task-1": {"task_id": "task-1", "shard_id": "shard-1",
                                     "kind": "review-phase", "status": "COMPLETED"}},
        "turns": {"turn-source": {"turn_id": "turn-source", "task_id": "task-1",
                                          "shard_id": "shard-1",
                                          "provider_turn_id": "provider-source",
                                          "status": "COMPLETED"}},
    }
    swarm.save_session(sdir, session)
    prompt = tmp_path / "clarification.md"
    prompt.write_text("clarify", encoding="utf-8")
    monkeypatch.chdir(tmp_path)
    monkeypatch.setattr(swarm, "load_registry_checked", lambda path: {
        "participants": [{"id": "claude-opus", "adapter": "adapter.py"}],
    })
    asks = []
    monkeypatch.setattr(ac, "ask_participant", lambda *a, **k: asks.append(a[1]) or
                        ac.InvocationResult(ok=True, kind="ok", review_id=a[1],
                        session_id="child-session", provider_turn_id="provider-turn-2",
                        text="clarified"))
    args = Namespace(session_id="swarm-1", shard_task_id="task-1",
                     idempotency_key="clar-op-1", prompt_file=str(prompt), registry=None)
    assert swarm.cmd_clarify(args) == 0
    assert swarm.cmd_clarify(args) == 0  # fresh load from session.json inside command
    assert asks == ["child-1"]
    persisted = swarm.load_session(sdir)
    assert persisted["clarification_results"]["clar-op-1"]["provider_turn_id"] == "provider-turn-2"
    clarification_tasks = [task for task in persisted["tasks"].values()
                           if task.get("kind") == "clarification"]
    assert len(clarification_tasks) == 1
    assert clarification_tasks[0]["parent_task_id"] == "task-1"
    assert clarification_tasks[0]["shard_id"] == "shard-1"
