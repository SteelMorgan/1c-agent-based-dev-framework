"""RED registry v3 contract: explicit provider-native fork capability."""
from __future__ import annotations

import pytest

import registry


def _registry(native_fork: dict) -> dict:
    return {
        "version": 3,
        "participants": [{
            "id": "claude-opus",
            "family": "claude",
            "model": "claude-opus-5",
            "adapter": "scripts/adapters/claude_opus_review.py",
            "cli": "claude",
            "context_budget": 120000,
            "enabled": True,
            "gate_legal": True,
            "quota_introspection": "none",
            "quota_status": "unavailable",
            "native_fork": native_fork,
        }],
    }


def test_registry_v3_accepts_complete_native_fork_capability():
    capability = {
        "supported": True,
        "route": "claude-session-fork",
        "min_cli_version": "2.1.0",
        "exact_checkpoint": True,
        "automation_safe": True,
    }
    assert registry.validate_registry(_registry(capability), base_dir=None) == []


@pytest.mark.parametrize(
    "missing",
    ["supported", "route", "min_cli_version", "exact_checkpoint", "automation_safe"],
)
def test_registry_v3_rejects_missing_native_fork_field(missing):
    capability = {
        "supported": True,
        "route": "claude-session-fork",
        "min_cli_version": "2.1.0",
        "exact_checkpoint": True,
        "automation_safe": True,
    }
    capability.pop(missing)
    errors = registry.validate_registry(_registry(capability), base_dir=None)
    assert any("native_fork" in error and missing in error for error in errors)


@pytest.mark.parametrize(
    "capability",
    [
        {
            "supported": True,
            "route": "synthetic-start",
            "min_cli_version": "2.1.0",
            "exact_checkpoint": True,
            "automation_safe": True,
        },
        {
            "supported": True,
            "route": "",
            "min_cli_version": "2.1.0",
            "exact_checkpoint": False,
            "automation_safe": True,
        },
    ],
)
def test_registry_v3_never_treats_synthetic_or_inexact_route_as_native(capability):
    errors = registry.validate_registry(_registry(capability), base_dir=None)
    assert errors
    assert any("native_fork" in error or "checkpoint" in error for error in errors)
