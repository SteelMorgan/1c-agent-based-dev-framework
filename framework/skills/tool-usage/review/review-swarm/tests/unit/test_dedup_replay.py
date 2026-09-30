"""SU-D09..D12 — реплей dedup-journal и примеры промптов (RVSW-01, E2E-F1/E2E-F2;
консилиум E2E-03, вердикт ADOPTED: элементы E1–E6 + red-team F-24/F-26).

- SU-D09: дедупликация записей на входе fold'а реплея (E3): семантический дубль
  (action, finding_ids, reason, actor — без ts/session_id) применяется один раз,
  порядок файла сохранён, сам журнал остаётся сырым append-only аудитом;
- SU-D10: идемпотентность реплея (F-07/F-08): дубль override_auto_confirm в
  сыром журнале НЕ рушит fold — кластер получает маркер один раз; негативный
  контроль: без отсечения дублей тот же журнал падает (apply_journal
  неидемпотентен — причина существования fold-input dedup);
- SU-D11: fold — строго в порядке файла (F-24): split→merge и merge→split дают
  разные проекции (порядко-зависимость apply_journal зафиксирована тестом);
- SU-D12: примеры валидных fenced-блоков в промпт-инструкциях туров 2–4,
  rereview и gate (E2E-F2): последний fenced-блок каждой инструкции — пример,
  парсящийся ядром (parse_* ядра берут последний блок текста).
"""
from __future__ import annotations

import pytest

import swarm
import swarm_core


def _finding(fid, author, path, start, end=None, category="correctness",
             severity="P2"):
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


@pytest.fixture
def pool():
    """F-001/F-002 — неуникальный автоподтверждённый кластер; F-003 — уникальная."""
    return [
        _finding("F-001", "claude-opus", "src/a.py", 42),
        _finding("F-002", "codex-gpt", "src/a.py", 44),
        _finding("F-003", "kimi-k2", "src/b.py", 7),
    ]


def _cluster_by_findings(result, finding_ids):
    return next(
        cl for cl in result["clusters"]
        if {f["finding_id"] for f in cl["findings"]} == set(finding_ids)
    )


# ---------- SU-D09: дедупликация на входе fold'а ----------

def test_su_d09_fold_input_dedup_semantics():
    """SU-D09/E3: дубль (тот же action, те же finding_ids в том же порядке, тот
    же reason/actor) отсекается на входе fold'а; ts/session_id на тождественность
    не влияют (сырые поля аудита); порядок записей — порядок файла."""
    merge = swarm_core.dedup_journal_entry(
        "merge", ["F-001", "F-002"], "одна первопричина", ts="t1")
    merge_dup = dict(merge, ts="t2", session_id="swarm-9")  # дубль с иным ts
    split = swarm_core.dedup_journal_entry("split", ["F-003"], "разные дефекты")
    # порядок finding_ids значим (категория merge — от первой находки): НЕ дубль
    merge_reordered = swarm_core.dedup_journal_entry(
        "merge", ["F-002", "F-001"], "одна первопричина")
    # другой reason — осознанная новая запись, НЕ дубль
    merge_other_reason = swarm_core.dedup_journal_entry(
        "merge", ["F-001", "F-002"], "другое обоснование")

    unique, cut = swarm_core.dedup_journal_entries(
        [merge, merge_dup, split, merge_reordered, merge_other_reason])
    assert cut == 1
    assert unique == [merge, split, merge_reordered, merge_other_reason]


def test_su_d09_empty_journal_is_noop():
    """SU-D09: пустой вход fold'а — пустая проекция, отсечено 0."""
    assert swarm_core.dedup_journal_entries([]) == ([], 0)


# ---------- SU-D10: идемпотентность реплея (негативный контроль) ----------

