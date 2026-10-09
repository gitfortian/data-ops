"""Offline counterexamples for real recovery-denial HTTP and DB invariants."""
import unittest

from historical_recovery import PendingEvidence, RECOVERY, transport_cursor
from historical_recovery_denials import (
    INVALID_CURSORS, SNAPSHOT_MAX_ROWS, assert_denied, assert_unchanged,
    request_raw, source_snapshot, usage_snapshot, verify_negative_kind,
)


class FakeResponse:
    def __init__(self, status=400, code=None):
        self.status_code = status
        self.code = code

    def json(self):
        return {"code": self.code}


class FakeTransport:
    def __init__(self, mode="authorized"):
        self.mode = mode
        self.calls = []

    def request(self, method, url, params, headers, timeout, allow_redirects):
        self.calls.append((method, url, dict(params), dict(headers), timeout, allow_redirects))
        if self.mode == "anonymous":
            return FakeResponse(401)
        if self.mode == "restricted":
            return FakeResponse(403)
        if "X-YAK-SECURITY-PROJECT-ID" not in headers:
            return FakeResponse(400)
        if method == "GET":
            return FakeResponse(405)
        if params.get("sourceVersionIdentity") == "01" or params.get("productKey") != self.kind_key:
            return FakeResponse(400)
        if any(name in params for name in ("beforeAuditId", "beforeInvocationId")):
            return FakeResponse(200, 48001)
        raise AssertionError("No valid own-project recovery POST is allowed by the negative matrix")


class FakeApi:
    def __init__(self, kind):
        self.kind = kind
        self.base_url = "http://example.invalid:18081"
        self.project_id = "42"
        self.session = FakeTransport()
        self.session.kind_key = kind + ":7"
        self.calls = []

    def request(self, method, path, params=None):
        self.calls.append((method, path, dict(params), self.project_id))
        assert self.project_id == "43"
        assert method == "POST"
        assert path == RECOVERY[self.kind]["path"]
        return {
            "productKey": self.kind + ":7", "sourceVersionIdentity": "9",
            "requestedLimit": 200, "visitedAuditCount": 0,
            "normalizedOrAlreadyPresentCount": 0,
            "normalizationGapCount": 0, "normalizationUnavailableCount": 0,
            "retryRequired": False, "retainedAuditExhausted": True,
            RECOVERY[self.kind]["requested"]: None,
            RECOVERY[self.kind]["next"]: None,
        }


class FakeCursor:
    def __init__(self, db):
        self.db = db
        self.query = ""
        self.params = ()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def execute(self, query, params):
        self.query, self.params = query, params
        self.db.calls.append((query, params))

    def fetchall(self):
        if "FROM yak_ops_consumption_usage_evidence" in self.query:
            return list(self.db.usage)
        project_id = self.params[0]
        if project_id == 43:
            return list(self.db.foreign_source)
        if "yak_dataset_query_performance" in self.query:
            return list(self.db.dataset_source)
        if "yak_ops_data_service_call_log" in self.query:
            return list(self.db.service_source)
        raise AssertionError("Unexpected SQL source")


class FakeDB:
    def __init__(self):
        self.calls = []
        self.usage = [{"id": 5, "project_id": 42, "source_identity": "principal"}]
        self.dataset_source = [{"id": 9007199254740993, "query_id": "old-query"}]
        self.service_source = [{"id": 9007199254740993, "consumer_id": 19}]
        self.foreign_source = []

    def cursor(self):
        return FakeCursor(self)


