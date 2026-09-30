"""SU-TG — детерминированный триаж тарифа (RVSW-01, T-10, FR-15/AC-15, TD §8).

Покрытие test-plan SU-TG01..TG03:
- SU-TG01: triage(paths, map): пересечение с картой критичности → "swarm";
  иначе "gray" — чистая функция TD §8.1;
- SU-TG02: границы glob-паттернов стартового набора (auth/payments/billing/
  migrations/crypto/platform-registry/contracts/secrets) на фабрике карты;
- SU-TG03: серая зона — рубрика Оркестратора (--tier), без отдельной модели
  оценки: gray без рубрики — fail-closed; рубрика вне enum — fail-closed;
  калибровочная квота (каждое 4-е лёгкое → роем, TD §8.2/TBD-02) — по счётчику
  light_reviews_completed из config.json, без LLM-оценки.
"""
from __future__ import annotations

from pathlib import Path

import pytest

import swarm  # noqa: E402 — CLI-модуль ядра роя (T-10)

REFERENCES = Path(__file__).resolve().parents[2] / "references"

STARTER_MAP_TEXT = """# Карта критичности — тестовая фабрика

## Паттерны полного роя

- `**/auth*/**`
- `**/payment*/**`
- `**/billing*/**`
- `**/migrations/**`
- `**/crypto*/**`
- `infra/platform-registry/**`
- `packages/contracts/**`
- `secrets/**`

## Привязка forced-линз

- `secrets/**` → security
- `**/auth*/**` → security
- `**/migrations/**` → data-contracts
- `packages/contracts/**` → data-contracts
"""


@pytest.fixture()
def starter_map():
    return swarm.parse_criticality_map(STARTER_MAP_TEXT)


# ---------- SU-TG01: пересечение с картой → swarm, иначе gray ----------

def test_su_tg01_hit_gives_swarm(starter_map):
    assert swarm.triage(["services/auth/session.py"], starter_map) == "swarm"


def test_su_tg01_no_hit_gives_gray(starter_map):
    assert swarm.triage(["apps/web/header.tsx", "docs/guide.md"],
                        starter_map) == "gray"


def test_su_tg01_empty_paths_gives_gray(starter_map):
    assert swarm.triage([], starter_map) == "gray"


def test_su_tg01_pure_function(starter_map):
    """Чистая функция (NFR-04): повторный вызов без мутации входа."""
    paths = ["packages/contracts/api.ts"]
    snapshot = dict(starter_map)
    assert swarm.triage(paths, starter_map) == "swarm"
    assert swarm.triage(paths, starter_map) == "swarm"
    assert starter_map == snapshot


# ---------- SU-TG02: границы glob-паттернов стартового набора ----------

@pytest.mark.parametrize("path", [
    "services/auth/login.py",
    "services/authentication/oauth.py",
    "auth/token.py",                       # корневой каталог auth — тоже hit
    "services/payments/charge.py",
    "apps/web/billing/invoice.tsx",
    "packages/data/migrations/0042.sql",
    "libs/crypto-utils/hash.py",
    "infra/platform-registry/services.yaml",
    "packages/contracts/review.ts",
    "secrets/identity/dev.pem",
])
def test_su_tg02_starter_patterns_hit(starter_map, path):
    assert swarm.criticality_hits([path], starter_map) == [path]


@pytest.mark.parametrize("path", [
    "services/authors.py",                 # подстрока без '/' — не auth-каталог
    "apps/web/auth.py",                    # файл auth.py вне auth-каталога
    "docs/billing.md",                     # файл, не каталог billing*/
    "services/crypto.py",
    "packages/contracts.md",
    "apps/web/header.tsx",
])
def test_su_tg02_boundaries_no_hit(starter_map, path):
    assert swarm.criticality_hits([path], starter_map) == []


def test_su_tg02_hits_sorted_and_normalized(starter_map):
    hits = swarm.criticality_hits(
        ["./services\\auth\\login.py", "apps/web/x.tsx", "packages/contracts/a.ts"],
        starter_map,
    )
    assert hits == ["packages/contracts/a.ts", "services/auth/login.py"]


# ---------- Forced-линзы критичных путей (TD §8.1) ----------

def test_forced_lenses_resolved_from_bindings(starter_map):
    hits = ["secrets/identity/dev.pem", "packages/contracts/a.ts"]
    assert swarm.resolve_forced_lenses(hits, starter_map) == {
        "secrets/identity/dev.pem": "security",
        "packages/contracts/a.ts": "data-contracts",
    }


