"""Security-покрытие sandbox-copy адаптеров review-harness (RVSW-01, R-Final F-02/F-05).

Порт негативных тестов удалённого `tests/unit/test_cross_provider_review_copy.py`
(ссылался на удалённый скилл cross-provider-review) на harness-адаптеры
`review-harness/scripts/adapters/` — все три семейства, логика assertions
сохранена:
- copy_symlink_if_safe: абсолютные/эскейп-ссылки отклоняются/скипаются
  детерминированно; relative-ссылка не создаётся без материализации цели;
  цепочка с эскейпом назначения;
- copy_file_if_present: исчезнувший admitted-файл скипается, unrelated IO error
  пропагируется;
- cmd_start rollback: неудача копирования удаляет частичный sandbox, в т.ч. при
  падающем cleanup (диагностика rollback не подменяет исходную ошибку).

Новое (F-05): sync_sources/materialize_diff fail-closed при workspace_path
вне `.review-sandboxes/<review_id>/` (подмена review.json) и при dest_rel-эскейпе.
"""
from __future__ import annotations

import errno
import importlib.util
import os
import shutil
import subprocess
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import pytest

from tests.conftest import ADAPTER_PATHS

ALL_FAMILIES = ["claude", "codex", "kimi"]
COPY_MODES = ["full", "focused", "sync"]


