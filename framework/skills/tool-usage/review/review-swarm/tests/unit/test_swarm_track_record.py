"""SU-TR — track record роя (RVSW-01, T-11, FR-12/AC-12, FR-15/AC-15, TD §5.7, §8.2).

Покрытие test-plan SU-TR01..TR08:
- SU-TR01: запись observations по схеме TD §5.7 (одна на участника на сессию)
  в `.swarm-track-record/`, отдельно от консилиумного;
- SU-TR02: decay w = 0.5^(k/8), ячейка (модель × роль), category — атрибут
  наблюдения, не ячейка формулы;
- SU-TR03: метрики accept_rate / upheld_rate_as_author /
  overrule_precision_as_attacker (отчётная, в score не входит), score = 0.6/0.4;
- SU-TR04: пустые ячейки (n_eff < 3) → round-robin (интеграция с assign_lenses);
- SU-TR05: маркеры forced/gate_pass исключают наблюдение из strength-проекции;
  auto_confirmed_overridden/calibration_run записываются, но не входы формул;
- SU-TR06: счётчики охвата неуникальных, precision, доля fixed, nit, in/out-lens;
- SU-TR07: калибровочная квота — calibration_run=true в observations;
- SU-TR08: config.json — reviews_completed инкрементируется на close,
  calibration_every — из конфига, не из кода.
"""
from __future__ import annotations

import json

import pytest

import swarm
import swarm_core
import track_record

# ---------------------------------------------------------------------------
# Фабрики состояния сессии (транспортная форма swarm.py)
# ---------------------------------------------------------------------------

def make_participant(pid, family="claude", lens="security", forced=False,
                     state="active"):
    return {
        "id": pid, "family": family, "state": state, "lens": lens,
        "lens_forced": forced, "review_id": None, "adapter_session_id": None,
        "invocations": 0, "retries": 0,
    }


def make_finding(fid, author, category="security", severity="P2",
                 in_lens=True, path="src/x.py"):
    return {
        "finding_id": fid, "author_id": author,
        "location": {"path": path, "line_start": 10, "line_end": 12},
        "category": category, "severity": severity, "in_lens": in_lens,
        "claim": f"claim {fid}", "evidence": "фрагмент", "rationale": "",
    }


def make_vote(voter, verdict, fid):
    return {
        "finding_id": fid, "voter_id": voter, "verdict": verdict,
        "rationale": "",
        "evidence": {"path": "src/x.py", "line": 10, "quote": "q"},
    }


def make_thread(fid, author, status, votes=(), tour=2):
    return {
        "finding_id": fid, "author_id": author, "status": status,
        "phase": None, "exchanges": 1,
        "waves": [{"tour": tour, "votes": list(votes)}] if votes else [],
        "author_response": None, "evidence": [],
    }


def make_cluster(cid, findings, auto_confirmed, overridden=False):
    return {
        "cluster_id": cid, "path": findings[0]["location"]["path"],
        "category": findings[0]["category"],
        "line_start": 10, "line_end": 12,
        "findings": findings,
        "authors": sorted({f["author_id"] for f in findings}),
        "auto_confirmed": auto_confirmed,
        "auto_confirmed_overridden": overridden,
    }


def make_session(participants, findings, threads=None, dedup=None,
                 arbitration=None, **extra):
    session = {
        "session_id": "swarm-20260729-000000-deadbeef",
        "participants": participants,
        "findings": findings,
        "routed_findings": [],
        "threads": threads or {},
        "dedup": dedup,
        "arbitration": arbitration or {},
        "calibration_run": False,
        "quota_mode": None,
        "quota_fallback_reason": None,
    }
    session.update(extra)
    return session


