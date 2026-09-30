"""SU-RR — re-review фиксов (RVSW-01, T-12, FR-09/AC-09, TD §5.5).

Покрытие test-plan SU-RR01..RR03:
- SU-RR01: схема вердикта re-review (TD §5.5): enum fixed/partially/not_fixed/
  introduced_new_issue; new_issue обязателен при introduced_new_issue и
  запрещён при прочих вердиктах; evidence обязателен и резолвим в проверяемом
  наборе; finding_id сверяется с ожидаемым — всё fail-closed (MoveRejected);
- SU-RR02: new_issue уходит в общий пул находок (не в тред): возвращается
  сырой записью для validate_findings, получает сквозной F-id пула, автором
  становится автор re-review; тред-API треда валидации не затрагивается;
- SU-RR03: доля дошедших до fixed пишется в observations (поля T-11:
  fixed/partially/not_fixed; introduced_new_issue → not_fixed) и в
  fixed_rate проекции strengths; предикат конфликта «разработчик исправил /
  автор — нет» → арбитражная ветка (FR-09).
"""
from __future__ import annotations

import pytest

import swarm_core as core

CHECKED = {"src/a.py": 40, "src/b.py": 40}


def _block(payload: dict) -> str:
    import json
    return ("Re-review вердикт.\n\n```swarm-rereview\n"
            + json.dumps(payload, ensure_ascii=False) + "\n```\n")


def _verdict_payload(verdict: str, fid: str = "F-001", **extra):
    payload = {
        "finding_id": fid,
        "verdict": verdict,
        "evidence": {"path": "src/a.py", "line": 10, "quote": "исправленный фрагмент"},
        "rationale": "проверил фикс по коду",
    }
    payload.update(extra)
    return payload


def _new_issue(path="src/a.py", start=20, end=22):
    return {
        "location": {"path": path, "line_start": start, "line_end": end},
        "category": "correctness",
        "severity": "P2",
        "in_lens": True,
        "claim": "фикс внёс новую ошибку",
        "evidence": "фрагмент нового кода",
        "rationale": "пояснение",
    }


# ---------------------------------------------------------------------------
# SU-RR01: схема вердикта re-review (TD §5.5)
# ---------------------------------------------------------------------------

def test_su_rr01_all_verdict_values_accepted():
    for verdict in core.REREVIEW_VERDICT_VALUES:
        extra = {"new_issue": _new_issue()} if verdict == "introduced_new_issue" else {}
        raw = core.parse_rereview_move(_block(_verdict_payload(verdict, **extra)))
        parsed, new_issue = core.validate_rereview_verdict(
            raw, CHECKED, expected_finding_id="F-001")
        assert parsed["verdict"] == verdict
        assert parsed["finding_id"] == "F-001"
        assert parsed["evidence"]["path"] == "src/a.py"
        if verdict == "introduced_new_issue":
            assert new_issue == _new_issue()
        else:
            assert new_issue is None


def test_su_rr01_verdict_enum_is_exactly_four_values():
    assert set(core.REREVIEW_VERDICT_VALUES) == {
        "fixed", "partially", "not_fixed", "introduced_new_issue"}


def test_su_rr01_invalid_verdict_rejected():
    raw = core.parse_rereview_move(_block(_verdict_payload("resolved")))
    with pytest.raises(core.MoveRejected) as exc:
        core.validate_rereview_verdict(raw, CHECKED, expected_finding_id="F-001")
    assert exc.value.reason == "invalid_verdict"


def test_su_rr01_introduced_new_issue_requires_new_issue():
    raw = core.parse_rereview_move(_block(_verdict_payload("introduced_new_issue")))
    with pytest.raises(core.MoveRejected) as exc:
        core.validate_rereview_verdict(raw, CHECKED, expected_finding_id="F-001")
    assert exc.value.reason == "missing_field:new_issue"


def test_su_rr01_new_issue_forbidden_for_other_verdicts():
    raw = core.parse_rereview_move(
        _block(_verdict_payload("fixed", new_issue=_new_issue())))
    with pytest.raises(core.MoveRejected) as exc:
        core.validate_rereview_verdict(raw, CHECKED, expected_finding_id="F-001")
    assert exc.value.reason == "unexpected_new_issue"


def test_su_rr01_evidence_mandatory_and_resolvable():
    payload = _verdict_payload("fixed")
    del payload["evidence"]
    raw = core.parse_rereview_move(_block(payload))
    with pytest.raises(core.MoveRejected) as exc:
        core.validate_rereview_verdict(raw, CHECKED, expected_finding_id="F-001")
    assert exc.value.reason == "missing_field:evidence"

    raw = core.parse_rereview_move(_block(_verdict_payload(
        "fixed", evidence={"path": "src/zzz.py", "line": 1, "quote": "q"})))
    with pytest.raises(core.MoveRejected) as exc:
        core.validate_rereview_verdict(raw, CHECKED, expected_finding_id="F-001")
    assert exc.value.reason == "invalid_evidence"


