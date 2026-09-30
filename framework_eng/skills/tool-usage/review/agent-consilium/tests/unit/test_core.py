"""Unit-слой (AC-18): предикаты стоп-условий, kill-прокси, роли, strengths,
анонимизация, парсинг, transcript-целостность, реестр, бюджеты.

Чистые функции consilium_core.py — без процессов и LLM. Trace: UT-01..UT-22.
"""
from __future__ import annotations

import json
from datetime import datetime, timedelta, UTC

import pytest

import consilium_core as core


# ---------- UT-01..UT-07: стоп-условия фазы B (FR-02, AC-02/18) ----------

def test_ut01_convergence_conjunction(turn_factory):
    """UT-01: alive<=2 AND раунд без NF/PC AND r>=1 → конвергенция."""
    assert core.is_converged(alive_count=2, rounds_completed=1, nf=False, pc=False) is True
    assert core.is_converged(alive_count=1, rounds_completed=3, nf=False, pc=False) is True


def test_ut02_kill_under_polemics_is_not_convergence(turn_factory):
    """UT-02: kill до <=2 при продолжающейся полемике (NF или PC) — НЕ конвергенция."""
    assert core.is_converged(alive_count=2, rounds_completed=1, nf=True, pc=False) is False
    assert core.is_converged(alive_count=2, rounds_completed=1, nf=False, pc=True) is False
    reason, _ = core.evaluate_b_stop(
        alive_count=2, rounds_completed=1, nf=True, pc=False, freeze=False, stalemate_counter=0
    )
    assert reason is None  # фаза B продолжается


def test_ut03_degenerate_quorum2_no_convergence_before_round1():
    """UT-03: вырожденный кворум=2: конвергенция невозможна до завершения раунда 1."""
    assert core.is_converged(alive_count=2, rounds_completed=0, nf=False, pc=False) is False


def test_ut04_round_limit_default_two_rounds():
    """UT-04: после 2 раундов без NF/PC раунд 3 не планируется → stop=round_limit."""
    reason, _ = core.evaluate_b_stop(
        alive_count=3, rounds_completed=2, nf=False, pc=False, freeze=False, stalemate_counter=0
    )
    assert reason == "round_limit"


def test_ut05_round3_conditional_round4_hard_ceiling():
    """UT-05: раунд 3 ⟺ NF(2)|PC(2); после раунда 4 — безусловный stop."""
    assert core.round_3_allowed(nf2=True, pc2=False) is True
    assert core.round_3_allowed(nf2=False, pc2=True) is True
    assert core.round_3_allowed(nf2=False, pc2=False) is False
    reason, _ = core.evaluate_b_stop(
        alive_count=3, rounds_completed=4, nf=True, pc=True, freeze=False, stalemate_counter=0
    )
    assert reason == "round_limit"


def test_ut06_stalemate_two_rounds_without_position_changes():
    """UT-06: 2 раунда подряд без position_changes → stop=stalemate."""
    counter = core.update_stalemate(0, freeze=False, pc=False)
    assert counter == 1
    reason, counter = core.evaluate_b_stop(
        alive_count=3, rounds_completed=2, nf=True, pc=False, freeze=False, stalemate_counter=counter
    )
    assert reason == "stalemate"
    assert core.is_stalemate(counter) is True


def test_ut07_freeze_does_not_move_stalemate_counter():
    """UT-07: заморозка unresponsive не двигает stalemate-счётчик (FR-11)."""
    assert core.update_stalemate(1, freeze=True, pc=False) == 1
    assert core.update_stalemate(1, freeze=True, pc=True) == 1
    assert core.update_stalemate(0, freeze=True, pc=False) == 0
    reason, counter = core.evaluate_b_stop(
        alive_count=3, rounds_completed=2, nf=False, pc=False, freeze=True, stalemate_counter=1
    )
    assert counter == 1  # ни инкремента, ни сброса
    assert reason != "stalemate"


# ---------- UT-08..UT-11: kill-прокси (FR-03, AC-18) ----------

def _metrics(entries):
    """entries: {model: (survived, borrowed_in, upheld_against)}"""
    return {
        model: {"survived": s, "borrowed_in": b, "upheld_against": u}
        for model, (s, b, u) in entries.items()
    }


