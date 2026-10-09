"""Offline R6 source-reader outage contract; no real deployment or fault injection."""
from copy import deepcopy
import unittest

from historical_recovery import PendingEvidence, RECOVERY
from historical_recovery_reader_outage import (
    APP_INTERNAL_FAILURE, classify_reader_failure, request_params,
    select_existing_success, verify_stage,
)
from test_historical_recovery import response, usage
from test_historical_recovery_retry import SnapshotDB, source_row


BIG = 9007199254740993


class FakeHttpResponse:
    def __init__(self, status, body=None):
        self.status_code, self.body = status, body

    def json(self):
        return self.body


class FakeHttp:
    def __init__(self, db, fail=None):
        self.db = db
        self.fail = fail or FakeHttpResponse(503)
        self.calls = []

    def request(self, method, url, *, params, headers, timeout, allow_redirects):
        self.calls.append((method, url, deepcopy(params), deepcopy(headers),
                           timeout, allow_redirects))
        if isinstance(self.fail, Exception):
            raise self.fail
        return self.fail


class FakeApi:
    def __init__(self, kind, db, outage_response=None, restored_mode="valid"):
        self.project_id = "42"
        self.base_url = "https://golden.invalid:8080"
        self.session = FakeHttp(db, outage_response)
        self.kind, self.db = kind, db
        self.restored_mode = restored_mode
        self.normal_calls = []

    def request(self, method, path, params=None):
        self.normal_calls.append((method, path, deepcopy(params)))
        assert method == "POST" and path == RECOVERY[self.kind]["path"]
        assert params["productKey"] == self.kind + ":7"
        assert params["sourceVersionIdentity"] == "9"
        assert params["limit"] == 1
        if self.restored_mode == "mutate_usage":
            self.db.usage[0]["source_identity"] = "unexpected"
        elif self.restored_mode == "mutate_source":
            if self.kind == "DATASET":
                self.db.dataset_source[0]["query_id"] = "changed"
            else:
                self.db.service_source[0]["consumer_id"] = 20
        elif self.restored_mode == "extra_usage":
            self.db.usage.append({**deepcopy(self.db.usage[0]), "id": 9090})
        result = response(self.kind, self.kind + ":7", 9,
                          params.get(RECOVERY[self.kind]["cursor"]),
                          [source_row(self.kind)], limit=1)
        if self.restored_mode == "false_empty":
            result = response(self.kind, self.kind + ":7", 9,
                              params.get(RECOVERY[self.kind]["cursor"]), [], limit=1)
        elif self.restored_mode == "wrong_cursor":
            result[RECOVERY[self.kind]["next"]] = "42"
        return result


def fixture(kind):
    db = SnapshotDB()
    db.dataset_source = [source_row("DATASET")]
    db.service_source = [source_row("DATA_SERVICE")]
    db.usage = usage(kind, [source_row(kind)])
    return db


def select(db, kind):
    return select_existing_success(
        db, kind, 42, 43, {"id": "7", RECOVERY[kind]["version"]: "9"}, BIG)


def prior_report(selected):
    scenario = {
        "kind": selected["kind"], "productKey": selected["productKey"],
        "sourceVersionIdentity": selected["sourceVersionIdentity"],
        "auditId": selected["auditId"], "requestedCursor": selected["cursor"],
        "sourceSignature": selected["sourceSignature"],
        "evidenceRefDigest": selected["evidenceRefDigest"],
        "persistedUsageId": selected["persistedUsageId"],
        "stage": "REAL_SERVER_FAILURE_OBSERVED",
        "serverFailure": "HTTP_503",
        "usageAndSourceUnchanged": "PASSED",
    }
    return {
        "marker": "yak-golden-sample-v1",
        "sceneId": "J3-EXACT-VERSION-SOURCE-READER-OUTAGE",
        "result": "REAL_SERVER_FAILURE_OBSERVED",
        "projectId": "42", "controlProjectId": "43",
        "scenario": scenario,
    }


