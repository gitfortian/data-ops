"""Offline counterexamples for real 200+ retained source audit recovery validation."""
from copy import deepcopy
import unittest

from historical_recovery import (
    PAGE_LIMIT, RECOVERY, PendingEvidence, indexed_usage, positive_id, preflight,
    read_source, read_usage, recover_pages, source_refs, transport_cursor, validate_page,
)


BIG = 9007199254740993


def fixture(kind, count=201):
    rows = []
    for index in range(count):
        row = {"id": BIG + count - index}
        if kind == "DATASET":
            row.update(query_id=f"golden-old-{index}", subject_type="USER",
                       subject_source_domain="SECURITY_PRINCIPAL",
                       subject_source_identity="authorized-tester")
        else:
            row["consumer_id"] = 19
        rows.append(row)
    return rows


def usage(kind, rows):
    expected = source_refs(kind, rows)
    mode = RECOVERY[kind]["mode"]
    return [
        {"id": 9000 + i, "provider_evidence_ref": ref, "source_identity": identity,
         "consumption_mode": mode, "outcome": "SUCCESS",
         "deduplication_id": RECOVERY[kind]["provider"] + ":" + ref}
        for i, (ref, identity) in enumerate(expected.items())
    ]


def response(kind, product, version, before, rows, limit=PAGE_LIMIT):
    cfg = RECOVERY[kind]
    full = len(rows) == limit
    return {
        "productKey": product,
        "sourceVersionIdentity": str(version),
        "requestedLimit": limit,
        "visitedAuditCount": len(rows),
        "normalizedOrAlreadyPresentCount": len(rows),
        "normalizationGapCount": 0,
        "normalizationUnavailableCount": 0,
        "retryRequired": False,
        "retainedAuditExhausted": not full,
        cfg["requested"]: transport_cursor(kind, before),
        cfg["next"]: transport_cursor(kind, rows[-1]["id"]) if full else None,
    }


class FakeApi:
    def __init__(self, kind, rows, product, version):
        self.kind, self.rows, self.product, self.version = kind, rows, product, version
        self.calls = []

    def request(self, method, path, params=None):
        self.calls.append((method, path, deepcopy(params)))
        cfg = RECOVERY[self.kind]
        if len(self.calls) == 1:
            before = None
            start = 0
        else:
            value = params[cfg["cursor"]]
            before = positive_id(value)
            start = next(i for i, row in enumerate(self.rows) if row["id"] == before) + 1
        size = params["limit"]
        return response(self.kind, self.product, self.version, before,
                        self.rows[start:start + size], size)


class FakeCursor:
    def __init__(self, result):
        self.result = result
        self.query = None
        self.params = None

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def execute(self, query, params):
        self.query, self.params = query, params

    def fetchall(self):
        return self.result


class FakeDB:
    def __init__(self, result):
        self.cursor_impl = FakeCursor(result)

    def cursor(self):
        return self.cursor_impl


