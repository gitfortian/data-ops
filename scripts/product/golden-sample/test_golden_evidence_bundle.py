"""Pure offline R7/R8 counterexamples, no real server or database."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from bootstrap import MARKER
from historical_recovery import RECOVERY
from historical_recovery_roles import verify_roles
from golden_evidence_gate import SCENES, assess, correlate_artifact, validate_report, validate_pairs
from test_historical_recovery_denials import FakeTransport
from test_historical_recovery_reader_outage import fixture, FakeHttpResponse
from test_historical_recovery_retry import source_row
from test_historical_recovery import response


class Owner:
    def __init__(self, kind):
        self.kind = kind
        self.user = {"id": 11}
        self.project_id = "42"
        self.base_url = "https://sample.invalid"
        self.calls = []

    def request(self, method, path, params=None):
        self.calls.append((method, path, dict(params), self.project_id))
        assert method == "POST" and path == RECOVERY[self.kind]["path"]
        return response(self.kind, self.kind + ":7", 9,
                        params.get(RECOVERY[self.kind]["cursor"]),
                        [source_row(self.kind)] if self.project_id == "42" else [], 1)


class Restricted:
    def __init__(self, user_id=12, status="restricted"):
        self.user = {"id": user_id}
        self.base_url = "https://sample.invalid"
        self.session = FakeTransport(status)


class GoldenReviewTests(unittest.TestCase):
    def test_real_role_matrix_logic_offline_for_both_sources(self):
        for kind in RECOVERY:
            with self.subTest(kind=kind):
                db = fixture(kind)
                owner = Owner(kind)
                result = verify_roles(
                    owner, Restricted(), FakeTransport("anonymous"),
                    db, 42, 43, kind, {"id": "7", RECOVERY[kind]["version"]: "9"})
                self.assertEqual("PASSED", result["ownerAlreadyNormalizedReplay"])
                self.assertEqual("PASSED", result["controlProjectEmpty"])
                self.assertEqual("HTTP_401", result["anonymousDenied"])
                self.assertEqual("HTTP_403", result["restrictedDenied"])
                self.assertEqual("PASSED", result["usageAndSourceUnchanged"])
                self.assertNotEqual(result["ownerActorDigest"], result["restrictedActorDigest"])
                self.assertEqual(2, len(owner.calls))

    def test_same_actor_and_non_denial_rejected(self):
        with self.assertRaisesRegex(ValueError, "different"):
            verify_roles(Owner("DATASET"), Restricted(11),
                         FakeTransport("anonymous"), fixture("DATASET"), 42, 43,
                         "DATASET", {"id": "7", "versionId": "9"})
        for status in (200, 500):
            restricted = Restricted()
            class BadTransport:
                def request(self, *args, **kwargs):
                    return FakeHttpResponse(status, {"code": 200})
            restricted.session = BadTransport()
            with self.assertRaises(ValueError):
                verify_roles(Owner("DATASET"), restricted,
                             FakeTransport("anonymous"), fixture("DATASET"),
                             42, 43, "DATASET", {"id": "7", "versionId": "9"})

    def test_absent_evidence_cannot_automatically_pass(self):
        physical = {"marker": MARKER, "projectId": "42", "controlProjectId": "43",
                    "baseUrl": "https://sample.invalid"}
        consumption = {"marker": MARKER, "projectId": "42"}
        result = assess(physical, consumption, {})
        self.assertEqual("PENDING", result["productAcceptance"])
        self.assertFalse(result["automatedProductPass"])
        self.assertEqual(len(SCENES), sum(v == "PENDING"
                                         for v in result["evidenceSlots"].values()))
        with self.assertRaisesRegex(ValueError, "mismatch"):
            assess(physical, {"marker": MARKER, "projectId": "44"}, {})

    def test_forged_pass_and_wrong_cross_stage_audit_denied(self):
        report = {
            "marker": MARKER, "sceneId": SCENES["r5Blocked"][0],
            "result": SCENES["r5Blocked"][1], "productAcceptance": "DONE",
            "deploymentCommit": "a" * 40, "deploymentIdentity": "VERIFIED",
            "projectId": "42", "controlProjectId": "43",
            "repositoryCommit": "b" * 40, "capturedAt": "2026-10-09T00:00:00Z",
            "scenario": {"stage": "BLOCKED_REAL_OBSERVED", "kind": "DATASET",
                         "auditId": "9007199254740993",
                         "sourceVersionIdentity": "9"},
        }
        with self.assertRaisesRegex(ValueError, "falsely"):
            validate_report("r5Blocked", report, 42, 43)
        left = {"scenario": {
            "kind": "DATASET", "productKey": "DATASET:7",
            "sourceVersionIdentity": "9", "auditId": "9007199254740993",
            "requestedCursor": "9007199254740994", "sourceSignature": "digest",
            "providerEvidenceFingerprint": "ref",
            "usageAndSourceUnchanged": "PASSED",
            "blocked": {"continuationBlocked": True},
        }}
        right = {"scenario": {**left["scenario"], "normalizedUsageCreated": 1,
                              "sameCursorReplay": "PASSED"}}
        validate_pairs({"r5Blocked": left, "r5Recovered": right})
        right["scenario"]["requestedCursor"] = "1"
        with self.assertRaisesRegex(ValueError, "requestedCursor"):
            validate_pairs({"r5Blocked": left, "r5Recovered": right})
        with self.assertRaisesRegex(ValueError, "without actual"):
            validate_pairs({"r5Recovered": right})

    def test_local_hash_and_two_receipts_are_consistency_not_runtime_proof(self):
        with tempfile.TemporaryDirectory() as work:
            root = Path(work)
            artifact = root / "app.jar"
            artifact.write_bytes(b"synthetic-for-unit-test")
            digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
            build = root / "build.json"
            runtime = root / "runtime.json"
            build_doc = {"marker": MARKER, "repositoryCommit": "a" * 40,
                         "artifactSha256": digest}
            runtime_doc = {"marker": MARKER, "deployedCommit": "a" * 40,
                           "artifactSha256": digest,
                           "instanceBaseUrl": "https://sample.invalid",
                           "observationReference": "private controlled deployment log id"}
            build.write_text(json.dumps(build_doc))
            runtime.write_text(json.dumps(runtime_doc))
            output = correlate_artifact(artifact, build, runtime, "https://sample.invalid")
            self.assertEqual("ARTIFACT_HASH_AND_DECLARATIONS_CORRELATED", output["state"])
            self.assertFalse(output["runtimeIndependentlyVerified"])
            artifact.write_bytes(b"modified")
            with self.assertRaisesRegex(ValueError, "Actual artifact"):
                correlate_artifact(artifact, build, runtime, "https://sample.invalid")
            artifact.write_bytes(b"synthetic-for-unit-test")
            runtime_doc["deployedCommit"] = "b" * 40
            runtime.write_text(json.dumps(runtime_doc))
            with self.assertRaisesRegex(ValueError, "commit"):
                correlate_artifact(artifact, build, runtime, "https://sample.invalid")
            with self.assertRaisesRegex(ValueError, "together"):
                correlate_artifact(artifact, None, None, "https://sample.invalid")


if __name__ == "__main__":
    unittest.main()