def full_session():
    """Сессия с покрытием всех веток счётчиков TD §5.7."""
    participants = [
        make_participant("p1", lens="security"),
        make_participant("p2", lens="correctness"),
        make_participant("p3", lens="concurrency"),
    ]
    f1 = make_finding("F-001", "p1")                                   # thread confirmed
    f2 = make_finding("F-002", "p1", severity="P4")                    # thread withdrawn (nit)
    f3 = make_finding("F-003", "p2", category="concurrency",
                      severity="P1", in_lens=False)                    # contested → арбитраж overruled
    f4 = make_finding("F-004", "p2", category="concurrency")           # nonunique
    f5 = make_finding("F-005", "p3", category="concurrency")           # nonunique
    findings = [f1, f2, f3, f4, f5]
    dedup = {
        "clusters": [
            make_cluster("C-001", [f1], auto_confirmed=False),
            make_cluster("C-002", [f2], auto_confirmed=False),
            make_cluster("C-003", [f3], auto_confirmed=False),
            make_cluster("C-004", [f4, f5], auto_confirmed=True),
        ],
        "groups": {"nonunique_auto_confirmed": [], "unique_unconfirmed": []},
        "borderline": [],
    }
    threads = {
        "F-001": make_thread("F-001", "p1", "confirmed", votes=[
            make_vote("p2", "upheld", "F-001"),
            make_vote("p3", "upheld", "F-001"),
        ]),
        "F-002": make_thread("F-002", "p1", "withdrawn", votes=[
            make_vote("p2", "overruled", "F-002"),
            make_vote("p3", "overruled", "F-002"),
        ]),
        "F-003": make_thread("F-003", "p2", "contested", votes=[
            make_vote("p1", "overruled", "F-003"),
            make_vote("p3", "overruled", "F-003"),
        ]),
    }
    arbitration = {
        "F-003": {"finding_id": "F-003", "decision": "overruled",
                  "evidence_quote": "q", "location": {"path": "src/x.py", "line": 10},
                  "rationale": "", "ts": "2026-07-29T00:00:00+00:00"},
    }
    return make_session(participants, findings, threads=threads, dedup=dedup,
                        arbitration=arbitration)


def obs_by_pid(observations, pid):
    return next(o for o in observations if o["participant_id"] == pid)


def make_observation(pid, role, seq, **fields):
    base = {
        "review_session_id": f"swarm-seq{seq}", "date": "2026-07-29",
        "participant_id": pid, "family": "claude", "role": role,
        "category": None, "forced": False, "gate_pass": False,
        "findings_unique_confirmed": 0, "findings_unique_unconfirmed": 0,
        "findings_nonunique": 0, "nonunique_missed": 0,
        "upheld_as_author": 0, "overruled_as_author": 0, "unvalidated": 0,
        "attacks_made": 0, "attacks_confirmed_overrule": 0,
        "auto_confirmed_overridden": 0,
        "fixed": 0, "partially": 0, "not_fixed": 0,
        "nit_count": 0, "in_lens_count": 0, "out_of_lens_count": 0,
        "quota_mode": None, "quota_fallback_reason": None,
        "calibration_run": False,
        "review_seq": seq,
    }
    base.update(fields)
    return base


# ---------------------------------------------------------------------------
# SU-TR01: запись observations по схеме TD §5.7
# ---------------------------------------------------------------------------

SCHEMA_FIELDS = {
    "review_session_id", "date", "participant_id", "family", "role",
    "category", "forced", "gate_pass",
    "findings_unique_confirmed", "findings_unique_unconfirmed",
    "findings_nonunique", "nonunique_missed",
    "upheld_as_author", "overruled_as_author", "unvalidated",
    "attacks_made", "attacks_confirmed_overrule",
    "auto_confirmed_overridden",
    "fixed", "partially", "not_fixed",
    "nit_count", "in_lens_count", "out_of_lens_count",
    "quota_mode", "quota_fallback_reason", "calibration_run",
    "review_seq",
}


def test_su_tr01_one_observation_per_participant_schema():
    """SU-TR01: одна observation на участника на сессию; все поля схемы TD §5.7;
    role — линза сессии, forced — из lens_forced."""
    session = full_session()
    observations = swarm_core.build_observations(session, review_seq=1,
                                                 date="2026-07-29")
    assert len(observations) == 3
    for obs in observations:
        assert SCHEMA_FIELDS <= set(obs), (
            f"схема observation неполна: {SCHEMA_FIELDS - set(obs)}"
        )
        assert obs["review_session_id"] == session["session_id"]
        assert obs["review_seq"] == 1
        assert obs["date"] == "2026-07-29"
        assert obs["gate_pass"] is False
    assert obs_by_pid(observations, "p1")["role"] == "security"
    assert obs_by_pid(observations, "p2")["role"] == "correctness"
    assert obs_by_pid(observations, "p1")["forced"] is False


