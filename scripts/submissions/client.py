#!/usr/bin/env python3
"""Resumable pilot client; credentials come only from WILDBOOK_SUBMISSIONS_TOKEN."""
import argparse
import hashlib
import fcntl
import tempfile
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


class ApiError(Exception):
    def __init__(self, status, body, retry_after=None):
        self.status, self.body, self.retry_after = status, body, retry_after
        super().__init__(f"HTTP {status}: {body}")


class Client:
    def __init__(self, base, token):
        parts = urllib.parse.urlsplit(base)
        if parts.scheme != "https" and not (parts.scheme == "http" and parts.hostname in {"localhost", "127.0.0.1"}):
            raise ValueError("Use HTTPS (HTTP is allowed only on localhost)")
        if parts.username or parts.password or parts.query or parts.fragment:
            raise ValueError("Base URL must not contain credentials, query or fragment")
        self.base, self.token = base.rstrip("/"), token
        # Never forward a bearer credential to a redirect target.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl):
                return None
        self.http = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def request(self, method, path, data=None, headers=None):
        hdr = {"Authorization": "Bearer " + self.token, "Accept": "application/json"}
        if headers:
            hdr.update(headers)
        if isinstance(data, dict):
            data = json.dumps(data, separators=(",", ":")).encode()
            hdr["Content-Type"] = "application/json"
        req = urllib.request.Request(self.base + path, data=data, headers=hdr, method=method)
        try:
            with self.http.open(req, timeout=150) as response:
                body = response.read()
                return json.loads(body) if body else {}
        except urllib.error.HTTPError as ex:
            raw = ex.read(65536).decode("utf-8", errors="replace")
            raise ApiError(ex.code, raw, ex.headers.get("Retry-After")) from None


def save(path, state):
    fd, temporary = tempfile.mkstemp(prefix=path.name + ".", dir=path.parent)
    with os.fdopen(fd, "w") as out:
        json.dump(state, out, indent=2)
        out.flush()
        os.fsync(out.fileno())
    os.replace(temporary, path)
    directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)


def retry_safe(call, wait_seconds=300):
    deadline, attempt = time.monotonic() + wait_seconds, 0
    while True:
        delay = min(30, 2 ** min(attempt, 5))
        try:
            return call()
        except ApiError as ex:
            if ex.status not in {429, 500, 502, 503, 504}:
                raise
            if ex.retry_after and ex.retry_after.isdigit():
                delay = min(60, max(1, int(ex.retry_after)))
            if time.monotonic() + delay >= deadline:
                raise
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            if time.monotonic() + delay >= deadline:
                raise
        time.sleep(delay)
        attempt += 1


def upload(client, route, file, max_bytes):
    if file.stat().st_size > max_bytes:
        raise ValueError(f"File exceeds installation limit: {file.name}")
    content = file.read_bytes()
    digest = hashlib.sha256(content).hexdigest()
    boundary = "wildbook-" + uuid.uuid4().hex
    if any(c in file.name for c in '\r\n"\\') or not file.name.isascii():
        raise ValueError("Use safe ASCII filenames")
    payload = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{file.name}"\r\n'
               'Content-Type: application/octet-stream\r\n\r\n').encode() + content + f"\r\n--{boundary}--\r\n".encode()
    deadline = time.monotonic() + 300
    while True:
        manifest = retry_safe(lambda: client.request("GET", route + "/files"))
        for entry in manifest["files"]:
            if entry["name"] == file.name:
                if entry["sha256"] != digest:
                    raise ValueError(f"Different content already uploaded as {file.name}")
                return
        delay = 5
        try:
            client.request("POST", route + "/files", payload,
                           {"Content-Type": "multipart/form-data; boundary=" + boundary,
                            "If-Match": f'"{manifest["revision"]}"'})
            return
        except ApiError as ex:
            if ex.status not in {412, 429, 500, 502, 503, 504}:
                raise
            if ex.retry_after and ex.retry_after.isdigit():
                delay = min(60, max(1, int(ex.retry_after)))
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            pass
        if time.monotonic() + delay >= deadline:
            raise RuntimeError("Upload outcome unresolved; rerun with this state file")
        time.sleep(delay)


