"""HC-15: живой smoke цепочки start → fork → ask на РЕАЛЬНЫХ провайдерах.

Зачем отдельно от `review-swarm/tests/contract/test_native_fork_turn_lineage.py`:
тот контрактный тест подделывает исполняемый файл адаптера
(`review-harness/tests/stubs/stub_adapter.py`), и поэтому структурно НЕ может
воспроизвести класс дефектов, который ломает рой в бою:

- у стаба нет провайдера: `cmd_fork` — чистая запись меты, ни один внешний
  процесс не запускается, поэтому «провайдер отказал на форке» невыразимо;
- у стаба нет семантики рабочего каталога: он никогда не исполняет ничего с
  `cwd=workspace`, видимость сессии молча считается глобальной (реальный
  `claude --resume` ищет сессию в проектном каталоге, вычисленном из cwd);
- мета ребёнка в стабе строится по явному белому списку полей, поэтому класс
  «ребёнок унаследовал состояние родителя» (например, печать
  `native_fork_seal`, которая закрывает `ask`) невыразим структурно;
- форк в стабе не мутирует родителя.

Стаб зелёный 6/6 при красном бое — это и есть цена подделки. Здесь подделок
нет: настоящие адаптеры, настоящие вызовы провайдера, настоящий
`adapter_contract`. Тест проверяет ровно одно свойство, которое стаб пропускает:
после форка ребёнок ЖИВОЙ — у него своя провайдерская сессия, первый ход к нему
реально проходит, второй продолжает ту же линию.

Цена: ~3 обращения к провайдеру на семейство, односложные промпты, один
крошечный файл в контексте. Достаточно дёшево, чтобы гонять перед каждым боем.

Изоляция и уборка: cwd теста — `tmp_path`, поэтому `.review-sandboxes/`
создаётся во временном каталоге, а репозиторный runtime не затрагивается.
Каждое созданное ревью закрывается адаптерной командой `close` в `finally`,
даже когда тест упал.

Состав смешанный (решение владельца 2026-08-04): семейства, объявившие в
`adapters.yaml` `native_fork.supported: true` (claude, codex), проверяются
цепочкой с форком; объявившие `false` (kimi) — цепочкой прежнего пути. Списка
семейств в тесте нет: он читается из реестра, иначе разошёлся бы с решением
молча. Регресс форка claude/codex по-прежнему красит smoke.

Запуск (только явно, живые вызовы платные):
    CONSILIUM_REAL_CLI=1 pytest tests/contract/test_native_fork_live_smoke.py -m real_cli
    CONSILIUM_REAL_CLI=1 pytest ... -k codex        # одно семейство
Обычный `pytest tests` собирает тест и пропускает его (skipif по env).
"""
from __future__ import annotations

import hashlib
import json
import os
import uuid
from pathlib import Path

import pytest

import adapter_contract as ac
import registry as registry_module
from tests.conftest import ADAPTER_PATHS

REGISTRY_PATH = Path(__file__).resolve().parents[2] / "adapters.yaml"


def _registry_fork_declarations() -> dict[str, bool]:
    """Кто идёт точным форком — по РЕЕСТРУ, а не по списку в тесте.

    Решение владельца 2026-08-04: состав смешанный — kimi участвует БЕЗ точных
    форков (`native_fork.supported: false` в `adapters.yaml`), claude и codex
    продолжают форком. Реестр здесь и есть источник решения: захардкоженный
    список семейств разошёлся бы с ним молча.
    """
    registry = registry_module.load_registry(REGISTRY_PATH)
    declarations = {}
    for entry in registry.get("participants") or []:
        family = str(entry.get("family") or "")
        if family in ADAPTER_PATHS:
            declarations[family] = bool(
                (entry.get("native_fork") or {}).get("supported") is True)
    return declarations


FORK_DECLARATIONS = _registry_fork_declarations()
NATIVE_FORK_FAMILIES = sorted(f for f, forks in FORK_DECLARATIONS.items() if forks)
LEGACY_FAMILIES = sorted(f for f, forks in FORK_DECLARATIONS.items() if not forks)

# Односложные промпты: смысл теста — довести цепочку до конца, а не получить
# содержательный ответ. Любой непустой текст засчитывается.
PARENT_PROMPT = "Ответь одним словом: ок."
CHILD_TURN_1 = "Ответь одним словом: два."
CHILD_TURN_2 = "Ответь одним словом: три."