def test_su_tr01_forced_lens_marked_in_observation():
    """SU-TR01/FR-11: forced-назначение линзы помечается в observation."""
    participants = [make_participant("p1", lens="security", forced=True)]
    session = make_session(participants, [])
    (obs,) = swarm_core.build_observations(session, review_seq=1,
                                           date="2026-07-29")
    assert obs["forced"] is True
    assert obs["role"] == "security"


def test_su_tr01_write_track_record_durable_and_separate(tmp_path):
    """SU-TR01: write_track_record пишет `.swarm-track-record/` (durable),
    НЕ смешивается с `.consilium-track-record/`; observations.jsonl append-only,
    review_seq — из счётчика reviews_completed."""
    session = full_session()
    swarm.write_track_record(tmp_path, session)
    track_dir = tmp_path / ".swarm-track-record"
    assert (track_dir / "observations.jsonl").exists()
    assert not (tmp_path / ".consilium-track-record").exists(), (
        "track record роя смешан с консилиумным"
    )
    observations = track_record.read_observations(track_dir)
    assert len(observations) == 3
    assert {o["review_seq"] for o in observations} == {1}

    other = make_session([make_participant("p9", lens="tests")], [])
    swarm.write_track_record(tmp_path, other)
    observations = track_record.read_observations(track_dir)
    assert len(observations) == 4, "observations.jsonl не append-only"
    assert obs_by_pid(observations, "p9")["review_seq"] == 2


# ---------------------------------------------------------------------------
# SU-TR02: decay half-life 8, ячейка (модель × роль), category — атрибут
# ---------------------------------------------------------------------------

def test_su_tr02_decay_half_life_8_reference_values():
    """SU-TR02: w = 0.5^(k/8) — эталон: свежая observation весит вдвое больше
    observation восьмисессионной давности."""
    observations = [
        make_observation("p1", "security", 1,
                         findings_unique_confirmed=0,
                         findings_unique_unconfirmed=1),
        make_observation("p1", "security", 9,
                         findings_unique_confirmed=1,
                         findings_unique_unconfirmed=0),
    ]
    strengths = swarm_core.compute_swarm_strengths(observations)
    cell = strengths[("p1", "security")]
    # w_stale = 0.5^(8/8) = 0.5, w_fresh = 1.0 → accept = 1.0 / (0.5 + 1.0)
    assert cell["accept_rate"] == pytest.approx(1.0 / 1.5)
    assert cell["n_eff"] == 2.0


def test_su_tr02_cell_is_model_x_role_category_is_attribute():
    """SU-TR02: ячейка формулы — (participant × role); category не создаёт
    отдельных ячеек (атрибут наблюдения для отчётности)."""
    observations = [
        make_observation("p1", "security", 1, category="security",
                         findings_unique_confirmed=1),
        make_observation("p1", "security", 2, category="correctness",
                         findings_unique_confirmed=1),
        make_observation("p1", "correctness", 3, category="correctness",
                         findings_unique_confirmed=1),
    ]
    strengths = swarm_core.compute_swarm_strengths(observations)
    assert set(strengths) == {("p1", "security"), ("p1", "correctness")}, (
        "category породила лишнюю ячейку — она атрибут, не ячейка (FR-12)"
    )
    assert strengths[("p1", "security")]["n_eff"] == 2.0
    assert strengths[("p1", "correctness")]["n_eff"] == 1.0


# ---------------------------------------------------------------------------
# SU-TR03: метрики проекции и score = 0.6/0.4
# ---------------------------------------------------------------------------

def test_su_tr03_projection_metrics_and_score_weights():
    """SU-TR03: accept_rate по уникальным подтверждённым; upheld_rate_as_author;
    score = 0.6·accept_rate + 0.4·upheld_rate_as_author (веса наследуются)."""
    observations = [
        make_observation("p1", "security", seq,
                         findings_unique_confirmed=1, upheld_as_author=1,
                         overruled_as_author=1)
        for seq in (1, 2, 3)
    ]
    strengths = swarm_core.compute_swarm_strengths(observations)
    cell = strengths[("p1", "security")]
    assert cell["accept_rate"] == pytest.approx(1.0)   # unconfirmed = 0
    assert cell["upheld_rate_as_author"] == pytest.approx(0.5)
    assert cell["score"] == pytest.approx(
        track_record.SCORE_W_ACCEPT * 1.0 + track_record.SCORE_W_UPHELD * 0.5
    )
    assert track_record.SCORE_W_ACCEPT == 0.6
    assert track_record.SCORE_W_UPHELD == 0.4