def test_ut08_kill_proxy_sorting():
    """UT-08: сортировка survived asc, затем borrowed_in asc — кандидат детерминирован."""
    metrics = _metrics({"m1": (3, 0, 0), "m2": (1, 5, 0), "m3": (2, 1, 0)})
    assert core.kill_candidate(metrics) == "m2"  # min survived
    metrics = _metrics({"m1": (2, 3, 0), "m2": (2, 1, 0), "m3": (2, 2, 0)})
    assert core.kill_candidate(metrics) == "m2"  # survived равны → min borrowed_in


def test_ut09_kill_tiebreak_upheld_then_no_kill():
    """UT-09: равенство прокси → больше upheld-критик против; полное равенство → kill НЕТ."""
    metrics = _metrics({"m1": (2, 1, 3), "m2": (2, 1, 1), "m3": (5, 5, 0)})
    assert core.kill_candidate(metrics) == "m1"  # tie-break по upheld_against desc
    metrics = _metrics({"m1": (2, 1, 2), "m2": (2, 1, 2), "m3": (5, 5, 0)})
    assert core.kill_candidate(metrics) is None  # полное равенство → kill не производится


def test_ut10_borrowed_requires_resolvable_source_ref(turn_factory):
    """UT-10: borrowed засчитывается только при разрешимом source_ref (антинакрутка)."""
    source = turn_factory(seq=12, author="codex-gpt", elements=["E7"], type="proposal", wave="proposal", phase="A", round=0)
    borrower = turn_factory(
        seq=15, author="claude-opus",
        borrowed=[{"element": "E7", "source_ref": 12}, {"element": "E9", "source_ref": 999}],
    )
    own_record = turn_factory(seq=16, author="claude-opus", elements=["E1"])
    records = [source, borrower, own_record]
    valid, invalid = core.resolve_borrowed(borrower, records)
    assert valid == [{"element": "E7", "source_ref": 12}]
    assert invalid == [{"element": "E9", "source_ref": 999}]
    # ссылка на собственную запись — не засчитывается
    self_borrow = turn_factory(seq=17, author="claude-opus", borrowed=[{"element": "E1", "source_ref": 16}])
    valid, invalid = core.resolve_borrowed(self_borrow, records + [self_borrow])
    assert valid == []
    assert invalid == [{"element": "E1", "source_ref": 16}]


def test_ut11_upheld_critique_deterministic(turn_factory):
    """UT-11: upheld = атака на элемент, после которой ближайший position_changes автора ∈ {agree, withdraw}."""
    proposal = turn_factory(seq=1, author="m1", phase="A", round=0, wave="proposal", type="proposal", elements=["E1", "E2"])
    attack = turn_factory(
        seq=2, author="m2", type="attack",
        position_changes=[{"element": "m1:E1", "action": "disagree", "refs": [1]}],
    )
    response = turn_factory(
        seq=3, author="m1", type="response", wave="response",
        position_changes=[{"element": "m1:E1", "action": "agree", "refs": [2]}],
    )
    records = [proposal, attack, response]
    metrics = core.compute_model_metrics(records, ["m1", "m2"])
    assert metrics["m1"]["upheld_against"] == 1
    # disagree автора → критика НЕ upheld
    response2 = turn_factory(
        seq=3, author="m1", type="response", wave="response",
        position_changes=[{"element": "m1:E1", "action": "disagree", "refs": [2]}],
    )
    metrics2 = core.compute_model_metrics([proposal, attack, response2], ["m1", "m2"])
    assert metrics2["m1"]["upheld_against"] == 0
    # survived: 2 элемента − 0 withdrawn − 1 upheld = 1
    assert metrics["m1"]["survived"] == 1


