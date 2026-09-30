"""Ядро роя review-swarm (RVSW-01) — T-07: сбалансированная ротация линз (FR-11,
AC-11); T-08: схема находки, механическая валидация location (FR-05, AC-05) и
механический дедуп в группы + dedup-journal Оркестратора (FR-06, AC-06);
T-09: туры 2–4 — вердикты, стоп-правила (потолок 2 обмена, новизна evidence,
запрет новых находок в тредах), анонимность payload, досрочная остановка
all-upheld, contested (FR-07, AC-07, TD §5.2–5.3, §6.3.5–6.3.6);
T-11: track record роя — сборка observations из состояния сессии (схема
TD §5.7), read-only проекция strengths (decay half-life 8, ячейка
модель × роль, n_eff ≥ 3), история линз для ротации (FR-12, AC-12);
T-12: re-review фиксов — схема вердикта TD §5.5 (fixed/partially/not_fixed/
introduced_new_issue), маршрутизация new_issue в общий пул, предикат
конфликта «разработчик исправил / автор — нет» (FR-09, AC-09).

Чистые предикаты (NFR-04): без IO и глобального состояния; исключения — thin IO
загрузки реестра `domains.py` harness (переиспользование, TD §3.2) и
`checked_set_from_paths` (снимок проверяемого набора с диска).

Контракты:
- CODE_REVIEW_LENSES — шесть линз доменного пакета `code-review` (TD §5.8);
- SWARM_WAVE_TYPES — enum applies_to для реестра с пакетом роя: консилиумные
  метки CHECKLIST_WAVE_TYPES harness + метка туров роя `tour1`. Константа
  harness НЕ расширяется (граница владения; валидация — через параметр
  wave_types, см. domains.validate_domains);
- assign_lenses(participants, criticality_paths, history) — назначение линз:
    * forced-линзы критичных путей (TD §8.1: путь из карты критичности →
      соответствующая линза принудительно, например secrets/** → security)
      помечаются forced=True; forced-наблюдения исключаются из strength-
      статистики (TD §5.7 — назначение не было свободным выбором модели);
    * остальные — сбалансированной ротацией (exploration — дефолт, FR-11):
      запрет повтора линзы до исчерпания пула, выбор по min n_eff ячейки
      (модель × роль); argmax-by-strengths ОТСУТСТВУЕТ — exploitation дал бы
      confounded-выборку (RISK-06), score-поля истории ротацией игнорируются;
- strength_inputs(assignments) — не-forced назначения как входы strength-
  статистики (FR-12).

history — read-only снимок ячеек track record:
    {participant_id: {"lenses_used": [lens, ...],   # цикл запрета повтора
                      "n_eff": {lens: float}}}       # n_eff ячейки (модель × роль)
Отсутствующие участники/поля трактуются как пустая история; неизвестные поля
(score и пр.) игнорируются — ротация до n_eff не использует strengths.
"""
from __future__ import annotations

import copy
import json
import re
from pathlib import Path

import domains
import structured
import track_record

CODE_REVIEW_DOMAIN = "code-review"
CODE_REVIEW_LENSES = (
    "security", "correctness", "concurrency",
    "performance", "data-contracts", "tests",
)

SWARM_TOUR_TYPES = frozenset({"tour1"})  # чеклисты роя применяются в туре 1 (TD §5.8)
SWARM_WAVE_TYPES = domains.CHECKLIST_WAVE_TYPES | SWARM_TOUR_TYPES


def load_swarm_domains(path: Path) -> dict:
    """Загрузка domains.yaml с wave_types туров роя (fail-closed наследуется)."""
    return domains.load_domains(path, wave_types=SWARM_WAVE_TYPES)


def code_review_domain(registry: dict) -> dict | None:
    return domains.domain_by_id(registry, CODE_REVIEW_DOMAIN)


# ---------------------------------------------------------------------------
# Ротация линз (FR-11, AC-11) — чистые функции
# ---------------------------------------------------------------------------

def _cell(history: dict, participant: str) -> dict:
    return history.get(participant) or {}


def _used_cycle(history: dict, participant: str) -> set:
    return set(_cell(history, participant).get("lenses_used") or [])


def _n_eff(history: dict, participant: str, lens: str) -> float:
    return float((_cell(history, participant).get("n_eff") or {}).get(lens, 0))


def _min_n_eff(candidates, history: dict, lens: str):
    """Первый по порядку участник с минимальным n_eff ячейки (стабильный min —
    тай-брейк порядком состава; strengths/score не читаются)."""
    return min(candidates, key=lambda p: _n_eff(history, p, lens))


def _rotation_pool(taken: set, used_cycle: set, lenses) -> list:
    """Пул ротации: свободные в сессии линзы вне текущего цикла участника;
    цикл исчерпан → сброс (повтор легален); участников больше линз → повтор
    в рамках сессии."""
    pool = [lens for lens in lenses if lens not in taken and lens not in used_cycle]
    if pool:
        return pool
    pool = [lens for lens in lenses if lens not in taken]
    if pool:
        return pool
    return list(lenses)


def assign_lenses(participants, criticality_paths, history,
                  lenses=CODE_REVIEW_LENSES) -> dict:
    """Назначение линз участникам: {participant: (lens, forced)}.

    participants — состав сессии (порядок значим: тай-брейк баланса).
    criticality_paths — {путь diff из карты критичности: id линзы} (резолв карты
    — на вызывающей стороне, TD §8.1); каждая forced-линза назначается ровно
    одному участнику. history — снимок ячеек track record (см. модуль).
    Fail-closed: неизвестная линза / forced-линз больше, чем участников → ValueError.
    """
    lenses = tuple(lenses)
    unknown = set(criticality_paths.values()) - set(lenses)
    if unknown:
        raise ValueError(f"forced-линза вне каталога code-review: {sorted(unknown)}")
    forced_lenses = list(dict.fromkeys(criticality_paths.values()))  # дедуп, порядок
    if len(forced_lenses) > len(participants):
        raise ValueError(
            f"forced-линз ({len(forced_lenses)}) больше, чем участников "
            f"({len(participants)}): одна основная линза на участника (FR-11)"
        )
    history = history or {}
    assignments: dict = {}
    taken: set = set()

    # 1) forced-линзы критичных путей: участник — по балансу (min n_eff ячейки),
    # предпочтение без повтора в цикле; forced ВНЕ strength-входов (TD §5.7).
    for lens in forced_lenses:
        free = [p for p in participants if p not in assignments]
        fresh = [p for p in free if lens not in _used_cycle(history, p)]
        participant = _min_n_eff(fresh or free, history, lens)
        assignments[participant] = (lens, True)
        taken.add(lens)

    # 2) остальные — сбалансированной ротацией (exploration, FR-11).
    for participant in participants:
        if participant in assignments:
            continue
        pool = _rotation_pool(taken, _used_cycle(history, participant), lenses)
        lens = min(pool, key=lambda l: (_n_eff(history, participant, l), lenses.index(l)))
        assignments[participant] = (lens, False)
        taken.add(lens)
    return assignments


def strength_inputs(assignments: dict) -> dict:
    """Не-forced назначения — входы strength-статистики (FR-12); forced
    исключены (их назначение — не свободный выбор модели, TD §5.7)."""
    return {p: lens for p, (lens, forced) in assignments.items() if not forced}


# ---------------------------------------------------------------------------
# Находки (FR-05, AC-05, TD §5.1) — схема и механическая валидация location
# ---------------------------------------------------------------------------

SWARM_STRUCTURED_TAG = "swarm-structured"
EMPTY_SWARM_STRUCTURED = {"findings": []}

FINDING_CATEGORIES = (
    "correctness", "security", "concurrency", "data_integrity",
    "api_contract", "performance", "tests",
)

# Синонимы таксономии (спека FR-05: concurrency/reliability, data integrity/
# миграции, API-контракты, тесты) → канонические id TD §5.1. Ключи — уже
# нормализованные (lower, неалфавитное → "_").
_CATEGORY_SYNONYMS = {
    "reliability": "concurrency",
    "concurrency_reliability": "concurrency",
    "data": "data_integrity",
    "data_integrity_migrations": "data_integrity",
    "migration": "data_integrity",
    "migrations": "data_integrity",
    "api": "api_contract",
    "api_contracts": "api_contract",
    "test": "tests",
    "perf": "performance",
}

