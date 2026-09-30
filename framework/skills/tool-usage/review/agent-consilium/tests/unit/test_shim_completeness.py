"""CR-S01 (RVSW-01, T-06, TD §14.2): shim-полнота consilium_core.

Snapshot публичных имён `consilium_core` ДО рефакторинга на harness (CR-00,
зафиксирован 2026-07-29 командой из test-plan §7). После замены переехавших
функций re-export shim'ами публичное множество обязано остаться надмножеством
snapshot — потеря функции при переезде ловится этим тестом, а не молчаливым
AttributeError в прогоне.
"""
from __future__ import annotations

import consilium_core as core

# CR-00 snapshot (2026-07-29, нерефакторённый consilium_core).
SNAPSHOT_PUBLIC_NAMES = {
    "CHECKLIST_WAVE_TYPES",
    "DEFAULT_DOMAIN",
    "DRAFT_BLOCK_TAG",
    "EMPTY_STRUCTURED",
    "EVALUATIVE_MARKERS",
    "EXPLORATION_EVERY",
    "INVOCATION_HARD_MAX",
    "INVOCATION_WARN_MAX",
    "MODERATOR_ID",
    "OPTIONAL_REGISTRY_FIELDS",
    "PARTICIPANT_INVOCATION_KINDS",
    "PARTICIPANT_TYPES",
    "Path",
    "ProtocolError",
    "RECORD_TYPES",
    "REQUIRED_REGISTRY_FIELDS",
    "ROLE_CATALOG",
    "SCORE_W_ACCEPT",
    "SCORE_W_UPHELD",
    "STRENGTHS_FLOOR",
    "STRENGTHS_HALF_LIFE",
    "STRUCTURED_BLOCK_RE",
    "SYNTHESIS_ELEMENT_RE",
    "SYNTHESIS_EXCLUSION_MARK",
    "SYNTHESIS_HEADER_RE",
    "SYNTHESIS_RANGE_RE",
    "TAINTED_DISCOUNT",
    "TRANSCRIPT_NAME",
    "UTC",
    "WALL_CLOCK_FLOOR_SEC",
    "WALL_CLOCK_MODERATOR_SEC",
    "WALL_CLOCK_WAVES_MAX",
    "WAVE_GRACE_SEC",
    "alive_model_ids",
    "annotations",
    "anonymize_text",
    "append_record",
    "applicable_checklist_items",
    "assign_phase_d_role",
    "assign_roles",
    "assign_roles_round_robin",
    "b_wave_blocked",
    "checklist_stats",
    "compute_model_metrics",
    "compute_strengths",
    "count_invocation",
    "create_anon_map",
    "datetime",
    "domain_by_id",
    "draft_template_text",
    "estimate_tokens",
    "evaluate_b_stop",
    "generate_digest_draft",
    "invocation_warning",
    "is_converged",
    "is_exploration_consilium",
    "is_stalemate",
    "json",
    "kill_candidate",
    "lint_digest",
    "load_domains",
    "load_registry",
    "next_seq",
    "observation_weight",
    "parse_adapters_yaml",
    "parse_domains_yaml",
    "parse_structured_block",
    "parse_synthesis",
    "quorum_status",
    "random",
    "re",
    "read_transcript",
    "render_bundle",
    "resolve_borrowed",
    "round_3_allowed",
    "round_has_new_findings",
    "round_has_position_changes",
    "should_skip_phase_d",
    "transcript_path",
    "update_stalemate",
    "utc_now",
    "valid_checklist_note",
    "validate_checklist_responses",
    "validate_domains",
    "validate_registry",
    "wall_clock_budget",
    "wall_clock_status",
}


def test_crs01_public_names_superset_of_snapshot():
    """Публичные имена consilium_core ⊇ snapshot CR-00 (потеря при переезде — красный)."""
    current = {name for name in dir(core) if not name.startswith("_")}
    missing = SNAPSHOT_PUBLIC_NAMES - current
    assert not missing, f"consilium_core потерял публичные имена при переезде: {sorted(missing)}"
