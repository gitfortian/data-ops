"""P0 R8/R1-R7 evidence handoff is not self-signed product acceptance."""
from copy import deepcopy
from datetime import datetime, timedelta, timezone
from hashlib import sha256
import json
from pathlib import Path
import tempfile
import unittest

from p0_exit_readiness import SCHEMA, SCENES, assess, main

COMMIT = "b" * 40
PROJECT = "42"
CONTROL = "43"
NOW = datetime(2026, 10, 9, 12, tzinfo=timezone.utc)


def stamp(time=NOW):
    return time.isoformat()


class ExitDecisionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.manifest = {
            "schema": SCHEMA,
            "repositoryCommit": COMMIT,
            "environmentRef": "qa-isolated-golden-20261009",
            "environmentClass": "AUTHORIZED_NON_PRODUCTION",
            "deployedAt": stamp(),
            "projectPair": {"primary": PROJECT, "control": CONTROL},
            "deployment": {},
            "evidence": {},
            "signoffs": {},
        }

    def artifact(self, slot, payload=None, kind=None):
        if payload is None:
            payload = {"runId": slot, "note": "synthetic, only an offline unit test"}
        file = self.root / (slot + ".json")
        file.write_text(json.dumps(payload), encoding="utf-8")
        self.manifest["evidence"][slot] = {
            "file": file.name,
            "sha256": sha256(file.read_bytes()).hexdigest(),
            "deploymentCommit": COMMIT,
            "projectId": PROJECT,
            "controlProjectId": CONTROL,
            "observedAt": stamp(NOW + timedelta(seconds=1)),
            "evidenceKind": kind or SCENES[slot],
            "reviewerRef": "qa-evidence-index-" + slot,
        }

    def full(self):
        backend = self.root / "backend.jar"
        frontend = self.root / "frontend-bundle.tgz"
        backend.write_bytes(b"test-backend-not-a-real-jar")
        frontend.write_bytes(b"test-frontend-not-a-real-distribution")
        backend_digest = sha256(backend.read_bytes()).hexdigest()
        frontend_digest = sha256(frontend.read_bytes()).hexdigest()
        self.manifest["deployment"] = {
            "backend": {"commit": COMMIT, "artifactSha256": backend_digest,
                        "artifactFile": backend.name,
                        "evidenceRef": "backend-packaged-hash"},
            "frontend": {"commit": COMMIT, "artifactSha256": frontend_digest,
                         "artifactFile": frontend.name,
                         "evidenceRef": "frontend-manifest-hash"},
            "process": {"commit": COMMIT, "artifactSha256": backend_digest,
                        "evidenceRef": "release-observed-process"},
            "releaseVerifiedProcess": True,
            "processObservationRef": "release-deployment-receipt-123",
        }
        self.manifest["signoffs"] = {
            role: {"decision": "APPROVED", "reviewer": role + "-reviewer",
                   "approvalRef": "ticket-" + role}
            for role in ("QA", "Product", "Release", "TruthOwner")
        }
        for slot in SCENES:
            if slot == "R8_STRUCTURAL_GATE":
                payload = {"result": "CONSISTENT_PARTIAL_EVIDENCE",
                           "projectId": PROJECT, "controlProjectId": CONTROL,
                           "capturedAt": stamp(NOW + timedelta(seconds=1)),
                           "productAcceptance": "PENDING",
                           "automatedProductPass": False,
                           "evidenceSlots": {
                               key: "STRUCTURALLY_VALIDATED" for key in (
                                   "r3", "r4", "r5Blocked", "r5Recovered",
                                   "r6Outage", "r6Restored", "roles")}}
            elif slot == "PHASE4_SOURCE_USAGE":
                fields = ("datasetRealQueryEvidence", "dataServicePublicInvokeEvidence",
                          "dataServiceExternalAuthorizationBoundary",
                          "dataServiceInvalidApiKeyRejected", "dataServiceUsageNormalized",
                          "canonicalProductIdentityStable", "sourceNavigationResolved",
                          "consumerImpactCaptured", "crossProjectChecked",
                          "forbiddenDatasetConsumeChecked", "datasetSourceSuccess",
                          "datasetExactAuditAndVersionLinked",
                          "datasetImpactSourceAuditLinked", "dataServiceSourceSuccess",
                          "dataServiceExactAuditRevisionConsumerLinked",
                          "dataServiceNormalizedUsageAndImpactLinked")
                payload = {
                    "probe": "phase4-real-env-acceptance",
                    "projectId": PROJECT, "commit": COMMIT,
                    "assertions": {field: True for field in fields},
                    "dataset": {"golden": {"queryPerformance": {"status": "SUCCESS"}}},
                    "dataService": {"golden": {"invocationRecord": {"success": True}}},
                }
            elif slot == "PHASE5_METRIC":
                payload = {
                    "probe": "phase5-real-env-acceptance", "projectId": PROJECT,
                    "commit": COMMIT,
                    "acceptanceStatus": "INCOMPLETE",
                    "assertions": {
                        key: True for key in (
                            "loggedInRealUser", "exactImmutableMetricVersionResolved",
                            "activePublicationExactVersion", "publicationLedgerCaptured",
                            "referenceUsagePresent", "impactIdentityStable",
                        )
                    },
                }
            elif slot == "PROJECT_ROLE_READS":
                payload = {"type": "LIVE_HTTP_READ_ONLY",
                           "verdict": "PROBES_PASS_PENDING_QA",
                           "deployment": {"declaredCommit": COMMIT},
                           "probes": [{"result": "PASS"} for _ in range(9)]}
            elif slot.startswith("R"):
                payload = {
                    "marker": "yak-golden-sample-v1",
                    "repositoryCommit": COMMIT,
                    "projectId": PROJECT,
                    "controlProjectId": CONTROL,
                    "capturedAt": stamp(NOW + timedelta(seconds=1)),
                    "deploymentCommit": None,
                    "productAcceptance": "PARTIAL",
                }
            else:
                payload = None
            self.artifact(slot, payload)

    def update_receipt(self, slot, payload):
        self.artifact(slot, payload)

    def test_no_evidence_remains_blocked_and_cannot_sign_exit(self):
        result = assess(self.manifest, self.root, COMMIT)
        self.assertEqual("BLOCKED_EVIDENCE", result["result"])
        self.assertFalse(result["automatedProductPass"])
        self.assertFalse(result["p0ExitSigned"])
        self.assertIn("R8_STRUCTURAL_GATE", result["blockers"])
        self.assertIn("DEPLOYMENT_ATTESTATION", result["blockers"])
        self.assertIn("QA_SIGNOFF", result["blockers"])

    def test_complete_receipts_only_qualify_for_independent_human_review(self):
        self.full()
        result = assess(self.manifest, self.root, COMMIT)
        self.assertEqual("READY_FOR_HUMAN_REVIEW", result["result"])
        self.assertEqual([], result["blockers"])
        self.assertFalse(result["p0ExitSigned"])
        self.assertFalse(result["automatedProductPass"])
        self.assertIn("backend:PACKAGED_HASH_VERIFIED", result["deploymentChecks"])
        self.assertIn("frontend:PACKAGED_HASH_VERIFIED", result["deploymentChecks"])
        self.assertIn("RUNNING_PROCESS:RELEASE_ATTESTED_NOT_MACHINE_PROVEN",
                      result["deploymentChecks"])
        self.assertEqual("HASH_VERIFIED_UNDER_HUMAN_REVIEW",
                         result["evidenceSlots"]["R8_STRUCTURAL_GATE"])

    def test_forced_pass_claim_in_r8_never_accepted(self):
        self.full()
        self.update_receipt("R8_STRUCTURAL_GATE", {
            "result": "CONSISTENT_PARTIAL_EVIDENCE",
            "projectId": PROJECT, "controlProjectId": CONTROL,
            "capturedAt": stamp(NOW + timedelta(seconds=1)),
            "productAcceptance": "DONE",
            "automatedProductPass": True,
        })
        with self.assertRaisesRegex(ValueError, "automatic product acceptance"):
            assess(self.manifest, self.root, COMMIT)

    def test_bad_hash_and_cross_project_mixing_are_rejected(self):
        self.full()
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            self.root.joinpath("R1_PHYSICAL.json").write_text('{"changed":true}')
            assess(self.manifest, self.root, COMMIT)
        self.full()
        self.manifest["evidence"]["R2_CONSUMPTION"]["controlProjectId"] = "99"
        with self.assertRaisesRegex(ValueError, "Project pair differs"):
            assess(self.manifest, self.root, COMMIT)

    def test_wrong_deployment_commit_and_process_artifact_are_rejected(self):
        self.full()
        self.manifest["evidence"]["R3_HISTORICAL_RECOVERY"]["deploymentCommit"] = "a" * 40
        with self.assertRaisesRegex(ValueError, "another deployment commit"):
            assess(self.manifest, self.root, COMMIT)
        self.full()
        self.manifest["deployment"]["process"]["artifactSha256"] = "d" * 64
        with self.assertRaisesRegex(ValueError, "Running backend digest"):
            assess(self.manifest, self.root, COMMIT)

    def test_old_source_reports_cannot_be_relabelled_with_a_current_sha_manifest(self):
        self.full()
        old = json.loads(self.root.joinpath("R1_PHYSICAL.json").read_text())
        old["repositoryCommit"] = "a" * 40
        old["capturedAt"] = "2026-10-04T04:19:01Z"
        self.update_receipt("R1_PHYSICAL", old)
        with self.assertRaisesRegex(ValueError, "older commit or Project"):
            assess(self.manifest, self.root, COMMIT)

    def test_changed_backend_package_cannot_pass_attestation_even_with_matching_declarations(self):
        self.full()
        self.root.joinpath("backend.jar").write_bytes(b"different-build-bytes")
        with self.assertRaisesRegex(ValueError, "packaged artifact SHA-256"):
            assess(self.manifest, self.root, COMMIT)

    def test_phase5_requires_observed_exact_version_and_publication_receipt(self):
        self.full()
        proof = json.loads(self.root.joinpath("PHASE5_METRIC.json").read_text())
        proof["assertions"]["activePublicationExactVersion"] = False
        self.update_receipt("PHASE5_METRIC", proof)
        with self.assertRaisesRegex(ValueError, "Phase5 Metric exact-version"):
            assess(self.manifest, self.root, COMMIT)

    def test_stale_artifacts_cannot_be_attached_to_new_deployment(self):
        self.full()
        self.manifest["evidence"]["R4_DENIAL"]["observedAt"] = stamp(
            NOW - timedelta(days=5))
        with self.assertRaisesRegex(ValueError, "predates"):
            assess(self.manifest, self.root, COMMIT)

    def test_disallowed_mock_and_path_traversal_are_rejected(self):
        self.full()
        self.manifest["evidence"]["BROWSER_J3"]["evidenceKind"] = "OFFLINE_MOCK"
        with self.assertRaisesRegex(ValueError, "cannot substitute mock"):
            assess(self.manifest, self.root, COMMIT)
        self.full()
        self.manifest["evidence"]["BROWSER_J3"]["file"] = "../stolen.json"
        with self.assertRaisesRegex(ValueError, "inside the controlled"):
            assess(self.manifest, self.root, COMMIT)

    def test_phase4_and_project_read_probes_have_independent_contracts(self):
        self.full()
        self.update_receipt("PROJECT_ROLE_READS", {
            "type": "LIVE_HTTP_READ_ONLY", "verdict": "PROBES_PASS_PENDING_QA",
            "deployment": {"declaredCommit": COMMIT},
            "probes": [{"result": "PASS"} for _ in range(8)]})
        with self.assertRaisesRegex(ValueError, "denial and read probes are incomplete"):
            assess(self.manifest, self.root, COMMIT)
        self.full()
        bad = json.loads(self.root.joinpath("PHASE4_SOURCE_USAGE.json").read_text())
        bad["assertions"]["datasetExactAuditAndVersionLinked"] = False
        self.update_receipt("PHASE4_SOURCE_USAGE", bad)
        with self.assertRaisesRegex(ValueError, "assertions are incomplete"):
            assess(self.manifest, self.root, COMMIT)

    def test_missing_signer_keeps_consolidated_exit_blocked(self):
        self.full()
        self.manifest["signoffs"]["QA"] = {"decision": "PENDING"}
        result = assess(self.manifest, self.root, COMMIT)
        self.assertEqual("BLOCKED_EVIDENCE", result["result"])
        self.assertIn("QA_SIGNOFF", result["blockers"])

    def test_no_manifest_cli_is_plan_not_success(self):
        self.assertEqual(2, main([]))

    def test_mismatched_repository_sha_is_rejected(self):
        self.full()
        with self.assertRaisesRegex(ValueError, "different repository commit"):
            assess(self.manifest, self.root, "a" * 40)


if __name__ == "__main__":
    unittest.main()