# Полная таблица severity (AC-05): P1..P3 — в метрику ценности, P4 — отдельный
# счётчик nit, P5 — информационный (note/question), без метрики.
SEVERITY_LABELS = {
    "P1": "blocker", "P2": "major", "P3": "minor", "P4": "nit", "P5": "note",
}
SEVERITY_IN_VALUE_METRIC = frozenset({"P1", "P2", "P3"})
SEVERITY_NIT = "P4"
SEVERITY_INFO = "P5"

_REQUIRED_FINDING_FIELDS = ("category", "severity", "claim", "evidence", "in_lens")


class FindingRejected(ValueError):
    """Находка отклонена ядром ДО учёта и дедупа (FR-05); reason — машинный код."""

    def __init__(self, reason: str, detail: str = ""):
        super().__init__(f"{reason}: {detail}" if detail else reason)
        self.reason = reason
        self.detail = detail


def parse_findings_block(text: str):
    """Fenced-блок находок тура 1 (механизм structured.py harness; схема полей —
    инструмента, TD §5). → (список сырых находок, warning|None)."""
    parsed, warning = structured.parse_structured_block(
        text, fence_tag=SWARM_STRUCTURED_TAG, empty_schema=EMPTY_SWARM_STRUCTURED
    )
    return parsed["findings"], warning


def normalize_path(path) -> str:
    """Каноническая форма пути location: '\\'→'/', без './' и дублей '/'.
    Fail-closed: абсолютные пути и '..' отвергаются."""
    if not isinstance(path, str) or not path.strip():
        raise ValueError(f"невалидный путь location: {path!r}")
    raw = path.replace("\\", "/")
    if raw.startswith("/"):
        raise ValueError(f"абсолютный путь location недопустим: {path!r}")
    parts = [seg for seg in raw.split("/") if seg not in ("", ".")]
    if any(seg == ".." for seg in parts) or not parts:
        raise ValueError(f"невалидный путь location: {path!r}")
    return "/".join(parts)


def normalize_location(location) -> dict:
    """Каноническая location: {path, line_start, line_end}; точечная строка →
    диапазон line..line; line_end по умолчанию = line_start."""
    if not isinstance(location, dict):
        raise ValueError("location отсутствует или не объект")
    path = normalize_path(location.get("path"))
    start, end = location.get("line_start"), location.get("line_end")
    if not isinstance(start, int) or isinstance(start, bool) or start < 1:
        raise ValueError(f"line_start обязателен (целое ≥ 1): {start!r}")
    if end is None:
        end = start
    if not isinstance(end, int) or isinstance(end, bool) or end < start:
        raise ValueError(f"line_end должен быть целым ≥ line_start: {end!r}")
    return {"path": path, "line_start": start, "line_end": end}


def canonical_category(category) -> str:
    """Канонизация category: регистр/разделители/синонимы таксономии →
    канонический id TD §5.1; неизвестная категория — fail-closed ValueError."""
    key = re.sub(r"[^0-9a-zа-я]+", "_", str(category).strip().lower()).strip("_")
    key = _CATEGORY_SYNONYMS.get(key, key)
    if key not in FINDING_CATEGORIES:
        raise ValueError(f"category вне таксономии TD §5.1: {category!r}")
    return key


def normalize_severity(severity) -> str:
    code = str(severity).strip().upper()
    if code not in SEVERITY_LABELS:
        raise ValueError(f"severity вне P1..P5: {severity!r}")
    return code


def severity_metric(severity) -> str | None:
    """Маршрутизация счётчиков (AC-05): 'value' (P1..P3) | 'nit' (P4) | None (P5)."""
    code = normalize_severity(severity)
    if code in SEVERITY_IN_VALUE_METRIC:
        return "value"
    if code == SEVERITY_NIT:
        return "nit"
    return None


def checked_set_from_paths(root, paths) -> dict:
    """Снимок проверяемого набора (diff + focused paths): {нормализованный путь:
    число строк}. Thin IO поверх реального дерева (sandbox-представление набора);
    отсутствующий файл — fail-closed FileNotFoundError."""
    root = Path(root)
    checked = {}
    for rel in paths:
        norm = normalize_path(rel)
        text = (root / norm).read_text(encoding="utf-8")
        checked[norm] = len(text.splitlines())
    return checked


def _validate_one(raw, checked: dict, author_id) -> dict:
    """Одна находка → каноническая форма TD §5.1 или FindingRejected."""
    if not isinstance(raw, dict):
        raise FindingRejected("invalid_finding", "запись не является объектом")
    author = author_id if author_id is not None else raw.get("author_id")
    if not author:
        raise FindingRejected("missing_field:author_id")
    for field in _REQUIRED_FINDING_FIELDS:
        if field not in raw or raw[field] is None:
            raise FindingRejected(f"missing_field:{field}")
    if not isinstance(raw["in_lens"], bool):
        raise FindingRejected("missing_field:in_lens", "in_lens обязан быть bool")
    for field in ("claim", "evidence"):
        if not isinstance(raw[field], str) or not raw[field].strip():
            raise FindingRejected(f"missing_field:{field}", "пустая строка")
    if "location" not in raw or raw["location"] is None:
        raise FindingRejected("missing_location")
    try:
        location = normalize_location(raw["location"])
    except ValueError as exc:
        raise FindingRejected("invalid_location", str(exc)) from exc
    line_count = checked.get(location["path"])
    if line_count is None:
        raise FindingRejected(
            "invalid_location",
            f"path_not_in_set: {location['path']} вне проверяемого набора",
        )
    if location["line_end"] > line_count:
        raise FindingRejected(
            "invalid_location",
            f"line_out_of_range: {location['line_end']} > {line_count} строк файла",
        )
    try:
        category = canonical_category(raw["category"])
    except ValueError as exc:
        raise FindingRejected("invalid_category", str(exc)) from exc
    try:
        severity = normalize_severity(raw["severity"])
    except ValueError as exc:
        raise FindingRejected("invalid_severity", str(exc)) from exc
    return {
        "author_id": author,
        "location": location,
        "category": category,
        "severity": severity,
        "in_lens": raw["in_lens"],
        "claim": raw["claim"],
        "evidence": raw["evidence"],
        "rationale": raw.get("rationale", ""),
    }


def validate_findings(raw_findings, checked: dict, author_id=None,
                      start_index: int = 1):
    """Валидация сырых находок участника против проверяемого набора (FR-05).

    → (accepted, rejected): принятые получают сквозной finding_id F-NNN (ядро,
    продолжение с start_index); отклонённые — записи для журнала сессии
    {index, author_id, reason, detail}, нумерации не получают и в дедуп
    не попадают (AC-05). author_id параметром — идентичность сессии участника
    (traceability); self-declared author_id записи используется только как
    fallback. Чистая функция: checked — снимок {путь: число строк}."""
    checked = {normalize_path(p): n for p, n in (checked or {}).items()}
    accepted, rejected = [], []
    next_index = start_index
    for i, raw in enumerate(raw_findings or []):
        try:
            finding = _validate_one(raw, checked, author_id)
        except FindingRejected as exc:
            rejected.append({
                "index": i,
                "author_id": author_id if author_id is not None
                else (raw.get("author_id") if isinstance(raw, dict) else None),
                "reason": exc.reason,
                "detail": exc.detail,
            })
            continue
        finding["finding_id"] = f"F-{next_index:03d}"
        next_index += 1
        accepted.append(finding)
    return accepted, rejected


def anonymize_finding(finding: dict, anon_map: dict) -> dict:
    """Копия находки для payload участников туров 2–4: author_id → anon_id
    (TD §5.2); finding_id сквозной идентичности не раскрывает. В транспорте
    ядра остаётся реальный author_id. Неизвестный автор — fail-closed."""
    anon = anon_map.get(finding["author_id"])
    if anon is None:
        raise ValueError(f"anon_id не найден для автора: {finding['author_id']}")
    out = dict(finding)
    out["author_id"] = anon
    return out


