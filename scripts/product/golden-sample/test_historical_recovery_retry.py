"""Offline real-fault staged Golden contracts; never call a deployed application."""
from copy import deepcopy
import unittest

from historical_recovery import PendingEvidence, RECOVERY, transport_cursor
from historical_recovery_retry import (
    LONG_MAX, compare_recovered_usage, request_one, select_audit, signature,
    validate_blocked, verify_stage,
)
from test_historical_recovery import response, usage
from test_historical_recovery_denials import FakeDB


BIG = 9007199254740993


def source_row(kind, audit_id=BIG):
    if kind == "DATASET":
        return {
            "id": audit_id, "query_id": "stable-real-query",
            "subject_type": "USER",
            "subject_source_domain": "SECURITY_PRINCIPAL",
            "subject_source_identity": "fixture-principal",
        }
    return {"id": audit_id, "consumer_id": 19}


class StagedApi:
    def __init__(self, db, kind, fault="gap", phase="blocked"):
        self.db, self.kind, self.fault, self.phase = db, kind, fault, phase
        self.calls = []
        self.project_id = "42"

    def request(self, method, path, params=None):
        self.calls.append((method, path, deepcopy(params), self.project_id))
        cfg = RECOVERY[self.kind]
        assert method == "POST" and path == cfg["path"]
        assert self.project_id == "42"
        assert params["productKey"] == self.kind + ":7"
        assert params["sourceVersionIdentity"] == "9"
        assert params["limit"] == 1
        if self.phase == "blocked":
            result = response(self.kind, self.kind + ":7", 9,
                              params.get(cfg["cursor"]), [source_row(self.kind)], 1)
            result["normalizedOrAlreadyPresentCount"] = 0
            result["retryRequired"] = True
            result[cfg["next"]] = None
            if self.fault == "unavailable":
                result["normalizationUnavailableCount"] = 1
            elif self.fault == "gap":
                result["normalizationGapCount"] = 1
            elif self.fault == "healthy":
                result["retryRequired"] = False
                result["normalizedOrAlreadyPresentCount"] = 1
                result[cfg["next"]] = str(BIG)
            elif self.fault == "false_exhaustion":
                result["normalizationGapCount"] = 1
                result["retainedAuditExhausted"] = True
            elif self.fault == "fake_next":
                result["normalizationGapCount"] = 1
                result[cfg["next"]] = str(BIG)
            elif self.fault == "side_effect":
                result["normalizationGapCount"] = 1
                self.db.usage += usage(self.kind, [source_row(self.kind)])
            return result
        if self.phase == "recovered":
            if self.fault != "no_usage" and not self.db.usage:
                self.db.usage += usage(self.kind, [source_row(self.kind)])
            if self.fault == "duplicate_usage":
                self.db.usage += usage(self.kind, [source_row(self.kind)])
            if self.fault == "audit_changed":
                if self.kind == "DATASET":
                    self.db.dataset_source[0]["query_id"] = "changed"
                else:
                    self.db.service_source[0]["consumer_id"] = 99
            return response(self.kind, self.kind + ":7", 9,
                            params.get(cfg["cursor"]), [source_row(self.kind)], 1)
        raise AssertionError("Unknown fake phase")


def fake_db(kind, row=None):
    db = FakeDB()
    db.usage = []
    db.dataset_source = [row or source_row("DATASET")]
    db.service_source = [row or source_row("DATA_SERVICE")]
    return db


def report_for(kind, scenario):
    return {
        "marker": "yak-golden-sample-v1",
        "result": "REAL_BLOCKED_PAGE_OBSERVED",
        "projectId": "42", "controlProjectId": "43",
        "scenario": scenario,
    }