class RealReaderOutageContractTest(unittest.TestCase):
    def test_genuine_http_5xx_and_application_internal_999_are_observed_without_data(self):
        for status in (500, 502, 503, 504, 599):
            with self.subTest(status=status):
                self.assertEqual(f"HTTP_{status}",
                                 classify_reader_failure(FakeHttpResponse(status)))
        self.assertEqual(
            "APP_INTERNAL_999",
            classify_reader_failure(FakeHttpResponse(
                200, {"code": APP_INTERNAL_FAILURE, "data": None})))

    def test_success_permission_redirect_and_unexpected_app_error_are_not_outage_proof(self):
        failures = [
            FakeHttpResponse(200, {"code": 200, "data": {"retainedAuditExhausted": True}}),
            FakeHttpResponse(200, {"code": 200, "data": {"visitedAuditCount": 0}}),
            FakeHttpResponse(200, {"code": 1001, "data": None}),
            FakeHttpResponse(200, {"code": 999, "data": {"retainedAuditExhausted": True}}),
            FakeHttpResponse(302), FakeHttpResponse(400), FakeHttpResponse(401),
            FakeHttpResponse(403), FakeHttpResponse(404), FakeHttpResponse(422),
        ]
        for reply in failures:
            with self.subTest(status=reply.status_code, payload=reply.body):
                with self.assertRaises(ValueError):
                    classify_reader_failure(reply)

    def test_both_sources_preserve_bigint_and_scope_and_never_change_db_on_outage(self):
        for kind in ("DATASET", "DATA_SERVICE"):
            with self.subTest(kind=kind):
                db = fixture(kind)
                selected = select(db, kind)
                self.assertEqual("9007199254740994", selected["cursor"])
                self.assertEqual("9007199254740993", selected["auditId"])
                self.assertEqual(str(db.usage[0]["id"]), selected["persistedUsageId"])
                api = FakeApi(kind, db, FakeHttpResponse(503))
                result = verify_stage(api, db, 42, 43, selected, "outage")
                self.assertEqual("REAL_SERVER_FAILURE_OBSERVED", result["stage"])
                self.assertEqual("HTTP_503", result["serverFailure"])
                self.assertEqual("PASSED", result["usageAndSourceUnchanged"])
                self.assertEqual(1, len(api.session.calls))
                method, path, params, headers, timeout, redirects = api.session.calls[0]
                self.assertEqual("POST", method)
                self.assertTrue(path.endswith(RECOVERY[kind]["path"]))
                self.assertEqual("42", headers["X-YAK-SECURITY-PROJECT-ID"])
                self.assertEqual(selected["cursor"], params[RECOVERY[kind]["cursor"]])
                self.assertEqual(30, timeout)
                self.assertFalse(redirects)
                self.assertEqual([], api.normal_calls)

    def test_restored_exact_page_reuses_idempotent_usage_and_same_request_twice(self):
        for kind in ("DATASET", "DATA_SERVICE"):
            with self.subTest(kind=kind):
                db = fixture(kind)
                selected = select(db, kind)
                prior = prior_report(selected)
                api = FakeApi(kind, db)
                result = verify_stage(api, db, 42, 43, selected, "restored", prior)
                self.assertEqual("REAL_RESTORED_SOURCE_PAGE_VERIFIED", result["stage"])
                self.assertEqual(1, result["restoredExactAuditCount"])
                self.assertEqual("PASSED", result["sameCursorReplay"])
                self.assertEqual("PASSED", result["usageAndSourceUnchanged"])
                self.assertEqual(2, len(api.normal_calls))
                self.assertEqual(api.normal_calls[0], api.normal_calls[1])
                self.assertEqual([], api.session.calls)
                self.assertEqual(1, len(db.usage))

    def test_outage_requires_server_failure_not_successful_empty_page(self):
        for reply in [
            FakeHttpResponse(200, {"code": 200, "data": {"visitedAuditCount": 0}}),
            FakeHttpResponse(403), FakeHttpResponse(302),
        ]:
            db = fixture("DATASET")
            with self.assertRaises(ValueError):
                verify_stage(FakeApi("DATASET", db, reply), db, 42, 43,
                             select(db, "DATASET"), "outage")

    def test_restoration_rejects_wrong_identity_or_fabricated_prior_failure(self):
        db = fixture("DATASET")
        selected = select(db, "DATASET")
        good = prior_report(selected)
        cases = [
            {"projectId": "43"},
            {"result": "PASS"},
            {"scenario": {**good["scenario"], "requestedCursor": "7"}},
            {"scenario": {**good["scenario"], "persistedUsageId": "1234"}},
            {"scenario": {**good["scenario"], "sourceSignature": "different"}},
            {"scenario": {**good["scenario"], "stage": "PENDING"}},
            {"scenario": {**good["scenario"], "usageAndSourceUnchanged": "NO"}},
        ]
        for changed in cases:
            with self.subTest(changed=changed):
                candidate = {**good, **changed}
                api = FakeApi("DATASET", db)
                with self.assertRaisesRegex(ValueError, "does not match|not for"):
                    verify_stage(api, db, 42, 43, selected, "restored", candidate)
                self.assertEqual([], api.normal_calls)

    def test_false_empty_cursor_and_mutation_cannot_pass_restored_stage(self):
        for mode in ("false_empty", "wrong_cursor", "mutate_usage", "mutate_source", "extra_usage"):
            with self.subTest(mode=mode):
                db = fixture("DATASET")
                selected = select(db, "DATASET")
                with self.assertRaises(ValueError):
                    verify_stage(FakeApi("DATASET", db, restored_mode=mode),
                                 db, 42, 43, selected, "restored", prior_report(selected))

    def test_missing_already_normalized_source_or_control_collision_remains_pending(self):
        db = fixture("DATASET")
        db.usage = []
        with self.assertRaisesRegex(PendingEvidence, "without already normalized"):
            select(db, "DATASET")
        db = fixture("DATASET")
        db.dataset_source = []
        with self.assertRaisesRegex(PendingEvidence, "no longer retained"):
            select(db, "DATASET")
        db = fixture("DATASET")
        db.foreign_source = [source_row("DATASET")]
        with self.assertRaisesRegex(PendingEvidence, "colliding"):
            select(db, "DATASET")

    def test_max_signed_bigint_is_transport_first_page_only(self):
        db = fixture("DATA_SERVICE")
        db.service_source = [source_row("DATA_SERVICE", 9223372036854775807)]
        db.usage = usage("DATA_SERVICE", db.service_source)
        selected = select_existing_success(db, "DATA_SERVICE", 42, 43,
                                           {"id": "7", "revisionId": "9"},
                                           9223372036854775807)
        self.assertIsNone(selected["cursor"])
        self.assertNotIn(RECOVERY["DATA_SERVICE"]["cursor"], request_params(selected))


if __name__ == "__main__":
    unittest.main()