def load_adapter(family: str):
    path = ADAPTER_PATHS[family]
    spec = importlib.util.spec_from_file_location(f"review_harness_copysec_{family}", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def make_source_repo(root: Path) -> None:
    (root / ".gitignore").write_text("**/.codex/tmp/\n", encoding="utf-8")
    (root / "selected/.codex/tmp").mkdir(parents=True)
    (root / ".codex/tmp").mkdir(parents=True)
    (root / "selected/tracked.md").write_text("tracked\n", encoding="utf-8")
    (root / "selected/untracked.md").write_text("untracked\n", encoding="utf-8")
    (root / "root-untracked.md").write_text("untracked\n", encoding="utf-8")
    (root / "selected/.codex/tmp/transient.md").write_text("transient\n", encoding="utf-8")
    (root / ".codex/tmp/transient.md").write_text("transient\n", encoding="utf-8")
    subprocess.run(["git", "init", "-q", str(root)], check=True)
    subprocess.run(
        ["git", "-C", str(root), "add", ".gitignore", "selected/tracked.md"],
        check=True,
    )


def make_symlink_source_repo(root: Path) -> Path:
    """Реальная admitted-связь .codex -> .claude (зеркало FWRK-03)."""
    target = root / ".claude/skills/architectural-control-tags"
    target.mkdir(parents=True)
    (target / "SKILL.md").write_text("# Architectural control tags\n", encoding="utf-8")
    link = root / ".codex/skills/architectural-control-tags"
    link.parent.mkdir(parents=True)
    link.symlink_to("../../.claude/skills/architectural-control-tags", target_is_directory=True)
    subprocess.run(["git", "init", "-q", str(root)], check=True)
    subprocess.run(
        ["git", "-C", str(root), "add",
         ".claude/skills/architectural-control-tags/SKILL.md",
         ".codex/skills/architectural-control-tags"],
        check=True,
    )
    return link


def make_sync_meta(root: Path, review_dir: Path, entries: list[dict]) -> dict:
    """Meta формата review.json для sync_sources; workspace — внутри sandbox
    (F-05: sync_sources fail-closed при workspace_path вне review_dir)."""
    workspace = review_dir / "workspace"
    workspace.mkdir(parents=True, exist_ok=True)
    return {
        "review_id": review_dir.name,
        "workspace_path": str(workspace.resolve()),
        "source_root": str(root.resolve()),
        "sources": entries,
    }


def sync_workspace(review_dir: Path) -> Path:
    return review_dir / "workspace"


def copy_symlink_fixture(adapter, mode: str, root: Path, workspace: Path) -> None:
    if mode == "full":
        adapter.copy_full_context(root, workspace)
        return
    source_entries = [
        {
            "source": str(root / ".claude/skills/architectural-control-tags"),
            "dest_rel": ".claude/skills/architectural-control-tags",
        },
        {"source": str(root / ".codex"), "dest_rel": ".codex"},
    ]
    if mode == "focused":
        for entry in source_entries:
            adapter.register_source(
                [],
                Path(entry["source"]),
                root,
                workspace=workspace,
            )
        return
    review_dir = workspace.parent
    adapter.sync_sources(make_sync_meta(root, review_dir, source_entries), review_dir)


def copy_codex_only(adapter, mode: str, root: Path, workspace: Path) -> None:
    """Focused/sync без материализации mapped-цели .claude в назначении."""
    if mode == "focused":
        adapter.register_source([], root / ".codex", root, workspace=workspace)
        return
    review_dir = workspace.parent
    adapter.sync_sources(
        make_sync_meta(root, review_dir, [{"source": str(root / ".codex"), "dest_rel": ".codex"}]),
        review_dir,
    )


def mode_workspace(tmp: Path, tag: str, family: str, mode: str) -> Path:
    """Workspace для режима: sync — внутри .review-sandboxes/<rid>/ (F-05),
    full/focused — обычный каталог (containment там не применим)."""
    if mode == "sync":
        review_dir = tmp / ".review-sandboxes" / f"{tag}-{family}-{mode}"
        workspace = review_dir / "workspace"
        workspace.mkdir(parents=True)
        return workspace
    workspace = tmp / f"workspace-{tag}-{family}-{mode}"
    workspace.mkdir(parents=True)
    return workspace


# ---------- F-02 (а): admitted internal symlink сохраняется на всех путях копирования ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
@pytest.mark.parametrize("mode", COPY_MODES)
def test_git_admitted_internal_directory_symlink_is_preserved_in_every_copy_path(tmp_path, family, mode):
    """Full/focused/sync копируют admitted-ссылку как ссылку, не разыменовывая."""
    adapter = load_adapter(family)
    root = tmp_path / f"source-link-{family}-{mode}"
    root.mkdir()
    source_link = make_symlink_source_repo(root)
    workspace = mode_workspace(tmp_path, "link", family, mode)

    copy_symlink_fixture(adapter, mode, root, workspace)

    copied_link = workspace / ".codex/skills/architectural-control-tags"
    copied_target = workspace / ".claude/skills/architectural-control-tags/SKILL.md"
    assert copied_link.is_symlink()
    assert os.readlink(copied_link) == os.readlink(source_link)
    assert copied_target.is_file()
    assert copied_link.resolve().is_relative_to(workspace.resolve())
    assert copied_link.resolve() == copied_target.parent.resolve()


# ---------- F-02 (а): абсолютные/эскейп-ссылки — детерминированный reject/skip ----------

@pytest.mark.parametrize("link_kind", ["absolute", "escaping"])
def test_absolute_and_escaping_admitted_symlinks_are_deterministically_rejected_or_skipped(tmp_path, link_kind):
    """Unsafe-ссылки не покидают source_root ни на одном адаптере/режиме;
    поведение (reject | skip) детерминировано по всей матрице."""
    outcomes: set[str] = set()
    for family in ALL_FAMILIES:
        adapter = load_adapter(family)
        for mode in COPY_MODES:
            tag = f"unsafe-{link_kind}"
            root = tmp_path / f"source-{tag}-{family}-{mode}"
            root.mkdir()
            outside = tmp_path / f"outside-{tag}-{family}-{mode}"
            outside.mkdir()
            sentinel = outside / "outside-secret.txt"
            sentinel.write_text("must never be copied\n", encoding="utf-8")
            link = root / ".codex/skills/unsafe-link"
            link.parent.mkdir(parents=True)
            lexical_target = str(outside) if link_kind == "absolute" else os.path.relpath(outside, link.parent)
            link.symlink_to(lexical_target, target_is_directory=True)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            subprocess.run(["git", "-C", str(root), "add", ".codex/skills/unsafe-link"], check=True)
            workspace = mode_workspace(tmp_path, tag, family, mode)
            source_entries = [{"source": str(root / ".codex"), "dest_rel": ".codex"}]

            try:
                if mode == "full":
                    adapter.copy_full_context(root, workspace)
                elif mode == "focused":
                    adapter.register_source([], root / ".codex", root, workspace=workspace)
                else:
                    review_dir = workspace.parent
                    adapter.sync_sources(make_sync_meta(root, review_dir, source_entries), review_dir)
            except ValueError as error:
                assert "unsafe" in str(error).lower() or "symlink" in str(error).lower()
                outcomes.add("rejected")
            else:
                outcomes.add("skipped")

            copied_link = workspace / ".codex/skills/unsafe-link"
            assert not copied_link.exists()
            assert not copied_link.is_symlink()
            assert list(workspace.rglob(sentinel.name)) == []
    assert len(outcomes) == 1, (
        f"{link_kind} behavior must be deterministic across adapters/modes: {outcomes}"
    )


# ---------- F-02 (а): relative-ссылка без материализованной цели в назначении ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
@pytest.mark.parametrize("mode", ["focused", "sync"])
def test_safe_relative_symlink_is_never_reproduced_dangling(tmp_path, family, mode):
    """Dangling-ссылка в назначении не воспроизводится ни на одном адаптере:
    вместо неё содержимое материализуется обычным деревом внутри workspace
    (ратифицированный D-6 фикс; исходный инвариант теста — «source safety alone
    не авторизует dangling destination link» — сохранён, механизм обновлён под
    текущее поведение адаптеров)."""
    adapter = load_adapter(family)
    root = tmp_path / f"source-missing-target-{family}-{mode}"
    root.mkdir()
    make_symlink_source_repo(root)
    workspace = mode_workspace(tmp_path, "missing-target", family, mode)

    copy_codex_only(adapter, mode, root, workspace)

    copied_link = workspace / ".codex/skills/architectural-control-tags"
    assert not copied_link.is_symlink()
    # материализация: реальное содержимое зеркала, внутри workspace
    assert copied_link.is_dir()
    assert (copied_link / "SKILL.md").is_file()
    assert copied_link.resolve().is_relative_to(workspace.resolve())
    # mapped-цель .claude в назначении не создаётся
    assert not (workspace / ".claude").exists()


# ---------- F-02 (а): цепочка назначения с эскейпом ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
@pytest.mark.parametrize("mode", ["focused", "sync"])
def test_safe_relative_symlink_is_not_created_when_destination_target_chain_escapes(tmp_path, family, mode):
    """Stale-ссылка в назначении не превращает source-safe ссылку в эскейп."""
    adapter = load_adapter(family)
    root = tmp_path / f"source-stale-target-{family}-{mode}"
    root.mkdir()
    make_symlink_source_repo(root)
    workspace = mode_workspace(tmp_path, "stale-target", family, mode)
    outside = tmp_path / f"outside-stale-target-{family}-{mode}"
    escaped_target = outside / "skills/architectural-control-tags"
    escaped_target.mkdir(parents=True)
    sentinel = escaped_target / "outside-secret.txt"
    sentinel.write_text("must remain untouched\n", encoding="utf-8")
    before = sorted(path.relative_to(outside) for path in outside.rglob("*"))
    (workspace / ".claude").symlink_to(outside, target_is_directory=True)

    try:
        copy_codex_only(adapter, mode, root, workspace)
    except ValueError as error:
        assert "symlink" in str(error).lower()

    copied_link = workspace / ".codex/skills/architectural-control-tags"
    assert not copied_link.exists()
    assert not copied_link.is_symlink()
    assert (workspace / ".claude").is_symlink()
    assert sentinel.read_text(encoding="utf-8") == "must remain untouched\n"
    assert sorted(path.relative_to(outside) for path in outside.rglob("*")) == before


# ---------- F-02 (в): исчезнувший admitted-файл скипается ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
@pytest.mark.parametrize("mode", COPY_MODES)
def test_copy_paths_skip_admitted_file_that_disappears_during_actual_copy(tmp_path, family, mode):
    """Admitted-файл, исчезнувший после enumeration, скипается на всех путях."""
    adapter = load_adapter(family)
    root = tmp_path / f"source-{family}-{mode}"
    root.mkdir()
    make_source_repo(root)
    transient = root / ("transient.md" if mode == "full" else "selected/transient.md")
    transient.write_text("transient\n", encoding="utf-8")
    subprocess.run(
        ["git", "-C", str(root), "add", str(transient.relative_to(root))],
        check=True,
    )
    workspace = mode_workspace(tmp_path, "race", family, mode)
    original_copy2 = shutil.copy2
    copy_calls: list[Path] = []

    def copy2_with_transient_race(src, dst, *args, **kwargs):
        source = Path(src)
        copy_calls.append(source)
        if source == transient:
            source.unlink()
            raise FileNotFoundError(errno.ENOENT, "vanished", str(source))
        return original_copy2(src, dst, *args, **kwargs)

    with patch.object(adapter.shutil, "copy2", side_effect=copy2_with_transient_race):
        if mode == "full":
            adapter.copy_full_context(root, workspace)
        elif mode == "focused":
            adapter.register_source([], root / "selected", root, workspace=workspace)
        else:
            review_dir = workspace.parent
            adapter.sync_sources(
                make_sync_meta(root, review_dir, [{"source": str(root / "selected"), "dest_rel": "selected"}]),
                review_dir,
            )

    copied_root = workspace if mode == "full" else workspace / "selected"
    assert transient in copy_calls
    assert not transient.exists()
    assert not (workspace / transient.relative_to(root)).exists()
    if mode == "full":
        assert (copied_root / "selected/tracked.md").is_file()
        assert (workspace / "root-untracked.md").is_file()
        assert not (workspace / ".codex/tmp/transient.md").exists()
    else:
        assert (copied_root / "tracked.md").is_file()


# ---------- F-02 (г): явно выбранный ignored-файл всё равно материализуется ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_explicit_ignored_focused_file_is_materialized(tmp_path, family):
    """Явно названный focused-файл не теряется из-за .gitignore."""
    adapter = load_adapter(family)
    root = tmp_path / f"source-ignored-focused-{family}"
    root.mkdir()
    (root / ".gitignore").write_text("*.log\n", encoding="utf-8")
    focused = root / "round-B2.log"
    focused.write_text("focused evidence\n", encoding="utf-8")
    subprocess.run(["git", "init", "-q", str(root)], check=True)
    subprocess.run(["git", "-C", str(root), "add", ".gitignore"], check=True)
    subprocess.run(
        ["git", "-C", str(root), "check-ignore", "-q", focused.name], check=True
    )
    workspace = tmp_path / f"workspace-ignored-focused-{family}"
    workspace.mkdir()

    sources: list[dict[str, str]] = []
    adapter.register_source(sources, focused, root, workspace=workspace)

    copied = workspace / focused.name
    assert copied.is_file()
    assert copied.read_text(encoding="utf-8") == "focused evidence\n"
    assert sources == [{
        "source": str(focused.resolve()),
        "dest_rel": focused.name,
    }]


# ---------- F-02 (в): unrelated IO error пропагируется ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_copy_paths_propagate_unrelated_io_error_from_actual_copy(tmp_path, family):
    """Толерантен только исчезнувший admitted source; прочие ошибки видимы."""
    adapter = load_adapter(family)
    root = tmp_path / f"source-io-{family}"
    root.mkdir()
    make_source_repo(root)
    workspace = tmp_path / f"workspace-io-{family}"
    failing_source = root / "selected/tracked.md"
    original_copy2 = shutil.copy2

    def copy2_with_io_error(src, dst, *args, **kwargs):
        if Path(src) == failing_source:
            raise OSError(errno.EIO, "disk error", str(src))
        return original_copy2(src, dst, *args, **kwargs)

    with patch.object(adapter.shutil, "copy2", side_effect=copy2_with_io_error):
        with pytest.raises(OSError, match="disk error"):
            adapter.register_source([], root / "selected", root, workspace=workspace)


# ---------- F-02 (б): cmd_start rollback в stable lock tombstone ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_start_copy_failure_leaves_only_stable_invocation_lock_tombstone(tmp_path, monkeypatch, family):
    """Неудачный start удаляет payload, но сохраняет inode lock для координации."""
    adapter = load_adapter(family)
    monkeypatch.chdir(tmp_path)
    review_id = f"copy-failure-{family}"
    args = SimpleNamespace(review_id=review_id, timeout_sec=1, full_context=True, paths=[])
    with patch.object(adapter, "copy_full_context", side_effect=FileNotFoundError("transient")):
        with pytest.raises(FileNotFoundError):
            adapter.cmd_start(args)
    review_dir = tmp_path / ".review-sandboxes" / review_id
    assert review_dir.is_dir()
    assert [entry.name for entry in review_dir.iterdir()] == ["invocation.lock"]


@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_start_preserves_copy_failure_when_rollback_cleanup_fails(tmp_path, monkeypatch, family):
    """Диагностика rollback не подменяет ошибку, из-за которой start упал."""
    adapter = load_adapter(family)
    monkeypatch.chdir(tmp_path)
    args = SimpleNamespace(
        review_id=f"rollback-failure-{family}", timeout_sec=1, full_context=True, paths=[],
    )
    start_error = FileNotFoundError(errno.ENOENT, "copy vanished", "transient.md")
    cleanup_error = OSError(errno.EIO, "rollback cleanup failed")
    with patch.object(adapter, "copy_full_context", side_effect=start_error):
        with patch.object(adapter.shutil, "rmtree", side_effect=cleanup_error):
            with pytest.raises(FileNotFoundError) as raised:
                adapter.cmd_start(args)
    assert raised.value is start_error


# ---------- F-05: sync_sources fail-closed при записи вне sandbox ----------

@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_sync_sources_rejects_workspace_outside_review_sandbox(tmp_path, family):
    """F-05: подмена workspace_path в meta на внешний путь → отказ, записи нет."""
    adapter = load_adapter(family)
    root = tmp_path / f"source-escape-{family}"
    root.mkdir()
    make_source_repo(root)
    review_dir = tmp_path / ".review-sandboxes" / f"escape-{family}"
    review_dir.mkdir(parents=True)
    outside = tmp_path / f"outside-workspace-{family}"
    outside.mkdir()
    meta = {
        "review_id": review_dir.name,
        "workspace_path": str(outside.resolve()),  # подмена: вне review_dir
        "source_root": str(root.resolve()),
        "sources": [{"source": str(root / "selected"), "dest_rel": "selected"}],
    }
    with pytest.raises(ValueError, match="outside|вне|escape"):
        adapter.sync_sources(meta, review_dir)
    assert list(outside.iterdir()) == []


@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_sync_sources_rejects_dest_rel_escape(tmp_path, family):
    """F-05: dest_rel с '..' из подменённой meta → отказ, записи вне workspace нет."""
    adapter = load_adapter(family)
    root = tmp_path / f"source-destrel-{family}"
    root.mkdir()
    make_source_repo(root)
    review_dir = tmp_path / ".review-sandboxes" / f"destrel-{family}"
    meta = make_sync_meta(
        root, review_dir,
        [{"source": str(root / "selected/tracked.md"), "dest_rel": "../escaped.md"}],
    )
    with pytest.raises(ValueError, match="outside|вне|escape"):
        adapter.sync_sources(meta, review_dir)
    assert not (review_dir / "escaped.md").exists()


@pytest.mark.parametrize("family", ALL_FAMILIES)
def test_cmd_sync_rejects_tampered_review_json(tmp_path, monkeypatch, family):
    """F-05: подменённый review.json (workspace_path вне sandbox) → cmd_sync
    отказывает fail-closed; внешний каталог не тронут."""
    adapter = load_adapter(family)
    monkeypatch.chdir(tmp_path)
    review_id = f"tampered-{family}"
    review_dir = tmp_path / ".review-sandboxes" / review_id
    review_dir.mkdir(parents=True)
    outside = tmp_path / f"outside-cmdsync-{family}"
    outside.mkdir()
    (review_dir / "review.json").write_text(
        '{"review_id": "%s", "workspace_path": "%s", "source_root": "%s", '
        '"sources": [], "session_id": "s", "brief": ""}'
        % (review_id, str(outside.resolve()).replace("\\", "\\\\"), str(tmp_path.resolve()).replace("\\", "\\\\")),
        encoding="utf-8",
    )
    with pytest.raises(ValueError, match="outside|вне|escape"):
        adapter.cmd_sync(SimpleNamespace(review_id=review_id))
    assert list(outside.iterdir()) == []