def test_f18_withdraw_after_attack_no_double_deduction(turn_factory):
    """F-18: withdraw после атаки штрафует survived один раз, не два (upheld − withdrawn)."""
    proposal = turn_factory(seq=1, author="m1", phase="A", round=0, wave="proposal", type="proposal", elements=["E1", "E2"])
    attack = turn_factory(
        seq=2, author="m2", type="attack",
        position_changes=[{"element": "m1:E1", "action": "disagree", "refs": [1]}],
    )
    withdraw_response = turn_factory(
        seq=3, author="m1", type="response", wave="response",
        position_changes=[{"element": "m1:E1", "action": "withdraw", "refs": [2]}],
    )
    metrics = core.compute_model_metrics([proposal, attack, withdraw_response], ["m1", "m2"])
    # отозванный элемент учтён и в withdrawn, и в upheld — survived вычитается один раз
    assert metrics["m1"]["survived"] == 1  # 2 − 1 (а не 2 − 1 − 1 = 0)
    # upheld_against сохраняет семантику факта «критика подтверждена» (track-record)
    assert metrics["m1"]["upheld_against"] == 1
    # контроль 1: самоотзыв без атаки — тоже один вычет
    own_withdraw = turn_factory(
        seq=2, author="m1", type="response", wave="response",
        position_changes=[{"element": "m1:E1", "action": "withdraw", "refs": [1]}],
    )
    metrics_ctrl = core.compute_model_metrics([proposal, own_withdraw], ["m1", "m2"])
    assert metrics_ctrl["m1"]["survived"] == 1
    assert metrics_ctrl["m1"]["upheld_against"] == 0


def test_f003_mixed_withdrawn_and_upheld_each_single_deduction(turn_factory):
    """F-003 (rev): у автора одновременно withdrawn-элемент и upheld-но-не-withdrawn —
    каждый вычитается ровно один раз: survived = declared − withdrawn − (upheld−withdrawn)."""
    proposal = turn_factory(
        seq=1, author="m1", phase="A", round=0, wave="proposal", type="proposal",
        elements=["E1", "E2", "E3", "E4"],
    )
    attack_e2 = turn_factory(
        seq=2, author="m2", type="attack",
        position_changes=[{"element": "m1:E2", "action": "disagree", "refs": [1]}],
    )
    attack_e3 = turn_factory(
        seq=3, author="m3", type="attack",
        position_changes=[{"element": "m1:E3", "action": "disagree", "refs": [1]}],
    )
    response = turn_factory(
        seq=4, author="m1", type="response", wave="response",
        position_changes=[
            {"element": "m1:E1", "action": "withdraw", "refs": [1]},   # самоотзыв без атаки
            {"element": "m1:E2", "action": "withdraw", "refs": [2]},   # отозван после атаки (upheld + withdrawn)
            {"element": "m1:E3", "action": "agree", "refs": [3]},      # upheld, НЕ отозван
        ],
    )
    metrics = core.compute_model_metrics([proposal, attack_e2, attack_e3, response], ["m1", "m2", "m3"])
    # 4 declared − E1 (withdrawn) − E2 (withdrawn, НЕ второй раз через upheld) − E3 (upheld) = 1
    assert metrics["m1"]["survived"] == 1
    # E2 и E3 — подтверждённые критики по элементам последней декларации
    assert metrics["m1"]["upheld_against"] == 2
    # F-002 (rev): критика по элементу, снятому из последней декларации,
    # не влияет ни на survived, ни на tie-break upheld_against
    redeclare = turn_factory(
        seq=5, author="m1", type="response", wave="response", elements=["E1", "E2", "E4"],
    )
    metrics_re = core.compute_model_metrics(
        [proposal, attack_e2, attack_e3, response, redeclare], ["m1", "m2", "m3"])
    assert metrics_re["m1"]["survived"] == 1  # 3 declared − E1 − E2; E3 вне декларации
    assert metrics_re["m1"]["upheld_against"] == 1  # только E2


def test_f004_duplicate_declaration_does_not_inflate_survived(turn_factory):
    """F-004 (rev): повторное объявление элемента в последней декларации не накручивает survived."""
    proposal = turn_factory(
        seq=1, author="m1", phase="A", round=0, wave="proposal", type="proposal",
        elements=["E1", "E1", "E2"],
    )
    metrics = core.compute_model_metrics([proposal], ["m1", "m2"])
    assert metrics["m1"]["survived"] == 2  # {E1, E2}, а не 3
    attack = turn_factory(
        seq=2, author="m2", type="attack",
        position_changes=[{"element": "m1:E1", "action": "disagree", "refs": [1]}],
    )
    response = turn_factory(
        seq=3, author="m1", type="response", wave="response",
        position_changes=[{"element": "m1:E1", "action": "agree", "refs": [2]}],
    )
    metrics2 = core.compute_model_metrics([proposal, attack, response], ["m1", "m2"])
    assert metrics2["m1"]["survived"] == 1  # 2 уникальных − 1 upheld
    assert metrics2["m1"]["upheld_against"] == 1


# ---------- UT-12: парсинг structured-хода (FR-04, TD 7.4) ----------

