"""SU-RT — сбалансированная ротация линз роя (RVSW-01, T-07, FR-11/AC-11, TD §5.8).

Покрытие test-plan SU-RT01..RT05:
- SU-RT01: пакет `code-review` в review-harness/domains.yaml — 6 линз TD §5.8,
  machine-readable risk_checklist, applies_to: [tour1]; валиден по domains.py
  ТОЛЬКО с wave_types туров роя (enum harness не расширяется — граница владения);
- SU-RT02: запрет повтора линзы участником до исчерпания пула;
- SU-RT03: до n_eff по паре (модель × роль) — нет argmax-by-strengths,
  сбалансированная ротация (exploration — дефолт); score/опыт ячейки назначение
  не притягивает;
- SU-RT04: путь из карты критичности → forced-линза (например secrets/** →
  security), маркер forced=True, исключение из strength-входов (TD §5.7);
- SU-RT05: остальные линзы при forced-назначении — сбалансированной ротацией.
"""
from __future__ import annotations

from pathlib import Path

import pytest

import domains
import swarm_core

DOMAINS_PATH = (
    Path(__file__).resolve().parents[3] / "review-harness" / "domains.yaml"
)


def _record_rotation_input(history: dict, assignments: dict) -> dict:
    """Эмуляция записи НЕ-forced наблюдений (strength_inputs) в историю ячеек:
    lenses_used — цикл запрета повтора; n_eff — счётчик ячейки (модель × роль)."""
    history = {pid: {"lenses_used": list(cell.get("lenses_used", [])),
                     "n_eff": dict(cell.get("n_eff", {}))}
               for pid, cell in history.items()}
    for pid, lens in swarm_core.strength_inputs(assignments).items():
        cell = history.setdefault(pid, {"lenses_used": [], "n_eff": {}})
        cell["lenses_used"].append(lens)
        cell["n_eff"][lens] = cell["n_eff"].get(lens, 0) + 1
    return history


# ---------- SU-RT01: пакет code-review в domains.yaml ----------

def test_su_rt01_code_review_pack_valid():
    """SU-RT01: 6 линз TD §5.8, machine-readable чеклисты, applies_to: [tour1];
    реестр валиден по domains.py с wave_types туров роя."""
    registry = domains.load_domains(DOMAINS_PATH, wave_types=swarm_core.SWARM_WAVE_TYPES)
    assert domains.validate_domains(registry, wave_types=swarm_core.SWARM_WAVE_TYPES) == []
    domain = domains.domain_by_id(registry, "code-review")
    assert domain is not None, "доменный пакет code-review отсутствует в domains.yaml"
    role_ids = [role["id"] for role in domain["roles"]]
    assert role_ids == list(swarm_core.CODE_REVIEW_LENSES)
    assert role_ids == [
        "security", "correctness", "concurrency",
        "performance", "data-contracts", "tests",
    ]
    for role in domain["roles"]:
        assert role["title"] and role["lens"], f"роль {role['id']}: нет title/lens"
        assert role["risk_checklist"], f"роль {role['id']}: пустой risk_checklist"
        for item in role["risk_checklist"]:
            assert item["item_id"] and item["text"], "пункт чеклиста не machine-readable"
            assert item["applies_to"] == ["tour1"], (
                f"{role['id']}/{item['item_id']}: чеклисты роя применяются в туре 1 (TD §5.8)"
            )
    assert domain["evidence_requirements"]


def test_su_rt01_tour1_outside_harness_enum_fail_closed():
    """SU-RT01 (граница): CHECKLIST_WAVE_TYPES harness НЕ расширяется tour-метками;
    валидация реестра без wave_types роя — fail-closed (applies_to вне enum)."""
    assert "tour1" not in domains.CHECKLIST_WAVE_TYPES
    with pytest.raises(domains.DomainsError):
        domains.load_domains(DOMAINS_PATH)


# ---------- SU-RT02: запрет повтора до исчерпания пула ----------

def test_su_rt02_no_repeat_within_cycle():
    """SU-RT02: участник не получает линзу, уже отыгранную в текущем цикле."""
    history = {"p1": {"lenses_used": ["security"], "n_eff": {"security": 1}}}
    assignments = swarm_core.assign_lenses(["p1", "p2", "p3"], {}, history)
    assert assignments["p1"][0] != "security"
    assert assignments["p1"][1] is False


