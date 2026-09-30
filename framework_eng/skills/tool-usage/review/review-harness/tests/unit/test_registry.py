"""HU-R01..R07 — единый реестр адаптеров v2 (RVSW-01, FR-02, AC-02; TD §4, §3.3).

Покрытие test-plan.md §3.1: round-trip полей v2, fail-closed на неизвестные поля,
запрет strengths, запрет вложенных структур, дубликаты id, инвариант
quota_introspection⟺quota_status, отказ на version != 2.
"""
from pathlib import Path

import pytest

import registry

SKILL_DIR = Path(__file__).resolve().parents[2]
REPO_REGISTRY_PATH = SKILL_DIR / "adapters.yaml"

BASE_FIELDS = (
    "id",
    "family",
    "model",
    "adapter",
    "cli",
    "context_budget",
    "enabled",
    "gate_legal",
    "quota_introspection",
    "quota_status",
)


def _fmt(value) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def make_yaml(entries: list[dict], version: int = 2) -> str:
    lines = [f"version: {version}", "participants:"]
    for entry in entries:
        for index, (key, value) in enumerate(entry.items()):
            prefix = "  - " if index == 0 else "    "
            lines.append(f"{prefix}{key}: {_fmt(value)}")
    return "\n".join(lines) + "\n"


def make_entry(**overrides) -> dict:
    entry = {
        "id": "codex-gpt",
        "family": "codex",
        "model": "gpt-5.6-sol",
        "adapter": "framework/skills/tool-usage/review/review-harness/scripts/adapters/codex_review.py",
        "cli": "codex",
        "context_budget": 120000,
        "enabled": True,
        "gate_legal": True,
        "quota_introspection": "codex-rollout",
        "quota_status": "proven",
    }
    entry.update(overrides)
    return entry


# HU-R01: round-trip всех полей v2 на боевом adapters.yaml; strengths отсутствует.
def test_hu_r01_repo_registry_round_trip():
    assert REPO_REGISTRY_PATH.exists(), f"нет боевого реестра: {REPO_REGISTRY_PATH}"
    parsed = registry.load_registry(REPO_REGISTRY_PATH)
    assert parsed["version"] == 3
    participants = parsed["participants"]
    assert len(participants) == 3

    expected = {
        "claude-opus": {
            "id": "claude-opus",
            "family": "claude",
            "model": "claude-opus-5",
            "adapter": "framework/skills/tool-usage/review/review-harness/scripts/adapters/claude_opus_review.py",
            "cli": "claude",
            "context_budget": 120000,
            "enabled": True,
            "gate_legal": True,
            "quota_introspection": "none",
            "quota_status": "unavailable",
        },
        "codex-gpt": {
            "id": "codex-gpt",
            "family": "codex",
            "model": "gpt-5.6-sol",
            "adapter": "framework/skills/tool-usage/review/review-harness/scripts/adapters/codex_review.py",
            "cli": "codex",
            "context_budget": 120000,
            "enabled": True,
            "gate_legal": True,
            "quota_introspection": "codex-rollout",
            "quota_status": "proven",
        },
        "kimi-k2": {
            "id": "kimi-k2",
            "family": "kimi",
            "model": "kimi-code/k3",
            "adapter": "framework/skills/tool-usage/review/review-harness/scripts/adapters/kimi_review.py",
            "cli": "kimi",
            "context_budget": 120000,
            "enabled": True,
            "gate_legal": True,
            "quota_introspection": "none",
            "quota_status": "unavailable",
        },
    }
    for entry in participants:
        want = expected[entry["id"]]
        for field in BASE_FIELDS:
            assert entry[field] == want[field], f"{entry['id']}: поле {field}"
        assert set(entry) == {*BASE_FIELDS, "native_fork"}, f"{entry['id']}: лишние поля"
        capability = entry["native_fork"]
        assert set(capability) == {
            "supported", "route", "min_cli_version",
            "exact_checkpoint", "automation_safe",
        }
        assert "strengths" not in entry

    # Схемная валидация без проверки существования файлов адаптеров
    # (адаптеры переезжают пакетом T-04; здесь — только схема v2).
    assert registry.validate_registry(parsed, base_dir=None) == []

    # Типы скаляров сохраняются при round-trip текста.
    for entry in registry.parse_adapters_yaml(make_yaml([make_entry()]))["participants"]:
        assert entry["enabled"] is True and entry["gate_legal"] is True
        assert isinstance(entry["context_budget"], int)


