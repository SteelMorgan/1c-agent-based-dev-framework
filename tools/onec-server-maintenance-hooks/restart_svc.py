#!/usr/bin/env python3
"""Tiny HTTP webhook for whitelisted 1C VM maintenance operations.

Listens on $LISTEN_ADDR:$LISTEN_PORT. Authenticates with a Bearer token read
from $TOKEN_FILE. Accepts POST /restart/<container-name>; container name must
be in the whitelist defined by $ALLOWED_CONTAINERS (comma-separated).

No third-party dependencies — stdlib only.
"""
from __future__ import annotations

import hmac
import json
import logging
import os
import re
import subprocess
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LISTEN_ADDR = os.environ.get("LISTEN_ADDR", "0.0.0.0")
LISTEN_PORT = int(os.environ.get("LISTEN_PORT", "8765"))
TOKEN_FILE = os.environ.get("TOKEN_FILE", "/etc/onec-restart/token")
ALLOWED = [
    c.strip()
    for c in os.environ.get("ALLOWED_CONTAINERS", "onec-server,onec-web").split(",")
    if c.strip()
]
DOCKER_BIN = os.environ.get("DOCKER_BIN", "/usr/bin/docker")
RESTART_TIMEOUT = int(os.environ.get("RESTART_TIMEOUT", "60"))
CACHE_TIMEOUT = int(os.environ.get("CACHE_TIMEOUT", "30"))

NAME_RE = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$")
COMPONENT_RE = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9_.?*-]{0,127}$")

log = logging.getLogger("onec-restart")


def load_token() -> str:
    with open(TOKEN_FILE, "r", encoding="utf-8") as fh:
        token = fh.read().strip()
    if not token:
        raise RuntimeError(f"empty token in {TOKEN_FILE}")
    return token


def parse_bool(value: object, default: bool) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        return value.strip().lower() in {"1", "true", "yes", "on"}
    return bool(value)


def valid_component_mask(value: str) -> bool:
    if not COMPONENT_RE.match(value):
        return False
    if "/" in value or "\\" in value:
        return False
    if value.strip("*.?") == "":
        return False
    return True