# ---------------------------------------------------------------------------
# Дедуп (FR-06, AC-06, TD §5.6) — механический предикат + журнал Оркестратора
# ---------------------------------------------------------------------------

OVERLAP_WINDOW_LINES = 4  # окно перекрытия строк по умолчанию (FR-06)

DEDUP_JOURNAL_ACTIONS = ("merge", "split", "override_auto_confirm")
AUTO_CONFIRMED_OVERRIDDEN = "auto_confirmed_overridden"


def _locations_overlap(a: dict, b: dict, window: int) -> bool:
    """Предикат окна перекрытия: диапазоны одного файла сближены не более чем
    на window строк (касание края окна — ещё пересечение)."""
    return (
        a["line_start"] <= b["line_end"] + window
        and b["line_start"] <= a["line_end"] + window
    )


def _strict_overlap(a: dict, b: dict) -> bool:
    return (a["line_start"] <= b["line_end"]
            and b["line_start"] <= a["line_end"])


def _dedup_form(finding: dict) -> dict:
    """Защитная канонизация входа дедупа (чистая функция, NFR-04)."""
    out = dict(finding)
    out["location"] = normalize_location(finding["location"])
    out["category"] = canonical_category(finding["category"])
    return out


def _make_cluster(findings: list) -> dict:
    authors = sorted({f["author_id"] for f in findings})
    return {
        "cluster_id": None,  # присваивается в _build_result
        "path": findings[0]["location"]["path"],
        "category": findings[0]["category"],
        "line_start": min(f["location"]["line_start"] for f in findings),
        "line_end": max(f["location"]["line_end"] for f in findings),
        "findings": sorted(findings, key=lambda f: f["finding_id"]),
        "authors": authors,
        "auto_confirmed": len(authors) >= 2,  # ≥2 слепые модели (FR-06)
        "auto_confirmed_overridden": False,
    }


def _cluster_findings(findings: list, window: int) -> list:
    """Механическая кластеризация по (location, category): сортировка +
    слияние по окну перекрытия (детерминированная цепочка, NFR-04)."""
    items = sorted(
        (_dedup_form(f) for f in findings),
        key=lambda f: (f["location"]["path"], f["category"],
                       f["location"]["line_start"], f["finding_id"]),
    )
    clusters: list = []
    last_by_key: dict = {}
    for item in items:
        key = (item["location"]["path"], item["category"])
        cluster = last_by_key.get(key)
        if cluster is not None and _locations_overlap(
            {"line_start": cluster["line_start"], "line_end": cluster["line_end"]},
            item["location"], window,
        ):
            cluster["findings"].append(item)
            cluster["line_end"] = max(cluster["line_end"],
                                      item["location"]["line_end"])
        else:
            cluster = {
                "key": key,
                "findings": [item],
                "line_start": item["location"]["line_start"],
                "line_end": item["location"]["line_end"],
            }
            clusters.append(cluster)
            last_by_key[key] = cluster
    return [_make_cluster(c["findings"]) for c in clusters]


def _borderline_pairs(clusters: list) -> list:
    """Пограничные случаи к Оркестратору (FR-06): строгое перекрытие диапазонов
    одного файла при РАЗНЫХ канонических категориях (неоднозначная категория) —
    механически не склеивается и не разводится."""
    pairs = []
    findings = [f for cl in clusters for f in cl["findings"]]
    for i, a in enumerate(findings):
        for b in findings[i + 1:]:
            if (a["location"]["path"] == b["location"]["path"]
                    and a["category"] != b["category"]
                    and _strict_overlap(a["location"], b["location"])):
                pairs.append({
                    "finding_ids": [a["finding_id"], b["finding_id"]],
                    "path": a["location"]["path"],
                    "reason": "range_overlap_category_mismatch",
                })
    return pairs


def _build_result(clusters: list) -> dict:
    """Группы с атрибуцией (AC-06): неуникальные автоподтверждённые /
    уникальные неподтверждённые (→ тур 2; сюда же попадают кластеры со снятым
    автоподтверждением — маркер auto_confirmed_overridden). Кластерные id
    детерминированы сортировкой."""
    clusters = sorted(clusters,
                      key=lambda c: (c["path"], c["category"], c["line_start"]))
    for n, cluster in enumerate(clusters, 1):
        cluster["cluster_id"] = f"C-{n:03d}"
    return {
        "clusters": clusters,
        "groups": {
            "nonunique_auto_confirmed":
                [c for c in clusters if c["auto_confirmed"]],
            "unique_unconfirmed":
                [c for c in clusters if not c["auto_confirmed"]],
        },
        "borderline": _borderline_pairs(clusters),
    }


def dedup_findings(findings: list, window: int = OVERLAP_WINDOW_LINES) -> dict:
    """Механический дедуп пула тура 1 (FR-06): нормализация location, окно
    перекрытия строк/диапазонов, канонизация category → три итога:
    неуникальные автоподтверждённые, уникальные неподтверждённые (тур 2),
    пограничные пары к Оркестратору. Чистая функция, идемпотентна (SU-D08)."""
    return _build_result(_cluster_findings(findings, window))


def dedup_journal_entry(action: str, finding_ids, reason: str,
                        actor: str = "orchestrator", ts=None,
                        session_id=None) -> dict:
    """Запись dedup-journal сессии (TD §5.6): ручная склейка/разделение/override
    — только Оркестратором и только с обоснованием (fail-closed)."""
    if action not in DEDUP_JOURNAL_ACTIONS:
        raise ValueError(f"action вне {DEDUP_JOURNAL_ACTIONS}: {action!r}")
    if actor != "orchestrator":
        raise ValueError(f"dedup-journal пишет только Оркестратор: {actor!r}")
    if not isinstance(reason, str) or not reason.strip():
        raise ValueError("обоснование (reason) обязательно и непусто")
    if not finding_ids:
        raise ValueError("finding_ids обязательны")
    entry = {
        "ts": ts,
        "session_id": session_id,
        "action": action,
        "finding_ids": list(finding_ids),
        "reason": reason,
        "actor": actor,
    }
    if action == "override_auto_confirm":
        entry["marker"] = AUTO_CONFIRMED_OVERRIDDEN
    return entry


def dedup_journal_entries(entries) -> tuple[list, int]:
    """Дедупликация записей dedup-journal на входе fold'а реплея (консилиум
    E2E-03, E3): семантический дубль — тот же (action, finding_ids в том же
    порядке, reason, actor); ts/session_id — поля сырого аудита, на
    тождественность не влияют. Порядок finding_ids значим (категория merge —
    от первой находки записи), поэтому не нормализуется. Сам журнал остаётся
    сырым append-only аудитом: отсечение — только на входе fold'а
    (идемпотентность проекции; apply_journal неидемпотентен — F-07/F-08,
    повторный override_auto_confirm fail-closed). → (записи в порядке файла
    без дублей, число отсечённых)."""
    seen = set()
    unique = []
    for entry in entries:
        key = (entry.get("action"), tuple(entry.get("finding_ids") or []),
               entry.get("reason"), entry.get("actor"))
        if key in seen:
            continue
        seen.add(key)
        unique.append(entry)
    return unique, len(entries) - len(unique)


def _recalc_cluster(cluster: dict) -> None:
    authors = sorted({f["author_id"] for f in cluster["findings"]})
    cluster["authors"] = authors
    cluster["line_start"] = min(f["location"]["line_start"]
                                for f in cluster["findings"])
    cluster["line_end"] = max(f["location"]["line_end"]
                              for f in cluster["findings"])
    if not cluster["auto_confirmed_overridden"]:
        cluster["auto_confirmed"] = len(authors) >= 2