def test_su_tr03_overrule_precision_is_report_only_not_in_score():
    """SU-TR03: overrule_precision_as_attacker = Σw·attacks_confirmed_overrule /
    Σw·attacks_made — отчётная метрика качества атаки, в score НЕ входит."""
    base = dict(findings_unique_confirmed=1, upheld_as_author=1)
    weak_attacker = [make_observation("p1", "security", s, **base,
                                      attacks_made=10,
                                      attacks_confirmed_overrule=0)
                     for s in (1, 2, 3)]
    sharp_attacker = [make_observation("p1", "security", s, **base,
                                       attacks_made=10,
                                       attacks_confirmed_overrule=9)
                      for s in (1, 2, 3)]
    weak = swarm_core.compute_swarm_strengths(weak_attacker)[("p1", "security")]
    sharp = swarm_core.compute_swarm_strengths(sharp_attacker)[("p1", "security")]
    assert weak["overrule_precision_as_attacker"] == pytest.approx(0.0)
    assert sharp["overrule_precision_as_attacker"] == pytest.approx(0.9)
    assert weak["score"] == pytest.approx(sharp["score"]), (
        "overrule_precision_as_attacker попала в score (TD §5.7 — отчётная метрика)"
    )


# ---------------------------------------------------------------------------
# SU-TR04: пустые ячейки (n_eff < 3) → round-robin
# ---------------------------------------------------------------------------

def test_su_tr04_lens_history_feeds_balanced_rotation():
    """SU-TR04: история ячеек из observations (цикл запрета повтора + n_eff)
    — вход assign_lenses; пустые ячейки (n_eff < FLOOR) → сбалансированная
    ротация, exploitation отсутствует."""
    observations = [
        make_observation("p1", "security", 1, findings_unique_confirmed=5),
        make_observation("p1", "correctness", 2, findings_unique_confirmed=5),
    ]
    history = swarm_core.lens_history(observations)
    cell = history["p1"]
    assert cell["lenses_used"] == ["security", "correctness"]
    assert cell["n_eff"] == {"security": 1.0, "correctness": 1.0}
    assert cell["n_eff"]["security"] < track_record.STRENGTHS_FLOOR

    assignments = swarm_core.assign_lenses(["p1"], {}, history)
    assert assignments["p1"][0] not in {"security", "correctness"}, (
        "повтор линзы в цикле — round-robin нарушен"
    )
    assert assignments["p1"][1] is False


def test_su_tr04_empty_cells_round_robin_no_argmax():
    """SU-TR04: все ячейки пусты (n_eff < 3) → назначение round-robin даже при
    «сильной» ячейке (высокий accept_rate не притягивает)."""
    observations = [
        make_observation("p1", "security", 1, findings_unique_confirmed=10),
        make_observation("p1", "security", 2, findings_unique_confirmed=10),
    ]
    history = swarm_core.lens_history(observations)
    seen = set()
    for _ in range(4):
        assignments = swarm_core.assign_lenses(["p1"], {}, history)
        lens = assignments["p1"][0]
        seen.add(lens)
        history["p1"]["lenses_used"].append(lens)
    assert len(seen) == 4, f"ротация концентрируется на «сильной» ячейке: {seen}"


def test_su_tr04_cycle_reset_after_pool_exhaustion():
    """SU-TR04: цикл запрета повтора восстанавливается из observations:
    после полного покрытия пула повтор легален (новый цикл)."""
    lenses = swarm_core.CODE_REVIEW_LENSES
    observations = [
        make_observation("p1", lens, seq) for seq, lens in enumerate(lenses, 1)
    ] + [make_observation("p1", lenses[0], len(lenses) + 1)]
    history = swarm_core.lens_history(observations)
    assert history["p1"]["lenses_used"] == [lenses[0]], (
        "повтор после исчерпания пула — начало нового цикла"
    )