def read_json_body(handler: BaseHTTPRequestHandler) -> dict:
    length = int(handler.headers.get("Content-Length", "0") or "0")
    if length == 0:
        return {}
    if length > 65536:
        raise ValueError("request body is too large")
    raw = handler.rfile.read(length)
    try:
        payload = json.loads(raw.decode("utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"invalid json: {exc.msg}") from exc
    if not isinstance(payload, dict):
        raise ValueError("json body must be an object")
    return payload


def clear_external_component_cache(container: str, component: str, dry_run: bool) -> dict:
    script = r'''
set -eu
component="$1"
dry_run="$2"
restart_required=0

for maps in /proc/[0-9]*/maps; do
  [ -r "$maps" ] || continue
  while IFS= read -r line; do
    case "$line" in
      *$component*)
        pid="${maps#/proc/}"
        pid="${pid%/maps}"
        comm="$(cat "/proc/$pid/comm" 2>/dev/null || true)"
        printf 'LOADED|%s|%s|%s\n' "$pid" "$comm" "$line"
        restart_required=1
        ;;
    esac
  done < "$maps"
done

find /tmp -maxdepth 1 -type f -name 'v8_*.so' -print 2>/dev/null | while IFS= read -r file; do
  base="${file##*/}"
  case "$base" in
    *$component*)
      size="$(stat -c %s "$file" 2>/dev/null || echo '')"
      printf 'MATCH|%s|%s\n' "$file" "$size"
      if [ "$dry_run" = "0" ]; then
        rm -f -- "$file"
        printf 'DELETED|%s\n' "$file"
      fi
      ;;
  esac
done

printf 'RESTART_REQUIRED|%s\n' "$restart_required"
'''
    proc = subprocess.run(
        [DOCKER_BIN, "exec", "-i", container, "sh", "-s", "--", component, "1" if dry_run else "0"],
        input=script,
        capture_output=True,
        text=True,
        timeout=CACHE_TIMEOUT,
    )

    matches = []
    deleted = []
    loaded = []
    restart_required = False
    for line in proc.stdout.splitlines():
        parts = line.split("|", 3)
        if not parts:
            continue
        kind = parts[0]
        if kind == "MATCH" and len(parts) >= 3:
            matches.append({"path": parts[1], "size": parts[2]})
        elif kind == "DELETED" and len(parts) >= 2:
            deleted.append(parts[1])
        elif kind == "LOADED" and len(parts) >= 4:
            loaded.append({"pid": parts[1], "process": parts[2], "mapping": parts[3]})
        elif kind == "RESTART_REQUIRED" and len(parts) >= 2:
            restart_required = parts[1] == "1"

    result = {
        "status": "dry_run" if dry_run else "cleared",
        "container": container,
        "component": component,
        "dry_run": dry_run,
        "matches": matches,
        "deleted": deleted,
        "loaded": loaded,
        "restart_required": restart_required,
    }
    if proc.returncode != 0:
        result["error"] = "cache clear command failed"
        result["stderr"] = proc.stderr.strip()
    return result


class Handler(BaseHTTPRequestHandler):
    server_version = "onec-restart/1.0"
    expected_token: str = ""

    def log_message(self, fmt: str, *args) -> None:
        log.info("%s - %s", self.client_address[0], fmt % args)

    def _send(self, code: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _check_auth(self) -> bool:
        header = self.headers.get("Authorization", "")
        prefix = "Bearer "
        if not header.startswith(prefix):
            return False
        return hmac.compare_digest(header[len(prefix):].strip(), self.expected_token)

    def do_GET(self) -> None:
        if self.path == "/health":
            self._send(
                200,
                {
                    "status": "ok",
                    "allowed": ALLOWED,
                    "endpoints": ["/restart/<container>", "/external-components/cache/clear"],
                },
            )
            return
        self._send(404, {"error": "not found"})

    def do_POST(self) -> None:
        if not self._check_auth():
            self._send(401, {"error": "unauthorized"})
            return

        if self.path == "/external-components/cache/clear":
            try:
                payload = read_json_body(self)
            except ValueError as exc:
                self._send(400, {"error": str(exc)})
                return

            container = str(payload.get("container", "onec-server")).strip()
            component = str(payload.get("component", "")).strip()
            dry_run = parse_bool(payload.get("dry_run"), True)

            if not NAME_RE.match(container):
                self._send(400, {"error": "invalid container name"})
                return
            if container not in ALLOWED:
                self._send(403, {"error": "container not allowed", "allowed": ALLOWED})
                return
            if not valid_component_mask(component):
                self._send(400, {"error": "invalid component mask"})
                return

            log.info(
                "clearing external component cache container=%s component=%s dry_run=%s",
                container,
                component,
                dry_run,
            )
            try:
                result = clear_external_component_cache(container, component, dry_run)
            except subprocess.TimeoutExpired:
                self._send(
                    504,
                    {
                        "error": "external component cache clear timed out",
                        "container": container,
                    },
                )
                return

            self._send(500 if "error" in result else 200, result)
            return

        if not self.path.startswith("/restart/"):
            self._send(404, {"error": "not found"})
            return

        name = self.path[len("/restart/"):]
        if not NAME_RE.match(name):
            self._send(400, {"error": "invalid container name"})
            return
        if name not in ALLOWED:
            log.warning("rejected restart for %s (not in whitelist)", name)
            self._send(403, {"error": "container not allowed", "allowed": ALLOWED})
            return

        log.info("restarting container %s", name)
        try:
            proc = subprocess.run(
                [DOCKER_BIN, "restart", name],
                capture_output=True,
                text=True,
                timeout=RESTART_TIMEOUT,
            )
        except subprocess.TimeoutExpired:
            log.error("docker restart %s timed out", name)
            self._send(504, {"error": "docker restart timed out", "container": name})
            return

        if proc.returncode != 0:
            log.error("docker restart %s failed: %s", name, proc.stderr.strip())
            self._send(
                500,
                {
                    "error": "docker restart failed",
                    "container": name,
                    "stderr": proc.stderr.strip(),
                },
            )
            return

        self._send(200, {"status": "restarted", "container": name})


def main() -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
        stream=sys.stdout,
    )
    Handler.expected_token = load_token()
    log.info(
        "listening on %s:%d (allowed=%s)",
        LISTEN_ADDR, LISTEN_PORT, ",".join(ALLOWED),
    )
    server = ThreadingHTTPServer((LISTEN_ADDR, LISTEN_PORT), Handler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        log.info("shutting down")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
