"""HC-FORK — provider-native fork contract для Claude/Codex/Kimi adapters."""
from __future__ import annotations

import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote

import pytest

import adapter_contract as ac
from tests.conftest import ADAPTER_PATHS

SNAPSHOT_DIGEST = "sha256:" + "b" * 64


@pytest.fixture()
def native_fork_provider_stub(stub_env, monkeypatch):
    """Provider-native fork routes absent from the ordinary CLI reply stub."""
    children: list[dict] = []

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, format, *args):  # noqa: A003 - stdlib signature
            return

        def _json(self, payload):
            body = json.dumps(payload).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):  # noqa: N802 - stdlib callback
            if self.path == "/api/v1/meta":
                self._json({"provider": "kimi-stub"})
            elif self.path == "/api/v1/sessions":
                self._json({"sessions": children})
            else:
                self.send_error(404)

        def do_POST(self):  # noqa: N802 - stdlib callback
            prefix, suffix = "/api/v1/sessions/", ":fork"
            if not (self.path.startswith(prefix) and self.path.endswith(suffix)):
                self.send_error(404)
                return
            if os.environ.get("STUB_MODE") == "fork-unsupported:kimi":
                self.send_error(501, "native fork unsupported")
                return
            parent = unquote(self.path[len(prefix):-len(suffix)])
            child = {"id": f"kimi-child-{len(children) + 1}", "parent_session_id": parent}
            children.append(child)
            self._json({"data": child})

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    kimi_home = stub_env["cwd"] / "kimi-home"
    kimi_home.mkdir()
    token = kimi_home / "server.token"
    token.write_text("stub-token", encoding="utf-8")
    token.chmod(0o600)
    monkeypatch.setenv("KIMI_CODE_HOME", str(kimi_home))
    monkeypatch.setenv("KIMI_WEB_ORIGIN", f"http://127.0.0.1:{server.server_port}")
    try:
        yield
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


@pytest.mark.parametrize("family", ["claude", "codex", "kimi"])
def test_native_fork_creates_distinct_child_session_and_cleanup(
    stub_env, native_fork_provider_stub, family,
):
    adapter = str(ADAPTER_PATHS[family])
    parent = ac.start_participant(
        adapter, "Parent snapshot", [], stub_env["cwd"], timeout_sec=30,
    )
    assert parent.ok, parent.error
    parent_meta = json.loads(
        (stub_env["cwd"] / ac.REVIEW_ROOT / parent.review_id / "review.json")
        .read_text(encoding="utf-8")
    )
    provider_checkpoint_id = parent_meta["provider_checkpoint_id"]

    fork_kwargs = {
        "operation_id": f"op-{family}-happy",
        "provider_checkpoint_id": provider_checkpoint_id,
        "snapshot_digest": SNAPSHOT_DIGEST,
        "timeout_sec": 30,
    }
    if family == "claude":
        fork_kwargs["question"] = "Проверить child task в native fork"
    child = ac.fork_participant(
        adapter, parent.review_id, f"{family}-child-review",
        stub_env["cwd"], **fork_kwargs,
    )
    assert child.ok, child.error
    assert child.review_id != parent.review_id
    assert child.session_id
    assert child.session_id != parent.session_id
    assert child.prompt_consumed is (family == "claude")

    child_meta = json.loads(
        (stub_env["cwd"] / ac.REVIEW_ROOT / child.review_id / "review.json")
        .read_text(encoding="utf-8")
    )
    lineage = child_meta.get("lineage", child_meta)
    assert lineage["parent_review_id"] == parent.review_id
    assert lineage["parent_session_id"] == parent.session_id
    if family == "claude":
        assert lineage["parent_provider_checkpoint_id"] == provider_checkpoint_id
        assert child_meta["provider_checkpoint_id"] != provider_checkpoint_id
        assert child_meta["provider_checkpoint_id"] == child_meta["provider_turn_id"]
    else:
        assert child_meta["provider_checkpoint_id"] == provider_checkpoint_id
    assert lineage.get("snapshot_digest", child_meta.get("snapshot_digest")) == SNAPSHOT_DIGEST
    assert (
        child_meta.get("fork_route") == "native"
        or lineage.get("kind") == "native_fork"
    )

    assert ac.close_participant(adapter, child.review_id, stub_env["cwd"])
    child_dir = stub_env["cwd"] / ac.REVIEW_ROOT / child.review_id
    assert child_dir.is_dir()
    assert [entry.name for entry in child_dir.iterdir()] == ["invocation.lock"]
    assert ac.close_participant(adapter, child.review_id, stub_env["cwd"]), (
        "close retry over lock-only tombstone must be idempotent"
    )
    assert [entry.name for entry in child_dir.iterdir()] == ["invocation.lock"]
    parent_dir = stub_env["cwd"] / ac.REVIEW_ROOT / parent.review_id
    assert (parent_dir / "review.json").exists(), "child close must not tombstone parent"
    assert ac.close_participant(adapter, parent.review_id, stub_env["cwd"])
    assert parent_dir.is_dir()
    assert [entry.name for entry in parent_dir.iterdir()] == ["invocation.lock"]
    assert ac.close_participant(adapter, parent.review_id, stub_env["cwd"])


