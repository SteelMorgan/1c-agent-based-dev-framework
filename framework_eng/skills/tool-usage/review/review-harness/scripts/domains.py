"""Доменные пакеты и риск-чеклисты — переезд из `consilium_core.py` (RVSW-01, TD §3.2).

Перенесены без изменения логики (ASM-01): вложенный stdlib-парсер domains.yaml
(parse_domains_yaml/validate_domains/load_domains/domain_by_id), валидация
чеклистов (valid_checklist_note/applicable_checklist_items/
validate_checklist_responses/checklist_stats), CHECKLIST_WAVE_TYPES,
DEFAULT_DOMAIN.

Точка адаптации границы: ProtocolError консилиума → DomainsError harness
(та же fail-closed семантика; T-06 оставит alias в ядре консилиума).

CHECKLIST_WAVE_TYPES — разделяемый словарь применимости пунктов чеклиста
(applies_to), а не знание о фазах: инструменты могут расширять enum своими
метками волн/туров через validate_domains(..., wave_types=...).
"""
from __future__ import annotations

import re
from pathlib import Path

CHECKLIST_WAVE_TYPES = {"proposal", "attack", "response", "redteam", "confirmation"}
DEFAULT_DOMAIN = "architecture"


class DomainsError(Exception):
    """Нарушение схемы/инварианта доменного реестра (fail-closed)."""


def _yaml_scalar(raw: str):
    value = raw.strip()
    if value == "true":
        return True
    if value == "false":
        return False
    if value.startswith("[") and value.endswith("]"):
        inner = value[1:-1].strip()
        return [item.strip() for item in inner.split(",") if item.strip()] if inner else []
    if re.fullmatch(r"-?\d+", value):
        return int(value)
    return value


def parse_domains_yaml(text: str) -> dict:
    """Минимальный вложенный YAML-парсер схемы E-1 (stdlib-only): version + domains
    со вложенными roles/risk_checklist/evidence_requirements. Значения скаляров
    читаются дословно (двоеточия, кавычки «») — split по первому ':'."""
    root: dict = {}
    stack: list[tuple[int, object]] = [(-1, root)]

    def parent_for(indent: int):
        while len(stack) > 1 and stack[-1][0] >= indent:
            stack.pop()
        return stack[-1][1]

    meaningful = [
        (len(line) - len(line.lstrip(" ")), line.strip())
        for line in text.splitlines()
        if line.strip() and not line.strip().startswith("#")
    ]
    for index, (indent, stripped) in enumerate(meaningful):
        line_number = index + 1
        if stripped.startswith("- "):
            container = parent_for(indent)
            item_text = stripped[2:].strip()
            # map-элемент списка: 'key: value' И следующая строка глубже (иначе —
            # скаляр с двоеточием, как пункты evidence_requirements)
            next_deeper = (
                index + 1 < len(meaningful) and meaningful[index + 1][0] > indent
            )
            if ":" in item_text and next_deeper:
                if not isinstance(container, list):
                    raise DomainsError(f"domains.yaml: список вне ключа, строка {line_number}")
                entry: dict = {}
                container.append(entry)
                key, _, value = item_text.partition(":")
                entry[key.strip()] = _yaml_scalar(value)
                stack.append((indent, entry))
            else:
                if not isinstance(container, list):
                    raise DomainsError(f"domains.yaml: скалярный элемент вне списка, строка {line_number}")
                container.append(_yaml_scalar(item_text))
            continue
        if ":" not in stripped:
            raise DomainsError(f"domains.yaml: нет ':' в строке {line_number}: {stripped!r}")
        key, _, value = stripped.partition(":")
        key = key.strip()
        parent = parent_for(indent)
        if not isinstance(parent, dict):
            raise DomainsError(f"domains.yaml: поле вне map, строка {line_number}")
        if value.strip():
            parent[key] = _yaml_scalar(value)
        else:
            parent[key] = []
            stack.append((indent, parent[key]))
    return root