# ---------------------------------------------------------------------------
# SU-TR05: маркеры вне strength-формул
# ---------------------------------------------------------------------------

def test_su_tr05_forced_and_gate_pass_excluded_from_projection():
    """SU-TR05: observations с forced=true / gate_pass=true исключаются из
    strength-проекции ЦЕЛИКОМ (TD §5.7 — назначение не свободный выбор модели)."""
    clean = [make_observation("p1", "security", s, findings_unique_confirmed=1)
             for s in (1, 2, 3)]
    forced = [make_observation("p1", "security", 4, forced=True,
                               findings_unique_confirmed=0,
                               findings_unique_unconfirmed=10)]
    gated = [make_observation("p2", "correctness", 4, gate_pass=True,
                              findings_unique_confirmed=10)]
    strengths = swarm_core.compute_swarm_strengths(clean + forced + gated)
    assert strengths[("p1", "security")]["n_eff"] == 3.0, (
        "forced-наблюдение попало в strength-проекцию"
    )
    assert strengths[("p1", "security")]["accept_rate"] == pytest.approx(1.0)
    assert ("p2", "correctness") not in strengths, (
        "gate_pass-наблюдение попало в strength-проекцию"
    )


def test_su_tr05_all_marked_observations_empty_projection():
    """SU-TR05: все наблюдения ячейки помечены → ячейки нет (пустые ячейки →
    round-robin на назначении, SU-TR04)."""
    observations = [make_observation("p1", "security", 1, forced=True)]
    assert swarm_core.compute_swarm_strengths(observations) == {}
    history = swarm_core.lens_history(observations)
    assert history == {} or history["p1"]["n_eff"] == {}


def test_su_tr05_auto_confirmed_overridden_recorded_not_formula_input():
    """SU-TR05: auto_confirmed_overridden записывается в observation (FR-06),
    но не вход в strength-формул (score не зависит от маркера)."""
    base = dict(findings_unique_confirmed=1, upheld_as_author=1)
    without = [make_observation("p1", "security", s, **base) for s in (1, 2, 3)]
    with_marker = [make_observation("p1", "security", s, **base,
                                    auto_confirmed_overridden=2)
                   for s in (1, 2, 3)]
    score_without = swarm_core.compute_swarm_strengths(without)[("p1", "security")]
    score_with = swarm_core.compute_swarm_strengths(with_marker)[("p1", "security")]
    assert score_with["score"] == pytest.approx(score_without["score"])


def test_su_tr05_overridden_cluster_counted_in_observation():
    """SU-TR05/FR-06: находки кластера со снятым автоподтверждением — счётчик
    auto_confirmed_overridden в observation автора; исход — по треду."""
    participants = [make_participant("p1"), make_participant("p2")]
    f1 = make_finding("F-001", "p1")
    f2 = make_finding("F-002", "p2")
    dedup = {
        "clusters": [make_cluster("C-001", [f1, f2], auto_confirmed=False,
                                  overridden=True)],
        "groups": {"nonunique_auto_confirmed": [], "unique_unconfirmed": []},
        "borderline": [],
    }
    threads = {
        "F-001": make_thread("F-001", "p1", "withdrawn"),
        "F-002": make_thread("F-002", "p2", "confirmed"),
    }
    session = make_session(participants, [f1, f2], threads=threads, dedup=dedup)
    observations = swarm_core.build_observations(session, review_seq=1,
                                                 date="2026-07-29")
    assert obs_by_pid(observations, "p1")["auto_confirmed_overridden"] == 1
    assert obs_by_pid(observations, "p2")["auto_confirmed_overridden"] == 1
    # неуникальный статус снят override'ом → находки уникальные, исход по треду
    assert obs_by_pid(observations, "p1")["findings_unique_unconfirmed"] == 1
    assert obs_by_pid(observations, "p2")["findings_unique_confirmed"] == 1


# ---------------------------------------------------------------------------
# SU-TR06: счётчики — охват, precision, доля fixed, nit, in/out-lens
# ---------------------------------------------------------------------------

