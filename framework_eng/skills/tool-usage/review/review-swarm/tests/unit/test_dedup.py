"""SU-D — механический дедуп находок (RVSW-01, T-08, FR-06/AC-06, TD §5.1/§5.6).

Покрытие test-plan SU-D01..D08 (чистые функции ядра, NFR-04):
- SU-D01: нормализация location (путь, строка/диапазон) + канонизация category —
  эквивалентные записи сходятся;
- SU-D02: окно перекрытия строк/диапазонов: пересекающиеся (в пределах окна) —
  один кластер; соседние вне окна — разные; границы детерминированы;
- SU-D03: группы с атрибуцией: неуникальные (≥2 слепые модели) / уникальные /
  пограничные (перекрытие location при разных категориях — к Оркестратору);
- SU-D04: неуникальные автоподтверждены; уникальные неподтверждённые → тур 2;
- SU-D05: dedup-journal (TD §5.6): merge/split с reason и actor=orchestrator,
  применение журнала к группам;
- SU-D06: override автоподтверждения → маркер auto_confirmed_overridden,
  обоснование обязательно;
- SU-D07: находка с невалидной location в дедуп не попадает (порядок конвейера);
- SU-D08: идемпотентность — повторный прогон на том же пуле не меняет группы.
"""
from __future__ import annotations

import pytest

import swarm_core


def _finding(fid, author, path, start, end=None, category="correctness",
             severity="P2"):
    """Принятая находка (форма после validate_findings) — вход дедупа."""
    return {
        "finding_id": fid,
        "author_id": author,
        "location": {"path": path, "line_start": start,
                     "line_end": start if end is None else end},
        "category": category,
        "severity": severity,
        "in_lens": True,
        "claim": f"claim {fid}",
        "evidence": f"{path}:{start}",
    }


def _group_ids(result, group):
    return {cl["cluster_id"] for cl in result["groups"][group]}


# ---------- SU-D01: нормализация + канонизация ----------

def test_su_d01_location_normalization_equivalent_forms_converge():
    """SU-D01: './src/a.py', 'src\\a.py', 'src//a.py' → 'src/a.py';
    точечная строка → диапазон line..line."""
    loc = swarm_core.normalize_location(
        {"path": "./src//a.py", "line_start": 10}
    )
    assert loc == {"path": "src/a.py", "line_start": 10, "line_end": 10}
    loc = swarm_core.normalize_location(
        {"path": "src\\a.py", "line_start": 10, "line_end": 12}
    )
    assert loc["path"] == "src/a.py"
    assert (loc["line_start"], loc["line_end"]) == (10, 12)


def test_su_d01_dedup_treats_equivalent_locations_as_same():
    """SU-D01: разные текстовые формы одной location — один кластер."""
    a = _finding("F-001", "claude-opus", "./src/a.py", 10)
    b = _finding("F-002", "codex-gpt", "src\\a.py", 10, 10)
    result = swarm_core.dedup_findings([a, b])
    assert len(result["clusters"]) == 1
    assert _group_ids(result, "nonunique_auto_confirmed")


def test_su_d01_category_canonicalization_synonyms_and_case():
    """SU-D01: регистр, разделители и синонимы таксономии → канонические id TD §5.1."""
    assert swarm_core.canonical_category("Security") == "security"
    assert swarm_core.canonical_category("api-contracts") == "api_contract"
    assert swarm_core.canonical_category("API") == "api_contract"
    assert swarm_core.canonical_category("Concurrency/Reliability") == "concurrency"
    assert swarm_core.canonical_category("data integrity") == "data_integrity"
    assert swarm_core.canonical_category("data_integrity_migrations") == "data_integrity"
    assert swarm_core.canonical_category("test") == "tests"
    assert swarm_core.canonical_category("perf") == "performance"
    with pytest.raises(ValueError):
        swarm_core.canonical_category("ux")


def test_su_d01_same_location_different_category_separate_clusters():
    """SU-D01/FR-06: группировка по (location, category) — одна точка кода
    с разными каноническими категориями механически НЕ склеивается."""
    a = _finding("F-001", "claude-opus", "src/a.py", 10, category="security")
    b = _finding("F-002", "codex-gpt", "src/a.py", 10, category="performance")
    result = swarm_core.dedup_findings([a, b])
    assert len(result["clusters"]) == 2


