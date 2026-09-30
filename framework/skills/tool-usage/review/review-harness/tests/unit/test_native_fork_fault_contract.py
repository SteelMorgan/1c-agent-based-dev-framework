"""Fault-contract for exactly-once native fork and provider checkpoint identity."""
from __future__ import annotations

import json
import subprocess
import sys

import pytest

import adapter_contract as ac

SNAPSHOT_DIGEST = "sha256:" + "a" * 64


def _parent(tmp_path):
    root = tmp_path / ac.REVIEW_ROOT / "parent"
    (root / "workspace").mkdir(parents=True)
    (root / "review.json").write_text(json.dumps({
        "review_id": "parent", "session_id": "provider-parent",
        "provider_checkpoint_id": "provider-checkpoint-7",
        "workspace_path": str(root / "workspace"), "status": "open",
    }), encoding="utf-8")


def test_fork_command_separates_operation_checkpoint_and_snapshot(monkeypatch, tmp_path):
    _parent(tmp_path)
    calls = []
    def run(cmd, **kwargs):
        calls.append(cmd)
        return subprocess.CompletedProcess(cmd, 0, (
            "review_id: child\nsession_id: provider-child\nparent_review_id: parent\n"
            "parent_session_id: provider-parent\nprovider_fork_ref: provider-child\n"
            "provider_checkpoint_id: provider-checkpoint-7\n"
            "provider_turn_id: provider-child-turn\n"
            f"snapshot_digest: {SNAPSHOT_DIGEST}\noperation_id: exec-1:shard-001\n"
            "prompt_consumed: false\nworkspace: /tmp/child\n"), "")
    monkeypatch.setattr(subprocess, "run", run)
    result = ac.fork_participant(
        "adapter.py", "parent", "child", tmp_path,
        operation_id="exec-1:shard-001",
        provider_checkpoint_id="provider-checkpoint-7",
        snapshot_digest=SNAPSHOT_DIGEST, timeout_sec=9,
    )
    assert result.ok, result.error
    assert calls == [[
        sys.executable, "adapter.py", "fork", "parent", "--child-review-id", "child",
        "--operation-id", "exec-1:shard-001",
        "--provider-checkpoint-id", "provider-checkpoint-7",
        "--snapshot-digest", SNAPSHOT_DIGEST, "--timeout-sec", "9",
    ]]


def test_retry_reconciles_same_operation_without_second_provider_fork(monkeypatch, tmp_path):
    _parent(tmp_path)
    child = tmp_path / ac.REVIEW_ROOT / "child"
    child.mkdir()
    (child / "review.json").write_text(json.dumps({
        "review_id": "child", "session_id": "provider-child",
        "parent_review_id": "parent", "parent_session_id": "provider-parent",
        "provider_checkpoint_id": "provider-checkpoint-7",
        "snapshot_digest": SNAPSHOT_DIGEST, "operation_id": "exec-1:shard-001",
        "provider_fork_ref": "provider-child", "prompt_consumed": False,
        "provider_turn_id": "provider-checkpoint-7",
    }), encoding="utf-8")
    monkeypatch.setattr(subprocess, "run", lambda *a, **k: pytest.fail("provider called twice"))
    result = ac.fork_participant(
        "adapter.py", "parent", "child", tmp_path,
        operation_id="exec-1:shard-001", provider_checkpoint_id="provider-checkpoint-7",
        snapshot_digest=SNAPSHOT_DIGEST,
    )
    assert result.ok and result.session_id == "provider-child"


def test_malformed_success_triggers_orphan_cleanup(monkeypatch, tmp_path):
    _parent(tmp_path)
    calls = []
    def run(cmd, **kwargs):
        calls.append(cmd)
        if "fork" in cmd:
            child = tmp_path / ac.REVIEW_ROOT / "child"
            child.mkdir(exist_ok=True)
            (child / "review.json").write_text(json.dumps({
                "review_id": "child", "session_id": "provider-orphan",
                "parent_review_id": "parent", "operation_id": "exec-1:shard-001",
            }), encoding="utf-8")
            return subprocess.CompletedProcess(cmd, 0, "review_id: child\n", "")
        return subprocess.CompletedProcess(cmd, 0, "closed", "")
    monkeypatch.setattr(subprocess, "run", run)
    result = ac.fork_participant(
        "adapter.py", "parent", "child", tmp_path,
        operation_id="exec-1:shard-001", provider_checkpoint_id="provider-checkpoint-7",
        snapshot_digest=SNAPSHOT_DIGEST,
    )
    assert not result.ok
    assert any("close" in cmd for cmd in calls), "malformed child must be cleaned immediately"


def test_lock_only_child_tombstone_is_reusable_but_live_child_is_not(monkeypatch, tmp_path):
    _parent(tmp_path)
    child = tmp_path / ac.REVIEW_ROOT / "child"
    child.mkdir()
    (child / "invocation.lock").write_text("", encoding="utf-8")
    calls = []
    stdout = (
        "review_id: child\nsession_id: provider-child\nparent_review_id: parent\n"
        "parent_session_id: provider-parent\nprovider_fork_ref: provider-child\n"
        "provider_checkpoint_id: provider-checkpoint-7\nprovider_turn_id: child-turn\n"
        f"snapshot_digest: {SNAPSHOT_DIGEST}\noperation_id: exec-1:shard-001\n"
        "prompt_consumed: false\nworkspace: /tmp/child\n"
    )
    monkeypatch.setattr(subprocess, "run", lambda cmd, **kwargs: calls.append(cmd) or
                        subprocess.CompletedProcess(cmd, 0, stdout, ""))
    result = ac.fork_participant(
        "adapter.py", "parent", "child", tmp_path,
        operation_id="exec-1:shard-001", provider_checkpoint_id="provider-checkpoint-7",
        snapshot_digest=SNAPSHOT_DIGEST,
    )
    assert result.ok, result.error
    assert sum("fork" in cmd for cmd in calls) == 1
