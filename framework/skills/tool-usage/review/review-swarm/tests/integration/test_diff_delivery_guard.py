"""F-13 (RVSW-01, delta cross-family): guard против молчаливой деградации
доставки diff в sandbox участника.

Первичная доставка diff к первому ходу тура 1 — через paths старта: адаптер
копирует untracked-файл `.swarm-sessions/<sid>/review.diff` на фазе copying.
Untracked-файл попадает в paths старта через git-admitted (`--others`) — это
работает только пока `.swarm-sessions/` НЕ игнорируется корневым .gitignore
репозитория. Если каталог когда-либо попадёт под ignore-правило, доставка
молча деградирует до fallback-копирования адаптера (или полного отсутствия
diff на первом ходу) — этот guard фиксирует инвариант исполняемо.

Проверка — `git check-ignore`: exit 1 = путь НЕ игнорируется (ожидаемое
состояние), exit 0 = игнорируется (инвариант сломан), прочее — ошибка git.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

# .framework/skills/review-swarm/tests/integration/<file> → корень репозитория
REPO_ROOT = Path(__file__).resolve().parents[5]


def test_f13_swarm_sessions_not_gitignored():
    """`.swarm-sessions/` не должен попадать под корневой .gitignore."""
    probe = ".swarm-sessions/guard-probe/review.diff"
    result = subprocess.run(
        ["git", "check-ignore", "-q", probe],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
    )
    assert result.returncode in (0, 1), (
        f"git check-ignore завершился с ошибкой {result.returncode}: "
        f"{result.stderr.strip()}"
    )
    assert result.returncode == 1, (
        f"{probe!r} игнорируется корневым .gitignore (exit 0) — untracked diff "
        "не попадёт в paths старта через git-admitted, доставка diff к первому "
        "ходу тура 1 молча деградирует (F-13). Убрать ignore-правило для "
        ".swarm-sessions/ или добавить негативную запись."
    )