def apply_journal(result: dict, entries, window: int = OVERLAP_WINDOW_LINES) -> dict:
    """Применение журнала ручных склеек Оркестратора (FR-06): merge объединяет
    кластеры перечисленных находок (категория — от первой находки записи),
    split выделяет перечисленные находки в отдельные кластеры, override_auto_confirm
    снимает автоподтверждение. Входной result не мутируется; неизвестная
    находка — fail-closed."""
    clusters = copy.deepcopy(result["clusters"])

    def cluster_of(fid):
        for cluster in clusters:
            if any(f["finding_id"] == fid for f in cluster["findings"]):
                return cluster
        raise ValueError(f"находка журнала не найдена в пуле: {fid}")

    for entry in entries:
        for key in ("action", "finding_ids", "reason", "actor"):
            if key not in entry:
                raise ValueError(f"запись журнала без поля {key}")
        if entry["actor"] != "orchestrator" or not str(entry["reason"]).strip():
            raise ValueError("запись журнала: только Оркестратор с обоснованием")
        action = entry["action"]
        if action == "merge":
            targets = []
            for fid in entry["finding_ids"]:
                cluster = cluster_of(fid)
                if cluster not in targets:
                    targets.append(cluster)
            base = targets[0]
            for other in targets[1:]:
                base["findings"].extend(other["findings"])
                clusters.remove(other)
            _recalc_cluster(base)
        elif action == "split":
            for fid in entry["finding_ids"]:
                cluster = cluster_of(fid)
                if len(cluster["findings"]) == 1:
                    continue
                moved = [f for f in cluster["findings"] if f["finding_id"] == fid]
                cluster["findings"] = [f for f in cluster["findings"]
                                       if f["finding_id"] != fid]
                clusters.append(_make_cluster(moved))
                _recalc_cluster(cluster)
        elif action == "override_auto_confirm":
            affected = {id(cluster_of(fid)) for fid in entry["finding_ids"]}
            if len(affected) != 1:
                raise ValueError("override_auto_confirm: находки разных кластеров")
            cluster = cluster_of(entry["finding_ids"][0])
            if not cluster["auto_confirmed"]:
                raise ValueError("override_auto_confirm: кластер не автоподтверждён")
            cluster["auto_confirmed"] = False
            cluster["auto_confirmed_overridden"] = True
        else:
            raise ValueError(f"action вне {DEDUP_JOURNAL_ACTIONS}: {action!r}")
    return _build_result(clusters)


def override_auto_confirm(result: dict, cluster_id: str, reason: str,
                          actor: str = "orchestrator", ts=None,
                          session_id=None):
    """Override автоподтверждения неуникальной находки (FR-06): скоррелированные
    ложные срабатывания — Оркестратор снимает автоподтверждение с маркером
    auto_confirmed_overridden и обоснованием; кластер уходит в неподтверждённые
    (тур 2). → (новый result, запись журнала). Само правило автоподтверждения
    не меняется (решения №2, №3)."""
    cluster = next((c for c in result["clusters"]
                    if c["cluster_id"] == cluster_id), None)
    if cluster is None:
        raise ValueError(f"кластер не найден: {cluster_id}")
    if not isinstance(reason, str) or not reason.strip():
        raise ValueError("override требует непустого обоснования")
    entry = dedup_journal_entry(
        "override_auto_confirm",
        [f["finding_id"] for f in cluster["findings"]],
        reason, actor=actor, ts=ts, session_id=session_id,
    )
    return apply_journal(result, [entry]), entry


# ---------------------------------------------------------------------------
# Туры 2–4 (FR-07, AC-07, TD §5.2–5.3, §6.3.5–6.3.6) — вердикты, стоп-правила,
# анонимность payload, досрочная остановка. Чистые предикаты (NFR-04); CLI
# state machine сессии — отдельный слой (T-10), здесь — только тред находки.
# ---------------------------------------------------------------------------

VERDICT_VALUES = ("upheld", "overruled", "reclassify", "uncertain")
AUTHOR_RESPONSE_VALUES = ("maintain", "withdraw", "accept_reclassify")

# Потолок 2 обмена после первичной атаки (решение №5): ответ автора (тур 3) —
# обмен 1, финальный вотум (тур 4) — обмен 2; третий обмен невозможен.
MAX_EXCHANGES_AFTER_ATTACK = 2

SWARM_VERDICT_TAG = "swarm-verdict"                # fenced-тег хода туров 2/4
SWARM_AUTHOR_RESPONSE_TAG = "swarm-author-response"  # fenced-тег хода тура 3

THREAD_STATUSES = ("open", "confirmed", "withdrawn", "reclassified", "contested")


class MoveRejected(ValueError):
    """Ход туров 2–4 отклонён ядром (fail-closed, NFR-01); reason — машинный код."""

    def __init__(self, reason: str, detail: str = ""):
        super().__init__(f"{reason}: {detail}" if detail else reason)
        self.reason = reason
        self.detail = detail


def _parse_move_block(text: str, fence_tag: str) -> dict:
    """Fenced-блок хода туров 2–4 (механизм structured.py harness; схема —
    инструмента). Отсутствующий/битый блок — fail-closed MoveRejected (ровно
    один retry — на уровне CLI, NFR-01)."""
    matches = list(structured.structured_block_re(fence_tag).finditer(text or ""))
    if not matches:
        raise MoveRejected("missing_block",
                           f"fenced-блок {fence_tag} отсутствует в ответе")
    try:
        parsed = json.loads(matches[-1].group("body"))
    except json.JSONDecodeError as exc:
        raise MoveRejected("invalid_json", str(exc)) from exc
    if not isinstance(parsed, dict):
        raise MoveRejected("invalid_move", "structured-блок не JSON-объект")
    return parsed


def _routed_findings(text: str) -> list:
    """Новые находки внутри ответа туров 2–4: в тред НЕ принимаются (запрет
    FR-07 — только тур 1 и re-review порождают находки), маршрутизируются в
    общий пул сырыми записями для validate_findings (нумерация — пула)."""
    parsed, _ = structured.parse_structured_block(
        text, fence_tag=SWARM_STRUCTURED_TAG, empty_schema=EMPTY_SWARM_STRUCTURED
    )
    return parsed["findings"]


def parse_verdict_move(text: str):
    """Ответ атакующего (тур 2/4) → (сырой вердикт, находки в общий пул)."""
    return _parse_move_block(text, SWARM_VERDICT_TAG), _routed_findings(text)


def parse_author_response_move(text: str):
    """Ответ автора (тур 3) → (сырой ответ, находки в общий пул)."""
    return (_parse_move_block(text, SWARM_AUTHOR_RESPONSE_TAG),
            _routed_findings(text))


def normalize_quote(quote) -> str:
    """Канонизация quote для предиката новизны: схлопывание пробельных
    последовательностей — оформление не обходит стоп-правило (SU-V02)."""
    if not isinstance(quote, str) or not quote.strip():
        raise ValueError(f"quote обязателен (непустая строка): {quote!r}")
    return " ".join(quote.split())


def validate_evidence(evidence, checked: dict) -> dict:
    """Structured evidence {path, line, quote} (TD §5.2): path из проверяемого
    набора, line — валидная строка файла, quote непустой. Fail-closed."""
    if not isinstance(evidence, dict):
        raise MoveRejected("invalid_evidence", "evidence отсутствует или не объект")
    try:
        path = normalize_path(evidence.get("path"))
        quote = normalize_quote(evidence.get("quote"))
    except ValueError as exc:
        raise MoveRejected("invalid_evidence", str(exc)) from exc
    line = evidence.get("line")
    if not isinstance(line, int) or isinstance(line, bool) or line < 1:
        raise MoveRejected("invalid_evidence",
                           f"line обязателен (целое ≥ 1): {line!r}")
    line_count = checked.get(path)
    if line_count is None:
        raise MoveRejected(
            "invalid_evidence", f"path_not_in_set: {path} вне проверяемого набора"
        )
    if line > line_count:
        raise MoveRejected(
            "invalid_evidence",
            f"line_out_of_range: {line} > {line_count} строк файла",
        )
    return {"path": path, "line": line, "quote": quote}


def evidence_key(evidence: dict) -> tuple:
    """Ключ новизны (path, line, нормализованный quote) — детерминированный
    предикат стоп-правила «каждый ход несёт новое evidence» (FR-07)."""
    return (evidence["path"], evidence["line"],
            normalize_quote(evidence["quote"]))