def test_ut12_parse_structured_block(structured_block):
    """UT-12: валидный блок → поля; битый/отсутствующий → пустые structured + warning, ход не отвергается."""
    data = {"elements": ["E1"], "new_findings": [{"id": "F-01", "text": "x"}],
            "position_changes": [], "borrowed": []}
    structured, warning = core.parse_structured_block("текст хода\n" + structured_block(data))
    assert warning is None
    assert structured["elements"] == ["E1"]
    assert structured["new_findings"] == [{"id": "F-01", "text": "x"}]

    empty5 = {"elements": [], "new_findings": [], "position_changes": [], "borrowed": [],
              "risk_checklist_responses": []}
    structured, warning = core.parse_structured_block("текст\n```consilium-structured\n{broken json\n```")
    assert structured == empty5
    assert warning  # предупреждение есть, ход не отвергается

    structured, warning = core.parse_structured_block("текст вообще без блока")
    assert structured == empty5
    assert warning


# ---------- UT-13: анонимизация (FR-04, AC-03/20) ----------

def test_ut13_anonymization_stable_and_no_leak(tmp_path, turn_factory):
    """UT-13: mapping стабилен в сессии; bundle без реальных id; transcript хранит author."""
    ids = ["claude-opus", "codex-gpt", "kimi-k2"]
    anon1 = core.create_anon_map(ids, seed=42)
    anon2 = core.create_anon_map(ids, seed=42)
    assert anon1 == anon2
    assert sorted(anon1.values()) == ["M1", "M2", "M3"]

    records = [
        turn_factory(seq=1, author="claude-opus", content="позиция claude-opus: E1 сильнее", type="attack"),
        turn_factory(seq=2, author="codex-gpt", content="ответ на атаку", type="response"),
    ]
    bundle = core.render_bundle(records, anon1)
    for real_id in ids:
        assert real_id not in bundle
    assert anon1["claude-opus"] in bundle

    # transcript сохраняет реального автора (traceability)
    session_dir = tmp_path / "sess"
    session_dir.mkdir()
    stored = core.append_record(session_dir, records[0])
    assert stored["author"] == "claude-opus"
    # запись без anon_id (фаза A) — anon_id отсутствует; с anon_id (фаза B) — сохраняется
    assert stored["anon_id"] is None
    stored_b = core.append_record(session_dir, turn_factory(
        author="codex-gpt", type="response", wave="response"))
    assert stored_b["anon_id"] is None
    with_anon = core.append_record(session_dir, {
        "author": "codex-gpt", "type": "attack", "wave": "attack",
        "anon_id": anon1["codex-gpt"], "content": "ход фазы B",
    })
    assert with_anon["anon_id"] == anon1["codex-gpt"]
    loaded = core.read_transcript(session_dir)
    assert [r["author"] for r in loaded] == ["claude-opus", "codex-gpt", "codex-gpt"]


# ---------- UT-14..UT-16: роли (FR-08, AC-08) ----------

def test_ut14_phase_d_rotation_and_fallback():
    """UT-14: фаза D — роль, которой не было; fallback — наименее давно занимавшаяся."""
    role = core.assign_phase_d_role(
        "claude-opus",
        session_roles=["architecture"],
        role_history=[("architecture", 1), ("security", 2)],
    )
    assert role != "architecture"
    assert role in core.ROLE_CATALOG
    # все роли каталога уже были в этом консилиуме → fallback «наименее давно занимавшаяся»
    all_roles = list(core.ROLE_CATALOG)
    history = [("security", 5), ("architecture", 2), ("pragmatics", 3), ("эксплуатация", 4)]
    role = core.assign_phase_d_role("claude-opus", session_roles=all_roles, role_history=history)
    assert role == "architecture"  # занималась давнее всех (seq=2)


def test_ut15_strengths_floor_round_robin_fallback():
    """UT-15: n_eff < 3 → strengths игнорируется, round-robin с запретом повтора."""
    ids = ["p1", "p2", "p3"]
    strengths = {("p1", "security"): 0.99}  # статистика «тянет» p1 в security
    n_eff = {("p1", "security"): 2}  # ниже floor
    roles = core.assign_roles(ids, strengths=strengths, n_eff=n_eff, exploration=False,
                              last_roles={"p1": "architecture"})
    assert set(roles.values()) <= set(core.ROLE_CATALOG)
    assert len(set(roles.values())) == len(ids)  # round-robin без дублей
    assert roles["p1"] != "architecture"  # запрет повторной роли
    # strengths с n_eff>=3 применяется
    roles2 = core.assign_roles(ids, strengths={("p1", "security"): 0.99},
                               n_eff={("p1", "security"): 3}, exploration=False,
                               last_roles={})
    assert roles2["p1"] == "security"