def test_su_tr06_observation_counters_from_session():
    """SU-TR06: счётчики observation собираются из состояния сессии (TD §5.7):
    unique confirmed/unconfirmed, nonunique + охват (missed), атаки, nit,
    in-lens/out-of-lens, категория как атрибут."""
    observations = swarm_core.build_observations(full_session(), review_seq=1,
                                                 date="2026-07-29")
    p1 = obs_by_pid(observations, "p1")
    assert p1["findings_unique_confirmed"] == 1      # F-001 confirmed
    assert p1["findings_unique_unconfirmed"] == 1    # F-002 withdrawn
    assert p1["findings_nonunique"] == 0
    assert p1["nonunique_missed"] == 1               # кластер F-004/F-005 без p1
    assert p1["upheld_as_author"] == 1
    assert p1["overruled_as_author"] == 1
    assert p1["attacks_made"] == 1                   # overruled-вотум по F-003
    assert p1["attacks_confirmed_overrule"] == 1     # F-003 завалена (арбитраж)
    assert p1["nit_count"] == 1                      # F-002 severity P4
    assert p1["in_lens_count"] == 2
    assert p1["out_of_lens_count"] == 0
    assert p1["category"] == "security"              # атрибут отчётности
    assert p1["fixed"] == 0 and p1["not_fixed"] == 0  # заготовка T-12

    p2 = obs_by_pid(observations, "p2")
    assert p2["findings_unique_unconfirmed"] == 1    # F-003 contested → overruled
    assert p2["findings_nonunique"] == 1             # F-004
    assert p2["nonunique_missed"] == 0
    assert p2["out_of_lens_count"] == 1              # F-003 in_lens=false
    assert p2["attacks_made"] == 1                   # overruled-вотум по F-002
    assert p2["attacks_confirmed_overrule"] == 1     # F-002 withdrawn

    p3 = obs_by_pid(observations, "p3")
    assert p3["findings_nonunique"] == 1             # F-005
    assert p3["attacks_made"] == 2                   # overruled по F-002 и F-003
    assert p3["attacks_confirmed_overrule"] == 2     # обе находки завалены


def test_su_tr06_report_metrics_coverage_precision_fixed():
    """SU-TR06: проекция отчётных метрик: охват неуникальных, precision
    (подтверждённые/всего выдвинутых), доля fixed, счётчики nit и in/out-lens."""
    observations = [
        make_observation("p1", "security", s,
                         findings_unique_confirmed=1,
                         findings_unique_unconfirmed=1,
                         findings_nonunique=2, nonunique_missed=2,
                         fixed=1, partially=0, not_fixed=1,
                         nit_count=1, in_lens_count=3, out_of_lens_count=1)
        for s in (1, 2, 3)
    ]
    cell = swarm_core.compute_swarm_strengths(observations)[("p1", "security")]
    assert cell["coverage_nonunique"] == pytest.approx(0.5)   # 2 / (2 + 2)
    assert cell["precision"] == pytest.approx(3.0 / 4.0)      # (1+2) / (1+1+2)
    assert cell["fixed_rate"] == pytest.approx(0.5)           # 1 / (1 + 0 + 1)
    # счётчики — decay-взвешенные суммы: w = (0.5^(2/8), 0.5^(1/8), 1)
    weight_sum = 0.5 ** (2 / 8) + 0.5 ** (1 / 8) + 1.0
    assert cell["nit_count"] == pytest.approx(weight_sum)
    assert cell["in_lens_count"] == pytest.approx(3.0 * weight_sum)
    assert cell["out_of_lens_count"] == pytest.approx(weight_sum)


def test_su_tr06_rates_none_on_empty_denominator():
    """SU-TR06: пустой знаменатель → None (нет данных), не деление на ноль."""
    observations = [make_observation("p1", "security", 1)]
    cell = swarm_core.compute_swarm_strengths(observations)[("p1", "security")]
    assert cell["accept_rate"] is None
    assert cell["upheld_rate_as_author"] is None
    assert cell["overrule_precision_as_attacker"] is None
    assert cell["coverage_nonunique"] is None
    assert cell["precision"] is None
    assert cell["fixed_rate"] is None
    assert cell["score"] == 0.0


# ---------------------------------------------------------------------------
# F-03 (R-Final): unvalidated — атака не состоялась, находка НЕ overruled
# ---------------------------------------------------------------------------