# ---------- SU-D02: окно перекрытия ----------

def test_su_d02_lines_within_window_one_cluster():
    """SU-D02: file:42 и file:45 при окне ≥4 строк — один кластер."""
    a = _finding("F-001", "claude-opus", "src/a.py", 42)
    b = _finding("F-002", "codex-gpt", "src/a.py", 45)
    result = swarm_core.dedup_findings([a, b], window=4)
    assert len(result["clusters"]) == 1


def test_su_d02_intersecting_ranges_one_cluster():
    """SU-D02: диапазоны с пересечением — один кластер."""
    a = _finding("F-001", "claude-opus", "src/a.py", 40, 50)
    b = _finding("F-002", "codex-gpt", "src/a.py", 48, 60)
    result = swarm_core.dedup_findings([a, b], window=0)
    assert len(result["clusters"]) == 1


def test_su_d02_adjacent_beyond_window_separate_clusters():
    """SU-D02: соседние location вне окна перекрытия — разные кластеры;
    граница окна детерминирована (при window=0 касание границы = пересечение)."""
    a = _finding("F-001", "claude-opus", "src/a.py", 42)
    b = _finding("F-002", "codex-gpt", "src/a.py", 52)  # зазор 10 строк > окна 4
    result = swarm_core.dedup_findings([a, b], window=4)
    assert len(result["clusters"]) == 2
    # детерминированная граница: ровно на краю окна — ещё один кластер
    edge = _finding("F-003", "codex-gpt", "src/a.py", 46)  # 42+4 — касание окна
    result = swarm_core.dedup_findings([a, edge], window=4)
    assert len(result["clusters"]) == 1
    beyond = _finding("F-004", "codex-gpt", "src/a.py", 47)  # за краем окна
    result = swarm_core.dedup_findings([a, beyond], window=4)
    assert len(result["clusters"]) == 2


def test_su_d02_different_files_never_cluster():
    """SU-D02: одинаковые строки в разных файлах — разные кластеры."""
    a = _finding("F-001", "claude-opus", "src/a.py", 42)
    b = _finding("F-002", "codex-gpt", "src/b.py", 42)
    result = swarm_core.dedup_findings([a, b])
    assert len(result["clusters"]) == 2


# ---------- SU-D03: группы с атрибуцией ----------

@pytest.fixture
def pool():
    """Пул тура 1: F-001/F-002 — одна находка двух слепых моделей; F-003 —
    уникальная; F-004/F-005 — перекрытие location при разных категориях
    (пограничный случай Оркестратора)."""
    return [
        _finding("F-001", "claude-opus", "src/a.py", 42),
        _finding("F-002", "codex-gpt", "src/a.py", 44),
        _finding("F-003", "kimi-k2", "src/b.py", 7),
        _finding("F-004", "claude-opus", "src/c.py", 10, 20, category="correctness"),
        _finding("F-005", "codex-gpt", "src/c.py", 15, 25, category="data_integrity"),
    ]


def test_su_d03_groups_with_attribution(pool):
    """SU-D03: неуникальные (≥2 слепые модели) / уникальные / пограничные;
    атрибуция «кто выявил» сохраняется в каждой группе."""
    result = swarm_core.dedup_findings(pool, window=4)
    nonunique = result["groups"]["nonunique_auto_confirmed"]
    unique = result["groups"]["unique_unconfirmed"]
    assert [{f["finding_id"] for f in cl["findings"]} for cl in nonunique] == [
        {"F-001", "F-002"}
    ]
    assert nonunique[0]["authors"] == ["claude-opus", "codex-gpt"]
    unique_ids = {f["finding_id"] for cl in unique for f in cl["findings"]}
    assert {"F-003", "F-004", "F-005"} <= unique_ids
    # пограничные: F-004/F-005 перекрываются по строкам, но категории разные
    borderline_ids = [set(b["finding_ids"]) for b in result["borderline"]]
    assert {"F-004", "F-005"} in borderline_ids