LIVE_TIMEOUT_SEC = 180

pytestmark = [
    pytest.mark.real_cli,
    pytest.mark.skipif(
        os.environ.get("CONSILIUM_REAL_CLI") != "1",
        reason="живые платные вызовы провайдеров: CONSILIUM_REAL_CLI=1",
    ),
]


def _adapter(family: str) -> str:
    path = ADAPTER_PATHS[family]
    if not path.exists():  # pragma: no cover - защита от переезда файлов
        pytest.fail(f"адаптер {family} не найден: {path}")
    return str(path)


def _meta(cwd: Path, review_id: str) -> dict:
    return json.loads(
        (cwd / ac.REVIEW_ROOT / review_id / "review.json").read_text(encoding="utf-8")
    )


def _close_quietly(adapter: str, review_id: str, cwd: Path) -> None:
    """Уборка не должна маскировать причину падения теста."""
    try:
        ac.close_participant(adapter, review_id, cwd)
    except Exception as exc:  # pragma: no cover - диагностика уборки
        print(f"[cleanup] close {review_id} не удался: {exc}")


@pytest.mark.parametrize("family", sorted(FORK_DECLARATIONS))
def test_hc15_live_capability_matches_registry_declaration(family, workdir):
    """Сверка объявленного с фактическим — живая в ОБЕ стороны.

    Реестр объявил форк → рантайм обязан его подтвердить (иначе форкающиеся
    семейства тихо деградируют). Реестр объявил «форка нет» → рантайм обязан
    отвечать тем же (иначе устаревшая запись реестра молча расходится с фактом
    ровно там, где реестр форк «выключил»). Это то же правило, что применяет
    `swarm._require_declared_legacy_route`, только на живом CLI.
    """
    probed = ac.probe_fork_capability(
        _adapter(family), workdir, timeout_sec=LIVE_TIMEOUT_SEC)
    assert probed.get("supported") is FORK_DECLARATIONS[family], (
        f"capability drift {family}: реестр={FORK_DECLARATIONS[family]!r}, "
        f"рантайм={probed.get('supported')!r}"
    )


@pytest.mark.parametrize("family", NATIVE_FORK_FAMILIES)
def test_hc15_live_native_fork_chain(family, workdir):
    """HC-15: живой `start → fork → ask → ask` доходит до конца у провайдера.

    Проверяется по существу, а не «команда не упала»:
      1. родитель поднялся и имеет native session + provider checkpoint;
      2. форк вернул ребёнку СВОЮ провайдерскую сессию (не сессию родителя);
      3. первый ход ребёнку РЕАЛЬНО прошёл и вернул непустой ответ;
      4. второй ход продолжает ту же линию (session_id ребёнка стабилен).
    """
    adapter = _adapter(family)
    (workdir / "context.md").write_text(
        "# smoke\nОдин крошечный файл контекста.\n", encoding="utf-8"
    )

    suffix = uuid.uuid4().hex[:8]
    parent_id = f"live-fork-{family}-parent-{suffix}"
    child_id = f"live-fork-{family}-child-{suffix}"
    operation_id = f"live-fork-op-{suffix}"
    snapshot_digest = "sha256:" + hashlib.sha256(operation_id.encode()).hexdigest()

    created: list[str] = []
    try:
        started = ac.start_participant(
            adapter, PARENT_PROMPT, ["context.md"], workdir,
            timeout_sec=LIVE_TIMEOUT_SEC, review_id=parent_id,
        )
        created.append(parent_id)
        assert started.ok, f"start({family}) провалился: {started.error}\n{started.stderr}"
        assert started.session_id, "start не вернул native session_id"
        parent_meta = _meta(workdir, parent_id)
        provider_checkpoint_id = parent_meta.get("provider_checkpoint_id")
        assert provider_checkpoint_id, "start не зафиксировал provider_checkpoint_id"

        forked = ac.fork_participant(
            adapter, parent_id, child_id, workdir,
            timeout_sec=LIVE_TIMEOUT_SEC,
            operation_id=operation_id,
            snapshot_digest=snapshot_digest,
            provider_checkpoint_id=provider_checkpoint_id,
        )
        created.append(child_id)
        assert forked.ok, f"fork({family}) провалился: {forked.error}\n{forked.stderr}"

        child_meta = _meta(workdir, child_id)
        child_session_id = child_meta.get("session_id")
        assert child_session_id, "у ребёнка нет собственной провайдерской сессии"
        assert child_session_id != started.session_id, (
            "форк вернул сессию родителя — отдельной линии ребёнка не возникло"
        )

        first = ac.ask_participant(
            adapter, child_id, CHILD_TURN_1, workdir, timeout_sec=LIVE_TIMEOUT_SEC,
        )
        assert first.ok, (
            f"первый ход ребёнку ({family}) провалился: {first.error}\n{first.stderr}"
        )
        assert (first.text or "").strip(), "первый ход ребёнку вернул пустой ответ"

        second = ac.ask_participant(
            adapter, child_id, CHILD_TURN_2, workdir, timeout_sec=LIVE_TIMEOUT_SEC,
        )
        assert second.ok, (
            f"второй ход ребёнку ({family}) провалился: {second.error}\n{second.stderr}"
        )
        assert (second.text or "").strip(), "второй ход ребёнку вернул пустой ответ"
        assert second.session_id == first.session_id == child_session_id, (
            "второй ход ушёл не в ту же линию: "
            f"child={child_session_id} first={first.session_id} second={second.session_id}"
        )
    finally:
        for review_id in reversed(created):
            _close_quietly(adapter, review_id, workdir)


