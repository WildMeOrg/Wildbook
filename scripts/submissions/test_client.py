import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
import hashlib

spec = importlib.util.spec_from_file_location("client", Path(__file__).with_name("client.py"))
client = importlib.util.module_from_spec(spec)
spec.loader.exec_module(client)


class RecoveryTests(unittest.TestCase):
    def test_lost_upload_response_reconciles_manifest_without_second_upload(self):
        class Server:
            def __init__(self):
                self.files = []
                self.uploads = 0
            def request(self, method, path, data=None, headers=None):
                if method == "GET":
                    return {"revision": len(self.files), "files": self.files}
                self.uploads += 1
                self.files = [{"name": "a.png", "sha256": hashlib.sha256(b"test-image").hexdigest()}]
                raise urllib.error.URLError("response lost after save")
        server = Server()
        with tempfile.TemporaryDirectory() as root, patch.object(client.time, "sleep"):
            image = Path(root) / "a.png"
            image.write_bytes(b"test-image")
            client.upload(server, "/draft", image, 1000)
        self.assertEqual(1, server.uploads)

    def test_numeric_equality_preserves_boolean_distinction(self):
        self.assertEqual(client.digest({"year": 2026.0}), client.digest({"year": 2026}))
        self.assertEqual(client.digest({"latitude": -0.0}), client.digest({"latitude": 0}))
        self.assertNotEqual(client.digest({"year": True}), client.digest({"year": 1}))

    def test_conflicts_are_never_automatically_retried(self):
        attempts = []
        def conflict():
            attempts.append(1)
            raise client.ApiError(412, "stale revision")
        with self.assertRaises(client.ApiError):
            client.retry_safe(conflict)
        self.assertEqual(1, len(attempts))

    def test_transport_rejects_credentials_and_remote_cleartext(self):
        for url in ["http://example.org", "https://user:password@example.org", "https://example.org?token=x"]:
            with self.assertRaises(ValueError):
                client.Client(url, "test-token")