@pytest.mark.parametrize("family", ["claude", "codex", "kimi"])
def test_native_fork_failure_has_no_synthetic_start_fallback(
    stub_env, native_fork_provider_stub, set_stub_mode, family,
):
    adapter = str(ADAPTER_PATHS[family])
    parent = ac.start_participant(
        adapter, "Parent snapshot", [], stub_env["cwd"], timeout_sec=30,
    )
    assert parent.ok, parent.error
    parent_meta = json.loads(
        (stub_env["cwd"] / ac.REVIEW_ROOT / parent.review_id / "review.json")
        .read_text(encoding="utf-8")
    )
    set_stub_mode(f"fork-unsupported:{family}")

    child = ac.fork_participant(
        adapter, parent.review_id, f"{family}-child-review",
        stub_env["cwd"], timeout_sec=30,
        operation_id=f"op-{family}-failure",
        provider_checkpoint_id=parent_meta["provider_checkpoint_id"],
        snapshot_digest=SNAPSHOT_DIGEST,
    )
    assert not child.ok
    assert child.kind == "error"

    invocations_path = stub_env["state_dir"] / "invocations.jsonl"
    invocations = [
        json.loads(line) for line in invocations_path.read_text(encoding="utf-8").splitlines()
        if line.strip() and json.loads(line)["cli"] == family
    ]
    # После native fork failure запрещено создавать независимую synthetic session.
    post_parent = invocations[1:]
    probes = [
        item for item in post_parent
        if any(flag in item["argv"] for flag in ("--version", "-V", "--help", "-h", "doctor"))
    ]
    non_probe = [item for item in post_parent if item not in probes]
    # Capability/version probes допустимы. После неуспешного adapter `fork`
    # запрещён только новый provider create/start без native-fork marker.
    synthetic_start = [
        item for item in non_probe
        if not any(marker in item["argv"] for marker in (
            "--fork-session", "thread/fork", "fork", "app-server",
        ))
    ]
    assert synthetic_start == []
    ac.close_participant(adapter, parent.review_id, stub_env["cwd"])


def test_claude_fork_fails_closed_when_parent_transcript_is_absent(
    stub_env, native_fork_provider_stub,
):
    """Страж переноса линии родителя остаётся fail-closed.

    Парный тест к happy-path выше: подделка воспроизводит приватный контракт
    хранения сессий (транскрипт лежит там, где его ищет CLI), поэтому форк
    проходит. Убираем ровно транскрипт — и форк ОБЯЗАН отказать с внятной
    причиной, без synthetic-старта. Без этой пары зелень happy-path ничего не
    доказывает: она была бы совместима и с ослабленным стражем.
    """
    adapter = str(ADAPTER_PATHS["claude"])
    parent = ac.start_participant(
        adapter, "Parent snapshot", [], stub_env["cwd"], timeout_sec=30,
    )
    assert parent.ok, parent.error
    parent_meta = json.loads(
        (stub_env["cwd"] / ac.REVIEW_ROOT / parent.review_id / "review.json")
        .read_text(encoding="utf-8")
    )
    transcripts = list(
        (stub_env["claude_config"] / "projects").rglob(f"{parent.session_id}.jsonl"))
    assert transcripts, "подделка не записала транскрипт родителя — тест бессмыслен"
    for path in transcripts:
        path.unlink()

    child = ac.fork_participant(
        adapter, parent.review_id, "claude-child-no-transcript", stub_env["cwd"],
        operation_id="op-claude-no-transcript",
        provider_checkpoint_id=parent_meta["provider_checkpoint_id"],
        snapshot_digest=SNAPSHOT_DIGEST, question="Проверить страж", timeout_sec=30,
    )
    assert not child.ok, "форк без транскрипта родителя прошёл — страж ослаблен"
    assert "не найден транскрипт родительской сессии" in (child.error or ""), (
        f"отказ не называет причину переноса линии: {child.error!r}"
    )
    child_dir = stub_env["cwd"] / ac.REVIEW_ROOT / "claude-child-no-transcript"
    assert not (child_dir / "review.json").exists(), (
        "после отказа стража остался материализованный ребёнок (synthetic start)"
    )


def test_kimi_capability_schema_is_exact(stub_env, native_fork_provider_stub):
    capability = ac.probe_fork_capability(
        str(ADAPTER_PATHS["kimi"]), stub_env["cwd"], timeout_sec=10,
    )
    assert set(capability) == {
        "supported", "route", "min_cli_version",
        "exact_checkpoint", "automation_safe",
    }
    assert capability == {
        "supported": True,
        "route": "kimi_web_rest_fork",
        "min_cli_version": "0.31.0",
        "exact_checkpoint": True,
        "automation_safe": True,
    }


@pytest.mark.parametrize("family", ["claude", "codex", "kimi"])
def test_fork_rejects_symlink_child_sandbox_without_touching_external(
    stub_env, native_fork_provider_stub, family,
):
    adapter = str(ADAPTER_PATHS[family])
    parent = ac.start_participant(adapter, "Parent", [], stub_env["cwd"], timeout_sec=30)
    assert parent.ok, parent.error
    parent_meta = json.loads(
        (stub_env["cwd"] / ac.REVIEW_ROOT / parent.review_id / "review.json")
        .read_text(encoding="utf-8")
    )
    external = stub_env["cwd"] / "external"
    external.mkdir()
    sentinel = external / "sentinel"
    sentinel.write_text("keep", encoding="utf-8")
    root = stub_env["cwd"] / ac.REVIEW_ROOT
    (root / f"{family}-symlink-child").symlink_to(external, target_is_directory=True)
    child = ac.fork_participant(
        adapter, parent.review_id, f"{family}-symlink-child", stub_env["cwd"],
        provider_checkpoint_id=parent_meta["provider_checkpoint_id"],
        snapshot_digest=SNAPSHOT_DIGEST, operation_id=f"op-{family}", timeout_sec=30,
    )
    assert not child.ok
    assert sentinel.read_text(encoding="utf-8") == "keep"