@pytest.mark.parametrize("family", LEGACY_FAMILIES)
def test_hc15_live_legacy_chain_without_native_fork(family, workdir):
    """Участник БЕЗ точных форков доходит до конца прежним путём.

    Смешанный состав (решение владельца 2026-08-04) не выводит такого участника
    из роя — он просто ведётся `ask` по собственному ревью тура 1. Живой smoke
    обязан проверять именно этот его маршрут, а не требовать от него форка:
      1. родитель поднялся и имеет провайдерскую сессию;
      2. первый ход РЕАЛЬНО прошёл и вернул непустой ответ;
      3. второй ход продолжает ТУ ЖЕ линию (контекст не потерян между ходами —
         это и есть то, ради чего прежний путь вообще годится);
      4. форк по нему не выполняется — маршрут выбран по объявленной
         возможности, а не «на всякий случай».
    """
    adapter = _adapter(family)
    (workdir / "context.md").write_text(
        "# smoke\nОдин крошечный файл контекста.\n", encoding="utf-8"
    )
    suffix = uuid.uuid4().hex[:8]
    parent_id = f"live-legacy-{family}-parent-{suffix}"

    created: list[str] = []
    try:
        started = ac.start_participant(
            adapter, PARENT_PROMPT, ["context.md"], workdir,
            timeout_sec=LIVE_TIMEOUT_SEC, review_id=parent_id,
        )
        created.append(parent_id)
        assert started.ok, f"start({family}) провалился: {started.error}\n{started.stderr}"
        assert started.session_id, "start не вернул native session_id"

        first = ac.ask_participant(
            adapter, parent_id, CHILD_TURN_1, workdir, timeout_sec=LIVE_TIMEOUT_SEC,
        )
        assert first.ok, (
            f"первый ход прежним путём ({family}) провалился: "
            f"{first.error}\n{first.stderr}"
        )
        assert (first.text or "").strip(), "первый ход прежним путём вернул пустой ответ"

        second = ac.ask_participant(
            adapter, parent_id, CHILD_TURN_2, workdir, timeout_sec=LIVE_TIMEOUT_SEC,
        )
        assert second.ok, (
            f"второй ход прежним путём ({family}) провалился: "
            f"{second.error}\n{second.stderr}"
        )
        assert (second.text or "").strip(), "второй ход прежним путём вернул пустой ответ"
        assert second.session_id == first.session_id == started.session_id, (
            "второй ход ушёл не в ту же линию — прежний путь потерял контекст: "
            f"parent={started.session_id} first={first.session_id} "
            f"second={second.session_id}"
        )

        forks = [entry for entry in (workdir / ac.REVIEW_ROOT).iterdir()
                 if entry.is_dir() and entry.name != parent_id]
        assert forks == [], (
            f"по участнику без объявленного форка возникли дочерние ревью: {forks}"
        )
    finally:
        for review_id in reversed(created):
            _close_quietly(adapter, review_id, workdir)
