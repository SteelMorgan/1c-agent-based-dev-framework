"""Хелперы scripted-ответов (structured-блоки роя) для integration-тестов
review-swarm (RVSW-01, T-10).

Вынесены из `conftest.py` в отдельный модуль с уникальным именем: bare
`from conftest import ...` неоднозначен при комбинированном прогоне нескольких
наборов тестов в одном pytest-процессе (конфликт basename `conftest`).
conftest.py реэкспортирует эти функции для обратной совместимости.
"""
from __future__ import annotations

import json
from pathlib import Path


def findings_block(findings: list[dict]) -> str:
    """Ответ участника тура 1 с fenced-блоком находок."""
    return ("Тур 1: находки по линзе.\n\n```swarm-structured\n"
            + json.dumps({"findings": findings}, ensure_ascii=False)
            + "\n```\n")


def verdict_block(finding_id: str, verdict: str, path: str, line: int,
                  quote: str, **extra) -> str:
    """Ответ атакующего туров 2/4."""
    payload = {
        "finding_id": finding_id,
        "verdict": verdict,
        "evidence": {"path": path, "line": line, "quote": quote},
        "rationale": "аргумент",
        **extra,
    }
    return ("Вердикт.\n\n```swarm-verdict\n"
            + json.dumps(payload, ensure_ascii=False) + "\n```\n")


def author_response_block(finding_id: str, response: str,
                          counter: dict | None = None) -> str:
    """Ответ автора тура 3."""
    payload = {"finding_id": finding_id, "response": response,
               "rationale": "позиция автора"}
    if counter is not None:
        payload["counter_evidence"] = counter
    return ("Ответ автора.\n\n```swarm-author-response\n"
            + json.dumps(payload, ensure_ascii=False) + "\n```\n")


def read_invocations(state_dir: Path) -> list[dict]:
    log = state_dir / "invocations.jsonl"
    if not log.exists():
        return []
    return [json.loads(line) for line in log.read_text(encoding="utf-8").splitlines()
            if line.strip()]