class MainFlowTests(unittest.TestCase):
    def test_main_persists_keys_reconciles_lost_responses_resumes_and_paginates(self):
        import json
        import os
        import sys
        class Server:
            base = "http://localhost"
            def __init__(self, state):
                self.state_file = state
                self.rows = []
                self.revision = 0
                self.state = "draft"
                self.creates = []
                self.commits = []
                self.pages = []
                self.lost_create = False
            def request(self, method, path, data=None, headers=None):
                saved = json.loads(self.state_file.read_text())
                if path.endswith("/capabilities"):
                    return {"admissionEnabled": True, "commitEnabled": True, "stagingAvailable": True, "limits": {"maxFileBytes": 10000}}
                if method == "POST" and path == "/api/v3/submissions":
                    self.creates.append(headers["Idempotency-Key"])
                    assert saved["createKey"] == headers["Idempotency-Key"]
                    if not self.lost_create:
                        self.lost_create = True
                        raise urllib.error.URLError("create response lost")
                    return {"id": "saved-id"}
                if path.endswith("/rows"):
                    if method == "PUT":
                        self.rows, self.revision = json.loads(json.dumps(data["rows"])), self.revision + 1
                        for row in self.rows:
                            row["fields"] = {k: int(v) if type(v) is float and v.is_integer() else v for k, v in row["fields"].items()}
                    return {"rows": self.rows, "revision": self.revision}
                if path.endswith("/validate"):
                    self.state = "validated"
                    return {"id": "validation-id", "revision": self.revision, "valid": True, "errors": []}
                if path.endswith("/commit"):
                    assert saved["commitRequest"] == data
                    assert saved["commitKey"] == headers["Idempotency-Key"]
                    assert headers["If-Match"] == f'"{saved["commitRevision"]}"'
                    self.commits.append(data.copy())
                    self.state = "imported"
                    raise urllib.error.URLError("accepted response lost")
                if "/results" in path:
                    self.pages.append(path)
                    return {"rows": []} if "cursor=" in path else {"rows": [], "nextCursor": "1"}
                result = {"state": self.state, "revision": self.revision}
                if self.state == "imported":
                    result["operationId"] = "original-operation"
                return result
        with tempfile.TemporaryDirectory() as root:
            state, rows = Path(root) / "state.json", Path(root) / "rows.json"
            rows.write_text(json.dumps({"rows": [{"clientRowId": "one", "fields": {"Encounter.year": 2026.0}}]}))
            server = Server(state)
            argv = ["client", "--base-url", server.base, "--state", str(state), "--rows", str(rows), "--media-dir", root]
            with patch.object(sys, "argv", argv), patch.dict(os.environ, {"WILDBOOK_SUBMISSIONS_TOKEN": "test"}), patch.object(client, "Client", return_value=server), patch.object(client.time, "sleep"), patch("builtins.print"):
                self.assertEqual(0, client.main())  # validate a whole-number float
                argv.append("--commit")
                self.assertEqual(0, client.main())  # resume after server normalized it to int
                self.assertEqual(0, client.main())  # accepted execution is not repeated
            self.assertEqual(2, len(server.creates))
            self.assertEqual(server.creates[0], server.creates[1])
            self.assertEqual(1, len(server.commits))
            self.assertEqual(4, len(server.pages))
            self.assertEqual("original-operation", json.loads(state.read_text())["operationId"])
            self.assertNotIn("test", state.read_text())

    def test_main_corrects_rows_in_same_draft_and_preserves_actionable_error(self):
        import json
        import os
        import sys
        class Server:
            base = "http://localhost"
            def __init__(self, old):
                self.rows = old["rows"]
                self.reject = False
                self.puts = 0
            def request(self, method, path, data=None, headers=None):
                if path.endswith("/capabilities"):
                    return {"admissionEnabled": True, "commitEnabled": True, "stagingAvailable": True, "limits": {"maxFileBytes": 10000}}
                if path.endswith("/rows"):
                    if method == "PUT":
                        self.puts += 1
                        if self.reject:
                            raise client.ApiError(422, "precise field error")
                        self.rows = data["rows"]
                    return {"rows": self.rows, "revision": 1}
                if path.endswith("/validate"):
                    return {"valid": True, "errors": [], "revision": 1, "id": "v"}
                return {"state": "draft", "revision": 1}
        with tempfile.TemporaryDirectory() as root:
            state, rows = Path(root) / "state.json", Path(root) / "rows.json"
            old = {"rows": [{"clientRowId": "one", "fields": {"Encounter.year": 2025}}]}
            new = {"rows": [{"clientRowId": "one", "fields": {"Encounter.year": 2026}}]}
            client.save(state, {"id": "same-draft", "rowsDigest": client.digest(old), "baseUrl": "http://localhost", "source": "submissions-reference-client"})
            rows.write_text(json.dumps(new))
            server = Server(old)
            argv = ["client", "--base-url", server.base, "--state", str(state), "--rows", str(rows), "--media-dir", root]
            with patch.object(sys, "argv", argv), patch.dict(os.environ, {"WILDBOOK_SUBMISSIONS_TOKEN": "test"}), patch.object(client, "Client", return_value=server), patch("builtins.print"):
                server.reject = True
                with self.assertRaisesRegex(client.ApiError, "precise field error"):
                    client.main()
                server.reject = False
                self.assertEqual(0, client.main())
            self.assertEqual("same-draft", json.loads(state.read_text())["id"])
            self.assertEqual(client.digest(new), json.loads(state.read_text())["rowsDigest"])

    def test_main_refuses_concurrent_state_use(self):
        import os
        import sys
        import fcntl
        with tempfile.TemporaryDirectory() as root:
            state = Path(root) / "state.json"
            fd = os.open(str(state) + ".lock", os.O_CREAT | os.O_WRONLY, 0o600)
            try:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
                with patch.object(sys, "argv", ["client", "--base-url", "http://localhost", "--state", str(state), "--cancel"]), patch.dict(os.environ, {"WILDBOOK_SUBMISSIONS_TOKEN": "test"}):
                    with self.assertRaisesRegex(ValueError, "Another client"):
                        client.main()
            finally:
                os.close(fd)


if __name__ == "__main__":
    unittest.main()