# HU-R02: неизвестное поле записи — fail-closed с диагностикой (RISK-10).
def test_hu_r02_unknown_field_fail_closed():
    parsed = registry.parse_adapters_yaml(make_yaml([make_entry(priority="high")]))
    errors = registry.validate_registry(parsed)
    assert any("priority" in error and "неизвестные" in error for error in errors)


# HU-R03: поле strengths в записи — отказ (самодекларация запрещена).
def test_hu_r03_strengths_forbidden():
    parsed = registry.parse_adapters_yaml(make_yaml([make_entry(strengths="security")]))
    errors = registry.validate_registry(parsed)
    assert any("strengths" in error for error in errors)


# HU-R04: вложенная структура (mapping в поле) — отказ (дизайн — плоские скаляры).
def test_hu_r04_nested_structure_rejected():
    nested = (
        "version: 2\n"
        "participants:\n"
        "  - id: codex-gpt\n"
        "    family: codex\n"
        "    quota_config:\n"
        "      window_minutes: 10080\n"
    )
    with pytest.raises(registry.RegistryError):
        registry.parse_adapters_yaml(nested)


# HU-R05: дубликат id; пустой family; несуществующий путь adapter — fail-closed.
def test_hu_r05_duplicate_id_rejected():
    parsed = registry.parse_adapters_yaml(
        make_yaml([make_entry(), make_entry(cli="codex2")])
    )
    errors = registry.validate_registry(parsed)
    assert any("дубликат id" in error for error in errors)


def test_hu_r05_empty_family_rejected():
    text = make_yaml([make_entry()]).replace("    family: codex", "    family:")
    parsed = registry.parse_adapters_yaml(text)
    errors = registry.validate_registry(parsed)
    assert any("family" in error for error in errors)


def test_hu_r05_missing_adapter_path_rejected(tmp_path):
    parsed = registry.parse_adapters_yaml(
        make_yaml([make_entry(adapter="scripts/adapters/absent_adapter.py")])
    )
    errors = registry.validate_registry(parsed, base_dir=tmp_path)
    assert any("adapter не найден" in error for error in errors)
    # Контроль: существующий путь ошибки не даёт.
    real = tmp_path / "scripts" / "adapters"
    real.mkdir(parents=True)
    (real / "codex_review.py").write_text("# stub\n", encoding="utf-8")
    parsed_ok = registry.parse_adapters_yaml(
        make_yaml([make_entry(adapter="scripts/adapters/codex_review.py")])
    )
    assert registry.validate_registry(parsed_ok, base_dir=tmp_path) == []


# HU-R06: инвариант quota_introspection != none ⟺ quota_status = proven; enum'ы.
@pytest.mark.parametrize(
    ("introspection", "status"),
    [
        ("codex-rollout", "unavailable"),  # способ есть, доказательства нет
        ("none", "proven"),  # доказательство без способа
    ],
)
def test_hu_r06_quota_invariant_violated(introspection, status):
    entry = make_entry(quota_introspection=introspection, quota_status=status)
    parsed = registry.parse_adapters_yaml(make_yaml([entry]))
    errors = registry.validate_registry(parsed)
    assert any("инвариант" in error for error in errors)


@pytest.mark.parametrize(
    ("introspection", "status"),
    [
        ("magic-api", "proven"),  # значение вне enum quota_introspection
        ("none", "unknown"),  # значение вне enum quota_status
    ],
)
def test_hu_r06_quota_enum_values(introspection, status):
    entry = make_entry(quota_introspection=introspection, quota_status=status)
    parsed = registry.parse_adapters_yaml(make_yaml([entry]))
    errors = registry.validate_registry(parsed)
    assert any("enum" in error or "допустимые значения" in error for error in errors)


@pytest.mark.parametrize(
    ("introspection", "status"),
    [("none", "unavailable"), ("codex-rollout", "proven")],
)
def test_hu_r06_quota_invariant_holds(introspection, status):
    entry = make_entry(quota_introspection=introspection, quota_status=status)
    parsed = registry.parse_adapters_yaml(make_yaml([entry]))
    assert registry.validate_registry(parsed) == []


# HU-R07: файл version: 1 — отказ (принимается только v2).
def test_hu_r07_version1_rejected():
    parsed = registry.parse_adapters_yaml(make_yaml([make_entry()], version=1))
    errors = registry.validate_registry(parsed)
    assert any("version" in error for error in errors)