def _check_novelty(evidence: dict, prior_evidence) -> None:
    prior = {evidence_key(e) for e in (prior_evidence or ())}
    if evidence_key(evidence) in prior:
        raise MoveRejected(
            "stale_evidence",
            "повтор аргумента без нового evidence отклоняется (FR-07)",
        )


def _check_finding_id(raw: dict, expected_finding_id) -> str:
    fid = raw.get("finding_id")
    if not fid:
        raise MoveRejected("missing_field:finding_id")
    if expected_finding_id is not None and fid != expected_finding_id:
        raise MoveRejected(
            "finding_mismatch", f"{fid!r} != {expected_finding_id!r}"
        )
    return fid


def validate_verdict(raw, checked: dict, prior_evidence=(), voter_id=None,
                     expected_finding_id=None) -> dict:
    """Вердикт тура 2/4 → каноническая форма TD §5.2 или MoveRejected.

    voter_id параметром — идентичность сессии голосующего (один вотум на
    участника контролирует тред); self-declared voter_id записи — fallback.
    prior_evidence — evidence предыдущих ходов треда (новизна, SU-V02).
    checked — снимок {нормализованный путь: число строк}. Чистая функция."""
    checked = {normalize_path(p): n for p, n in (checked or {}).items()}
    if not isinstance(raw, dict):
        raise MoveRejected("invalid_move", "вердикт не является объектом")
    fid = _check_finding_id(raw, expected_finding_id)
    voter = voter_id if voter_id is not None else raw.get("voter_id")
    if not voter:
        raise MoveRejected("missing_field:voter_id")
    verdict = raw.get("verdict")
    if verdict not in VERDICT_VALUES:
        raise MoveRejected("invalid_verdict",
                           f"verdict вне {VERDICT_VALUES}: {verdict!r}")
    out = {
        "finding_id": fid,
        "voter_id": voter,
        "verdict": verdict,
        "rationale": raw.get("rationale", ""),
    }
    if verdict == "reclassify":
        block = raw.get("reclassify")
        if (not isinstance(block, dict)
                or not (block.get("category") or block.get("severity"))):
            raise MoveRejected(
                "missing_field:reclassify",
                "reclassify требует хотя бы одно из полей category/severity",
            )
        reclassified = {}
        if block.get("category") is not None:
            try:
                reclassified["category"] = canonical_category(block["category"])
            except ValueError as exc:
                raise MoveRejected("invalid_category", str(exc)) from exc
        if block.get("severity") is not None:
            try:
                reclassified["severity"] = normalize_severity(block["severity"])
            except ValueError as exc:
                raise MoveRejected("invalid_severity", str(exc)) from exc
        out["reclassify"] = reclassified
    if raw.get("evidence") is None:
        raise MoveRejected("missing_field:evidence",
                           "evidence обязателен всегда (TD §5.2)")
    evidence = validate_evidence(raw["evidence"], checked)
    _check_novelty(evidence, prior_evidence)
    out["evidence"] = evidence
    return out


def validate_author_response(raw, checked: dict, prior_evidence=(),
                             expected_finding_id=None) -> dict:
    """Ответ автора тура 3 → каноническая форма TD §5.3 или MoveRejected.
    counter_evidence обязателен при maintain и подчинён предикату новизны."""
    checked = {normalize_path(p): n for p, n in (checked or {}).items()}
    if not isinstance(raw, dict):
        raise MoveRejected("invalid_move", "ответ автора не является объектом")
    fid = _check_finding_id(raw, expected_finding_id)
    response = raw.get("response")
    if response not in AUTHOR_RESPONSE_VALUES:
        raise MoveRejected(
            "invalid_response",
            f"response вне {AUTHOR_RESPONSE_VALUES}: {response!r}",
        )
    out = {"finding_id": fid, "response": response,
           "rationale": raw.get("rationale", "")}
    counter = raw.get("counter_evidence")
    if response == "maintain" and counter is None:
        raise MoveRejected("missing_field:counter_evidence",
                           "counter_evidence обязателен при maintain (TD §5.3)")
    if counter is not None:
        evidence = validate_evidence(counter, checked)
        _check_novelty(evidence, prior_evidence)
        out["counter_evidence"] = evidence
    return out


# ---------------------------------------------------------------------------
# Re-review фиксов (FR-09, AC-09, TD §5.5) — вердикт автора находки по diff
# исправления. Чистые предикаты (NFR-04); новая focused-сессия адаптера,
# доставка fix-diff (materialize_diff + обязательный sync) и state machine —
# слой CLI (T-12).
# ---------------------------------------------------------------------------

REREVIEW_VERDICT_VALUES = ("fixed", "partially", "not_fixed", "introduced_new_issue")
# Позиция разработчика о статусе фикса (вход предиката конфликта, FR-09).
REREVIEW_DEVELOPER_CLAIMS = ("fixed", "partially", "not_fixed")
# Итог, фиксируемый арбитражом Оркестратора при конфликте (FR-09 → FR-08);
# introduced_new_issue — вердикт автора, не решение арбитра (новая находка
# уже в пуле; для track record она эквивалентна not_fixed, T-11).
REREVIEW_ARBITRATION_DECISIONS = ("fixed", "partially", "not_fixed")

SWARM_REREVIEW_TAG = "swarm-rereview"  # fenced-тег вердикта re-review


def parse_rereview_move(text: str) -> dict:
    """Ответ автора в re-review → сырой вердикт (fenced swarm-rereview).
    Отсутствующий/битый блок — fail-closed MoveRejected (ровно один retry —
    на уровне CLI, NFR-01)."""
    return _parse_move_block(text, SWARM_REREVIEW_TAG)


def validate_rereview_verdict(raw, checked: dict, expected_finding_id=None):
    """Вердикт re-review → (каноническая форма TD §5.5, сырая new_issue | None).

    new_issue обязателен при introduced_new_issue и запрещён при прочих
    вердиктах (строгая схема, fail-closed); в общий пул уходит сырой записью —
    валидация и сквозная нумерация пула делаются validate_findings (SU-RR02).
    Предикат новизны evidence НЕ применяется: re-review — новая focused-
    сессия после завершения треда, evidence треда не существует. checked —
    снимок {нормализованный путь: число строк} АКТУАЛЬНЫХ исходников (с
    фиксом). Чистая функция."""
    checked = {normalize_path(p): n for p, n in (checked or {}).items()}
    if not isinstance(raw, dict):
        raise MoveRejected("invalid_move", "вердикт re-review не является объектом")
    fid = _check_finding_id(raw, expected_finding_id)
    verdict = raw.get("verdict")
    if verdict not in REREVIEW_VERDICT_VALUES:
        raise MoveRejected(
            "invalid_verdict",
            f"verdict вне {REREVIEW_VERDICT_VALUES}: {verdict!r}",
        )
    new_issue = raw.get("new_issue")
    if verdict == "introduced_new_issue":
        if not isinstance(new_issue, dict):
            raise MoveRejected(
                "missing_field:new_issue",
                "new_issue обязателен при introduced_new_issue (TD §5.5)",
            )
    elif new_issue is not None:
        raise MoveRejected(
            "unexpected_new_issue",
            "new_issue допустим только при introduced_new_issue (TD §5.5)",
        )
    if raw.get("evidence") is None:
        raise MoveRejected("missing_field:evidence",
                           "evidence обязателен всегда (TD §5.5)")
    out = {
        "finding_id": fid,
        "verdict": verdict,
        "evidence": validate_evidence(raw["evidence"], checked),
        "rationale": raw.get("rationale", ""),
    }
    return out, (new_issue if verdict == "introduced_new_issue" else None)


