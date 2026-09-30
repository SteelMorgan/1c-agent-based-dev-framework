#!/usr/bin/env python3
"""Ядро консилиума: чистая механика протокола (TD CONS-01) + shim'ы совместимости
на review-harness (RVSW-01 T-06, TD §14.1, FR-17).

Машина состояний фаз A–E, предикаты стоп-условий (FR-02), kill-прокси (FR-03),
анонимизация (FR-04), роли (FR-08), transcript (FR-04), кворум, digest-lint,
синтез, бюджеты вызовов (NFR-01) и wall-clock (TD 6) остаются здесь — это
протокольная логика консилиума.

Переехавшее в `framework/skills/tool-usage/review/review-harness/scripts/` (единый источник):
- `structured.py`   — fenced-блоки, anon_map/анонимизация, estimate_tokens;
- `track_record.py` — decay/strengths/exploration (seq_field="consilium_seq");
- `domains.py`      — domains.yaml, риск-чеклисты (DomainsError → ProtocolError);
- `registry.py`     — adapters.yaml схемы v2 (единственная схема; v1-ветка
  и legacy-валидатор удалены в R-Final F-04).

Публичные имена и дефолты сохранены (CR-00 snapshot, tests/unit/
test_shim_completeness.py); поведение протокола не изменено (FR-17). Shim'ы —
временная прослойка совместимости: новые потребители импортируют harness-
модули напрямую.
"""
from __future__ import annotations

import contextlib
import fcntl
import json
import random  # noqa: F401 — имя входит в CR-00 snapshot публичных имён
import re
import sys
from datetime import UTC, datetime
from pathlib import Path

# Бутстрэп sys.path на harness: consilium_core импортируется и из CLI
# (consilium.py уже настроил путь), и напрямую из unit-тестов.
_HARNESS_SCRIPTS = Path(__file__).resolve().parents[2] / "review-harness" / "scripts"
if _HARNESS_SCRIPTS.is_dir() and str(_HARNESS_SCRIPTS) not in sys.path:
    sys.path.append(str(_HARNESS_SCRIPTS))

import domains as _h_domains  # noqa: E402
import registry as _h_registry  # noqa: E402
import structured as _h_structured  # noqa: E402
import track_record as _h_track_record  # noqa: E402

# ---------------------------------------------------------------------------
# Константы протокола (TD 3.4, 4.2, 6)
# ---------------------------------------------------------------------------

TRANSCRIPT_NAME = "transcript.jsonl"
RECORD_TYPES = {
    "proposal", "attack", "response", "digest", "kill_decision", "final_statement",
    "synthesis", "redteam_attack", "confirmation", "verdict", "system", "error",
}
PARTICIPANT_TYPES = {"proposal", "attack", "response", "redteam_attack", "confirmation"}
MODERATOR_ID = "moderator"

# ---------------------------------------------------------------------------
# Human-critic режим (CONS-06, вердикт cons-20260804-152322-0a358311, E1–E13)
# ---------------------------------------------------------------------------
# Human-critic — session-scoped сущность уровня transcript (E1): НЕ participant
# реестра adapters.yaml, в session.participants не добавляется. Анонимный
# presentation-id — из общего пространства create_anon_map.
HUMAN_CRITIC_ID = "human-critic"
# Fail-closed привязка ходов к волнам (E2): только attack фазы B и redteam фазы D.
HUMAN_ATTACK_WAVES = {"B": "attack", "D": "redteam"}
# Типы transcript-записей human-хода по волне.
HUMAN_TURN_TYPES = {"attack": "attack", "redteam": "redteam_attack"}
DEFAULT_HUMAN_WAIT_CAP_SEC = 1800  # E6: разумный default капа ожидания human-хода

ROLE_CATALOG = ["security", "architecture", "pragmatics", "эксплуатация"]

INVOCATION_WARN_MAX = 25       # NFR-01/TD 6: default-диапазон
INVOCATION_HARD_MAX = 31       # NFR-01/TD 6: жёсткий потолок
PARTICIPANT_INVOCATION_KINDS = {"participant_turn", "confirmation", "final_statement"}

WALL_CLOCK_FLOOR_SEC = 7200    # CONS-02 OPT-1: нижний пол cap
WALL_CLOCK_WAVES_MAX = 13      # модель волн TD §6 (A:1 + B:4×2 + final_statement:≤2 + D:2)
WALL_CLOCK_MODERATOR_SEC = 1800  # надбавка на работу модератора между волнами
WAVE_GRACE_SEC = 120           # grace на сбор результатов волны (×2)


def wall_clock_budget(timeout_sec: int) -> tuple[int, int]:
    """CONS-02 OPT-1: cap = max(7200, 13 × (T + 240) + 1800); warn = 75 %.
    Пересчитывается от per-invocation timeout сессии (включая override)."""
    budget = max(
        WALL_CLOCK_FLOOR_SEC,
        WALL_CLOCK_WAVES_MAX * (timeout_sec + 2 * WAVE_GRACE_SEC) + WALL_CLOCK_MODERATOR_SEC,
    )
    return budget, int(budget * 0.75)


class ProtocolError(Exception):
    """Нарушение формального инварианта протокола (fail-closed)."""


def utc_now() -> str:
    return datetime.now(UTC).isoformat()


# ---------------------------------------------------------------------------
# Shim'ы на harness: structured (fenced-блоки, анонимизация, токены)
# ---------------------------------------------------------------------------

STRUCTURED_BLOCK_RE = _h_structured.STRUCTURED_BLOCK_RE
EMPTY_STRUCTURED = _h_structured.EMPTY_STRUCTURED
parse_structured_block = _h_structured.parse_structured_block
create_anon_map = _h_structured.create_anon_map
anonymize_text = _h_structured.anonymize_text
estimate_tokens = _h_structured.estimate_tokens

# ---------------------------------------------------------------------------
# Shim'ы на harness: track record (decay, strengths, exploration)
# seq_field по умолчанию "consilium_seq" — совместимость (T-03).
# ---------------------------------------------------------------------------