class SameCursorRealFaultGoldenTest(unittest.TestCase):
    def test_both_sources_real_gap_and_unavailable_block_with_no_usage_side_effects(self):
        for kind in ("DATASET", "DATA_SERVICE"):
            for fault in ("gap", "unavailable"):
                with self.subTest(kind=kind, fault=fault):
                    db = fake_db(kind)
                    api = StagedApi(db, kind, fault)
                    result = verify_stage(
                        api, db, kind,
                        {"id": "7", RECOVERY[kind]["version"]: "9"},
                        42, 43, BIG, "blocked")
                    self.assertEqual("BLOCKED_REAL_OBSERVED", result["stage"])
                    self.assertEqual("PASSED", result["usageAndSourceUnchanged"])
                    self.assertEqual("9007199254740994", result["requestedCursor"])
                    self.assertTrue(result["blocked"]["retryRequired"])
                    self.assertTrue(result["blocked"]["continuationBlocked"])
                    self.assertEqual(
                        int(fault == "gap"), result["blocked"]["gapOrIgnoredCount"])
                    self.assertEqual(
                        int(fault == "unavailable"), result["blocked"]["unavailableCount"])
                    self.assertEqual("9007199254740994",
                                     api.calls[0][2][RECOVERY[kind]["cursor"]])
                    self.assertEqual([], db.usage)

    def test_successful_retry_creates_exactly_one_usage_then_is_idempotent(self):
        for kind in ("DATASET", "DATA_SERVICE"):
            with self.subTest(kind=kind):
                db = fake_db(kind)
                fixture = {"id": "7", RECOVERY[kind]["version"]: "9"}
                blocked = verify_stage(
                    StagedApi(db, kind, "gap"), db, kind, fixture,
                    42, 43, BIG, "blocked")
                api = StagedApi(db, kind, phase="recovered")
                recovered = verify_stage(
                    api, db, kind, fixture,
                    42, 43, BIG, "recovered", report_for(kind, blocked))
                self.assertEqual("RECOVERED_REAL_VERIFIED", recovered["stage"])
                self.assertEqual(1, recovered["normalizedUsageCreated"])
                self.assertEqual("PASSED", recovered["sameCursorReplay"])
                self.assertEqual("PASSED", recovered["sourceAuditUnchanged"])
                self.assertEqual(1, len(db.usage))
                self.assertEqual(2, len(api.calls))
                self.assertEqual(api.calls[0][2], api.calls[1][2])

    def test_wrong_report_or_changed_source_blocks_replay(self):
        kind = "DATA_SERVICE"
        fixture = {"id": "7", "revisionId": "9"}
        db = fake_db(kind)
        blocked = verify_stage(
            StagedApi(db, kind), db, kind, fixture, 42, 43, BIG, "blocked")
        report = report_for(kind, blocked)
        for changed in (
            {"result": "PASS"},
            {"projectId": "43"},
            {"scenario": {**blocked, "requestedCursor": "42"}},
            {"scenario": {**blocked, "auditId": str(BIG + 1)}},
            {"scenario": {**blocked, "sourceSignature": "fake-hash"}},
        ):
            with self.subTest(changed=changed):
                candidate = {**report, **changed}
                api = StagedApi(db, kind, phase="recovered")
                with self.assertRaisesRegex(ValueError, "does not match|identical source"):
                    verify_stage(api, db, kind, fixture, 42, 43, BIG,
                                 "recovered", candidate)
                self.assertEqual([], api.calls)
        db.service_source[0]["consumer_id"] = 123
        api = StagedApi(db, kind, phase="recovered")
        with self.assertRaisesRegex(ValueError, "identical source"):
            verify_stage(api, db, kind, fixture, 42, 43, BIG, "recovered", report)
        self.assertEqual([], api.calls)

    def test_broken_negative_probes_do_not_look_like_actual_gap(self):
        for fault in ("healthy", "false_exhaustion", "fake_next", "side_effect"):
            with self.subTest(fault=fault):
                db = fake_db("DATASET")
                api = StagedApi(db, "DATASET", fault)
                with self.assertRaisesRegex(ValueError, "GAP|falsely advanced|changed persisted Usage"):
                    verify_stage(api, db, "DATASET", {"id": "7", "versionId": "9"},
                                 42, 43, BIG, "blocked")

    def test_missing_usage_wrong_identity_and_duplicate_usage_cannot_pass_recovery(self):
        for fault, msg in [
            ("no_usage", "did not create exactly one"),
            ("duplicate_usage", "did not create exactly one"),
            ("audit_changed", "changed source-owned audit"),
        ]:
            with self.subTest(fault=fault):
                db = fake_db("DATASET")
                fixture = {"id": "7", "versionId": "9"}
                blocked = verify_stage(
                    StagedApi(db, "DATASET"), db, "DATASET", fixture,
                    42, 43, BIG, "blocked")
                api = StagedApi(db, "DATASET", fault, phase="recovered")
                with self.assertRaisesRegex(ValueError, msg):
                    verify_stage(api, db, "DATASET", fixture,
                                 42, 43, BIG, "recovered", report_for("DATASET", blocked))

    def test_target_not_retained_or_already_normalized_is_pending(self):
        db = fake_db("DATASET")
        with self.assertRaisesRegex(PendingEvidence, "not retained"):
            select_audit(db, "DATASET", 42, 7, 9, BIG - 1)
        db.usage = usage("DATASET", [source_row("DATASET")])
        with self.assertRaisesRegex(PendingEvidence, "already normalized"):
            verify_stage(StagedApi(db, "DATASET"), db, "DATASET",
                         {"id": "7", "versionId": "9"}, 42, 43, BIG, "blocked")

    def test_overlong_cursor_and_changed_version_never_count_as_replay(self):
        self.assertEqual("9007199254740994", transport_cursor("DATASET", BIG + 1))
        with self.assertRaises(ValueError):
            transport_cursor("DATASET", LONG_MAX + 1)
        db = fake_db("DATA_SERVICE", source_row("DATA_SERVICE", LONG_MAX))
        source, row, before, expected = select_audit(
            db, "DATA_SERVICE", 42, 7, 9, LONG_MAX)
        self.assertIsNone(before)
        self.assertEqual(1, len(source))
        self.assertEqual(LONG_MAX, row["id"])
        self.assertEqual(1, len(expected))

    def test_denial_helpers_reject_unjustified_continuation_and_usage_identity(self):
        kind = "DATASET"
        blocked = response(kind, kind + ":7", 9, str(BIG + 1),
                           [source_row(kind)], 1)
        blocked.update({
            "normalizedOrAlreadyPresentCount": 0, "normalizationGapCount": 1,
            "retryRequired": True, "nextBeforeAuditId": None,
        })
        self.assertEqual(1, validate_blocked(kind, blocked, kind + ":7", 9,
                                             str(BIG + 1))["gapOrIgnoredCount"])
        for modified in (
            {"requestedBeforeAuditId": str(BIG)},
            {"visitedAuditCount": 2},
            {"retryRequired": False},
            {"normalizationGapCount": 0},
            {"retainedAuditExhausted": True},
        ):
            with self.subTest(modified=modified):
                with self.assertRaises(ValueError):
                    validate_blocked(kind, {**blocked, **modified}, kind + ":7",
                                     9, str(BIG + 1))
        before = []
        expected = {"query:stable-real-query": "fixture-principal"}
        wrong = usage(kind, [source_row(kind)])
        wrong[0]["source_identity"] = "cross-project-principal"
        with self.assertRaisesRegex(ValueError, "disagrees"):
            compare_recovered_usage(before, wrong, expected, kind)


if __name__ == "__main__":
    unittest.main()