def rereview_conflict(verdict: str, developer_claim: str = "fixed") -> bool:
    """Конфликт re-review (FR-09): позиция разработчика о фиксе расходится с
    вердиктом автора находки → эскалация на арбитраж Оркестратора (FR-08).
    Совпадение позиций — согласие, вердикт финален без арбитража."""
    if verdict not in REREVIEW_VERDICT_VALUES:
        raise ValueError(f"verdict вне {REREVIEW_VERDICT_VALUES}: {verdict!r}")
    if developer_claim not in REREVIEW_DEVELOPER_CLAIMS:
        raise ValueError(
            f"developer_claim вне {REREVIEW_DEVELOPER_CLAIMS}: {developer_claim!r}"
        )
    return verdict != developer_claim


# ---------------------------------------------------------------------------
# Gate-вердикт (FR-16, AC-16/AC-24, TD §7): наследуемая gate-семантика
# cross-provider-review. Отдельный structured-вызов ПОСЛЕ репорта и диспозиций
# Оркестратора; схема fenced-блока — инструмента (роя), механизм — harness.
# ---------------------------------------------------------------------------

SWARM_GATE_TAG = "swarm-gate-verdict"  # fenced-тег gate-вердикта

# Finalization gate (Hard Rule 15/16): вердикт блокирующий.
GATE_COMPLETION_DECISIONS = ("APPROVE_COMPLETION", "BLOCK_COMPLETION")
# Acceptance-bound review: итоговый вердикт ревьюера по диспозициям.
GATE_ACCEPTANCE_VERDICTS = ("accept", "conditional_accept", "reject", "re-review")
# Позиции ревьюера по находке (наследуется review-prompt: ответ на несогласие —
# agree/partial/disagree/withdrawn; out_of_scope — диспозиция Оркестратора,
# не позиция ревьюера).
GATE_POSITIONS = ("agree", "partial", "disagree", "withdrawn")

GATE_MODES = ("acceptance", "completion")


def parse_gate_verdict_move(text: str) -> dict:
    """Ответ gate-ревьюера → сырой вердикт (fenced swarm-gate-verdict).
    Отсутствующий/битый блок — fail-closed MoveRejected (ровно один retry —
    на уровне CLI, NFR-01)."""
    return _parse_move_block(text, SWARM_GATE_TAG)


def validate_gate_verdict(raw, mode: str, finding_ids=()) -> dict:
    """Gate-вердикт → каноническая форма (TD §7). Строгая схема, fail-closed:
    decision/verdict вне enum — отказ; позиции acceptance — только по известным
    находкам сессии и только из GATE_POSITIONS. finding_ids — ids находок
    сессии (пустой → проверка принадлежности отключена). Чистая функция."""
    if mode not in GATE_MODES:
        raise ValueError(f"неизвестный gate-режим: {mode!r} (вне {GATE_MODES})")
    if not isinstance(raw, dict):
        raise MoveRejected("invalid_move", "gate-вердикт не является объектом")
    if mode == "completion":
        decision = raw.get("decision")
        if decision not in GATE_COMPLETION_DECISIONS:
            raise MoveRejected(
                "invalid_verdict",
                f"decision вне {GATE_COMPLETION_DECISIONS}: {decision!r}",
            )
        findings = raw.get("findings") or []
        if not isinstance(findings, list):
            raise MoveRejected("invalid_move", "findings не список")
        return {
            "decision": decision,
            "findings": findings,
            "rationale": str(raw.get("rationale", "")),
            "escalation_needed": bool(raw.get("escalation_needed", False)),
        }
    verdict = raw.get("verdict")
    if verdict not in GATE_ACCEPTANCE_VERDICTS:
        raise MoveRejected(
            "invalid_verdict",
            f"verdict вне {GATE_ACCEPTANCE_VERDICTS}: {verdict!r}",
        )
    known = set(finding_ids or ())
    positions = raw.get("positions") or []
    if not isinstance(positions, list):
        raise MoveRejected("invalid_move", "positions не список")
    canonical = []
    for entry in positions:
        if not isinstance(entry, dict):
            raise MoveRejected("invalid_move", "позиция не объект {finding_id, position}")
        fid = entry.get("finding_id")
        if known and fid not in known:
            raise MoveRejected("unknown_finding",
                               f"позиция по неизвестной находке {fid!r}")
        position = entry.get("position")
        if position not in GATE_POSITIONS:
            raise MoveRejected(
                "invalid_position",
                f"position вне {GATE_POSITIONS}: {position!r}",
            )
        canonical.append({"finding_id": fid, "position": position})
    return {
        "verdict": verdict,
        "positions": canonical,
        "rationale": str(raw.get("rationale", "")),
    }


# --- Тред валидации находки (стоп-правила как чистые предикаты, NFR-04) ------

def start_thread(finding: dict) -> dict:
    """Тред валидации уникальной неподтверждённой находки (тур 2 → 3 → 4).

    Состояние — plain dict (JSON-сериализуемо); apply_* возвращают НОВЫЙ тред,
    вход не мутируется. evidence_keys — множество как отсортированный список
    (детерминированная сериализация)."""
    return {
        "finding_id": finding["finding_id"],
        "author_id": finding["author_id"],
        "status": "open",
        "phase": "tour2",        # tour2 | author_response | tour4 | None
        "exchanges": 0,          # обмены после первичной атаки (потолок 2)
        "waves": [],             # [{"tour": 2|4, "votes": [вердикт, ...]}]
        "author_response": None,
        "evidence": [],          # canonical evidence всех ходов треда
    }


def thread_status(thread: dict) -> str:
    return thread["status"]


def next_move(thread: dict) -> str | None:
    """Ожидаемый ход: 'tour2' | 'author_response' | 'tour4' | None (тред
    закрыт — досрочная остановка all-upheld не планирует туры 3–4, SU-V04)."""
    return thread["phase"] if thread["status"] == "open" else None


def thread_evidence(thread: dict) -> list:
    """Evidence всех ходов треда — вход предиката новизны для следующего хода."""
    return list(thread["evidence"])


def _require_open(thread: dict) -> None:
    if thread["status"] != "open":
        raise MoveRejected(
            "thread_closed",
            f"тред находки закрыт ({thread['status']}): "
            f"потолок {MAX_EXCHANGES_AFTER_ATTACK} обмена / досрочная остановка",
        )


def apply_verdict_wave(thread: dict, verdicts) -> dict:
    """Волна вотумов тура 2/4: ровно один голос на участника волны, автор
    находки не голосует; all-upheld тура 2 → досрочное confirmed (туры 3–4
    не планируются); all-upheld тура 4 → confirmed, иначе — неснятое
    disagreement → contested (SU-V06). Входной тред не мутируется."""
    _require_open(thread)
    phase = thread["phase"]
    if phase not in ("tour2", "tour4"):
        raise MoveRejected("unexpected_move", f"ожидается ход: {phase}")
    verdicts = list(verdicts or ())
    if not verdicts:
        raise MoveRejected("empty_wave", "волна вотумов пуста")
    voters = [v["voter_id"] for v in verdicts]
    if len(set(voters)) != len(voters):
        raise MoveRejected("duplicate_vote",
                           "ровно один вотум на участника в волне (FR-07)")
    if thread["author_id"] in voters:
        raise MoveRejected("author_vote",
                           "голосуют соседние модели, не автор (TD §5.2)")
    out = copy.deepcopy(thread)
    tour = 2 if phase == "tour2" else 4
    out["waves"].append({"tour": tour, "votes": copy.deepcopy(verdicts)})
    out["evidence"].extend(copy.deepcopy([v["evidence"] for v in verdicts]))
    all_upheld = all(v["verdict"] == "upheld" for v in verdicts)
    if tour == 2:
        if all_upheld:
            out["status"] = "confirmed"   # досрочное подтверждение (SU-V04)
            out["phase"] = None
        else:
            out["phase"] = "author_response"
    else:
        out["exchanges"] += 1             # финальный вотум — обмен 2 (потолок)
        out["status"] = "confirmed" if all_upheld else "contested"
        out["phase"] = None
    return out