def test_f03_open_thread_is_unvalidated_not_overruled():
    """F-03 (R-Final): тред open (обрыв по wall-clock / волна без валидных
    вотумов) — атака не состоялась: находка пишется в счётчик unvalidated и
    НЕ попадает в overruled_as_author / findings_unique_unconfirmed."""
    participants = [make_participant("p1"), make_participant("p2")]
    f1 = make_finding("F-001", "p1")                    # open — unvalidated
    f2 = make_finding("F-002", "p1")                    # withdrawn — overruled
    dedup = {
        "clusters": [
            make_cluster("C-001", [f1], auto_confirmed=False),
            make_cluster("C-002", [f2], auto_confirmed=False),
        ],
        "groups": {"nonunique_auto_confirmed": [], "unique_unconfirmed": []},
        "borderline": [],
    }
    threads = {
        "F-001": make_thread("F-001", "p1", "open"),
        "F-002": make_thread("F-002", "p1", "withdrawn", votes=[
            make_vote("p2", "overruled", "F-002"),
        ]),
    }
    session = make_session(participants, [f1, f2], threads=threads, dedup=dedup)
    observations = swarm_core.build_observations(session, review_seq=1,
                                                 date="2026-07-29")
    p1 = obs_by_pid(observations, "p1")
    assert p1["unvalidated"] == 1                      # F-001 — атаки не было
    assert p1["findings_unique_unconfirmed"] == 1      # только withdrawn F-002
    assert p1["overruled_as_author"] == 1
    assert p1["findings_unique_confirmed"] == 0
    assert p1["upheld_as_author"] == 0


def test_f03_finding_without_thread_is_unvalidated():
    """F-03 (R-Final): находка без треда (ранний close из DEDUP, routed из
    re-review) — атака не состоялась: unvalidated, не overruled (FR-12:
    upheld/overruled — только по итогу состоявшейся атаки)."""
    session = make_session([make_participant("p1")],
                           [make_finding("F-001", "p1")])
    (obs,) = swarm_core.build_observations(session, review_seq=1,
                                           date="2026-07-29")
    assert obs["unvalidated"] == 1
    assert obs["findings_unique_unconfirmed"] == 0
    assert obs["overruled_as_author"] == 0


def test_f03_unvalidated_excluded_from_strength_denominators():
    """F-03 (R-Final): unvalidated не входит в знаменатели accept_rate и
    upheld_rate_as_author — ячейка только с unvalidated не имеет rate (None),
    score = 0 (атака не состоялась → основания оценки нет)."""
    observations = [make_observation("p1", "security", 1, unvalidated=3)]
    cell = swarm_core.compute_swarm_strengths(observations)[("p1", "security")]
    assert cell["accept_rate"] is None
    assert cell["upheld_rate_as_author"] is None
    assert cell["score"] == 0.0


# ---------------------------------------------------------------------------
# SU-TR07: калибровочная квота — calibration_run в observations
# ---------------------------------------------------------------------------
def test_su_tr07_calibration_run_recorded_in_observations():
    """SU-TR07 (TD §8.2): калибровочный прогон фиксируется маркером
    calibration_run=true в observations всех участников сессии."""
    session = make_session([make_participant("p1"), make_participant("p2")], [],
                           calibration_run=True)
    observations = swarm_core.build_observations(session, review_seq=4,
                                                 date="2026-07-29")
    assert all(o["calibration_run"] is True for o in observations)


def test_su_tr07_calibration_observations_feed_statistics():
    """SU-TR07 (FR-15): калибровочные наблюдения КАЛИБРУЮТ статистику —
    входят в strength-проекцию (иначе выборка смещена, RISK-07); маркер
    calibration_run сам не вход формулы."""
    plain = [make_observation("p1", "security", s, findings_unique_confirmed=1)
             for s in (1, 2)]
    calibration = [make_observation("p1", "security", 3, calibration_run=True,
                                    findings_unique_confirmed=1)]
    strengths = swarm_core.compute_swarm_strengths(plain + calibration)
    assert strengths[("p1", "security")]["n_eff"] == 3.0, (
        "калибровочное наблюдение выпало из статистики — выборка смещена (RISK-07)"
    )


