"""Единый реестр адаптеров harness (RVSW-01, FR-02, AC-02) — схема v3.

Переезд парсера/валидатора из `consilium_core.py` (CONS-01) с расширением
схемы по TD §4: обязательные поля id/family/model/adapter/cli/context_budget/
enabled/gate_legal/quota_introspection/quota_status; опциональных полей нет.

Инварианты (RISK-10, stdlib-only, fail-closed):
- неизвестные поля записи отклоняются с диагностикой;
- поле `strengths` запрещено (read-only проекция из track record);
- единственная вложенная структура — строго типизированный `native_fork`;
- `version: 2` принимается для совместимости со старыми callers;
- квотный инвариант: `quota_introspection != "none"` ⟺ `quota_status == "proven"`.
"""
from __future__ import annotations

import re
from pathlib import Path

REGISTRY_VERSION = 3
COMPATIBLE_REGISTRY_VERSIONS = (2, 3)

REQUIRED_REGISTRY_FIELDS = (
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
NATIVE_FORK_FIELDS = (
    "supported",
    "route",
    "min_cli_version",
    "exact_checkpoint",
    "automation_safe",
)
OPTIONAL_REGISTRY_FIELDS: tuple = ()
FORBIDDEN_REGISTRY_FIELDS = ("strengths",)

QUOTA_INTROSPECTION_VALUES = ("none", "codex-rollout")
QUOTA_STATUS_VALUES = ("proven", "unavailable")


class RegistryError(Exception):
    """Синтаксическая ошибка реестра (парсинг). Схемные — см. validate_registry."""


def _parse_scalar(raw: str):
    value = raw.strip()
    if value == "true":
        return True
    if value == "false":
        return False
    if re.fullmatch(r"-?\d+", value):
        return int(value)
    return value


def _indent_of(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


def parse_adapters_yaml(text: str) -> dict:
    """Минимальный stdlib-only парсер схем v2/v3.

    В v3 допускается ровно одна вложенная mapping: ``native_fork``.
    Остальные вложенные структуры отклоняются fail-closed.
    """
    registry: dict = {"version": None, "participants": []}
    current: dict | None = None
    nested_native_fork: dict | None = None
    native_fork_indent: int | None = None
    in_participants = False
    lines = text.splitlines()

    def next_content_indent(start: int) -> int | None:
        for following in lines[start:]:
            if not following.strip() or following.strip().startswith("#"):
                continue
            return _indent_of(following)
        return None

    for index, raw_line in enumerate(lines):
        line_number = index + 1
        if not raw_line.strip() or raw_line.strip().startswith("#"):
            continue
        stripped = raw_line.strip()
        indent = _indent_of(raw_line)
        if nested_native_fork is not None and native_fork_indent is not None:
            if indent > native_fork_indent:
                if stripped.startswith("- ") or ":" not in stripped:
                    raise RegistryError(
                        f"adapters.yaml: некорректное поле native_fork, строка {line_number}"
                    )
                key, _, value = stripped.partition(":")
                key = key.strip()
                value = re.sub(r"\s+#.*$", "", value.strip())
                if not value:
                    raise RegistryError(
                        f"adapters.yaml: вложенная структура в native_fork.{key} запрещена "
                        f"(строка {line_number})"
                    )
                nested_native_fork[key] = _parse_scalar(value)
                continue
            nested_native_fork = None
            native_fork_indent = None
        if stripped.startswith("version:") and not in_participants:
            registry["version"] = _parse_scalar(stripped.split(":", 1)[1])
            continue
        if stripped == "participants:":
            in_participants = True
            continue
        if not in_participants:
            raise RegistryError(f"adapters.yaml: неожиданная строка {line_number}: {raw_line!r}")
        if stripped.startswith("- "):
            current = {}
            registry["participants"].append(current)
            stripped = stripped[2:].strip()
            if not stripped:
                continue
        if current is None:
            raise RegistryError(f"adapters.yaml: поле вне участника, строка {line_number}")
        if ":" not in stripped:
            raise RegistryError(f"adapters.yaml: нет ':' в строке {line_number}: {raw_line!r}")
        key, _, value = stripped.partition(":")
        key = key.strip()
        # Inline-комментарий (пробел + '#') не является частью скаляра.
        value = re.sub(r"\s+#.*$", "", value.strip())
        if not value:
            following_indent = next_content_indent(index + 1)
            if following_indent is not None and following_indent > _indent_of(raw_line):
                if key == "native_fork" and registry.get("version") == 3:
                    nested_native_fork = {}
                    native_fork_indent = indent
                    current[key] = nested_native_fork
                    continue
                raise RegistryError(
                    f"adapters.yaml: вложенная структура в поле {key!r} запрещена "
                    f"(строка {line_number}); допустима только mapping native_fork в schema v3"
                )
        current[key] = _parse_scalar(value)
    return registry


def load_registry(path: Path) -> dict:
    return parse_adapters_yaml(Path(path).read_text(encoding="utf-8"))


def validate_registry(registry: dict, base_dir: Path | None = None) -> list[str]:
    """Валидация схем v2/v3 (TD §4): version, обязательные/неизвестные/запрещённые
    поля, дубликаты id, пустой family, существование adapter (при заданном
    base_dir), типы и enum'ы квотных полей, инвариант
    quota_introspection != "none" ⟺ quota_status == "proven"."""
    errors: list[str] = []
    version = registry.get("version")
    if version not in COMPATIBLE_REGISTRY_VERSIONS:
        errors.append(
            f"adapters.yaml: неподдерживаемая version: {version!r} "
            f"(допустимые: {list(COMPATIBLE_REGISTRY_VERSIONS)})"
        )
    participants = registry.get("participants") or []
    if not participants:
        errors.append("adapters.yaml: пустой список participants")
    seen: set[str] = set()
    allowed = set(REQUIRED_REGISTRY_FIELDS) | set(OPTIONAL_REGISTRY_FIELDS)
    if version == 3:
        allowed.add("native_fork")
    for entry in participants:
        pid = entry.get("id") or "<без id>"
        for field in REQUIRED_REGISTRY_FIELDS:
            if field not in entry:
                errors.append(f"участник {pid}: отсутствует обязательное поле {field}")
        if version == 3 and "native_fork" not in entry:
            errors.append(f"участник {pid}: отсутствует обязательное поле native_fork")
        unknown = set(entry) - allowed - set(FORBIDDEN_REGISTRY_FIELDS)
        if unknown:
            errors.append(f"участник {pid}: неизвестные поля реестра: {sorted(unknown)}")
        for field in FORBIDDEN_REGISTRY_FIELDS:
            if field in entry:
                errors.append(f"участник {pid}: поле {field} запрещено в реестре (самодекларация; FR-02)")
        if pid in seen:
            errors.append(f"дубликат id участника: {pid}")
        seen.add(pid)
        if not entry.get("family"):
            errors.append(f"участник {pid}: пустой family")
        adapter = entry.get("adapter")
        if adapter and base_dir is not None:
            adapter_path = Path(str(adapter))
            if not adapter_path.is_absolute():
                adapter_path = Path(base_dir) / adapter_path
            if not adapter_path.exists():
                errors.append(f"участник {pid}: adapter не найден: {adapter}")
        budget = entry.get("context_budget")
        if budget is not None and (not isinstance(budget, int) or isinstance(budget, bool) or budget <= 0):
            errors.append(f"участник {pid}: context_budget должен быть положительным int")
        for flag in ("enabled", "gate_legal"):
            value = entry.get(flag)
            if value is not None and not isinstance(value, bool):
                errors.append(f"участник {pid}: {flag} должен быть bool")
        if version == 3 and "native_fork" in entry:
            capability = entry["native_fork"]
            if not isinstance(capability, dict):
                errors.append(f"участник {pid}: native_fork должен быть mapping")
            else:
                for field in NATIVE_FORK_FIELDS:
                    if field not in capability:
                        errors.append(
                            f"участник {pid}: native_fork — отсутствует обязательное поле {field}"
                        )
                unknown_native = set(capability) - set(NATIVE_FORK_FIELDS)
                if unknown_native:
                    errors.append(
                        f"участник {pid}: неизвестные поля native_fork: {sorted(unknown_native)}"
                    )
                for flag in ("supported", "exact_checkpoint", "automation_safe"):
                    value = capability.get(flag)
                    if value is not None and not isinstance(value, bool):
                        errors.append(f"участник {pid}: native_fork.{flag} должен быть bool")
                for field in ("route", "min_cli_version"):
                    value = capability.get(field)
                    if not isinstance(value, str) or not value.strip():
                        errors.append(
                            f"участник {pid}: native_fork.{field} должен быть непустой str"
                        )
                route = capability.get("route")
                if isinstance(route, str) and "synthetic" in route.lower():
                    errors.append(f"участник {pid}: native_fork.route не может быть synthetic")
                if capability.get("supported") is True and capability.get("exact_checkpoint") is not True:
                    errors.append(
                        f"участник {pid}: native_fork с supported=true требует exact_checkpoint=true"
                    )
        introspection = entry.get("quota_introspection")
        if introspection is not None and introspection not in QUOTA_INTROSPECTION_VALUES:
            errors.append(
                f"участник {pid}: quota_introspection вне enum: {introspection!r} "
                f"(допустимые значения: {list(QUOTA_INTROSPECTION_VALUES)})"
            )
        status = entry.get("quota_status")
        if status is not None and status not in QUOTA_STATUS_VALUES:
            errors.append(
                f"участник {pid}: quota_status вне enum: {status!r} "
                f"(допустимые значения: {list(QUOTA_STATUS_VALUES)})"
            )
        if introspection in QUOTA_INTROSPECTION_VALUES and status in QUOTA_STATUS_VALUES:
            if (introspection != "none") != (status == "proven"):
                errors.append(
                    f"участник {pid}: нарушен инвариант quota_introspection⟺quota_status: "
                    f"{introspection!r}/{status!r} (ожидается none⟺unavailable, иначе proven)"
                )
    return errors