def apply_author_response(thread: dict, response: dict) -> dict:
    """Ответ автора тура 3 — ровно один ход (второй отклоняется, SU-V01/V05):
    withdraw → withdrawn; accept_reclassify → reclassified; maintain → тур 4.
    Входной тред не мутируется."""
    _require_open(thread)
    if thread["author_response"] is not None:
        raise MoveRejected("move_limit",
                           "ответ автора — ровно один ход на находку (FR-07)")
    if thread["phase"] != "author_response":
        raise MoveRejected("unexpected_move",
                           f"ожидается ход: {thread['phase']}")
    out = copy.deepcopy(thread)
    out["author_response"] = copy.deepcopy(response)
    out["exchanges"] += 1                 # ответ автора — обмен 1 (потолок 2)
    if "counter_evidence" in response:
        out["evidence"].append(copy.deepcopy(response["counter_evidence"]))
    kind = response["response"]
    if kind == "withdraw":
        out["status"], out["phase"] = "withdrawn", None
    elif kind == "accept_reclassify":
        out["status"], out["phase"] = "reclassified", None
    else:  # maintain — disagreement не снято, последний обмен: тур 4
        out["phase"] = "tour4"
    return out


# --- Анонимность payload туров 2–4 (TD §5.2, AC-07) ---------------------------

def tour_payload(finding: dict, anon_map: dict) -> dict:
    """Payload находки для атакующих туров 2/4: anon_id вместо author_id;
    ключ author_id в payload ОТСУТСТВУЕТ (утечка реального id — дефект).
    Неизвестный автор — fail-closed (граница анонимизации CONS-01 RISK-07)."""
    anon = anon_map.get(finding["author_id"])
    if anon is None:
        raise ValueError(f"anon_id не найден для автора: {finding['author_id']}")
    return {
        "finding_id": finding["finding_id"],
        "anon_id": anon,
        "location": dict(finding["location"]),
        "category": finding["category"],
        "severity": finding["severity"],
        "in_lens": finding.get("in_lens"),
        "claim": finding["claim"],
        "evidence": finding["evidence"],
        "rationale": finding.get("rationale", ""),
    }


def objections_payload(verdicts, anon_map: dict) -> list:
    """Агрегированные возражения для автора (тур 3): голоса под anon_id
    голосующих, voter_id не утекает. Неизвестный голосующий — fail-closed."""
    out = []
    for verdict in verdicts:
        anon = anon_map.get(verdict["voter_id"])
        if anon is None:
            raise ValueError(
                f"anon_id не найден для голосующего: {verdict['voter_id']}"
            )
        anonymized = {k: v for k, v in verdict.items() if k != "voter_id"}
        anonymized["anon_id"] = anon
        out.append(anonymized)
    return out


# ---------------------------------------------------------------------------
# Track record роя (FR-12, AC-12, TD §5.7) — сборка observations из состояния
# сессии, read-only проекция strengths, история линз для ротации. Чистые
# функции (NFR-04): decay/веса/порог переиспользованы из harness track_record
# (наследование формул, TD §3.2); хранение (.swarm-track-record/) — слой CLI.
# ---------------------------------------------------------------------------

SWARM_SEQ_FIELD = "review_seq"  # счётчик сессий роя (у инструментов разные, FR-01е)

# Маркеры, исключающие наблюдение из strength-проекции ЦЕЛИКОМ (TD §5.7:
# назначение/роль не были свободным выбором модели). auto_confirmed_overridden
# и calibration_run — записываемые маркеры, но НЕ основание исключения:
# первый — счётчик находок, второй калибрует статистику (FR-15, RISK-07).
STRENGTH_EXCLUDED_MARKERS = ("forced", "gate_pass")

# Статусы треда, засчитываемые находке как подтверждение (reclassified — находка
# по существу upheld с переклассификацией, TD §5.3).
_CONFIRMED_THREAD_STATUSES = frozenset({"confirmed", "reclassified"})

# Вердикт атаки: только overruled (upheld — защита находки, uncertain/reclassify
# — не атака). Заготовка диспозиций re-review (T-12) в observation.
_REREVIEW_FIXED = {"fixed": "fixed", "partially": "partially"}


def _finding_outcomes(session: dict) -> dict:
    """Итог каждой находки сессии: {finding_id: {nonunique, overridden,
    confirmed, unvalidated}}. Неуникальная автоподтверждённая (>=2 слепые
    модели, FR-06) — confirmed без треда; снятое автоподтверждение (override
    Оркестратора) — исход по треду; contested — решением арбитража (финально,
    FR-08), без арбитража disagreement неснято → не подтверждена; open
    (деградация, TD §11) и находки без дедупа/треда (ранний close, routed из
    туров 2–4) — UNVALIDATED (F-03, R-Final): атака не состоялась, находка
    записывается отдельным счётчиком и НЕ входит в overruled/знаменатели
    accept_rate/upheld_rate_as_author (FR-12: upheld — пережившие атаку)."""
    outcomes: dict = {}
    dedup = session.get("dedup") or {}
    for cluster in dedup.get("clusters", []) or []:
        for finding in cluster["findings"]:
            auto_confirmed = bool(cluster.get("auto_confirmed"))
            outcomes[finding["finding_id"]] = {
                "nonunique": auto_confirmed,
                "overridden": bool(cluster.get("auto_confirmed_overridden")),
                "confirmed": auto_confirmed,
                # кластер без автоподтверждения и без треда — атаки не было;
                # тред ниже перезапишет по своему статусу
                "unvalidated": not auto_confirmed,
            }
    arbitration = session.get("arbitration") or {}
    for fid, thread in (session.get("threads") or {}).items():
        status = thread.get("status")
        if status in _CONFIRMED_THREAD_STATUSES:
            confirmed = True
        elif status == "contested":
            decision = (arbitration.get(fid) or {}).get("decision")
            confirmed = decision in ("upheld", "reclassified")
        else:  # withdrawn / open
            confirmed = False
        entry = outcomes.setdefault(
            fid, {"nonunique": False, "overridden": False, "confirmed": False,
                  "unvalidated": False})
        entry["confirmed"] = confirmed
        # open — атака не состоялась (unvalidated); withdrawn/contested —
        # атака была, исход по вотумам/арбитражу
        entry["unvalidated"] = status == "open"
    return outcomes


def _modal_category(findings: list) -> str | None:
    """Категория — атрибут наблюдения (отчётность, не ячейка формулы, FR-12):
    модальная категория находок участника, тай-брейк — алфавитный (детерминизм)."""
    counts: dict[str, int] = {}
    for finding in findings:
        counts[finding["category"]] = counts.get(finding["category"], 0) + 1
    if not counts:
        return None
    return min(counts, key=lambda c: (-counts[c], c))