def test_forced_lenses_unknown_lens_fail_closed():
    bad = swarm.parse_criticality_map(
        "## Паттерны полного роя\n\n- `x/**`\n\n"
        "## Привязка forced-линз\n\n- `x/**` → несуществующая\n"
    )
    with pytest.raises(ValueError, match="линз"):
        swarm.resolve_forced_lenses(["x/y.py"], bad)


# ---------- SU-TG03: серая зона — рубрика Оркестратора, калибровка ----------

def test_su_tg03_gray_requires_rubric(starter_map):
    """Стаб НЕ делает LLM-оценку: gray без рубрики вызывающего — fail-closed."""
    with pytest.raises(ValueError, match="рубрик"):
        swarm.triage_decision(["apps/web/x.tsx"], starter_map)


def test_su_tg03_rubric_light(starter_map):
    decision = swarm.triage_decision(["apps/web/x.tsx"], starter_map,
                                     gray_tier="light")
    assert decision["tier"] == "light"
    assert decision["reason"] == "orchestrator_rubric"
    assert decision["calibration"] is False


def test_su_tg03_rubric_swarm(starter_map):
    decision = swarm.triage_decision(["apps/web/x.tsx"], starter_map,
                                     gray_tier="swarm")
    assert decision["tier"] == "swarm"
    assert decision["reason"] == "orchestrator_rubric"


def test_su_tg03_invalid_rubric_fail_closed(starter_map):
    with pytest.raises(ValueError, match="light|swarm"):
        swarm.triage_decision(["apps/web/x.tsx"], starter_map,
                              gray_tier="medium")


def test_su_tg03_map_hit_ignores_rubric(starter_map):
    """Путь из карты ВСЕГДА идёт полным роем (AC-15) — рубрика не понижает."""
    decision = swarm.triage_decision(["secrets/a.pem"], starter_map,
                                     gray_tier="light")
    assert decision["tier"] == "swarm"
    assert decision["reason"] == "criticality_map"
    assert decision["hits"] == ["secrets/a.pem"]


# ---------- Калибровочная квота N=4 (TD §8.2, TBD-02) ----------

@pytest.mark.parametrize("completed,expected", [
    (0, False), (1, False), (2, False), (3, True),   # каждое 4-е лёгкое — роем
    (4, False), (7, True), (8, False),
])
def test_calibration_due_counter(completed, expected):
    config = {"light_reviews_completed": completed, "calibration_every": 4}
    assert swarm.calibration_due(config) is expected


def test_calibration_default_every_4():
    assert swarm.calibration_due({"light_reviews_completed": 3}) is True
    assert swarm.calibration_due({}) is False


def test_calibration_forces_swarm_on_gray(starter_map):
    """Калибровочное ревью исполняется роем независимо от триажа (TD §8.2)."""
    config = {"light_reviews_completed": 3, "calibration_every": 4}
    decision = swarm.triage_decision(["apps/web/x.tsx"], starter_map,
                                     config=config, gray_tier="light")
    assert decision["tier"] == "swarm"
    assert decision["reason"] == "calibration_quota"
    assert decision["calibration"] is True


def test_calibration_not_due_keeps_rubric(starter_map):
    config = {"light_reviews_completed": 2, "calibration_every": 4}
    decision = swarm.triage_decision(["apps/web/x.tsx"], starter_map,
                                     config=config, gray_tier="light")
    assert decision["tier"] == "light"
    assert decision["calibration"] is False


# ---------- Стартовая карта references/criticality-map.md (FR-15, ASM-03) ----------

def test_starter_criticality_map_reference():
    """Реальная карта в references/: полный стартовый набор TD §8.1 и валидные
    линзовые привязки (только линзы каталога code-review)."""
    path = REFERENCES / "criticality-map.md"
    assert path.exists(), "references/criticality-map.md не создан"
    criticality_map = swarm.load_criticality_map(path)
    expected = {
        "**/auth*/**", "**/payment*/**", "**/billing*/**", "**/migrations/**",
        "**/crypto*/**", "infra/platform-registry/**", "packages/contracts/**",
        "secrets/**",
    }
    assert expected <= set(criticality_map["patterns"])
    import swarm_core
    for lens in criticality_map["lens_bindings"].values():
        assert lens in swarm_core.CODE_REVIEW_LENSES