def test_su_rr01_finding_id_mismatch_rejected():
    raw = core.parse_rereview_move(_block(_verdict_payload("fixed", fid="F-002")))
    with pytest.raises(core.MoveRejected) as exc:
        core.validate_rereview_verdict(raw, CHECKED, expected_finding_id="F-001")
    assert exc.value.reason == "finding_mismatch"


def test_su_rr01_missing_block_rejected():
    with pytest.raises(core.MoveRejected) as exc:
        core.parse_rereview_move("свободный текст без fenced-блока")
    assert exc.value.reason == "missing_block"


# ---------------------------------------------------------------------------
# SU-RR02: new_issue — в общий пул находок, не в тред
# ---------------------------------------------------------------------------

def test_su_rr02_new_issue_routed_to_pool_with_author_of_rereview():
    raw = core.parse_rereview_move(
        _block(_verdict_payload("introduced_new_issue", new_issue=_new_issue())))
    _, new_issue_raw = core.validate_rereview_verdict(
        raw, CHECKED, expected_finding_id="F-001")

    # Маршрутизация — тот же механизм общего пула, что и для находок,
    # маршрутизированных из туров 2–4: validate_findings со сквозной
    # нумерацией пула и author_id автора re-review.
    accepted, rejected = core.validate_findings(
        [new_issue_raw], CHECKED, author_id="claude-opus", start_index=7)
    assert rejected == []
    assert len(accepted) == 1
    pooled = accepted[0]
    assert pooled["finding_id"] == "F-007"          # сквозная нумерация пула
    assert pooled["author_id"] == "claude-opus"     # автор re-review, не self-declared

    # Находка НЕ попадает в тред исходной находки: тред F-001 не существует
    # для re-review (вход — состояние после report), и pool-находка не
    # материализует тред сама — start_thread к ней не применялся.
    thread = core.start_thread(pooled)  # только если Оркестратор явно заведёт
    assert thread["finding_id"] == "F-007" != "F-001"


# ---------------------------------------------------------------------------
# SU-RR03: доля fixed в observations (AC-09/AC-12) и предикат конфликта
# ---------------------------------------------------------------------------

def _session_with_rereview(rereview: dict):
    participants = [
        {
            "id": "claude-opus", "family": "claude", "state": "active",
            "lens": "security", "lens_forced": False, "review_id": "rev-1",
            "adapter_session_id": None, "invocations": 1, "retries": 0,
        },
    ]
    findings = [
        {
            "finding_id": fid, "author_id": "claude-opus",
            "location": {"path": "src/a.py", "line_start": 10, "line_end": 12},
            "category": "security", "severity": "P2", "in_lens": True,
            "claim": f"claim {fid}", "evidence": "фрагмент", "rationale": "",
        }
        for fid in rereview
    ]
    return {
        "session_id": "swarm-20260729-120000-deadbeef",
        "participants": participants,
        "findings": findings,
        "routed_findings": [],
        "threads": {},
        "dedup": None,
        "arbitration": {},
        "calibration_run": False,
        "quota_mode": None,
        "quota_fallback_reason": None,
        "rereview": rereview,
    }


def test_su_rr03_fixed_share_in_observations():
    session = _session_with_rereview({
        "F-001": "fixed",
        "F-002": "partially",
        "F-003": "not_fixed",
        "F-004": "introduced_new_issue",   # → not_fixed (контракт T-11)
    })
    observations = core.build_observations(session, review_seq=5, date="2026-07-29")
    assert len(observations) == 1
    obs = observations[0]
    assert obs["fixed"] == 1
    assert obs["partially"] == 1
    assert obs["not_fixed"] == 2

    strengths = core.compute_swarm_strengths(observations)
    cell = strengths[("claude-opus", "security")]
    assert cell["fixed_rate"] == pytest.approx(1 / 4)


def test_su_rr03_conflict_predicate():
    # Разработчик утверждает «исправил» (default), автор — нет → конфликт
    # → арбитраж Оркестратора по FR-08.
    assert core.rereview_conflict("not_fixed", "fixed") is True
    assert core.rereview_conflict("partially", "fixed") is True
    assert core.rereview_conflict("introduced_new_issue", "fixed") is True
    assert core.rereview_conflict("fixed", "fixed") is False
    # Заявление разработчика «частично» + вердикт «частично» — согласия нет
    # только при расхождении позиций.
    assert core.rereview_conflict("partially", "partially") is False
    with pytest.raises(ValueError):
        core.rereview_conflict("resolved", "fixed")
    with pytest.raises(ValueError):
        core.rereview_conflict("fixed", "resolved")
