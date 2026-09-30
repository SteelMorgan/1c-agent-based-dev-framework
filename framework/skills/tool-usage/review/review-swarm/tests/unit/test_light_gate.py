"""SU-L01..SU-L08 — unit-слой T-13: выбор ревьюера лёгкого тарифа (floor-фильтр
cross-family + gate_legal, fail-closed без self-review), gate-предикаты
(диспозиции, итерации ≤3, эскалация), парсинг gate-вердикта
(RVSW-01, FR-14/FR-15/FR-16, AC-14/AC-16/AC-19; TD §7, §10.4).

Решение владельца 2026-08-03 (cross-family gate policy): блокирующий
ревьюер (и назначение gate-ревьюера в рое) обязан быть из ДРУГОГО семейства
моделей, чем вызывающий — не просто другим id того же семейства. Это отменяет
прежний identity-floor. `select_light_reviewer`/`designate_gate_reviewer`
принимают обязательный `caller_family`; фильтр — `family != caller_family`.
"""
from __future__ import annotations

import pytest

import swarm
import swarm_core as core


def _entry(pid, family, enabled=True, gate_legal=True,
           quota_status="unavailable", introspection="none", cli=None):
    return {
        "id": pid,
        "family": family,
        "model": "stub-model",
        "adapter": f"adapters/{family}.py",
        "cli": cli or family,
        "context_budget": 120000,
        "enabled": enabled,
        "gate_legal": gate_legal,
        "quota_introspection": introspection,
        "quota_status": quota_status,
    }


def _pool():
    return [
        _entry("claude-opus", "claude"),
        _entry("codex-gpt", "codex", quota_status="proven",
               introspection="codex-rollout"),
        _entry("kimi-k2", "kimi", gate_legal=False),
    ]


# ---------- SU-L01: семейство вызывающего ----------

def test_su_l01_resolve_caller_family():
    registry = {"participants": _pool()}
    assert swarm.resolve_caller_family(registry, "claude-opus") == "claude"
    assert swarm.resolve_caller_family(registry, "kimi-k2") == "kimi"
    with pytest.raises(ValueError, match="primary"):
        swarm.resolve_caller_family(registry, "primary")


def test_su_l01_declared_caller_policy_assertion():
    registry = {"participants": _pool()}
    assert swarm.resolve_declared_caller(
        registry, "claude-opus"
    ) == ("claude-opus", "claude")
    with pytest.raises(swarm.CliError, match="явный --caller"):
        swarm.resolve_declared_caller(registry, None)
    with pytest.raises(swarm.CliError, match="unknown"):
        swarm.resolve_declared_caller(registry, "unknown")


# ---------- SU-L02: квотный выбор лёгкого тарифа (cross-family floor) ----------

def test_su_l02_floor_excludes_own_family_not_only_exact_caller():
    pool = _pool()
    selection = swarm.select_light_reviewer(
        pool, caller_id="claude-opus", caller_family="claude", gate=False)
    assert selection["adapter_id"] == "codex-gpt"  # первый по реестру после floor
    assert selection["candidates"] == ["codex-gpt", "kimi-k2"]
    # claude недоступен introspection → blind с причиной (FR-14б)
    assert selection["quota_mode"] == "blind"
    assert "kimi" in selection["quota_fallback_reason"]
    # Владелец 2026-08-03 (cross-family): участник ТОГО ЖЕ family, что
    # caller, — не eligible, даже при другом id. Пул из одних claude-участников
    # остаётся пуст, а не «разрешает другого claude».
    same_family_pool = [
        _entry("claude-opus", "claude"),
        _entry("claude-sonnet", "claude"),
    ]
    with pytest.raises(swarm.CliError, match="self-review"):
        swarm.select_light_reviewer(
            same_family_pool, caller_id="claude-opus", caller_family="claude",
            gate=False)


def test_su_l02_blind_rotation_no_repeat():
    pool = _pool()
    selection = swarm.select_light_reviewer(
        pool, caller_id="claude-opus", caller_family="claude", gate=False,
        last_choice="codex-gpt")
    assert selection["adapter_id"] == "kimi-k2"


def test_su_l02_gate_requires_gate_legal():
    pool = _pool()
    selection = swarm.select_light_reviewer(
        pool, caller_id="claude-opus", caller_family="claude", gate=True)
    # kimi gate_legal=False отсечен floor-фильтром ДО взвешивания (FR-14/FR-16)
    assert selection["candidates"] == ["codex-gpt"]
    assert selection["adapter_id"] == "codex-gpt"


def test_su_l02_empty_pool_fail_closed_exact_caller_only():
    pool = [_entry("claude-opus", "claude")]
    with pytest.raises(swarm.CliError) as excinfo:
        swarm.select_light_reviewer(
            pool, caller_id="claude-opus", caller_family="claude", gate=False)
    assert "self-review" in str(excinfo.value)