def test_su_d10_replay_duplicate_override_does_not_crash(pool):
    """SU-D10/E3 (F-07/F-08): повторный --journal-file дублирует записи в сыром
    аудите; реплей отсекает дубли на входе fold'а — override_auto_confirm
    применяется один раз, маркер на месте, протокол не падает."""
    result = swarm_core.dedup_findings(pool, window=4)
    override = swarm_core.dedup_journal_entry(
        "override_auto_confirm", ["F-001", "F-002"],
        "скоррелированное ложное срабатывание")
    journal = [override, dict(override)]  # сырой аудит: запись продублирована

    # Негативный контроль неидемпотентности (F-07/F-08): без отсечения дублей
    # fold того же журнала fail-closed — повторный override по уже снятому
    # автоподтверждению отклоняется ядром.
    with pytest.raises(ValueError, match="override_auto_confirm"):
        swarm_core.apply_journal(result, journal, window=4)

    unique, cut = swarm_core.dedup_journal_entries(journal)
    assert cut == 1
    replayed = swarm_core.apply_journal(result, unique, window=4)
    moved = _cluster_by_findings(replayed, ["F-001", "F-002"])
    assert moved["auto_confirmed"] is False
    assert moved["auto_confirmed_overridden"] is True


# ---------- SU-D11: порядок fold'а — порядок файла ----------

def test_su_d11_fold_is_order_dependent_file_order(pool):
    """SU-D11/F-24: fold порядко-зависим и идёт строго в порядке журнала:
    split→merge возвращает находки в один кластер, merge→split — разводит."""
    result = swarm_core.dedup_findings(pool, window=4)
    split = swarm_core.dedup_journal_entry("split", ["F-002"], "разные дефекты")
    merge = swarm_core.dedup_journal_entry(
        "merge", ["F-001", "F-002"], "одна первопричина")

    split_then_merge = swarm_core.apply_journal(result, [split, merge], window=4)
    merged = _cluster_by_findings(split_then_merge, ["F-001", "F-002"])
    assert merged["auto_confirmed"] is True

    merge_then_split = swarm_core.apply_journal(result, [merge, split], window=4)
    singles = {
        frozenset(f["finding_id"] for f in cl["findings"])
        for cl in merge_then_split["clusters"]
    }
    assert frozenset({"F-001"}) in singles and frozenset({"F-002"}) in singles


# ---------- SU-D12: примеры валидных fenced-блоков в промптах (E2E-F2) ----------

INSTRUCTION_EXAMPLES = {
    # инструкция → (fenced-тег, парсер ядра сырого хода)
    "VERDICT_INSTRUCTION": (
        "swarm-verdict", lambda text: swarm_core.parse_verdict_move(text)[0]),
    "AUTHOR_RESPONSE_INSTRUCTION": (
        "swarm-author-response",
        lambda text: swarm_core.parse_author_response_move(text)[0]),
    "REREVIEW_INSTRUCTION": (
        "swarm-rereview", swarm_core.parse_rereview_move),
    "GATE_VERDICT_COMPLETION_INSTRUCTION": (
        "swarm-gate-verdict", swarm_core.parse_gate_verdict_move),
    "GATE_VERDICT_ACCEPTANCE_INSTRUCTION": (
        "swarm-gate-verdict", swarm_core.parse_gate_verdict_move),
}


@pytest.mark.parametrize("instruction_name", sorted(INSTRUCTION_EXAMPLES))
def test_su_d12_instruction_example_block_parses(instruction_name):
    """SU-D12/E2E-F2: каждая инструкция хода несёт ПРИМЕР валидного fenced-блока
    (не только шаблон с плейсхолдерами); пример — последний fenced-блок текста
    (ядро берёт последний) и парсится соответствующим парсером ядра."""
    fence_tag, parse = INSTRUCTION_EXAMPLES[instruction_name]
    text = getattr(swarm, instruction_name)
    assert "ПРИМЕР" in text, f"{instruction_name}: нет примера валидного блока"
    raw = parse(text)  # MoveRejected, если пример битый/отсутствует
    assert isinstance(raw, dict) and raw, f"{instruction_name}: пустой пример"