def test_ut16_exploration_budget_every_4th():
    """UT-16: (c+1) % 4 == 0 → exploration round-robin вразрез статистике."""
    assert core.is_exploration_consilium(3) is True
    assert core.is_exploration_consilium(0) is False
    assert core.is_exploration_consilium(8) is False
    ids = ["p1", "p2", "p3"]
    roles = core.assign_roles(ids, strengths={("p1", "security"): 0.99},
                              n_eff={("p1", "security"): 10}, exploration=True, last_roles={})
    # exploration: strengths проигнорирован, роли — чистый round-robin
    assert roles == core.assign_roles_round_robin(ids, last_roles={})


# ---------- UT-17..UT-18: strengths (TBD-02, TD 3.4, AC-09) ----------

def test_ut17_strengths_formula(track_record_factory):
    """UT-17: веса 0.6/0.4, decay 0.5^(k/8), перенормировка при пустом знаменателе."""
    observations = track_record_factory([
        {"consilium_seq": 1, "findings_accepted": 3, "findings_withdrawn": 1,
         "critiques_upheld": 2, "critiques_overruled": 1},
        {"consilium_seq": 2, "findings_accepted": 1, "findings_withdrawn": 1,
         "critiques_upheld": 1, "critiques_overruled": 1},
    ])
    result = core.compute_strengths(observations)
    entry = result[("claude-opus", "architecture")]
    w1 = 0.5 ** (1 / 8)
    accept_rate = (w1 * 3 + 1) / (w1 * 4 + 2)
    upheld_rate = (w1 * 2 + 1) / (w1 * 3 + 2)
    assert entry["accept_rate"] == pytest.approx(accept_rate, rel=1e-9)
    assert entry["upheld_rate"] == pytest.approx(upheld_rate, rel=1e-9)
    assert entry["score"] == pytest.approx(0.6 * accept_rate + 0.4 * upheld_rate, rel=1e-9)
    assert entry["n_eff"] == pytest.approx(2.0)

    # пустой знаменатель accept → компонента исключается, веса перенормируются
    observations = track_record_factory([
        {"consilium_seq": 1, "findings_accepted": 0, "findings_withdrawn": 0,
         "critiques_upheld": 2, "critiques_overruled": 2},
    ])
    entry = core.compute_strengths(observations)[("claude-opus", "architecture")]
    assert entry["accept_rate"] is None
    assert entry["score"] == pytest.approx(0.5)  # = upheld_rate


def test_ut18_moderator_participant_segregation(track_record_factory):
    """UT-18: tainted исключены при >=3 clean; при <3 clean — tainted с x0.5; n_eff."""
    clean = [
        {"consilium_seq": i, "findings_accepted": 2, "findings_withdrawn": 0,
         "critiques_upheld": 1, "critiques_overruled": 0}
        for i in (1, 2, 3)
    ]
    tainted = [{"consilium_seq": 4, "moderator_is_participant": True,
                "findings_accepted": 0, "findings_withdrawn": 5,
                "critiques_upheld": 0, "critiques_overruled": 5}]
    entry = core.compute_strengths(track_record_factory(clean + tainted))[("claude-opus", "architecture")]
    assert entry["n_clean"] == 3
    assert entry["n_tainted"] == 1
    assert entry["n_eff"] == pytest.approx(3.0)  # tainted исключён
    assert entry["accept_rate"] == pytest.approx(1.0)  # провал tainted не влияет

    # clean < 3 → tainted подключаются с дисконтом x0.5
    entry = core.compute_strengths(track_record_factory(clean[:2] + tainted))[("claude-opus", "architecture")]
    assert entry["n_clean"] == 2
    assert entry["n_tainted"] == 1
    assert entry["n_eff"] == pytest.approx(2.5)  # 2 + 0.5*1
    assert entry["accept_rate"] < 1.0  # провал tainted учтён с дисконтом