class HistoricalAuditRecoveryGoldenContractTest(unittest.TestCase):
    def test_dataset_beyond_recent_window_requires_missing_old_usage(self):
        source = fixture("DATASET", 201)
        before = usage("DATASET", source[:PAGE_LIMIT])
        refs, existing, older = preflight("DATASET", source, before)
        self.assertEqual(201, len(refs))
        self.assertEqual(200, len(existing))
        self.assertEqual(["query:golden-old-200"], older)

        with self.assertRaisesRegex(PendingEvidence, "do not exceed one page"):
            preflight("DATASET", source[:200], before)
        with self.assertRaisesRegex(PendingEvidence, "no unnormalized historical"):
            preflight("DATASET", source, usage("DATASET", source))

    def test_data_service_requires_a_real_managed_consumer(self):
        source = fixture("DATA_SERVICE")
        expected = source_refs("DATA_SERVICE", source)
        self.assertIn("invocation:" + str(source[-1]["id"]), expected)
        source[-1]["consumer_id"] = None
        with self.assertRaisesRegex(ValueError, "no managed Consumer"):
            source_refs("DATA_SERVICE", source)

    def test_dataset_rejects_unattributable_and_repeated_source_identity(self):
        rows = fixture("DATASET")
        rows[0]["subject_type"] = "ANONYMOUS"
        with self.assertRaisesRegex(ValueError, "not attributable"):
            source_refs("DATASET", rows)
        rows = fixture("DATASET")
        rows[-1]["query_id"] = rows[0]["query_id"]
        with self.assertRaisesRegex(ValueError, "duplicate provider evidence"):
            source_refs("DATASET", rows)
        rows = fixture("DATASET")
        rows[1]["id"] = rows[0]["id"]
        with self.assertRaisesRegex(ValueError, "strictly descending"):
            source_refs("DATASET", rows)

    def test_usage_requires_source_owned_consumer_and_no_duplicate_projection(self):
        source = fixture("DATA_SERVICE", 2)
        expected = source_refs("DATA_SERVICE", source)
        good = usage("DATA_SERVICE", source)
        self.assertEqual(2, len(indexed_usage(good, expected, "API_INVOKE")))
        bad = deepcopy(good)
        bad[0]["source_identity"] = "different managed consumer"
        with self.assertRaisesRegex(ValueError, "disagrees"):
            indexed_usage(bad, expected, "API_INVOKE")
        with self.assertRaisesRegex(ValueError, "duplicate normalized"):
            indexed_usage(good + [good[0]], expected, "API_INVOKE")

    def test_bigint_cursor_is_lossless_decimal_text_for_data_service(self):
        self.assertEqual(str(BIG), transport_cursor("DATA_SERVICE", BIG))
        self.assertEqual(BIG, transport_cursor("DATASET", str(BIG)))
        for invalid in ["0", "-1", "01", "1.0", "9223372036854775808", ""]:
            with self.subTest(invalid=invalid):
                with self.assertRaises(ValueError):
                    positive_id(invalid)

    def test_dataset_201_pages_are_exact_and_revisit_oldest_source_row(self):
        source = fixture("DATASET", 201)
        api = FakeApi("DATASET", source, "DATASET:7", 9)
        outcome = recover_pages(api, "DATASET", "DATASET:7", 9, source)
        self.assertEqual({"pages": 2, "visited": 201}, outcome)
        self.assertEqual(2, len(api.calls))
        self.assertEqual("POST", api.calls[0][0])
        self.assertEqual("/api/v1/consumption/impact/dataset-version-recovery", api.calls[0][1])
        self.assertNotIn("beforeAuditId", api.calls[0][2])
        self.assertEqual(source[199]["id"], api.calls[1][2]["beforeAuditId"])
        self.assertEqual(200, api.calls[0][2]["limit"])

    def test_data_service_exactly_400_needs_empty_exhaustion_page(self):
        source = fixture("DATA_SERVICE", 400)
        api = FakeApi("DATA_SERVICE", source, "DATA_SERVICE:7", BIG)
        outcome = recover_pages(api, "DATA_SERVICE", "DATA_SERVICE:7", BIG, source, max_pages=2)
        self.assertEqual({"pages": 3, "visited": 400}, outcome)
        self.assertNotIn("beforeInvocationId", api.calls[0][2])
        self.assertEqual(str(source[199]["id"]), api.calls[1][2]["beforeInvocationId"])
        self.assertEqual(str(source[399]["id"]), api.calls[2][2]["beforeInvocationId"])
        self.assertIsInstance(api.calls[2][2]["beforeInvocationId"], str)

    def test_recovery_refuses_skipped_cursor_unverified_counts_or_forged_exhaustion(self):
        source = fixture("DATA_SERVICE")
        result = response("DATA_SERVICE", "DATA_SERVICE:7", BIG, None, source[:200])
        for changes, expression in [
            ({"nextBeforeInvocationId": "42"}, "next cursor"),
            ({"normalizedOrAlreadyPresentCount": 199}, "GAP"),
            ({"retainedAuditExhausted": True}, "exhaustion"),
            ({"requestedBeforeInvocationId": "45"}, "request cursor"),
            ({"sourceVersionIdentity": "123"}, "immutable Revision"),
        ]:
            with self.subTest(changes=changes):
                corrupted = {**result, **changes}
                with self.assertRaisesRegex(ValueError, expression):
                    validate_page("DATA_SERVICE", corrupted, "DATA_SERVICE:7", BIG,
                                  None, source[:200], 200)

    def test_source_query_scopes_before_limit_and_normalized_query_includes_project(self):
        for kind, predicates in [
            ("DATASET", ["project_id=%s", "dataset_id=%s",
                         "dataset_version_id=%s", "status='SUCCESS'"]),
            ("DATA_SERVICE", ["project_id=%s", "api_id=%s",
                              "source_revision_id=%s", "success=1"]),
        ]:
            with self.subTest(kind=kind):
                db = FakeDB(fixture(kind, 1))
                self.assertEqual(1, len(read_source(db, kind, 42, 7, 9, 201)))
                query = " ".join(db.cursor_impl.query.split())
                self.assertTrue(all(x in query for x in predicates))
                self.assertIn("ORDER BY id DESC LIMIT %s", query)
                self.assertEqual((42, 7, 9, 201), db.cursor_impl.params)
                read_usage(db, kind, 42, kind + ":7", 9, 201)
                query = " ".join(db.cursor_impl.query.split())
                self.assertIn("project_id=%s", query)
                self.assertIn("product_key=%s", query)
                self.assertIn("source_version_identity=%s", query)
                self.assertIn("provider=%s", query)
                self.assertEqual(42, db.cursor_impl.params[0])
                self.assertEqual(kind + ":7", db.cursor_impl.params[1])


if __name__ == "__main__":
    unittest.main()