def validate_domains(registry: dict, wave_types: set | None = None) -> list[str]:
    """Валидация схемы domains.yaml (E-1, AC-E01): обязательные поля, уникальность
    id доменов/ролей/item_id, ≥2 роли, ≥1 пункта чеклиста, applies_to ⊆ enum.
    wave_types — допустимые метки applies_to; дефолт = CHECKLIST_WAVE_TYPES."""
    allowed_wave_types = CHECKLIST_WAVE_TYPES if wave_types is None else wave_types
    errors: list[str] = []
    if registry.get("version") != 1:
        errors.append(f"domains.yaml: неподдерживаемая version: {registry.get('version')!r}")
    domain_list = registry.get("domains") or []
    if not domain_list:
        errors.append("domains.yaml: пустой список domains")
    seen_domains: set[str] = set()
    for domain in domain_list:
        if not isinstance(domain, dict):
            errors.append("domains.yaml: домен не является map")
            continue
        did = domain.get("id") or "<без id>"
        for field in ("id", "description", "roles", "evidence_requirements"):
            if not domain.get(field):
                errors.append(f"домен {did}: отсутствует обязательное поле {field}")
        if did in seen_domains:
            errors.append(f"дубликат id домена: {did}")
        seen_domains.add(did)
        roles = domain.get("roles") or []
        if len(roles) < 2:
            errors.append(f"домен {did}: ролей меньше 2 (кворум ролей)")
        seen_roles: set[str] = set()
        for role in roles:
            rid = role.get("id") or "<без id>"
            for field in ("id", "title", "lens", "risk_checklist"):
                if not role.get(field):
                    errors.append(f"домен {did}, роль {rid}: отсутствует поле {field}")
            if rid in seen_roles:
                errors.append(f"домен {did}: дубликат id роли {rid}")
            seen_roles.add(rid)
            items = role.get("risk_checklist") or []
            if not items:
                errors.append(f"домен {did}, роль {rid}: пустой risk_checklist")
            seen_items: set[str] = set()
            for item in items:
                iid = item.get("item_id") or "<без item_id>"
                for field in ("item_id", "text", "applies_to"):
                    if not item.get(field):
                        errors.append(f"домен {did}, роль {rid}, пункт {iid}: отсутствует поле {field}")
                if iid in seen_items:
                    errors.append(f"домен {did}, роль {rid}: дубликат item_id {iid}")
                seen_items.add(iid)
                applies = item.get("applies_to") or []
                unknown = set(applies) - allowed_wave_types
                if unknown:
                    errors.append(f"домен {did}, роль {rid}, пункт {iid}: applies_to вне enum: {sorted(unknown)}")
    return errors


def load_domains(path: Path, wave_types: set | None = None) -> dict:
    """Загрузка domains.yaml с actionable-отказом при отсутствии/битом реестре (E-1)."""
    path = Path(path)
    if not path.exists():
        raise DomainsError(
            f"domains.yaml не найден: {path}. Создайте реестр доменных пакетов "
            f"по схеме references/track-record-schema.md."
        )
    try:
        registry = parse_domains_yaml(path.read_text(encoding="utf-8"))
    except DomainsError:
        raise
    except Exception as exc:
        raise DomainsError(f"domains.yaml не парсится ({path}): {exc}") from exc
    errors = validate_domains(registry, wave_types=wave_types)
    if errors:
        raise DomainsError(
            "domains.yaml невалиден (fail-closed):\n" + "\n".join(f"  - {e}" for e in errors)
        )
    return registry


def domain_by_id(registry: dict, domain_id: str) -> dict | None:
    for domain in registry.get("domains") or []:
        if domain.get("id") == domain_id:
            return domain
    return None


def valid_checklist_note(note) -> bool:
    """note валиден для hit/na: непустой после trim, не «-», ≥3 символов (E-4)."""
    if not isinstance(note, str):
        return False
    stripped = note.strip()
    return len(stripped) >= 3 and stripped != "-"


def applicable_checklist_items(role: dict, wave_type: str) -> list[dict]:
    """Пункты чеклиста роли, применимые к типу хода (applies_to)."""
    return [
        item for item in role.get("risk_checklist") or []
        if wave_type in (item.get("applies_to") or [])
    ]


def validate_checklist_responses(responses, role: dict, wave_type: str) -> list[str]:
    """Fail-closed валидация risk_checklist_responses (таблица вырожденных случаев E-4):
    покрытие по applies_to, лишний item_id, дубликат с конфликтом verdict, enum,
    note для hit/na. Возвращает перечень ошибок ([] = валидно)."""
    errors: list[str] = []
    if not isinstance(responses, list):
        return ["risk_checklist_responses не является списком"]
    catalog_ids = {item.get("item_id") for item in role.get("risk_checklist") or []}
    required = {item.get("item_id") for item in applicable_checklist_items(role, wave_type)}
    verdicts: dict[str, set] = {}
    for response in responses:
        if not isinstance(response, dict):
            errors.append(f"ответ чеклиста не является объектом: {response!r}")
            continue
        item_id = response.get("item_id")
        verdict = response.get("verdict")
        if item_id not in catalog_ids:
            errors.append(f"item_id вне каталога чеклиста роли: {item_id!r}")
            continue
        if verdict not in {"hit", "clear", "na"}:
            errors.append(f"item_id {item_id}: verdict вне enum hit/clear/na: {verdict!r}")
        elif verdict in {"hit", "na"} and not valid_checklist_note(response.get("note")):
            errors.append(f"item_id {item_id}: verdict {verdict} без валидного note")
        verdicts.setdefault(item_id, set()).add(verdict)
    missing = required - set(verdicts)
    if missing:
        errors.append(f"чеклист покрыт не полностью по applies_to ({wave_type}): {sorted(missing)}")
    for item_id, variants in verdicts.items():
        if len(variants) > 1:
            errors.append(f"дубликат item_id {item_id} с конфликтующими verdict: {sorted(variants)}")
    return errors


def checklist_stats(records: list[dict], participant_id: str) -> dict:
    """Агрегат verdict'ов чеклиста участника по ПРИНЯТЫМ ходам сессии (E-4);
    final_statement исключён."""
    stats = {"hit": 0, "clear": 0, "na": 0}
    for record in records:
        if record.get("author") != participant_id or record.get("type") == "final_statement":
            continue
        for response in (record.get("structured") or {}).get("risk_checklist_responses") or []:
            verdict = response.get("verdict")
            if verdict in stats:
                stats[verdict] += 1
    return stats