# ---------- UT-19: transcript seq + no-rewrite (FR-04, AC-03) ----------

def test_ut19_transcript_seq_and_no_rewrite(tmp_path, turn_factory):
    """UT-19: append присваивает seq=last+1; перезапись существующей записи — отказ."""
    session_dir = tmp_path / "sess"
    session_dir.mkdir()
    r1 = core.append_record(session_dir, turn_factory(author="p1"))
    assert r1["seq"] == 1
    r2 = core.append_record(session_dir, turn_factory(author="p2"))
    assert r2["seq"] == 2
    # попытка записи с чужим/существующим seq — отказ
    with pytest.raises(core.ProtocolError):
        core.append_record(session_dir, turn_factory(seq=1, author="p1", content="подмена"))
    with pytest.raises(core.ProtocolError):
        core.append_record(session_dir, turn_factory(seq=5, author="p1"))
    loaded = core.read_transcript(session_dir)
    assert [r["seq"] for r in loaded] == [1, 2]


# ---------- UT-20: валидация adapters.yaml (FR-07, AC-06) ----------

def test_ut20_registry_validation(tmp_path, participant_entry, registry_factory):
    """UT-20: дубликат id, пустой family, несуществующий adapter, поле strengths — fail-closed."""
    good = registry_factory([
        participant_entry("claude-opus", "claude"),
        participant_entry("codex-gpt", "codex"),
    ])
    registry = core.load_registry(good)
    assert core.validate_registry(registry) == []
    assert "strengths" not in registry["participants"][0]

    dup = registry_factory([
        participant_entry("claude-opus", "claude"),
        participant_entry("claude-opus", "codex"),
    ])
    errors = core.validate_registry(core.load_registry(dup))
    assert any("дубликат" in e or "duplicate" in e for e in errors)

    empty_family = registry_factory([
        dict(participant_entry("claude-opus", "claude"), family=""),
        participant_entry("codex-gpt", "codex"),
    ])
    errors = core.validate_registry(core.load_registry(empty_family))
    assert any("family" in e for e in errors)

    missing_adapter = registry_factory([
        dict(participant_entry("claude-opus", "claude"), adapter="/nonexistent/adapter.py"),
        participant_entry("codex-gpt", "codex"),
    ])
    errors = core.validate_registry(core.load_registry(missing_adapter))
    assert any("adapter" in e for e in errors)

    with_strengths = registry_factory([
        dict(participant_entry("claude-opus", "claude"), strengths="security:0.9"),
        participant_entry("codex-gpt", "codex"),
    ])
    errors = core.validate_registry(core.load_registry(with_strengths))
    assert any("strengths" in e for e in errors)


# ---------- UT-21: счётчик вызовов (NFR-01, AC-13) ----------

def test_ut21_invocation_counting_and_budget():
    """UT-21: инкремент на ход участника (вкл. confirmation/final_statement), не на модератора;
    warn > 25; на 31 волны фазы B блокируются."""
    state = {"invocation_count": 0}
    for kind in ("participant_turn", "confirmation", "final_statement"):
        state = core.count_invocation(state, kind)
    assert state["invocation_count"] == 3
    for kind in ("digest", "synthesis", "verdict", "kill_decision"):
        state = core.count_invocation(state, kind)  # действия модератора — не считаются
    assert state["invocation_count"] == 3

    assert core.invocation_warning(25) is False
    assert core.invocation_warning(26) is True
    assert core.b_wave_blocked(30) is False
    assert core.b_wave_blocked(31) is True


# ---------- UT-22: wall-clock бюджет (TD 6, AC-18; CONS-02 OPT-1) ----------

def test_ut22_wall_clock_budget():
    """UT-22: предикат wall-clock от бюджета сессии; default-формула cap (CONS-02)."""
    now = datetime.now(UTC)
    budget, warn = core.wall_clock_budget(900)  # default T=900 → 16620/12465
    started_ok = (now - timedelta(seconds=100)).isoformat()
    started_warn = (now - timedelta(seconds=warn + 100)).isoformat()
    started_exceeded = (now - timedelta(seconds=budget + 1)).isoformat()
    assert core.wall_clock_status(started_ok, now=now) == "ok"
    assert core.wall_clock_status(started_warn, now=now) == "warn"
    assert core.wall_clock_status(started_exceeded, now=now) == "exceeded"
    assert budget == 16620
    assert warn == 12465