def test_su_tr07_quota_marks_carried_to_observation():
    """SU-TR07/FR-14: quota_mode и quota_fallback_reason сессии переносятся
    в observations (квотно-слепая ротация маркируется, не молчаливая оценка)."""
    session = make_session([make_participant("p1")], [],
                           quota_mode="blind",
                           quota_fallback_reason="introspection unavailable: kimi")
    (obs,) = swarm_core.build_observations(session, review_seq=1,
                                           date="2026-07-29")
    assert obs["quota_mode"] == "blind"
    assert obs["quota_fallback_reason"] == "introspection unavailable: kimi"


# ---------------------------------------------------------------------------
# SU-TR08: config.json — счётчики на close, calibration_every из конфига
# ---------------------------------------------------------------------------

def test_su_tr08_reviews_completed_incremented_on_write(tmp_path):
    """SU-TR08: reviews_completed инкрементируется на запись track record
    (close сессии); light_reviews_completed — счётчик лёгкого тарифа (T-13)
    тем же механизмом bump_counter."""
    session = full_session()
    swarm.write_track_record(tmp_path, session)
    config = track_record.read_track_config(tmp_path / ".swarm-track-record")
    assert config["reviews_completed"] == 1
    swarm.write_track_record(tmp_path, session)
    config = track_record.read_track_config(tmp_path / ".swarm-track-record")
    assert config["reviews_completed"] == 2

    track_dir = tmp_path / ".swarm-track-record"
    assert track_record.bump_counter(track_dir, "light_reviews_completed") == 1
    assert track_record.bump_counter(track_dir, "light_reviews_completed") == 2
    config = track_record.read_track_config(track_dir)
    assert config["reviews_completed"] == 2, "bump light-счётчика тронул reviews_completed"


def test_su_tr08_calibration_every_seeded_from_config_not_code(tmp_path):
    """SU-TR08 (TD §8.2): calibration_every живёт в config.json (пересмотр N
    без правки кода); write_track_record засевает 4 и НЕ затирает пересмотренное."""
    session = full_session()
    track_dir = tmp_path / ".swarm-track-record"
    swarm.write_track_record(tmp_path, session)
    config = track_record.read_track_config(track_dir)
    assert config["calibration_every"] == 4

    # пересмотр N Оркестратором — правкой конфига, не кода
    config["calibration_every"] = 7
    track_record.write_track_config(track_dir, config)
    swarm.write_track_record(tmp_path, session)
    assert track_record.read_track_config(track_dir)["calibration_every"] == 7
    assert swarm.calibration_due({"light_reviews_completed": 6,
                                  "calibration_every": 7}) is True


def test_su_tr08_strengths_json_regenerated_cache(tmp_path):
    """SU-TR08: strengths.json — генерируемый кэш из observations (источник
    истины — observations.jsonl); ключи «pid|role», метрики TD §5.7."""
    session = full_session()
    swarm.write_track_record(tmp_path, session)
    strengths_path = tmp_path / ".swarm-track-record" / "strengths.json"
    assert strengths_path.exists()
    strengths = json.loads(strengths_path.read_text(encoding="utf-8"))
    assert "p1|security" in strengths
    cell = strengths["p1|security"]
    assert cell["n_eff"] == 1.0
    assert cell["accept_rate"] == pytest.approx(0.5)
    assert cell["upheld_rate_as_author"] == pytest.approx(0.5)
    assert cell["overrule_precision_as_attacker"] == pytest.approx(1.0)


# ---------------------------------------------------------------------------
# История линз из track record для convene (тонкий IO-слой swarm.py)
# ---------------------------------------------------------------------------

def test_load_lens_history_from_track_dir(tmp_path):
    """convene читает историю ячеек из .swarm-track-record/ (exploration до
    n_eff); отсутствующий каталог → пустая история (дефолт FR-11)."""
    assert swarm.load_lens_history(tmp_path) == {}
    track_dir = tmp_path / ".swarm-track-record"
    track_record.append_observations(track_dir, [
        make_observation("p1", "security", 1),
        make_observation("p1", "security", 2, forced=True),  # forced вне цикла
        make_observation("p1", "correctness", 3),
    ])
    history = swarm.load_lens_history(tmp_path)
    assert history["p1"]["lenses_used"] == ["security", "correctness"], (
        "forced-наблюдение попало в цикл запрета повтора"
    )
    assert history["p1"]["n_eff"] == {"security": 1.0, "correctness": 1.0}