def test_su_d03_same_author_twice_is_not_nonunique():
    """SU-D03: неуникальность — по СЛЕПЫМ МОДЕЛЯМ (≥2 разных автора); две находки
    одного автора в одной точке — уникальный кластер, автоподтверждения нет."""
    a = _finding("F-001", "claude-opus", "src/a.py", 42)
    b = _finding("F-002", "claude-opus", "src/a.py", 43)
    result = swarm_core.dedup_findings([a, b], window=4)
    assert len(result["clusters"]) == 1
    assert _group_ids(result, "nonunique_auto_confirmed") == set()
    assert len(result["groups"]["unique_unconfirmed"]) == 1


# ---------- SU-D04: маршрутизация по протоколу ----------

def test_su_d04_nonunique_auto_confirmed_unique_goes_to_tour2(pool):
    """SU-D04: неуникальные помечены auto_confirmed (бесплатная валидация,
    решение №3); уникальные неподтверждённые — группа маршрута в тур 2."""
    result = swarm_core.dedup_findings(pool, window=4)
    for cl in result["groups"]["nonunique_auto_confirmed"]:
        assert cl["auto_confirmed"] is True
        assert cl["auto_confirmed_overridden"] is False
    for cl in result["groups"]["unique_unconfirmed"]:
        assert cl["auto_confirmed"] is False


# ---------- SU-D05: dedup-journal ----------

def test_su_d05_journal_entry_schema():
    """SU-D05: запись журнала по TD §5.6: action merge|split|override_auto_confirm,
    finding_ids, непустой reason, actor=orchestrator."""
    entry = swarm_core.dedup_journal_entry(
        "merge", ["F-004", "F-005"], "одна первопричина: гонка при миграции",
        ts="2026-07-29T17:00:00Z", session_id="swarm-1",
    )
    assert entry == {
        "ts": "2026-07-29T17:00:00Z",
        "session_id": "swarm-1",
        "action": "merge",
        "finding_ids": ["F-004", "F-005"],
        "reason": "одна первопричина: гонка при миграции",
        "actor": "orchestrator",
    }
    with pytest.raises(ValueError):
        swarm_core.dedup_journal_entry("merge", ["F-1"], "  ")  # пустое обоснование
    with pytest.raises(ValueError):
        swarm_core.dedup_journal_entry("merge", ["F-1"], "r", actor="reviewer")
    with pytest.raises(ValueError):
        swarm_core.dedup_journal_entry("delete", ["F-1"], "r")


def test_su_d05_apply_merge_and_split(pool):
    """SU-D05: ручная склейка пограничной пары → один неуникальный кластер
    (категория — от первой находки записи); ручное разделение — обратно."""
    result = swarm_core.dedup_findings(pool, window=4)
    merge = swarm_core.dedup_journal_entry(
        "merge", ["F-004", "F-005"], "одна первопричина"
    )
    merged = swarm_core.apply_journal(result, [merge], window=4)
    nonunique_ids = [
        {f["finding_id"] for f in cl["findings"]}
        for cl in merged["groups"]["nonunique_auto_confirmed"]
    ]
    assert {"F-004", "F-005"} in nonunique_ids
    cluster = next(
        cl for cl in merged["clusters"]
        if {f["finding_id"] for f in cl["findings"]} == {"F-004", "F-005"}
    )
    assert cluster["category"] == "correctness"  # категория первой находки склейки
    # split: Оркестратор разделяет механически склеенный кластер
    split = swarm_core.dedup_journal_entry(
        "split", ["F-002"], "разные дефекты, соседние строки"
    )
    split_result = swarm_core.apply_journal(result, [split], window=4)
    singles = [
        {f["finding_id"] for f in cl["findings"]}
        for cl in split_result["groups"]["unique_unconfirmed"]
    ]
    assert {"F-001"} in singles and {"F-002"} in singles
    assert _group_ids(split_result, "nonunique_auto_confirmed") == set()


def test_su_d05_apply_journal_unknown_finding_fail_closed(pool):
    """SU-D05: journal ссылается на несуществующую находку — fail-closed."""
    result = swarm_core.dedup_findings(pool, window=4)
    bad = swarm_core.dedup_journal_entry("merge", ["F-999", "F-001"], "r")
    with pytest.raises(ValueError, match="F-999"):
        swarm_core.apply_journal(result, [bad], window=4)