def test_su_rt02_full_cycle_covers_pool_then_reset():
    """SU-RT02: за 6 сессий участник покрывает весь пул без повтора; на 7-й
    (пул исчерпан) повтор разрешён — цикл сбрасывается."""
    history: dict = {}
    seen: list[str] = []
    for _ in swarm_core.CODE_REVIEW_LENSES:
        assignments = swarm_core.assign_lenses(["p1"], {}, history)
        lens, forced = assignments["p1"]
        assert forced is False
        seen.append(lens)
        history = _record_rotation_input(history, assignments)
    assert set(seen) == set(swarm_core.CODE_REVIEW_LENSES), (
        f"цикл не покрыл пул линз: {seen}"
    )
    assignments = swarm_core.assign_lenses(["p1"], {}, history)
    assert assignments["p1"][0] in seen  # исчерпание пула — повтор легален


def test_su_rt02_no_lens_duplication_within_session():
    """SU-RT02: в одной сессии линзы распределяются без дублей (пока линз хватает)."""
    participants = ["p1", "p2", "p3", "p4"]
    assignments = swarm_core.assign_lenses(participants, {}, {})
    lenses = [lens for lens, _ in assignments.values()]
    assert len(set(lenses)) == len(participants)


# ---------- SU-RT03: нет argmax-by-strengths до n_eff ----------

def test_su_rt03_no_argmax_by_strengths():
    """SU-RT03: «сильная» ячейка (высокий score / накопленный n_eff) НЕ притягивает
    назначение — ротация сбалансированная (min n_eff), exploitation запрещён до n_eff."""
    history = {
        "p1": {
            "lenses_used": [],
            "n_eff": {"security": 2.5},  # ниже STRENGTHS_FLOOR=3, но максимум среди ячеек
            "score": {"security": 0.99},  # шум: strengths-статистика ротацией игнорируется
        }
    }
    assignments = swarm_core.assign_lenses(["p1"], {}, history)
    assert assignments["p1"][0] != "security", (
        "argmax-by-strengths: назначение притянуто к самой сильной ячейке"
    )


def test_su_rt03_exploration_spreads_evenly_below_n_eff():
    """SU-RT03: exploration до n_eff — назначения распределяются по линзам
    равномерно (каждая сессия — новая линза), а не концентрируются."""
    history: dict = {}
    seen: list[str] = []
    for _ in range(3):
        assignments = swarm_core.assign_lenses(["p1"], {}, history)
        lens = assignments["p1"][0]
        seen.append(lens)
        history = _record_rotation_input(history, assignments)
    assert len(set(seen)) == 3, f"ротация концентрируется вместо exploration: {seen}"


# ---------- SU-RT04: forced-линза для пути карты критичности ----------

def test_su_rt04_critical_path_forces_lens_marked_and_excluded():
    """SU-RT04: путь из карты критичности (secrets/** → security) назначает линзу
    принудительно; назначение помечено forced=True и исключено из strength-входов."""
    assignments = swarm_core.assign_lenses(
        ["p1", "p2", "p3"], {"secrets/app.key": "security"}, {}
    )
    forced = {pid: lens for pid, (lens, flag) in assignments.items() if flag}
    assert forced == {"p1": "security"}
    inputs = swarm_core.strength_inputs(assignments)
    assert set(inputs) == {"p2", "p3"}, "forced-назначение попало в strength-входы"
    assert "security" not in inputs.values()


def test_su_rt04_unknown_forced_lens_rejected():
    """SU-RT04 (граница): forced-линза вне каталога code-review — fail-closed."""
    with pytest.raises(ValueError, match="линза"):
        swarm_core.assign_lenses(["p1"], {"weird/path": "no-such-lens"}, {})


# ---------- SU-RT05: смешанное назначение (forced + ротация) ----------

def test_su_rt05_remaining_lenses_by_balanced_rotation():
    """SU-RT05: forced забирает свою линзу, остальные участники — сбалансированной
    ротацией по свободным линзам с учётом истории (запрет повтора + min n_eff)."""
    history = {
        "p2": {"lenses_used": ["correctness"], "n_eff": {"correctness": 1}},
        "p3": {"lenses_used": [], "n_eff": {"performance": 2}},
    }
    assignments = swarm_core.assign_lenses(
        ["p1", "p2", "p3"], {"secrets/app.key": "security"}, history
    )
    assert assignments["p1"] == ("security", True)
    # p2: повтор correctness запрещён, security занята forced → первая свободная по балансу
    assert assignments["p2"] == ("concurrency", False)
    # p3: min n_eff среди свободных (performance=2 — самый «сытый», не выбирается)
    assert assignments["p3"] == ("correctness", False)
    lenses = [lens for lens, _ in assignments.values()]
    assert len(set(lenses)) == 3