def test_su_l02_empty_pool_fail_closed_same_family_only():
    """Cross-family floor (владелец, 2026-08-03): пул из ДВУХ участников
    одного family с caller тоже пуст после floor — недостаточно, что id
    отличается от literal caller."""
    pool = [_entry("claude-opus", "claude"), _entry("claude-sonnet", "claude")]
    with pytest.raises(swarm.CliError) as excinfo:
        swarm.select_light_reviewer(
            pool, caller_id="claude-opus", caller_family="claude", gate=False)
    assert "self-review" in str(excinfo.value)


def test_su_l02_gate_without_legal_candidate_fail_closed():
    pool = [
        _entry("claude-opus", "claude"),
        _entry("codex-gpt", "codex", gate_legal=False),
        _entry("kimi-k2", "kimi", gate_legal=False),
    ]
    with pytest.raises(swarm.CliError):
        swarm.select_light_reviewer(
            pool, caller_id="claude-opus", caller_family="claude", gate=True)


def test_su_l02_weighted_when_all_proven_fresh():
    pool = [
        _entry("claude-opus", "claude"),
        _entry("codex-gpt", "codex", quota_status="proven",
               introspection="codex-rollout"),
        _entry("codex-alt", "codex", quota_status="proven",
               introspection="codex-rollout", cli="codex"),
    ]
    quota_data = {
        "codex-gpt": {"remaining_percent": 40.0, "resets_at": 2000.0},
        "codex-alt": {"remaining_percent": 90.0, "resets_at": 2000.0},
    }
    selection = swarm.select_light_reviewer(
        pool, caller_id="claude-opus", caller_family="claude", gate=False,
        quota_data=quota_data, now=1000.0)
    assert selection["quota_mode"] == "weighted"
    assert selection["adapter_id"] == "codex-alt"  # argmax remaining_percent
    assert selection["quota_fallback_reason"] is None


# ---------- SU-L03: назначение gate-ревьюера в рое (AC-24, cross-family) ----------

def test_su_l03_designate_auto_first_legal_non_caller():
    reviewer = swarm.designate_gate_reviewer(
        _pool(), caller_id="claude-opus", caller_family="claude")
    assert reviewer["id"] == "codex-gpt"  # kimi не gate_legal


def test_su_l03_designate_requested_valid():
    reviewer = swarm.designate_gate_reviewer(
        _pool(), caller_id="codex-gpt", caller_family="codex",
        requested="claude-opus")
    assert reviewer["id"] == "claude-opus"


def test_su_l03_designate_requested_same_family_refused():
    """Владелец 2026-08-03 (cross-family gate policy): запрошенный
    gate-ревьюер ТОГО ЖЕ family, что caller, отказан, даже при другом id
    (отменяет прежний identity-floor «любой, кроме literal caller»)."""
    pool = _pool() + [_entry("claude-sonnet", "claude")]
    with pytest.raises(swarm.CliError):
        swarm.designate_gate_reviewer(
            pool, caller_id="claude-opus", caller_family="claude",
            requested="claude-sonnet")


def test_su_l03_designate_requested_exact_caller_refused():
    with pytest.raises(swarm.CliError):
        swarm.designate_gate_reviewer(
            _pool(), caller_id="claude-opus", caller_family="claude",
            requested="claude-opus")


def test_su_l03_designate_requested_not_gate_legal_refused():
    with pytest.raises(swarm.CliError):
        swarm.designate_gate_reviewer(
            _pool(), caller_id="claude-opus", caller_family="claude",
            requested="kimi-k2")


def test_su_l03_designate_no_candidate_refused():
    pool = [
        _entry("claude-opus", "claude"),
        _entry("kimi-k2", "kimi", gate_legal=False),
    ]
    with pytest.raises(swarm.CliError, match="лёгк"):
        swarm.designate_gate_reviewer(
            pool, caller_id="claude-opus", caller_family="claude")


def test_su_l03_designate_no_candidate_refused_same_family_only():
    """Пул из ДВУХ участников одного family с caller (оба gate_legal) —
    после cross-family floor кандидатов нет, отказ, а не выбор второго."""
    pool = [
        _entry("claude-opus", "claude"),
        _entry("claude-sonnet", "claude"),
    ]
    with pytest.raises(swarm.CliError, match="лёгк"):
        swarm.designate_gate_reviewer(
            pool, caller_id="claude-opus", caller_family="claude")


# ---------- SU-L04: диспозиции Оркестратора (acceptance-bound, FR-16) ----------

def test_su_l04_dispositions_full_enum():
    raw = {"F-001": "agree", "F-002": "partial", "F-003": "disagree",
           "F-004": "withdrawn", "F-005": "out_of_scope"}
    out = swarm.validate_dispositions(raw, ["F-001", "F-002", "F-003", "F-004", "F-005"])
    assert out == raw