def build_observations(session: dict, review_seq: int, date: str) -> list:
    """Observations сессии (TD §5.7): ровно одна на участника; счётчики — из
    состояния сессии (findings, dedup, треды, арбитраж). review_seq — порядковый
    номер сессии роя (вход decay), date — дата записи; оба параметром — ядро
    чистое (NFR-04), часы/счётчики — слой CLI. Чистая функция."""
    outcomes = _finding_outcomes(session)
    findings = list(session.get("findings") or []) + list(
        session.get("routed_findings") or [])
    by_author: dict[str, list] = {}
    for finding in findings:
        by_author.setdefault(finding["author_id"], []).append(finding)

    # Охват неуникальных: автоподтверждённый кластер, в котором участник НЕ
    # автор, — пропущенная им подтверждённая находка (FR-12, AC-12).
    missed: dict[str, int] = {}
    dedup = session.get("dedup") or {}
    for cluster in dedup.get("clusters", []) or []:
        if not cluster.get("auto_confirmed"):
            continue
        for participant in session.get("participants", []):
            if participant["id"] not in cluster["authors"]:
                missed[participant["id"]] = missed.get(participant["id"], 0) + 1

    # Атаки: overruled-вотумы участника в волнах туров 2/4; атака обоснована,
    # если находка по итогу не подтверждена (withdrawn / contested без upheld).
    attacks: dict[str, list] = {}
    for fid, thread in (session.get("threads") or {}).items():
        for wave in thread.get("waves", []):
            for vote in wave.get("votes", []):
                if vote.get("verdict") != "overruled":
                    continue
                attacks.setdefault(vote["voter_id"], []).append(fid)

    rereview = session.get("rereview") or {}  # заготовка T-12: fid → verdict
    observations = []
    for participant in session.get("participants", []):
        pid = participant["id"]
        authored = by_author.get(pid, [])
        unique_confirmed = unique_unconfirmed = nonunique = 0
        upheld = overruled = overridden_count = 0
        unvalidated = 0
        nit = in_lens = out_of_lens = 0
        fixed = partially = not_fixed = 0
        for finding in authored:
            outcome = outcomes.get(finding["finding_id"]) or {
                "nonunique": False, "overridden": False, "confirmed": False,
                # находка вне дедупа и тредов (ранний close, routed) — атаки
                # не было (F-03, R-Final)
                "unvalidated": True}
            if outcome["overridden"]:
                overridden_count += 1
            if outcome["nonunique"]:
                nonunique += 1
            elif outcome.get("unvalidated"):
                # атака не состоялась — НЕ overruled и НЕ знаменатель
                # accept_rate/upheld_rate_as_author (F-03, R-Final; FR-12)
                unvalidated += 1
            elif outcome["confirmed"]:
                unique_confirmed += 1
                upheld += 1
            else:
                unique_unconfirmed += 1
                overruled += 1
            if finding["severity"] == SEVERITY_NIT:
                nit += 1
            if finding.get("in_lens"):
                in_lens += 1
            else:
                out_of_lens += 1
            verdict = rereview.get(finding["finding_id"])
            if verdict in _REREVIEW_FIXED:
                if verdict == "fixed":
                    fixed += 1
                else:
                    partially += 1
            elif verdict is not None:
                not_fixed += 1  # not_fixed / introduced_new_issue (T-12)
        made = attacks.get(pid, [])
        confirmed_overrule = sum(
            1 for fid in made
            if not (outcomes.get(fid) or {}).get("confirmed", False)
        )
        observations.append({
            "review_session_id": session["session_id"],
            "date": date,
            "participant_id": pid,
            "family": participant["family"],
            "role": participant["lens"],
            "category": _modal_category(authored),
            "forced": bool(participant.get("lens_forced")),
            "gate_pass": bool(participant.get("gate_pass", False)),  # T-13
            "findings_unique_confirmed": unique_confirmed,
            "findings_unique_unconfirmed": unique_unconfirmed,
            "findings_nonunique": nonunique,
            "nonunique_missed": missed.get(pid, 0),
            "upheld_as_author": upheld,
            "overruled_as_author": overruled,
            "unvalidated": unvalidated,
            "attacks_made": len(made),
            "attacks_confirmed_overrule": confirmed_overrule,
            "auto_confirmed_overridden": overridden_count,
            "fixed": fixed,
            "partially": partially,
            "not_fixed": not_fixed,
            "nit_count": nit,
            "in_lens_count": in_lens,
            "out_of_lens_count": out_of_lens,
            "quota_mode": session.get("quota_mode"),
            "quota_fallback_reason": session.get("quota_fallback_reason"),
            "calibration_run": bool(session.get("calibration_run", False)),
            SWARM_SEQ_FIELD: int(review_seq),
        })
    return observations


def compute_swarm_strengths(observations: list,
                            seq_field: str = SWARM_SEQ_FIELD) -> dict:
    """Read-only проекция strengths роя из observations (TD §5.7).

    Ячейка — (participant_id × role); decay w = 0.5^(k/8) (harness
    observation_weight, наследуется); n_eff — число чистых наблюдений ячейки
    (n_eff < STRENGTHS_FLOOR → round-robin на назначении, не здесь).
    Наблюдения с forced/gate_pass исключены ЦЕЛИКОМ (TD §5.7). Метрики:
    accept_rate (уникальные подтверждённые), upheld_rate_as_author, score =
    0.6·accept + 0.4·upheld (веса наследуются); overrule_precision_as_attacker,
    охват неуникальных, precision (подтверждённые/выдвинутые), доля fixed,
    nit/in-lens/out-of-lens — отчётные, в score не входят. Чистая функция."""
    clean = [
        obs for obs in (observations or [])
        if not any(obs.get(marker) for marker in STRENGTH_EXCLUDED_MARKERS)
    ]
    if not clean:
        return {}
    max_seq = max(int(obs.get(seq_field, 0)) for obs in clean)
    groups: dict[tuple, list] = {}
    for obs in clean:
        groups.setdefault((obs["participant_id"], obs["role"]), []).append(obs)

    result = {}
    for key, group in groups.items():
        weighted = [
            (track_record.observation_weight(max_seq - int(obs.get(seq_field, 0))), obs)
            for obs in group
        ]

        def wsum(field: str) -> float:
            return sum(w * float(obs.get(field, 0) or 0) for w, obs in weighted)

        def rate(num: float, den: float) -> float | None:
            return None if den == 0 else num / den

        unique_confirmed = wsum("findings_unique_confirmed")
        unique_unconfirmed = wsum("findings_unique_unconfirmed")
        nonunique = wsum("findings_nonunique")
        upheld = wsum("upheld_as_author")
        overruled = wsum("overruled_as_author")
        fixed = wsum("fixed")

        accept_rate = rate(unique_confirmed, unique_confirmed + unique_unconfirmed)
        upheld_rate = rate(upheld, upheld + overruled)
        if accept_rate is None and upheld_rate is None:
            score = 0.0
        elif accept_rate is None:
            score = upheld_rate
        elif upheld_rate is None:
            score = accept_rate
        else:
            score = (track_record.SCORE_W_ACCEPT * accept_rate
                     + track_record.SCORE_W_UPHELD * upheld_rate)
        attacks_made = wsum("attacks_made")
        result[key] = {
            "score": score,
            "n_eff": float(len(group)),
            "accept_rate": accept_rate,
            "upheld_rate_as_author": upheld_rate,
            # Отчётные метрики (в score НЕ входят, TD §5.7):
            "overrule_precision_as_attacker": rate(
                wsum("attacks_confirmed_overrule"), attacks_made),
            "coverage_nonunique": rate(
                nonunique, nonunique + wsum("nonunique_missed")),
            "precision": rate(
                unique_confirmed + nonunique,
                unique_confirmed + unique_unconfirmed + nonunique),
            "fixed_rate": rate(
                fixed, fixed + wsum("partially") + wsum("not_fixed")),
            "nit_count": wsum("nit_count"),
            "in_lens_count": wsum("in_lens_count"),
            "out_of_lens_count": wsum("out_of_lens_count"),
        }
    return result


def lens_history(observations: list, seq_field: str = SWARM_SEQ_FIELD) -> dict:
    """Снимок ячеек track record для assign_lenses (FR-11/FR-12):
    {pid: {"lenses_used": цикл запрета повтора, "n_eff": {линза: n_eff}}}.

    lenses_used — роли свободных (не forced/gate_pass) наблюдений участника по
    возрастанию seq; текущий цикл — различный суффикс (повтор роли = граница
    цикла: ротация не назначает линзу, пока пул не исчерпан, T-07). n_eff — из
    проекции strengths (пустые ячейки < FLOOR → round-robin, SU-TR04).
    Чистая функция."""
    clean = [
        obs for obs in (observations or [])
        if not any(obs.get(marker) for marker in STRENGTH_EXCLUDED_MARKERS)
    ]
    history: dict = {}
    by_participant: dict[str, list] = {}
    for obs in clean:
        by_participant.setdefault(obs["participant_id"], []).append(obs)
    for pid, participant_obs in by_participant.items():
        cycle: list = []
        for obs in sorted(participant_obs,
                          key=lambda o: int(o.get(seq_field, 0))):
            role = obs["role"]
            if role in cycle:
                cycle = []  # исчерпание пула — предыдущий цикл закрыт
            cycle.append(role)
        history[pid] = {"lenses_used": cycle, "n_eff": {}}
    for (pid, role), entry in compute_swarm_strengths(clean, seq_field).items():
        cell = history.setdefault(pid, {"lenses_used": [], "n_eff": {}})
        cell["n_eff"][role] = entry["n_eff"]
    return history