class RecoveryDenialGoldenContractTest(unittest.TestCase):
    def test_fail_closed_denial_does_not_accept_success_redirect_or_server_fault(self):
        for response in [
            FakeResponse(200, 200), FakeResponse(500), FakeResponse(302),
            FakeResponse(200), FakeResponse(200, 500), FakeResponse(200, 50000),
        ]:
            with self.subTest(status=response.status_code, code=response.code):
                with self.assertRaises(ValueError):
                    assert_denied(response, "negative path")
        self.assertEqual("HTTP_401", assert_denied(FakeResponse(401), "anonymous"))
        self.assertEqual("HTTP_405", assert_denied(FakeResponse(405), "GET"))
        self.assertEqual("APP_DENIED_48001",
                         assert_denied(FakeResponse(200, 48001), "missing project"))

    def test_raw_requests_scope_only_explicit_project_and_never_follow_redirect(self):
        transport = FakeTransport("anonymous")
        params = {"productKey": "DATASET:7"}
        request_raw(transport, "POST", "https://example.invalid/", "/probe", params)
        request_raw(transport, "GET", "https://example.invalid/", "/probe", params, 42)
        self.assertEqual({}, transport.calls[0][3])
        self.assertEqual({"X-YAK-SECURITY-PROJECT-ID": "42"}, transport.calls[1][3])
        self.assertEqual(30, transport.calls[1][4])
        self.assertIs(False, transport.calls[1][5])
        self.assertTrue(transport.calls[1][1].endswith("/probe"))

    def test_read_only_evidence_queries_are_scoped_and_bounded(self):
        db = FakeDB()
        self.assertEqual(db.usage, usage_snapshot(db, 42, 43))
        sql, params = db.calls[-1]
        self.assertIn("WHERE project_id IN (%s, %s)", sql)
        self.assertEqual((42, 43, SNAPSHOT_MAX_ROWS + 1), params)
        self.assertEqual(db.dataset_source, source_snapshot(db, "DATASET", 42, 7, 9))
        sql, params = db.calls[-1]
        self.assertIn("dataset_version_id=%s", sql)
        self.assertEqual((42, 7, 9, SNAPSHOT_MAX_ROWS + 1), params)
        self.assertEqual(db.service_source,
                         source_snapshot(db, "DATA_SERVICE", 42, 7, 9))
        self.assertEqual((42, 7, 9, SNAPSHOT_MAX_ROWS + 1), db.calls[-1][1])

    def test_snapshot_limit_and_mutation_are_never_treated_as_empty_source(self):
        db = FakeDB()
        db.usage = [{"id": n} for n in range(SNAPSHOT_MAX_ROWS + 1)]
        with self.assertRaisesRegex(PendingEvidence, "snapshot budget"):
            usage_snapshot(db, 42, 43)
        db = FakeDB()
        db.dataset_source = [{"id": n} for n in range(SNAPSHOT_MAX_ROWS + 1)]
        with self.assertRaisesRegex(PendingEvidence, "snapshot budget"):
            source_snapshot(db, "DATASET", 42, 7, 9)
        db = FakeDB()
        before = usage_snapshot(db, 42, 43)
        source_states = {("DATASET", 42, 7, 9): source_snapshot(db, "DATASET", 42, 7, 9)}
        db.usage.append({"id": 7})
        with self.assertRaisesRegex(ValueError, "changed persisted Usage"):
            assert_unchanged(db, 42, 43, before, source_states)
        db.usage.pop()
        db.dataset_source.append({"id": 8})
        with self.assertRaisesRegex(ValueError, "changed the source"):
            assert_unchanged(db, 42, 43, before, source_states)

    def test_real_negative_matrix_for_both_kinds_requires_no_201_row_setup(self):
        for kind in ("DATASET", "DATA_SERVICE"):
            with self.subTest(kind=kind):
                api = FakeApi(kind)
                db = FakeDB()
                anon = FakeTransport("anonymous")
                result = verify_negative_kind(
                    api, db, kind, {"id": "7", RECOVERY[kind]["version"]: "9"},
                    42, 43, anon)
                self.assertEqual("PASSED", result["usageAndSourceUnchanged"])
                self.assertEqual("PASSED", result["negativeCases"]["controlProjectNoSource"])
                self.assertEqual("PENDING_NO_RESTRICTED_TEST_ACCOUNT",
                                 result["negativeCases"]["restrictedIdentity"])
                self.assertEqual("HTTP_401", result["negativeCases"]["anonymousRejected"])
                self.assertEqual("HTTP_400", result["negativeCases"]["missingProjectRejected"])
                self.assertEqual("HTTP_405", result["negativeCases"]["getDoesNotRecover"])
                for cursor in INVALID_CURSORS:
                    self.assertEqual("APP_DENIED_48001",
                                     result["negativeCases"]["invalidCursor_" + cursor])
                self.assertEqual("43", api.calls[0][3])
                self.assertEqual("42", api.project_id)
                self.assertEqual(1, len(api.calls))
                self.assertEqual(12, len(api.session.calls))
                self.assertTrue(len(db.calls) > len(api.session.calls))

    def test_restricted_identity_is_measured_or_fails_and_never_claimed_automatically(self):
        api = FakeApi("DATASET")
        db = FakeDB()
        anon = FakeTransport("anonymous")
        restricted = type("Restricted", (), {"session": FakeTransport("restricted")})()
        result = verify_negative_kind(
            api, db, "DATASET", {"id": "7", "versionId": "9"}, 42, 43,
            anon, restricted)
        self.assertEqual("HTTP_403", result["negativeCases"]["restrictedIdentity"])
        self.assertEqual(1, len(restricted.session.calls))
        self.assertEqual("42", api.project_id)

    def test_foreign_project_source_collision_is_pending_without_touching_usage(self):
        db = FakeDB()
        db.foreign_source = [{"id": 1}]
        api = FakeApi("DATASET")
        with self.assertRaisesRegex(PendingEvidence, "control Project has same"):
            verify_negative_kind(api, db, "DATASET",
                                 {"id": "7", "versionId": "9"}, 42, 43,
                                 FakeTransport("anonymous"))
        self.assertEqual([], api.calls)

    def test_missing_retained_success_is_pending_not_fabricated_from_usage(self):
        db = FakeDB()
        db.service_source = []
        api = FakeApi("DATA_SERVICE")
        with self.assertRaisesRegex(PendingEvidence, "no retained successful"):
            verify_negative_kind(api, db, "DATA_SERVICE",
                                 {"id": "7", "revisionId": "9"}, 42, 43,
                                 FakeTransport("anonymous"))
        self.assertEqual([], api.calls)

    def test_bigint_cursor_contract_is_shared_by_both_real_denial_flows(self):
        self.assertEqual("9007199254740993", transport_cursor("DATASET", "9007199254740993"))
        self.assertEqual("9007199254740993", transport_cursor("DATA_SERVICE", "9007199254740993"))


if __name__ == "__main__":
    unittest.main()
