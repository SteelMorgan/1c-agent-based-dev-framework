"""Exactly-once ask/reconcile contract at harness boundary."""
from __future__ import annotations

import json
import subprocess
import sys

import adapter_contract as ac


def _completed(operation="op-1", parent="parent-turn"):
    return {"state": "completed", "evidence_complete": True,
            "operation_id": operation, "provider_turn_id": "child-turn",
            "parent_provider_turn_id": parent, "session_id": "session-1",
            "text": "cached"}


def test_reconcile_argv_and_completed_cached_result(monkeypatch, tmp_path):
    calls = []
    monkeypatch.setattr(subprocess, "run", lambda cmd, **kwargs: calls.append(cmd) or
                        subprocess.CompletedProcess(cmd, 0, json.dumps(_completed()), ""))
    result = ac.reconcile_participant(
        "adapter.py", "review-1", "op-1", tmp_path,
        parent_provider_turn_id="parent-turn",
    )
    assert result.ok and result.provider_turn_id == "child-turn" and result.text == "cached"
    assert calls == [[sys.executable, "adapter.py", "reconcile", "review-1",
                      "--operation-id", "op-1", "--parent-provider-turn-id",
                      "parent-turn", "--json"]]


def test_reconcile_absent_requires_complete_evidence(monkeypatch, tmp_path):
    for evidence, expected in ((True, "absent"), (False, "ambiguous")):
        payload = {"state": "absent", "evidence_complete": evidence, "operation_id": "op-1"}
        monkeypatch.setattr(subprocess, "run", lambda *a, payload=payload, **k:
                            subprocess.CompletedProcess(a[0], 0, json.dumps(payload), ""))
        assert ac.reconcile_participant("a.py", "r1", "op-1", tmp_path).kind == expected


def test_reconcile_operation_mismatch_fails_closed(monkeypatch, tmp_path):
    payload = _completed(operation="different-op")
    monkeypatch.setattr(subprocess, "run", lambda *a, **k:
                        subprocess.CompletedProcess(a[0], 0, json.dumps(payload), ""))
    result = ac.reconcile_participant("a.py", "r1", "op-1", tmp_path)
    assert not result.ok and result.kind == "ambiguous"


def test_exact_ask_exit_zero_still_reconciles_and_returns_cached(monkeypatch, tmp_path):
    calls = []
    def run(cmd, **kwargs):
        calls.append(cmd)
        if "reconcile" in cmd:
            return subprocess.CompletedProcess(cmd, 0, json.dumps(_completed()), "")
        return subprocess.CompletedProcess(cmd, 0, "provider exited zero", "")
    monkeypatch.setattr(subprocess, "run", run)
    result = ac.ask_participant(
        "adapter.py", "review-1", "question", tmp_path,
        operation_id="op-1", parent_provider_turn_id="parent-turn",
    )
    assert result.ok and result.text == "cached"
    assert sum("ask" in cmd for cmd in calls) == 1
    assert sum("reconcile" in cmd for cmd in calls) == 1


def test_only_complete_absence_authorizes_single_error_retry(monkeypatch, tmp_path):
    calls = []
    def run(cmd, **kwargs):
        calls.append(cmd)
        if "reconcile" in cmd:
            payload = {"state": "absent", "evidence_complete": True, "operation_id": "op-1"}
            return subprocess.CompletedProcess(cmd, 0, json.dumps(payload), "")
        return subprocess.CompletedProcess(cmd, 1, "", "provider error")
    monkeypatch.setattr(subprocess, "run", run)
    result = ac.ask_participant(
        "adapter.py", "review-1", "question", tmp_path,
        operation_id="op-1", parent_provider_turn_id="parent-turn",
    )
    assert not result.ok and result.attempts == 2
    assert sum("ask" in cmd for cmd in calls) == 2


def test_ambiguous_reconcile_never_reasks(monkeypatch, tmp_path):
    calls = []
    def run(cmd, **kwargs):
        calls.append(cmd)
        if "reconcile" in cmd:
            payload = {"state": "ambiguous", "evidence_complete": False,
                       "operation_id": "op-1", "reason": "inventory unavailable"}
            return subprocess.CompletedProcess(cmd, 0, json.dumps(payload), "")
        return subprocess.CompletedProcess(cmd, 1, "", "provider error")
    monkeypatch.setattr(subprocess, "run", run)
    result = ac.ask_participant(
        "adapter.py", "review-1", "question", tmp_path,
        operation_id="op-1", parent_provider_turn_id="parent-turn",
    )
    assert not result.ok
    assert sum("ask" in cmd for cmd in calls) == 1