# ---------- SU-D06: override автоподтверждения ----------

def test_su_d06_override_auto_confirm_marks_and_reroutes(pool):
    """SU-D06: скоррелированное ложное срабатывание — Оркестратор снимает
    автоподтверждение; кластер получает маркер auto_confirmed_overridden и
    уходит в неподтверждённые (тур 2); запись журнала несёт маркер и reason."""
    result = swarm_core.dedup_findings(pool, window=4)
    cluster_id = result["groups"]["nonunique_auto_confirmed"][0]["cluster_id"]
    new_result, entry = swarm_core.override_auto_confirm(
        result, cluster_id, "обе модели повторили одну ошибку обучения"
    )
    assert entry["action"] == "override_auto_confirm"
    assert entry["marker"] == "auto_confirmed_overridden"
    assert entry["actor"] == "orchestrator"
    assert entry["reason"]
    assert new_result["groups"]["nonunique_auto_confirmed"] == []
    moved = next(
        cl for cl in new_result["groups"]["unique_unconfirmed"]
        if cl["cluster_id"] == cluster_id
    )
    assert moved["auto_confirmed"] is False
    assert moved["auto_confirmed_overridden"] is True


def test_su_d06_override_requires_reason_and_auto_confirmed(pool):
    """SU-D06: override без обоснования / на неавтоподтверждённом кластере —
    fail-closed."""
    result = swarm_core.dedup_findings(pool, window=4)
    cluster_id = result["groups"]["nonunique_auto_confirmed"][0]["cluster_id"]
    with pytest.raises(ValueError):
        swarm_core.override_auto_confirm(result, cluster_id, "")
    unique_id = result["groups"]["unique_unconfirmed"][0]["cluster_id"]
    with pytest.raises(ValueError):
        swarm_core.override_auto_confirm(result, unique_id, "нечего снимать")


# ---------- SU-D07: порядок конвейера ----------

def test_su_d07_invalid_location_never_reaches_dedup(tmp_path):
    """SU-D07: находка с галлюцинированной location отсекается валидацией
    (SU-F02) и в пул дедупа не попадает."""
    root = tmp_path / "set"
    root.mkdir()
    (root / "a.py").write_text("x = 1\n" * 100, encoding="utf-8")
    checked = swarm_core.checked_set_from_paths(root, ["a.py"])
    raw = [
        {"location": {"path": "a.py", "line_start": 42}, "category": "security",
         "severity": "P1", "in_lens": True, "claim": "c1", "evidence": "a.py:42"},
        {"location": {"path": "ghost.py", "line_start": 1}, "category": "security",
         "severity": "P1", "in_lens": True, "claim": "c2", "evidence": "ghost.py:1"},
        {"location": {"path": "a.py", "line_start": 44}, "category": "security",
         "severity": "P1", "in_lens": True, "claim": "c3", "evidence": "a.py:44"},
    ]
    accepted, rejected = swarm_core.validate_findings(
        raw, checked, author_id="claude-opus"
    )
    assert len(accepted) == 2 and len(rejected) == 1
    accepted_b, _ = swarm_core.validate_findings(
        [{"location": {"path": "a.py", "line_start": 43}, "category": "security",
          "severity": "P1", "in_lens": True, "claim": "c4", "evidence": "a.py:43"}],
        checked, author_id="codex-gpt", start_index=len(accepted) + 1,
    )
    result = swarm_core.dedup_findings(accepted + accepted_b, window=4)
    all_ids = {f["finding_id"] for cl in result["clusters"] for f in cl["findings"]}
    assert all_ids == {"F-001", "F-002", "F-003"}  # ghost-находки нет
    assert len(result["groups"]["nonunique_auto_confirmed"]) == 1


# ---------- SU-D08: идемпотентность ----------

def test_su_d08_dedup_idempotent(pool):
    """SU-D08: повторный прогон дедупа на том же пуле даёт те же группы
    (детерминизм чистой функции)."""
    first = swarm_core.dedup_findings(pool, window=4)
    second = swarm_core.dedup_findings(pool, window=4)
    assert first == second
