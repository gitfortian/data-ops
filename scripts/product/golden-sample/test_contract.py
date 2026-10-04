"""Guard fixture ownership and prevent partial results from passing as evidence."""

from copy import deepcopy
import unittest

from acceptance import validate_manifest, validate_section
from bootstrap import Api, DATABASE, MARKER, SECTION_OWNERS, TABLES, records, unique_match


def manifest():
    return {"schemaVersion": 1, "marker": MARKER, "database": DATABASE, "projectId": "42",
            "assets": [{"table": t, "id": str(i), "sourceType": "METADATA",
                        "sourceId": str(i + 100), "assetKey": f"physical:{i}"}
                       for i, t in enumerate(TABLES, 1)],
            "monitors": [{"id": "11", "table": "golden_orders", "expectedResult": "PASSED"},
                         {"id": "12", "table": "golden_orders_bad", "expectedResult": "NOT_PASSED"}]}


class FixtureContractTest(unittest.TestCase):
    def test_duplicate_names_cannot_select_an_arbitrary_business_object(self):
        with self.assertRaisesRegex(ValueError, "Ambiguous"):
            unique_match([{"name": "sample", "id": 1}, {"name": "sample", "id": 2}],
                         lambda row: row["name"] == "sample", "project")

    def test_truncated_page_cannot_provision_a_duplicate(self):
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            records({"records": [{"id": 1}], "total": 2})
        self.assertEqual(records({"bizData": [{"id": 1}], "total": 1}), [{"id": 1}])
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            records({"bizData": [{"id": 1}], "pagination": {"total": 2}})

    def test_unknown_page_envelope_cannot_be_treated_as_empty(self):
        with self.assertRaises(ValueError):
            records({"unexpected": []})

    def test_complete_manifest_keeps_failed_quality_sample_distinct(self):
        validate_manifest(manifest())
        changed = manifest()
        changed["monitors"][1]["expectedResult"] = "PASSED"
        with self.assertRaisesRegex(ValueError, "distinguish"):
            validate_manifest(changed)

    def test_incomplete_or_duplicate_asset_identity_is_rejected(self):
        changed = manifest()
        changed["assets"][1]["id"] = "1"
        with self.assertRaises(ValueError):
            validate_manifest(changed)
        changed = manifest()
        changed["assets"].pop()
        with self.assertRaises(ValueError):
            validate_manifest(changed)

    def test_owner_and_explanation_are_required_for_every_section(self):
        for kind, owner in SECTION_OWNERS.items():
            section = {"sectionType": kind, "ownerDomain": owner, "status": "EMPTY",
                       "reason": "No source evidence", "evidence": []}
            validate_section(section, kind)
            for field in ("ownerDomain", "reason"):
                changed = deepcopy(section)
                changed.pop(field)
                with self.assertRaises(ValueError):
                    validate_section(changed, kind)

    def test_unreadable_section_cannot_claim_execution_evidence(self):
        for status in ("UNAVAILABLE", "PERMISSION_DENIED", "NOT_APPLICABLE"):
            with self.assertRaisesRegex(ValueError, "claimed evidence"):
                validate_section({"sectionType": "QUALITY", "ownerDomain": "QUALITY",
                                  "status": status, "reason": "No readable result",
                                  "evidence": [{"referenceId": "invented"}]}, "QUALITY")

    def test_server_failure_is_not_a_successful_project_isolation_check(self):
        from types import SimpleNamespace
        api = object.__new__(Api)
        api.base_url, api.project_id = "http://fixture", "42"
        for http_status, code in ((500, 48001), (200, 999)):
            response = SimpleNamespace(status_code=http_status, json=lambda: {"code": code})
            api.session = SimpleNamespace(request=lambda *args, **kwargs: response)
            with self.assertRaises(ValueError):
                api.request("GET", "/api/v1/assets/1", expected_code=48001)


if __name__ == "__main__":
    unittest.main()