STRENGTHS_FLOOR = _h_track_record.STRENGTHS_FLOOR      # проекция применяется при n_eff >= 3
STRENGTHS_HALF_LIFE = _h_track_record.STRENGTHS_HALF_LIFE  # TD 3.4: half-life 8 консилиумов
EXPLORATION_EVERY = _h_track_record.EXPLORATION_EVERY  # TD 3.4: каждый 4-й консилиум — exploration
TAINTED_DISCOUNT = _h_track_record.TAINTED_DISCOUNT    # TD 3.4: дисконт «модератор = участник»
SCORE_W_ACCEPT = _h_track_record.SCORE_W_ACCEPT
SCORE_W_UPHELD = _h_track_record.SCORE_W_UPHELD
observation_weight = _h_track_record.observation_weight
compute_strengths = _h_track_record.compute_strengths
is_exploration_consilium = _h_track_record.is_exploration_consilium

# ---------------------------------------------------------------------------
# Shim'ы на harness: домены и риск-чеклисты (DomainsError → ProtocolError)
# ---------------------------------------------------------------------------

CHECKLIST_WAVE_TYPES = _h_domains.CHECKLIST_WAVE_TYPES
DEFAULT_DOMAIN = _h_domains.DEFAULT_DOMAIN
domain_by_id = _h_domains.domain_by_id
valid_checklist_note = _h_domains.valid_checklist_note
applicable_checklist_items = _h_domains.applicable_checklist_items
validate_checklist_responses = _h_domains.validate_checklist_responses
checklist_stats = _h_domains.checklist_stats

# Shared domains.yaml harness содержит пакет code-review роя с меткой applies_to
# `tour1` (RVSW-01, TD §5.8; владелец семантики — review-swarm swarm_core):
# default-валидация консилиума принимает объединённый enum (правило границы FR-01 —
# harness сам меток инструментов не знает, объединение объявлено здесь, на
# стороне инструмента); семантика волн консилиума (CHECKLIST_WAVE_TYPES) не меняется.
_DEFAULT_WAVE_TYPES = CHECKLIST_WAVE_TYPES | {"tour1"}


def parse_domains_yaml(text: str) -> dict:
    """Re-export harness-парсера; DomainsError приводится к ProtocolError."""
    try:
        return _h_domains.parse_domains_yaml(text)
    except _h_domains.DomainsError as exc:
        raise ProtocolError(str(exc)) from exc


def validate_domains(registry: dict, wave_types: set | None = None) -> list[str]:
    """Re-export harness-валидатора; default wave_types — shared enum (см. выше)."""
    return _h_domains.validate_domains(
        registry, wave_types=_DEFAULT_WAVE_TYPES if wave_types is None else wave_types)


def load_domains(path: Path, wave_types: set | None = None) -> dict:
    """Re-export harness-загрузчика (actionable-отказ); DomainsError → ProtocolError."""
    try:
        return _h_domains.load_domains(
            path, wave_types=_DEFAULT_WAVE_TYPES if wave_types is None else wave_types)
    except _h_domains.DomainsError as exc:
        raise ProtocolError(str(exc)) from exc


# ---------------------------------------------------------------------------
# Shim'ы на harness: реестр adapters.yaml (FR-07; v2 — TD RVSW-01 §4)
# ---------------------------------------------------------------------------

REQUIRED_REGISTRY_FIELDS = _h_registry.REQUIRED_REGISTRY_FIELDS
OPTIONAL_REGISTRY_FIELDS = _h_registry.OPTIONAL_REGISTRY_FIELDS


def parse_adapters_yaml(text: str) -> dict:
    """Re-export harness-парсера (схема плоских скаляров); RegistryError → ProtocolError."""
    try:
        return _h_registry.parse_adapters_yaml(text)
    except _h_registry.RegistryError as exc:
        raise ProtocolError(str(exc)) from exc


def load_registry(path: Path) -> dict:
    return parse_adapters_yaml(Path(path).read_text(encoding="utf-8"))


def validate_registry(registry: dict, base_dir: Path | None = None) -> list[str]:
    """Shim на harness-валидатор схемы v2 (FR-02, единый реестр).
    v1-ветка и legacy-валидатор удалены (R-Final F-04): фикстуры консилиума
    мигрированы на v2, миграционного слоя нет. Без base_dir существование
    adapter проверяется от cwd — поведение legacy-проверки абсолютных путей."""
    return _h_registry.validate_registry(
        registry, base_dir=Path.cwd() if base_dir is None else base_dir)


# ---------------------------------------------------------------------------
# Transcript (FR-04): append-only, сквозная нумерация, no-rewrite
# ---------------------------------------------------------------------------

def transcript_path(session_dir: Path) -> Path:
    return Path(session_dir) / TRANSCRIPT_NAME


def read_transcript(session_dir: Path) -> list[dict]:
    path = transcript_path(session_dir)
    if not path.exists():
        return []
    records = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line:
            records.append(json.loads(line))
    return records


def next_seq(records: list[dict]) -> int:
    return (records[-1]["seq"] + 1) if records else 1


TRANSCRIPT_LOCK_NAME = "transcript.lock"


