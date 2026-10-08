import json
from copy import deepcopy
from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
from consumption import (
    node, assert_observed_usage, assert_denied_did_not_create_usage, assert_recovered_usage,
)


class NodeOwnershipTest(unittest.TestCase):
    def api(self, items, checkpoint=None):
        return SimpleNamespace(project_id=7, provisioned_nodes=checkpoint or {},
                               request=lambda method, path, body=None: items)

    def test_existing_foreign_unconfigured_node_is_not_owned(self):
        item = {"id": "99", "name": "Sample", "type": "DATASET"}
        value, owned = node(self.api([item]), "Sample", "DATASET")
        self.assertEqual(item, value)
        self.assertFalse(owned)

    def test_checkpoint_allows_resume_only_same_identity(self):
        item = {"id": "99", "name": "Sample", "type": "DATASET"}
        self.assertTrue(node(self.api([item], {"Sample": "99"}), "Sample", "DATASET")[1])
        self.assertFalse(node(self.api([item], {"Sample": "98"}), "Sample", "DATASET")[1])

    def test_type_collision_and_duplicate_name_are_rejected(self):
        with self.assertRaises(ValueError):
            node(self.api([{"id": "99", "name": "Sample", "type": "DATA_SERVICE"}]), "Sample", "DATASET")
        with self.assertRaises(ValueError):
            node(self.api([{"id": "99", "name": "Sample"}, {"id": "98", "name": "Sample"}]), "Sample", "DATASET")

    def test_creation_checkpoints_identity_before_source_configuration(self):
        with tempfile.TemporaryDirectory() as folder:
            api = self.api([])
            api.progress_path = Path(folder) / "progress.local.json"
            api.request = lambda method, path, body=None: [] if method == "GET" else {"id": "99", **body}
            value, owned = node(api, "Sample", "DATASET")
            self.assertTrue(owned)
            self.assertEqual("99", value["id"])
            self.assertEqual({"Sample": "99"}, json.loads(api.progress_path.read_text())["nodes"])


def impact(consumer_id="21", version_id="9007199254740999", evidence_ref="DATA_SERVICE_INVOCATION:invocation:50"):
    return {"usageState": "READY", "consumers": [{
        "consumerRef": {"consumerType": "DATA_SERVICE",
                        "sourceDomain": "DATA_SERVICE_CONSUMER",
                        "sourceIdentity": consumer_id, "displayHint": "integration"},
        "observedModes": ["API_INVOKE"], "declaredModes": [],
        "successfulUsageCount": 1, "providerEvidenceRefs": [evidence_ref],
        "observedVersions": [{
            "sourceVersion": {"identity": version_id, "displayVersion": "r4"},
            "successfulUsageCount": 1, "providerEvidenceRefs": [evidence_ref],
        }],
    }]}


class GoldenConsumptionEvidenceContractTest(unittest.TestCase):
    REF = "DATA_SERVICE_INVOCATION:invocation:50"
    REVISION = "9007199254740999"

    def valid(self, value=None):
        return assert_observed_usage(
            impact() if value is None else value,
            "DATA_SERVICE", "DATA_SERVICE_CONSUMER", "21",
            "API_INVOKE", self.REVISION, self.REF)

    def test_verified_success_requires_exact_consumer_and_source_revision(self):
        self.assertEqual("21", self.valid()["consumerRef"]["sourceIdentity"])

    def test_same_consumer_ref_under_wrong_source_version_does_not_pass(self):
        value = impact(version_id="9007199254740998")
        with self.assertRaisesRegex(ValueError, "exact consumed"):
            self.valid(value)
        value = impact()
        value["consumers"][0]["observedVersions"][0]["providerEvidenceRefs"] = []
        with self.assertRaisesRegex(ValueError, "different source version"):
            self.valid(value)

    def test_subscription_or_wrong_consumer_is_not_actual_usage(self):
        value = impact(consumer_id="22")
        with self.assertRaisesRegex(ValueError, "stable Consumer identity"):
            self.valid(value)
        value = impact()
        value["consumers"][0]["observedModes"] = []
        with self.assertRaisesRegex(ValueError, "Subscription is not evidence"):
            self.valid(value)
        value = impact()
        value["consumers"][0]["successfulUsageCount"] = 0
        with self.assertRaisesRegex(ValueError, "no real successful Usage"):
            self.valid(value)

    def test_source_gap_cannot_mark_partial_usage_as_golden_pass(self):
        value = impact()
        value["usageState"] = "UNAVAILABLE"
        with self.assertRaisesRegex(ValueError, "incomplete"):
            self.valid(value)

    def test_denied_or_disabled_invoke_keeps_success_evidence_unchanged(self):
        before = {self.REF}
        assert_denied_did_not_create_usage(
            before, impact(), "DATA_SERVICE", "DATA_SERVICE_CONSUMER", "21")
        value = impact()
        value["consumers"][0]["providerEvidenceRefs"].append("invocation:unexpected-success")
        with self.assertRaisesRegex(ValueError, "changed successful Usage"):
            assert_denied_did_not_create_usage(
                before, value, "DATA_SERVICE", "DATA_SERVICE_CONSUMER", "21")

    def test_recovered_invoke_must_add_real_evidence_on_pinned_revision(self):
        after = impact()
        new_ref = "DATA_SERVICE_INVOCATION:invocation:51"
        after["consumers"][0]["providerEvidenceRefs"].append(new_ref)
        after["consumers"][0]["observedVersions"][0]["providerEvidenceRefs"].append(new_ref)
        self.assertEqual([new_ref], assert_recovered_usage(
            {self.REF}, after, "DATA_SERVICE", "DATA_SERVICE_CONSUMER", "21",
            self.REVISION))
        with self.assertRaisesRegex(ValueError, "did not create new"):
            assert_recovered_usage({self.REF}, impact(), "DATA_SERVICE",
                                   "DATA_SERVICE_CONSUMER", "21", self.REVISION)
        wrong = deepcopy(after)
        wrong["consumers"][0]["observedVersions"][0]["sourceVersion"]["identity"] = "different"
        with self.assertRaisesRegex(ValueError, "not attributed"):
            assert_recovered_usage({self.REF}, wrong, "DATA_SERVICE",
                                   "DATA_SERVICE_CONSUMER", "21", self.REVISION)


if __name__ == "__main__":
    unittest.main()