def normalize_numbers(value):
    if isinstance(value, dict):
        return {key: normalize_numbers(item) for key, item in value.items()}
    if isinstance(value, list):
        return [normalize_numbers(item) for item in value]
    if type(value) is float:
        if not __import__("math").isfinite(value):
            raise ValueError("JSON numbers must be finite")
        return int(value) if value.is_integer() else value
    return value  # bool stays distinct from int


def digest(value):
    return hashlib.sha256(json.dumps(normalize_numbers(value), sort_keys=True).encode()).hexdigest()


def run(args, client):
    state = json.loads(args.state.read_text()) if args.state.exists() else None
    if state and (state["baseUrl"] != client.base or state["source"] != args.source):
        raise ValueError("State belongs to a different source or installation")
    root = "/api/v3/submissions"
    if args.cancel:
        if not state or "id" not in state:
            raise ValueError("No saved submission to cancel")
        route = root + "/" + state["id"]
        current = retry_safe(lambda: client.request("GET", route))
        retry_safe(lambda: client.request("DELETE", route, headers={"If-Match": f'"{current["revision"]}"'}))
        state["cancelled"] = True
        save(args.state, state)
        print("Cancelled", state["id"])
        return 0
    if not args.rows or not args.media_dir:
        raise ValueError("--rows and --media-dir are required except for --cancel")
    rows = json.loads(args.rows.read_text())
    if not isinstance(rows, dict) or not isinstance(rows.get("rows"), list) or not rows["rows"]:
        raise ValueError("Input must be an object containing a nonempty rows array")
    names = set()
    for row in rows["rows"]:
        if not isinstance(row, dict) or not isinstance(row.get("fields"), dict) or not isinstance(row.get("clientRowId"), str):
            raise ValueError("Each row needs clientRowId and a fields object")
        for key, name in row["fields"].items():
            if key.startswith("Encounter.mediaAsset"):
                if not isinstance(name, str) or Path(name).name != name or name in {".", ".."}:
                    raise ValueError("Media references must be filenames")
                names.add(name)
    rows_digest = digest(rows)
    if state is None:
        state = {"createKey": str(uuid.uuid4()), "rowsDigest": rows_digest, "baseUrl": client.base, "source": args.source}
    if state.get("cancelled"):
        raise ValueError("Submission was cancelled; use a new state file for a new batch")
    if "createRequest" not in state:
        state["createRequest"] = {"contractVersion": "1", "source": {"name": args.source}}
        # Pre-mode client state represented import-only, even if its first request never arrived.
        state["createRequest"]["processing"] = {"mode": "import-only" if args.state.exists() else (args.processing_mode or "detect-and-identify")}
    if args.processing_mode and state["createRequest"].get("processing", {}).get("mode") != args.processing_mode:
        raise ValueError("Processing mode is fixed for saved state; do not change it on resume")
    save(args.state, state)
    if "id" not in state:
        caps = retry_safe(lambda: client.request("GET", root + "/capabilities"))
        if not caps["admissionEnabled"] or not caps.get("stagingAvailable", False):
            raise ValueError("New intake is unavailable; retain the state and try later")
        created = retry_safe(lambda: client.request("POST", root,
            state["createRequest"], {"Idempotency-Key": state["createKey"]}))
        state["id"] = created["id"]
        save(args.state, state)
    route = root + "/" + state["id"]
    if args.reset_commit:
        current = retry_safe(lambda: client.request("GET", route))
        if current["state"] not in {"draft", "validated"} or "operationId" in current:
            raise ValueError("Cannot reset an accepted execution; inspect status/results")
        for key in ["commitRequest", "commitKey", "commitRevision", "operationId"]:
            state.pop(key, None)
        save(args.state, state)
    if "commitRequest" in state and state["rowsDigest"] != rows_digest:
        raise ValueError("Commit intent is frozen; inspect status, then use --reset-commit only if it was never accepted")
    if "commitRequest" not in state:
        caps = retry_safe(lambda: client.request("GET", root + "/capabilities"))
        if not caps["admissionEnabled"] or not caps.get("stagingAvailable", False):
            raise ValueError("Intake is unavailable; retain the state and try later")
        for name in sorted(names):
            upload(client, route, args.media_dir / name, caps["limits"]["maxFileBytes"])
        stored = retry_safe(lambda: client.request("GET", route + "/rows"))
        if digest({"rows": stored["rows"]}) != rows_digest:
            if stored["rows"] and digest({"rows": stored["rows"]}) != state["rowsDigest"]:
                raise ValueError("Draft rows were changed by another client; inspect before replacing")
            try:
                client.request("PUT", route + "/rows", rows, {"If-Match": f'"{stored["revision"]}"'})
            except ApiError as ex:
                if ex.status < 500 and ex.status != 429:
                    raise  # retain actionable validation/conflict response
                check = retry_safe(lambda: client.request("GET", route + "/rows"))
                if digest({"rows": check["rows"]}) != rows_digest:
                    raise
            except (urllib.error.URLError, TimeoutError, ConnectionError):
                check = retry_safe(lambda: client.request("GET", route + "/rows"))
                if digest({"rows": check["rows"]}) != rows_digest:
                    raise RuntimeError("Rows update unresolved; rerun with this state file") from None
        confirmed = retry_safe(lambda: client.request("GET", route + "/rows"))
        server_digest = digest({"rows": confirmed["rows"]})
        if server_digest != rows_digest:
            raise ValueError("Rows changed before validation; inspect the draft")
        state["rowsDigest"] = server_digest
        save(args.state, state)
        current = retry_safe(lambda: client.request("GET", route))
        report = retry_safe(lambda: client.request("POST", route + "/validate", {}, {"If-Match": f'"{current["revision"]}"'}))
        print(json.dumps({"submissionId": state["id"], "valid": report["valid"], "errors": report["errors"]}, indent=2))
        if not report["valid"] or not args.commit:
            return 0 if report["valid"] else 2
        if not caps["commitEnabled"]:
            raise ValueError("Commit is disabled; draft is retained")
        state["commitRequest"] = {"validationId": report["id"]}
        state["commitKey"] = str(uuid.uuid4())
        state["commitRevision"] = report["revision"]
        save(args.state, state)
    status = retry_safe(lambda: client.request("GET", route))
    if status["state"] in {"draft", "validated"}:
        if not args.commit:
            raise ValueError("Commit intent is saved; use --commit to resume it")
        def commit_once():
            try:
                return client.request("POST", route + "/commit", state["commitRequest"],
                    {"Idempotency-Key": state["commitKey"], "If-Match": f'"{state["commitRevision"]}"'})
            except (ApiError, urllib.error.URLError, TimeoutError, ConnectionError):
                observed = retry_safe(lambda: client.request("GET", route))
                if "operationId" in observed:
                    return observed
                raise
        accepted = retry_safe(commit_once)
        state["operationId"] = accepted["operationId"]
        save(args.state, state)
    deadline, delay = time.monotonic() + args.poll_seconds, 2
    while True:
        status = retry_safe(lambda: client.request("GET", route))
        if status["state"] in {"imported", "failed", "needs_reconciliation", "cancelled", "expired"}:
            break
        if time.monotonic() >= deadline:
            print("Still processing; rerun with the same state file.")
            return 3
        time.sleep(delay)
        delay = min(30, delay * 2)
    page_path = route + "/results"
    while True:
        page = retry_safe(lambda: client.request("GET", page_path))
        print(json.dumps(page, indent=2))
        if "nextCursor" not in page:
            break
        page_path = route + "/results?cursor=" + urllib.parse.quote(page["nextCursor"])
    return 0 if status["state"] == "imported" else 2


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--rows", type=Path)
    parser.add_argument("--media-dir", type=Path)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--source", default="submissions-reference-client")
    parser.add_argument("--processing-mode", choices=["detect-and-identify", "import-only"], help="New submissions default to detect-and-identify; mode is fixed after creation")
    parser.add_argument("--commit", action="store_true")
    parser.add_argument("--cancel", action="store_true", help="Cancel an editable saved draft")
    parser.add_argument("--reset-commit", action="store_true", help="Clear unaccepted commit intent after checking server state")
    parser.add_argument("--poll-seconds", type=int, default=900)
    args = parser.parse_args()
    token = os.environ.get("WILDBOOK_SUBMISSIONS_TOKEN")
    if not token:
        parser.error("Set WILDBOOK_SUBMISSIONS_TOKEN to an explicitly scoped bearer token")
    transport = Client(args.base_url, token)
    lock_fd = os.open(str(args.state) + ".lock", os.O_WRONLY | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    try:
        try:
            fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError("Another client is using this state file") from None
        return run(args, transport)
    finally:
        os.close(lock_fd)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ApiError, ValueError, RuntimeError, OSError) as error:
        print(str(error), file=__import__("sys").stderr)
        raise SystemExit(2)