def test_su_l04_dispositions_subset_allowed():
    out = swarm.validate_dispositions({"F-002": "disagree"}, ["F-001", "F-002"])
    assert out == {"F-002": "disagree"}


def test_su_l04_disposition_outside_enum_rejected():
    with pytest.raises(ValueError, match="enum"):
        swarm.validate_dispositions({"F-001": "maybe"}, ["F-001"])


def test_su_l04_disposition_unknown_finding_rejected():
    with pytest.raises(ValueError, match="F-099"):
        swarm.validate_dispositions({"F-099": "agree"}, ["F-001"])


# ---------- SU-L05: итерации gate ≤3 и эскалация (FR-16) ----------

def test_su_l05_positive_verdict_approves():
    assert swarm.gate_verdict_positive("completion", {"decision": "APPROVE_COMPLETION"})
    assert swarm.gate_verdict_positive("acceptance", {"verdict": "accept"})
    # F-007 (E2E-02): conditional_accept — промежуточный статус, НЕ approved
    assert not swarm.gate_verdict_positive("acceptance", {"verdict": "conditional_accept"})
    assert not swarm.gate_verdict_positive("completion", {"decision": "BLOCK_COMPLETION"})
    assert not swarm.gate_verdict_positive("acceptance", {"verdict": "reject"})
    assert not swarm.gate_verdict_positive("acceptance", {"verdict": "re-review"})


def test_su_l05_resolve_gate_status():
    assert swarm.resolve_gate_status(positive=True, iterations=1) == "approved"
    assert swarm.resolve_gate_status(positive=False, iterations=1) == "blocked"
    assert swarm.resolve_gate_status(positive=False, iterations=2) == "blocked"
    # 3-я итерация без согласия → эскалация (наследуется, Hard Rule 16)
    assert swarm.resolve_gate_status(positive=False, iterations=3) == "escalated"


# ---------- SU-L06: wall-clock бюджет лёгкого тарифа (TD §11) ----------

def test_su_l06_light_budget():
    budget, warn_at = swarm.light_wall_clock_budget(900, findings_count=0)
    assert budget == max(3600, (1 + 3 + 0) * (900 + 240) + 900)
    assert warn_at == int(budget * 0.75)
    budget_f, _ = swarm.light_wall_clock_budget(900, findings_count=20)
    assert budget_f == (1 + 3 + 3) * 1140 + 900  # ceil(20/8)=3 re-review волны


# ---------- SU-L07/SU-L08: парсинг gate-вердикта (ядро) ----------

def _block(payload) -> str:
    import json
    return ("Текст.\n\n```swarm-gate-verdict\n"
            + json.dumps(payload, ensure_ascii=False) + "\n```\n")


def test_su_l07_parse_completion_verdict():
    raw = core.parse_gate_verdict_move(_block({
        "decision": "BLOCK_COMPLETION", "findings": [], "rationale": "gap"}))
    verdict = core.validate_gate_verdict(raw, "completion")
    assert verdict["decision"] == "BLOCK_COMPLETION"
    assert verdict["escalation_needed"] is False


def test_su_l07_completion_decision_outside_enum_rejected():
    raw = core.parse_gate_verdict_move(_block({"decision": "MAYBE"}))
    with pytest.raises(core.MoveRejected):
        core.validate_gate_verdict(raw, "completion")


def test_su_l08_parse_acceptance_verdict():
    raw = core.parse_gate_verdict_move(_block({
        "verdict": "conditional_accept",
        "positions": [{"finding_id": "F-001", "position": "partial"}],
        "rationale": "почти согласен"}))
    verdict = core.validate_gate_verdict(raw, "acceptance", finding_ids=["F-001"])
    assert verdict["verdict"] == "conditional_accept"
    assert verdict["positions"] == [{"finding_id": "F-001", "position": "partial"}]


def test_su_l08_acceptance_bad_position_rejected():
    raw = core.parse_gate_verdict_move(_block({
        "verdict": "accept",
        "positions": [{"finding_id": "F-001", "position": "out_of_scope"}]}))
    # out_of_scope — диспозиция Оркестратора, не позиция ревьюера
    with pytest.raises(core.MoveRejected):
        core.validate_gate_verdict(raw, "acceptance", finding_ids=["F-001"])


def test_su_l08_acceptance_unknown_finding_rejected():
    raw = core.parse_gate_verdict_move(_block({
        "verdict": "accept",
        "positions": [{"finding_id": "F-099", "position": "agree"}]}))
    with pytest.raises(core.MoveRejected):
        core.validate_gate_verdict(raw, "acceptance", finding_ids=["F-001"])


def test_su_l08_missing_block_rejected():
    with pytest.raises(core.MoveRejected):
        core.parse_gate_verdict_move("свободный текст без fenced-блока")