@contextlib.contextmanager
def transcript_lock(session_dir: Path):
    """Межпроцессная блокировка transcript (F-001/F-003, CONS-06 rev): check-then-act
    (чтение seq / проверка «1 human-ход на волну») и append выполняются под одним
    flock — два процесса не выделят дубликат seq и не запишут два human-хода на волну."""
    session_dir = Path(session_dir)
    session_dir.mkdir(parents=True, exist_ok=True)
    with (session_dir / TRANSCRIPT_LOCK_NAME).open("a", encoding="utf-8") as lock_file:
        fcntl.flock(lock_file, fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(lock_file, fcntl.LOCK_UN)


def _append_record_unlocked(session_dir: Path, record: dict) -> dict:
    """Тело append_record без блокировки (вызывается из-под transcript_lock)."""
    session_dir = Path(session_dir)
    session_dir.mkdir(parents=True, exist_ok=True)
    records = read_transcript(session_dir)
    expected = next_seq(records)
    if "seq" in record and record["seq"] is not None:
        if record["seq"] != expected:
            raise ProtocolError(
                f"no-rewrite: запись с seq={record['seq']} отклонена, ожидался seq={expected}"
            )
    record_type = record.get("type")
    if record_type not in RECORD_TYPES:
        raise ProtocolError(f"неизвестный type записи: {record_type!r}")
    author = record.get("author")
    if author == MODERATOR_ID and record_type in PARTICIPANT_TYPES:
        raise ProtocolError(
            f"модератор не голосует и не выдвигает модель: type={record_type} с author=moderator запрещён (FR-05)"
        )
    stored = {
        "seq": expected,
        "ts": record.get("ts") or utc_now(),
        "session_id": record.get("session_id"),
        "phase": record.get("phase"),
        "round": record.get("round", 0),
        "wave": record.get("wave"),
        "author": author,
        "anon_id": record.get("anon_id"),
        "type": record_type,
        "refs": list(record.get("refs") or []),
        "content": record.get("content", ""),
        "structured": {**EMPTY_STRUCTURED, **(record.get("structured") or {})},
    }
    with transcript_path(session_dir).open("a", encoding="utf-8") as f:
        f.write(json.dumps(stored, ensure_ascii=False) + "\n")
    return stored


def append_record(session_dir: Path, record: dict) -> dict:
    """Добавляет запись в transcript. Инварианты:
    - seq сквозной: record без seq получает last+1; явный seq != last+1 → отказ (no-rewrite);
    - модератор не может быть автором хода участника (FR-05, IT-15);
    - type ∈ RECORD_TYPES;
    - выделение seq и append атомарны под flock (F-003, CONS-06 rev).
    """
    with transcript_lock(session_dir):
        return _append_record_unlocked(session_dir, record)


def append_human_turn(session_dir: Path, record: dict,
                      phase: str, round_no: int, wave: str) -> dict:
    """Атомарная фиксация human-хода (CONS-06 E2 + rev F-001): проверка
    «максимум 1 human-ход на волну» и append под одной блокировкой transcript."""
    with transcript_lock(session_dir):
        records = read_transcript(session_dir)
        if wave_has_human_turn(records, phase, round_no, wave):
            raise ProtocolError(
                f"human-ход на волну {phase}:{round_no}:{wave} уже зафиксирован — "
                f"максимум 1 human-ход на волну (E2)"
            )
        return _append_record_unlocked(session_dir, record)


# ---------------------------------------------------------------------------
# Предикаты стоп-условий фазы B (FR-02, TD 4.2)
# ---------------------------------------------------------------------------

def round_has_new_findings(records: list[dict], round_no: int, exclude_authors: set | None = None) -> bool:
    """Новые findings раунда (FR-02 + CONS-03 E-4): ∃ запись раунда с непустыми
    structured.new_findings ИЛИ с ответом чеклиста verdict=hit с валидным note
    (расширение ВХОДА предиката; механика стоп-условий неизменна).
    exclude_authors (CONS-06 E3): записи human-critic в стоп-предикатах не участвуют."""
    for record in records:
        if record.get("round") != round_no:
            continue
        if exclude_authors and record.get("author") in exclude_authors:
            continue
        structured = record.get("structured") or {}
        if structured.get("new_findings"):
            return True
        for response in structured.get("risk_checklist_responses") or []:
            if response.get("verdict") == "hit" and valid_checklist_note(response.get("note")):
                return True
    return False


def round_has_position_changes(records: list[dict], round_no: int,
                               exclude_authors: set | None = None) -> bool:
    """Смена позиций раунда (FR-02). exclude_authors (CONS-06 E3): human-записи
    не двигают стоп-предикат pc."""
    return any(
        r.get("round") == round_no and (r.get("structured") or {}).get("position_changes")
        and not (exclude_authors and r.get("author") in exclude_authors)
        for r in records
    )


# ---------------------------------------------------------------------------
# Human-critic: предикаты привязки и валидация хода (CONS-06 E2/E8/E9)
# ---------------------------------------------------------------------------

def human_turn_wave_allowed(phase: str | None, wave: str | None) -> bool:
    """Привязка human-хода к волне (E2, fail-closed предикат ядра):
    только attack фазы B и redteam фазы D."""
    return wave is not None and HUMAN_ATTACK_WAVES.get(phase or "") == wave


def wave_has_human_turn(records: list[dict], phase: str, round_no: int, wave: str) -> bool:
    """Максимум 1 human-ход на волну (E2): уже есть запись human-critic
    за (phase, round, wave)?"""
    return any(
        r.get("author") == HUMAN_CRITIC_ID
        and r.get("phase") == phase and r.get("round") == round_no and r.get("wave") == wave
        for r in records
    )


def human_turns_in_phase(records: list[dict], phase: str) -> int:
    """Число ходов human-critic в фазе (E8: факт ≥1 хода в B запрещает skip фазы D)."""
    return sum(
        1 for r in records
        if r.get("author") == HUMAN_CRITIC_ID and r.get("phase") == phase
        and r.get("type") in PARTICIPANT_TYPES
    )


def _validate_refs_list(refs, what: str) -> None:
    """refs — список целых seq ≥ 1 (F-008/F-018, CONS-06 rev)."""
    if not isinstance(refs, list):
        raise ProtocolError(f"human turn-файл: {what} должен быть списком seq")
    for ref in refs:
        if not isinstance(ref, int) or isinstance(ref, bool) or ref < 1:
            raise ProtocolError(f"human turn-файл: {what} содержит невалидный seq: {ref!r}")


def _validate_human_structured(structured: dict) -> None:
    """Вложенная валидация structured human-хода (F-008/F-018): типы полей и
    вложенных записей; чеклист у человека отсутствует (нет роли)."""
    allowed = set(EMPTY_STRUCTURED)
    unknown = set(structured) - allowed
    if unknown:
        raise ProtocolError(f"human turn-файл: неизвестные поля structured: {sorted(unknown)}")
    elements = structured.get("elements", [])
    if not isinstance(elements, list) or not all(isinstance(e, str) and e for e in elements):
        raise ProtocolError("human turn-файл: structured.elements — список непустых строк")
    for finding in structured.get("new_findings") or []:
        if not isinstance(finding, dict) or not isinstance(finding.get("id"), str) \
                or not isinstance(finding.get("text"), str) or not finding["text"].strip():
            raise ProtocolError("human turn-файл: new_findings[] — {id: str, text: непустая str}")
    for change in structured.get("position_changes") or []:
        if not isinstance(change, dict) or not isinstance(change.get("element"), str) \
                or not change["element"]:
            raise ProtocolError("human turn-файл: position_changes[] требует element: str")
        if change.get("action") not in {"agree", "disagree", "refine", "withdraw"}:
            raise ProtocolError(
                f"human turn-файл: position_changes[] action ∉ "
                f"{{agree, disagree, refine, withdraw}}: {change.get('action')!r}")
        _validate_refs_list(change.get("refs") or [], "position_changes[].refs")
    for entry in structured.get("borrowed") or []:
        if not isinstance(entry, dict) or not isinstance(entry.get("element"), str):
            raise ProtocolError("human turn-файл: borrowed[] — {element: str, source_ref: seq}")
        source_ref = entry.get("source_ref")
        if not isinstance(source_ref, int) or isinstance(source_ref, bool) or source_ref < 1:
            raise ProtocolError(
                f"human turn-файл: borrowed[].source_ref — целый seq ≥ 1: {source_ref!r}")
    checklist = structured.get("risk_checklist_responses") or []
    if checklist:
        raise ProtocolError(
            "human turn-файл: risk_checklist_responses у human-critic отсутствует (нет роли)")


def validate_human_turn(payload: dict, protected_ids: list[str]) -> dict:
    """Валидация human turn-файла (E2/E9, fail-closed).

    Схема файла: JSON-объект {content: str, structured: dict, human_approved: true}.
    - обязательная аттестация human_approved: true — без неё отказ (функция audit,
      не access control; явная ложная аттестация фиксируется в transcript);
    - content — непустая строка, structured — объект с типизированными
      вложенными полями (F-008/F-018);
    - lint по exact protected identifiers (E9): внутренние id участников и
      HUMAN_CRITIC_ID не должны встречаться в content/structured (широкие
      маркерные словари отвергнуты вердиктом).

    Возвращает нормализованный ход {content, structured, refs}."""
    if not isinstance(payload, dict):
        raise ProtocolError("human turn-файл: ожидался JSON-объект "
                            "{content, structured, human_approved}")
    if payload.get("human_approved") is not True:
        raise ProtocolError(
            "human turn-файл отклонён: обязательная аттестация human_approved: true "
            "отсутствует или не равна true (fail-closed, CONS-06 E2)"
        )
    content = payload.get("content")
    if not isinstance(content, str) or not content.strip():
        raise ProtocolError("human turn-файл: content обязателен (непустая строка)")
    structured = payload.get("structured", {})
    if structured is None:
        structured = {}
    if not isinstance(structured, dict):
        raise ProtocolError("human turn-файл: structured должен быть JSON-объектом")
    _validate_human_structured(structured)
    lint_text = content + "\n" + json.dumps(structured, ensure_ascii=False)
    for protected in protected_ids:
        if protected and protected in lint_text:
            raise ProtocolError(
                f"human turn-файл содержит защищённый идентификатор: {protected!r} "
                f"(CONS-06 E9: exact protected identifiers)"
            )
    refs = payload.get("refs") or []
    _validate_refs_list(refs, "refs")
    return {"content": content, "structured": structured, "refs": refs}


def phase_d_skip_with_human(skip: bool, details: dict, records: list[dict],
                            human_critic_enabled: bool) -> tuple[bool, dict]:
    """Поправка предиката пропуска фазы D для human-critic режима (E8):
    при включённом режиме skip запрещён ТОЛЬКО если человек фактически сделал
    ≥1 ход в фазе B (иначе действует обычный предикат should_skip_phase_d)."""
    if skip and human_critic_enabled and human_turns_in_phase(records, "B") >= 1:
        return False, {
            "reason": "human-critic участвовал в фазе B — фаза D обязательна "
                      "(CONS-06 E8); обычный предикат пропуска отменён",
            "human_override": True,
        }
    return skip, details


def is_converged(alive_count: int, rounds_completed: int, nf: bool, pc: bool) -> bool:
    """Конвергенция — конъюнкция (FR-02): живых <= 2 И раунд без NF/PC И r >= 1."""
    return alive_count <= 2 and not nf and not pc and rounds_completed >= 1


def round_3_allowed(nf2: bool, pc2: bool) -> bool:
    """3-й раунд проводится ⟺ раунд 2 принёс NF или PC (FR-02)."""
    return nf2 or pc2


def update_stalemate(counter: int, freeze: bool, pc: bool) -> int:
    """Заморозка unresponsive не двигает счётчик (FR-11); PC сбрасывает; иначе +1."""
    if freeze:
        return counter
    if pc:
        return 0
    return counter + 1


def is_stalemate(counter: int) -> bool:
    return counter >= 2


def evaluate_b_stop(
    alive_count: int,
    rounds_completed: int,
    nf: bool,
    pc: bool,
    freeze: bool,
    stalemate_counter: int,
) -> tuple[str | None, int]:
    """Оценка стоп-условий после завершения раунда B.

    Возвращает (причина | None, новый stalemate-счётчик).
    Причины: "converged" | "stalemate" | "round_limit".
    """
    counter = update_stalemate(stalemate_counter, freeze, pc)
    if is_converged(alive_count, rounds_completed, nf, pc):
        return "converged", counter
    if is_stalemate(counter):
        return "stalemate", counter
    if rounds_completed >= 4:
        return "round_limit", counter
    if rounds_completed >= 2 and not (nf or pc):
        return "round_limit", counter
    return None, counter


# ---------------------------------------------------------------------------
# Kill-прокси (FR-03, TD 4.4) и антинакрутка borrowed (FR-04)
# ---------------------------------------------------------------------------

def resolve_borrowed(record: dict, records: list[dict]) -> tuple[list[dict], list[dict]]:
    """Разрешимость borrowed[].source_ref: существующий seq записи ДРУГОГО автора,
    содержащей объявление заимствованного элемента. Возвращает (valid, invalid)."""
    by_seq = {r["seq"]: r for r in records if r.get("seq") is not None}
    valid: list[dict] = []
    invalid: list[dict] = []
    for entry in (record.get("structured") or {}).get("borrowed") or []:
        source = by_seq.get(entry.get("source_ref"))
        if (
            source is not None
            and source.get("author") != record.get("author")
            and entry.get("element") in ((source.get("structured") or {}).get("elements") or [])
        ):
            valid.append(entry)
        else:
            invalid.append(entry)
    return valid, invalid


def _split_element(element: str) -> tuple[str | None, str]:
    """'<owner>:<element>' → (owner, element); без префикса → (None, element)."""
    if ":" in element:
        owner, _, rest = element.partition(":")
        return owner, rest
    return None, element


def _element_belongs_to(element: str, author: str) -> bool:
    owner, _ = _split_element(element)
    return owner in (None, author)


def compute_model_metrics(records: list[dict], model_ids: list[str],
                          excluded_attack_authors: set | None = None) -> dict:
    """Прокси-метрики kill-критерия (TD 4.4): survived / borrowed_in / upheld_against.

    - elements(m) — последняя декларация элементов автора m (дедуплицированная);
    - survived(m) = |elements| − |withdrawn автором| − |элементы с upheld-критикой,
      не отозванные автором| (F-18: отозванный после атаки вычитается один раз);
    - borrowed_in(m) — разрешимые borrowed чужих записей на записи m (антинакрутка);
    - upheld_against(m) — элементы ПОСЛЕДНЕЙ декларации с upheld-критикой
      (tie-break kill; критика по снятому из декларации элементу не влияет);
    - upheld critique: атака (disagree чужого автора) на элемент m:E, после которой
      ближайший position_changes автора m по этому элементу ∈ {agree, withdraw} (TD 3.3).
    - excluded_attack_authors (CONS-06 E4, политика 2): атаки этих авторов
      (human-critic) не попадают в upheld/metrics; withdraw модели после
      human-критики по-прежнему снижает survived (withdrawn-наполнение не меняется).

    Источники семантики: определение upheld критики —
    references/transcript-schema.md (TD 3.3); контракт формулы survived/upheld_against
    (двойной вычет F-18, фильтр по declared, дедупликация) зафиксирован в
    tasks/agentic-operations/CONS-06-human-critic-mode/consilium-verdict.md (E13, F-18)
    и покрыт тестами test_f18_* в tests/unit/test_core.py.
    """
    excluded_attack_authors = excluded_attack_authors or set()
    ordered = sorted((r for r in records if r.get("seq") is not None), key=lambda r: r["seq"])
    elements: dict[str, list[str]] = {}
    for record in ordered:
        author = record.get("author")
        declared = (record.get("structured") or {}).get("elements") or []
        if author in model_ids and declared:
            elements[author] = list(declared)

    withdrawn: dict[str, set[str]] = {m: set() for m in model_ids}
    attacked: list[tuple[int, str, str]] = []  # (seq, target_author, element)
    author_changes: dict[str, list[tuple[int, str, str]]] = {m: [] for m in model_ids}

    for record in ordered:
        author = record.get("author")
        for change in (record.get("structured") or {}).get("position_changes") or []:
            element = change.get("element") or ""
            owner, bare = _split_element(element)
            action = change.get("action")
            if author in model_ids and _element_belongs_to(element, author):
                author_changes[author].append((record["seq"], bare, action))
                if action == "withdraw":
                    withdrawn[author].add(bare)
            if owner is not None and owner in model_ids and owner != author and action == "disagree":
                # F-005 (rev, сознательная асимметрия): атака засчитывается только с явным
                # owner-префиксом (m:E), тогда как withdraw автора принимается и с голым
                # именем — голая атака неприписываема и не идёт в upheld. Пересмотр при
                # появлении злоупотреблений (follow-up в consilium-verdict.md, E13).
                if author in excluded_attack_authors:
                    # CONS-06 E4 (политика 2): атака human-critic не засчитывается
                    # в upheld/metrics — влияние на kill только через суверенный
                    # withdraw модели (withdrawn-наполнение выше не затрагивается).
                    continue
                attacked.append((record["seq"], owner, bare))

    upheld: dict[str, set[str]] = {m: set() for m in model_ids}
    for attack_seq, target, bare in attacked:
        later = [
            (seq, action) for seq, elem, action in author_changes.get(target, [])
            if elem == bare and seq > attack_seq
        ]
        if later:
            later.sort()
            if later[0][1] in {"agree", "withdraw"}:
                upheld[target].add(bare)

    borrowed_in: dict[str, int] = {m: 0 for m in model_ids}
    for record in ordered:
        if record.get("author") not in model_ids:
            continue
        valid, _ = resolve_borrowed(record, ordered)
        by_seq = {r["seq"]: r for r in ordered}
        for entry in valid:
            source_author = by_seq[entry["source_ref"]].get("author")
            if source_author in borrowed_in:
                borrowed_in[source_author] += 1

    metrics = {}
    for m in model_ids:
        # F-004: дедупликация — повторное объявление элемента в последней
        # декларации не накручивает survived (вычитаемые множества дедуплицированы,
        # len(declared) по списку давал бы лишний вычет-кредит).
        declared = set(elements.get(m, []))
        # F-18: элемент, отозванный автором после атаки, попадает и в withdrawn,
        # и в upheld (withdraw ∈ {agree, withdraw} по TD 3.3). В survived он
        # вычитается один раз: из upheld-множества исключаем уже отозванные.
        # Факт «критика подтверждена» для track-record собирается отдельно
        # (_critique_stats в consilium.py) и здесь не затрагивается.
        # F-002: и survived, и tie-break upheld_against считаются по элементам
        # последней декларации — критика по снятому из декларации элементу
        # не влияет ни на одну kill-метрику.
        upheld_declared = upheld[m] & declared
        upheld_unwithdrawn = upheld_declared - withdrawn[m]
        # F-006: clamp max(survived, 0) убран — оба вычитаемых множества ⊆ declared
        # и не пересекаются, поэтому survived ≥ 0 по построению; отрицательное
        # значение теперь сигнализирует о регрессии формулы, а не глушится.
        survived = len(declared) - len(withdrawn[m] & declared) - len(upheld_unwithdrawn)
        metrics[m] = {
            "survived": survived,
            "borrowed_in": borrowed_in[m],
            "upheld_against": len(upheld_declared),
        }
    return metrics


def kill_candidate(metrics: dict) -> str | None:
    """Детерминированный кандидат на kill (FR-03a):
    survived asc → borrowed_in asc → upheld_against desc; полное равенство → None."""
    if not metrics:
        return None
    ranked = sorted(
        metrics.items(),
        key=lambda item: (item[1]["survived"], item[1]["borrowed_in"], -item[1]["upheld_against"]),
    )
    if len(ranked) >= 2:
        first, second = ranked[0][1], ranked[1][1]
        if (
            first["survived"] == second["survived"]
            and first["borrowed_in"] == second["borrowed_in"]
            and first["upheld_against"] == second["upheld_against"]
        ):
            return None
    return ranked[0][0]


# ---------------------------------------------------------------------------
# Анонимизированная выдача волны (FR-04, TD 5.1–5.2)
# ---------------------------------------------------------------------------

def render_bundle(records: list[dict], anon_map: dict) -> str:
    """Анонимизированная выдача волны участникам (TD 5.1): author → anon_id,
    служебные поля убраны, реальные id вычищены и из содержимого.

    Membership-инвариант анонимизации (CONS-06 E3): participant-visible запись
    с author вне anon_map → ProtocolError (молчаливый fallback на реальный id
    раскрывал бы авторство — fail-closed)."""
    blocks = []
    for record in records:
        author = record.get("author")
        if author not in anon_map:
            raise ProtocolError(
                f"membership-инвариант анонимизации: автор {author!r} записи "
                f"seq={record.get('seq')} вне anon_map — bundle не рендерится (CONS-06 E3)"
            )
        structured = {
            key: value for key, value in (record.get("structured") or {}).items()
            if key != "risk_checklist_responses"  # E-4: ответы чеклиста участникам не рассылаются
        }
        payload = {
            "author": anon_map[author],
            "type": record.get("type"),
            "round": record.get("round"),
            "wave": record.get("wave"),
            "content": record.get("content", ""),
            "structured": structured,
        }
        rendered = json.dumps(payload, ensure_ascii=False, indent=2)
        blocks.append(anonymize_text(rendered, anon_map))
    return "\n\n".join(blocks)


def lint_digest(text: str, real_ids: list[str], budget_tokens: int) -> list[str]:
    """Lint дайджеста модератора (TD 5.2): без реальных id, в пределах бюджета."""
    violations = []
    for real_id in real_ids:
        if real_id and real_id in text:
            violations.append(f"дайджест содержит реальный id участника: {real_id}")
    tokens = estimate_tokens(text)
    if tokens > budget_tokens:
        violations.append(
            f"дайджест превышает context_budget: {tokens} > {budget_tokens} токенов"
        )
    return violations


# ---------------------------------------------------------------------------
# Роли (FR-08, TD 4.5)
# ---------------------------------------------------------------------------

def assign_roles_round_robin(participant_ids: list[str], last_roles: dict | None = None,
                             role_catalog: list[str] | None = None) -> dict:
    """Round-robin по каталогу ролей с запретом повторной роли участника.
    role_catalog — каталог ролей домена сессии (CONS-03 E-2); default — ROLE_CATALOG
    (= пакет architecture, регресс AC-E02)."""
    catalog = role_catalog or ROLE_CATALOG
    last_roles = last_roles or {}
    used: set[str] = set()
    assigned: dict[str, str] = {}
    for index, pid in enumerate(participant_ids):
        rotated = catalog[index % len(catalog):] + catalog[: index % len(catalog)]
        choice = next(
            (role for role in rotated if role not in used and role != last_roles.get(pid)),
            None,
        )
        if choice is None:
            choice = next((role for role in rotated if role not in used), rotated[0])
        assigned[pid] = choice
        used.add(choice)
    return assigned


def assign_roles(
    participant_ids: list[str],
    strengths: dict | None = None,
    n_eff: dict | None = None,
    exploration: bool = False,
    last_roles: dict | None = None,
    role_catalog: list[str] | None = None,
) -> dict:
    """Назначение ролей фазы A (FR-08, TD 4.5):
    (1) strengths при n_eff >= FLOOR (argmax score, запрет повтора роли);
    (2) fallback round-robin с запретом повтора;
    (3) exploration — только round-robin (статистика игнорируется).
    """
    catalog = role_catalog or ROLE_CATALOG
    last_roles = last_roles or {}
    if exploration or not strengths:
        return assign_roles_round_robin(participant_ids, last_roles, role_catalog=catalog)

    n_eff = n_eff or {}
    candidates = []
    for pid in participant_ids:
        for role in catalog:
            key = (pid, role)
            score = strengths.get(key)
            if score is not None and (n_eff.get(key) or 0) >= STRENGTHS_FLOOR and role != last_roles.get(pid):
                candidates.append((score, pid, role))
    candidates.sort(key=lambda item: (-item[0], item[1], item[2]))

    assigned: dict[str, str] = {}
    used: set[str] = set()
    for _score, pid, role in candidates:
        if pid not in assigned and role not in used:
            assigned[pid] = role
            used.add(role)

    remaining = [pid for pid in participant_ids if pid not in assigned]
    if remaining:
        fallback = assign_roles_round_robin(participant_ids, last_roles, role_catalog=catalog)
        for pid in remaining:
            role = fallback[pid]
            if role in used:
                role = next(
                    (r for r in catalog if r not in used and r != last_roles.get(pid)),
                    next((r for r in catalog if r not in used), role),
                )
            assigned[pid] = role
            used.add(role)
    return assigned


def assign_phase_d_role(
    participant_id: str,
    session_roles: list[str],
    role_history: list[tuple[str, int]],
    role_catalog: list[str] | None = None,
) -> str:
    """Кросс-ротация фазы D (FR-08): роль, которой не было в этом консилиуме;
    fallback — наименее давно занимавшаяся (по track record, max consilium_seq).
    role_catalog — каталог домена сессии (CONS-03 E-2)."""
    catalog = role_catalog or ROLE_CATALOG
    fresh = [role for role in catalog if role not in session_roles]
    if fresh:
        return fresh[0]
    last_used: dict[str, int] = {}
    for role, seq in role_history:
        last_used[role] = max(last_used.get(role, -1), seq)
    return min(catalog, key=lambda role: (last_used.get(role, -1), catalog.index(role)))


# ---------------------------------------------------------------------------
# Кворум (FR-06) и живые модели (FR-11)
# ---------------------------------------------------------------------------

def quorum_status(participants: list[dict]) -> dict:
    """Кворум (FR-06): >= 2 участников из >= 2 разных family; homogeneity warning
    при > 1 участника одного family."""
    families = sorted({p["family"] for p in participants})
    return {
        "ok": len(participants) >= 2 and len(families) >= 2,
        "families": families,
        "homogeneity_warning": len(participants) > len(families),
    }


def alive_model_ids(records: list[dict], participants: list[dict]) -> list[str]:
    """Живые модели (FR-11, F-05): не killed И имеют заявленную модель (proposal).

    Участник, ставший unresponsive до proposal (start/фаза A не состоялась), заморожен
    БЕЗ модели: исключается из подсчёта живых моделей (конвергенция достижима) и из
    kill-кандидатов. Unresponsive с proposal — замороженная живая модель (остаётся
    объектом критики и заимствования).
    """
    proposed = {r.get("author") for r in records if r.get("type") == "proposal"}
    return [
        p["id"] for p in participants
        if p.get("state") != "killed" and p.get("id") in proposed
    ]


# ---------------------------------------------------------------------------
# Бюджет вызовов (NFR-01, TD 6) и wall-clock (TD 6)
# ---------------------------------------------------------------------------

def count_invocation(state: dict, kind: str) -> dict:
    """1 ход участника = 1 вызов адаптера (вкл. confirmation и final_statement);
    действия модератора в счёт не входят (TD 6)."""
    updated = dict(state)
    if kind in PARTICIPANT_INVOCATION_KINDS:
        updated["invocation_count"] = int(updated.get("invocation_count", 0)) + 1
    return updated


def invocation_warning(count: int) -> bool:
    """Предупреждение при выходе за default-диапазон 25 (NFR-01)."""
    return count > INVOCATION_WARN_MAX


def b_wave_blocked(count: int) -> bool:
    """Жёсткий потолок 31: новые волны фазы B блокируются (TD 6)."""
    return count >= INVOCATION_HARD_MAX


def wall_clock_status(
    started_at: str,
    now: datetime | None = None,
    budget_sec: int | None = None,
    warn_at_sec: int | None = None,
    paused_sec: float = 0.0,
) -> str:
    """"ok" | "warn" (>= 75 %) | "exceeded" — бюджет из состояния сессии
    (CONS-02 OPT-1: пересчёт от per-invocation timeout); default — от T=900.
    paused_sec (CONS-06 E5): накопленное время ожидания human-хода вычитается
    из elapsed — бюджет останавливается на шаге человека (НЕ сдвиг started_at)."""
    if budget_sec is None or warn_at_sec is None:
        budget_sec, warn_at_sec = wall_clock_budget(900)
    started = datetime.fromisoformat(started_at)
    now = now or datetime.now(UTC)
    if started.tzinfo is None:
        started = started.replace(tzinfo=UTC)
    elapsed = max(0.0, (now - started).total_seconds() - float(paused_sec or 0.0))
    if elapsed >= budget_sec:
        return "exceeded"
    if elapsed >= warn_at_sec:
        return "warn"
    return "ok"


# ---------------------------------------------------------------------------
# CONS-02 OPT-2: экстрактивный черновик дайджеста
# ---------------------------------------------------------------------------

# Именованный словарь маркеров оценочности (AC-D04): negative-проверка применяется
# ТОЛЬКО к тексту шаблона вне fenced-блоков participant-extract. Пополнение словаря
# не ослабляет проверку.
EVALUATIVE_MARKERS = [
    "рекомендую", "рекомендуется", "следует выбрать", "лучший", "лучшая",
    "наилучший", "предпочтительно", "предпочтительнее", "сильнейшая модель",
    "слабейшая модель", "победила", "правильное решение", "по моему мнению",
]

DRAFT_BLOCK_TAG = "participant-extract"

_DRAFT_HEADER = (
    "# Черновик дайджеста (машинный, экстрактивный)\n\n"
    "Ниже — только извлечённые из transcript факты ходов (атаки, изменения позиций, "
    "заимствования, findings) по раундам. Модератор обязан просмотреть черновик "
    "против transcript и при необходимости дополнить; финальный дайджест авторит "
    "модератор и передаётся через --digest-file."
)


def generate_digest_draft(records: list[dict], anon_map: dict) -> str:
    """Экстрактивный черновик дайджеста (OPT-2): только факты ходов из
    structured-полей, анонимизированные по anon_map; полезная нагрузка — строго
    внутри fenced-блоков ```participant-extract; шаблон — без оценок (антиякорение)."""
    parts = [_DRAFT_HEADER]
    by_round: dict = {}
    for record in records:
        if record.get("type") not in PARTICIPANT_TYPES:
            continue
        by_round.setdefault((record.get("phase"), record.get("round")), []).append(record)

    for (phase, round_no) in sorted(by_round, key=lambda k: (str(k[0]), k[1])):
        facts: list[str] = []
        for record in by_round[(phase, round_no)]:
            author = anon_map.get(record.get("author"), record.get("author"))
            seq = record.get("seq")
            structured = record.get("structured") or {}
            declared = structured.get("elements") or []
            if declared:
                facts.append(
                    f"{author}: элементы модели {', '.join(str(e) for e in declared)} [seq:{seq}]"
                )
            for change in structured.get("position_changes") or []:
                element = anonymize_text(str(change.get("element") or ""), anon_map)
                refs = ", ".join(f"seq:{n}" for n in change.get("refs") or [])
                facts.append(
                    f"{author}: {change.get('action')} по {element}"
                    + (f" (refs: {refs})" if refs else "") + f" [seq:{seq}]"
                )
            for borrowed in structured.get("borrowed") or []:
                source_ref = borrowed.get("source_ref")
                facts.append(
                    f"{author}: заимствование {borrowed.get('element')}"
                    + (f" (источник: seq:{source_ref})" if source_ref else " (источник неразрешим)")
                    + f" [seq:{seq}]"
                )
            for finding in structured.get("new_findings") or []:
                facts.append(f"{author}: finding {finding.get('id')} [seq:{seq}]")
        if facts:
            block = "\n".join(f"- {fact}" for fact in facts)
            parts.append(
                f"## Фаза {phase}, раунд {round_no}\n\n"
                f"```{DRAFT_BLOCK_TAG}\n{block}\n```"
            )
    return "\n\n".join(parts) + "\n"


def draft_template_text(draft: str) -> str:
    """Текст черновика ВНЕ fenced-блоков participant-extract (предмет negative-
    проверки антиякорения AC-D04)."""
    outside: list[str] = []
    in_block = False
    for line in draft.splitlines():
        if line.strip().startswith("```"):
            in_block = not in_block
            continue
        if not in_block:
            outside.append(line)
    return "\n".join(outside)


# ---------------------------------------------------------------------------
# CONS-02 OPT-3: формат синтеза и предикат пропуска фазы D
# ---------------------------------------------------------------------------

SYNTHESIS_ELEMENT_RE = re.compile(
    r"^- \[E(\d+)\] (?P<text>.+?) \(refs: (?P<refs>(?:seq:\d+(?:,\s*)?)+)\)\s*$"
)
SYNTHESIS_RANGE_RE = re.compile(r"seq:\d+\s*-\s*\d+")  # диапазоны запрещены
SYNTHESIS_HEADER_RE = re.compile(r"^#{1,6}\s+")
SYNTHESIS_EXCLUSION_MARK = "исключ"  # заголовок секции обоснования исключения вкладов


def parse_synthesis(text: str) -> list[dict]:
    """Парсинг синтеза по шаблону `- [E<n>] <текст> (refs: seq:<n>, …)` (OPT-3).

    Fail-closed: диапазоны refs; строки вне шаблона элемента и вне whitelist-секций
    (заголовок; секция обоснования исключения вкладов); требуется ≥1 элемента и
    секция обоснования исключения вкладов (защита от умолчания, RISK-D04).
    """
    if SYNTHESIS_RANGE_RE.search(text):
        raise ProtocolError("синтез: диапазоны refs вида seq:2-4 запрещены (перечисление через запятую)")
    elements: list[dict] = []
    in_exclusion = False
    has_exclusion = False
    for line_number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if not stripped:
            continue
        match = SYNTHESIS_ELEMENT_RE.match(stripped)
        if match:
            refs = [int(n) for n in re.findall(r"seq:(\d+)", match.group("refs"))]
            elements.append({"id": f"E{match.group(1)}", "text": match.group("text"), "refs": refs})
            continue
        if SYNTHESIS_HEADER_RE.match(stripped):
            in_exclusion = SYNTHESIS_EXCLUSION_MARK in stripped.lower()
            has_exclusion = has_exclusion or in_exclusion
            continue
        if in_exclusion:
            continue  # свободный текст секции обоснования исключения вкладов
        raise ProtocolError(
            f"синтез: строка {line_number} вне шаблона элемента и вне разрешённых секций: {stripped!r}"
        )
    if not elements:
        raise ProtocolError("синтез без валидных элементов (требуется ≥1 элемента)")
    if not has_exclusion:
        raise ProtocolError("синтез без секции обоснования исключения вкладов (RISK-D04)")
    return elements


def should_skip_phase_d(
    elements: list[dict],
    records: list[dict],
    participants: list[dict],
) -> tuple[bool, dict]:
    """Предикат пропуска фазы D (OPT-3), консервативный: любая неопределённость →
    фаза D обязательна. Пропуск ⟺ все элементы трассируются ровно к ОДНОЙ модели-
    источнику (membership: id элемента ∈ structured.elements referenced-записи),
    она входит в alive_model_ids и НЕ unresponsive; refs обязательны и разрешимы;
    ref на запись author=moderator — элемент модератора → пропуск запрещён."""
    by_seq = {r["seq"]: r for r in records if r.get("seq") is not None}
    sources: set[str] = set()
    for element in elements:
        if not element["refs"]:
            return False, {"reason": f"элемент {element['id']} без refs"}
        for ref in element["refs"]:
            record = by_seq.get(ref)
            if record is None:
                return False, {"reason": f"элемент {element['id']}: неразрешимый ref seq:{ref}"}
            if record.get("author") == MODERATOR_ID:
                return False, {"reason": f"элемент {element['id']}: ref seq:{ref} на запись модератора"}
            declared = (record.get("structured") or {}).get("elements") or []
            if element["id"] not in declared:
                return False, {
                    "reason": f"элемент {element['id']}: membership-проверка не пройдена "
                              f"(нет в elements записи seq:{ref})"
                }
            sources.add(record["author"])
    if len(sources) != 1:
        return False, {"reason": f"моделей-источников синтеза: {sorted(sources) or 'нет'} (нужна ровно одна)"}
    source = next(iter(sources))
    alive = alive_model_ids(records, participants)
    if source not in alive:
        return False, {"reason": f"модель-источник {source} вне alive_model_ids"}
    source_state = next((p.get("state") for p in participants if p.get("id") == source), None)
    if source_state == "unresponsive":
        return False, {"reason": f"модель-источник {source} unresponsive — подтверждение неискажённости невозможно"}
    return True, {"source": source, "elements": len(elements), "sources": sorted(sources)}
