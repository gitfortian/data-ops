#!/usr/bin/env python3
"""Provision and exercise Dataset/Data Service samples through their owning APIs."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from datetime import datetime, timezone

from bootstrap import Api, MARKER, PROJECT_NAME, current_commit, records, required_env, unique_match

NODES = "/api/v1/data-development/nodes"
SQL = "SELECT order_id, customer_code, quantity FROM yak_golden_sample.golden_orders"
SERVICE_PATH = "/yak-golden-sample-v1-orders"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def node(api, name, kind):
    existing = unique_match(records(api.request("GET", NODES)),
                            lambda item: item.get("name") == name, "sample node")
    if existing:
        require(existing.get("type") == kind, "Sample node name is occupied by a different type")
        return existing, api.provisioned_nodes.get(name) == str(existing["id"])
    item = api.request("POST", NODES, {"name": name, "type": kind, "directoryId": None})
    api.provisioned_nodes[name] = str(item["id"])
    api.progress_path.write_text(json.dumps({
        "marker": MARKER, "projectId": str(api.project_id), "nodes": api.provisioned_nodes
    }), encoding="utf-8")
    return item, True


def dataset(api, source_id):
    item, created = node(api, "Golden Sample Orders Dataset", "DATASET")
    path = f"{NODES}/{item['id']}/dataset"
    context = api.request("GET", path)
    saved = context.get("dataset")
    if saved:
        require(saved.get("description") == MARKER and saved.get("draftSql") == SQL
                and str(saved.get("draftDataSourceId")) == str(source_id),
                "Existing Dataset sample has changed; initialization refused")
    else:
        require(created, "Existing unconfigured Dataset node is not owned by this run")
        fields = api.request("POST", path + "/preview", {"dataSourceId": str(source_id), "sql": SQL})
        api.request("PUT", path, {"dataSourceId": str(source_id), "sql": SQL,
                                 "description": MARKER, "fields": fields})
    result = api.request("POST", path + "/publish")["dataset"]
    return {"nodeId": str(item["id"]), "id": str(result["datasetId"]),
            "versionId": str(result["currentVersion"]["versionId"])}


def data_service(api, source_id):
    item, created = node(api, "Golden Sample Orders API", "DATA_SERVICE")
    path = f"{NODES}/{item['id']}/data-service"
    context = api.request("GET", path)
    draft = (context.get("draft") or {}).get("definition")
    if draft and draft.get("sql"):
        require(draft.get("description") == MARKER and draft.get("sql") == SQL
                and str(draft.get("dataSourceId")) == str(source_id)
                and draft.get("path") == SERVICE_PATH,
                "Existing API sample has changed; initialization refused")
    else:
        require(created, "Existing unconfigured API node is not owned by this run")
        api.request("PUT", path + "/draft", {
            "dataSourceId": int(source_id), "sql": SQL, "serviceName": "Golden Sample Orders API",
            "path": SERVICE_PATH, "method": "GET", "description": MARKER,
            "parameters": [], "responseFields": [
                {"name": name, "type": kind, "nullable": False, "description": MARKER}
                for name, kind in [("order_id", "integer"), ("customer_code", "string"), ("quantity", "integer")]],
            "maxRows": 10, "timeoutSeconds": 30, "paginationEnabled": False,
            "autoParseParameters": True, "baseRevision": 0})
        context = api.request("GET", path)
    revision = api.request("POST", path + "/publish",
                           {"expectedDraftRevision": context["draft"]["draftRevision"]})
    published = api.request("POST", path + "/publication/online")
    return {"nodeId": str(item["id"]), "id": str(published["id"]),
            "revisionId": str(revision["id"]), "runtimePath": published["runtimePath"]}


def consumer_key(api, service_id):
    path = "/api/v1/data-service/consumers"
    name = "Golden Sample Integration"
    consumer = unique_match(records(api.request("GET", path)),
                            lambda item: item.get("name") == name, "sample Consumer")
    if consumer:
        require(consumer.get("description") == MARKER and consumer.get("enabled") is True,
                "Existing Consumer is not an enabled sample")
        require(consumer.get("accessScope") == "SELECTED"
                and [str(value) for value in consumer.get("apiIds", [])] == [str(service_id)],
                "Existing Consumer grant has changed; initialization refused")
    else:
        consumer = api.request("POST", path, {"name": name, "description": MARKER,
                                              "enabled": True, "defaultRateLimitPerMinute": 60})
        api.request("PUT", f"{path}/{consumer['id']}/access",
                    {"accessScope": "SELECTED", "apiIds": [int(service_id)]})
    key_path = f"{path}/{consumer['id']}/keys"
    keys = records(api.request("GET", key_path))
    sample_key = unique_match(keys, lambda item: item.get("name") == MARKER, "sample API Key")
    require(not keys or (len(keys) == 1 and sample_key), "Consumer has non-sample API keys")
    # No raw key is persisted by this tool. Rotate only its dedicated sample key on rerun.
    key = api.request("POST", f"{key_path}/{sample_key['id']}/rotate") if sample_key else api.request(
        "POST", key_path, {"name": MARKER, "rateLimitPerMinute": 60})
    return str(consumer["id"]), key["secret"]


def reconcile(api, kind, source_id):
    def last_run():
        item = unique_match(api.request("GET", "/api/v1/assets/reconcile/status"),
                            lambda value: value.get("sourceType") == kind, "Asset provider")
        return (item or {}).get("lastRun")
    before = last_run()
    api.request("POST", "/api/v1/assets/reconcile", {"sourceTypes": [kind], "full": False})
    for _ in range(45):
        run = last_run()
        if run and run != before:
            require(not run.get("error"), "Asset reconciliation failed")
            break
        time.sleep(1)
    else:
        raise ValueError("Asset reconciliation timed out")
    lookup = api.request("GET", "/api/v1/assets/source-lookup",
                         params={"sourceType": kind, "sourceId": source_id})
    require(lookup.get("state") == "FOUND", "Sample source was not indexed")
    return lookup


def source_evidence(script, env):
    runner = Path(__file__).resolve().parents[1] / script
    result = subprocess.run([os.environ.get("YAK_OPS_NODE", "node"), str(runner)],
                            env=env, capture_output=True, text=True, encoding="utf-8", timeout=120)
    # Child output can contain server errors or credentials: never echo it on failure.
    require(result.returncode == 0, f"{script} failed; inspect the source service locally")
    return json.loads(result.stdout)


def known_consumer(impact, consumer_type, domain, identity):
    """Find one exact source-owned Consumer identity (not a matching display name)."""
    matches = [
        item for item in impact.get("consumers", [])
        if item.get("consumerRef", {}).get("consumerType") == consumer_type
        and item.get("consumerRef", {}).get("sourceDomain") == domain
        and str(item.get("consumerRef", {}).get("sourceIdentity")) == str(identity)
    ]
    require(len(matches) == 1, "Impact did not preserve one exact stable Consumer identity")
    return matches[0]


def assert_observed_usage(impact, consumer_type, domain, identity, mode, version_id, evidence_ref):
    """Acceptance requires actual success for the exact Consumer AND executed version."""
    require(impact.get("usageState") == "READY",
            "Source reconciliation is incomplete; successful Usage cannot be fully verified")
    consumer = known_consumer(impact, consumer_type, domain, identity)
    require(mode in consumer.get("observedModes", []),
            "Subscription is not evidence of the requested consumption mode")
    require(evidence_ref in consumer.get("providerEvidenceRefs", []),
            "Consumer Impact is missing exact successful source evidence")
    versions = [item for item in consumer.get("observedVersions", [])
                if str((item.get("sourceVersion") or {}).get("identity")) == str(version_id)]
    require(len(versions) == 1, "Impact is missing one exact consumed Dataset/Service version")
    observed = versions[0]
    require(evidence_ref in observed.get("providerEvidenceRefs", []),
            "Consumption evidence belongs to a different source version")
    require(observed.get("successfulUsageCount", 0) > 0
            and consumer.get("successfulUsageCount", 0) >= observed["successfulUsageCount"],
            "Observed version has no real successful Usage")
    return consumer


def consumer_usage_refs(impact, consumer_type, domain, identity):
    return set(known_consumer(impact, consumer_type, domain, identity)
               .get("providerEvidenceRefs", []))


def assert_denied_did_not_create_usage(before_refs, after_impact, consumer_type, domain, identity):
    """Invalid Key or unavailable source must not create a successful Usage."""
    require(after_impact.get("usageState") == "READY",
            "Failed invocation could not be checked against source Usage evidence")
    after_refs = consumer_usage_refs(after_impact, consumer_type, domain, identity)
    require(after_refs == before_refs, "Denied/disabled invoke changed successful Usage evidence")


def assert_recovered_usage(before_refs, after_impact, consumer_type, domain, identity, version_id):
    """Require recovery to produce a NEW success on the still-published source revision."""
    require(after_impact.get("usageState") == "READY",
            "Recovery source Usage evidence remains incomplete")
    consumer = known_consumer(after_impact, consumer_type, domain, identity)
    new_refs = set(consumer.get("providerEvidenceRefs", [])) - before_refs
    require(new_refs, "Recovered successful invoke did not create new attributable Usage Evidence")
    versions = [item for item in consumer.get("observedVersions", [])
                if str((item.get("sourceVersion") or {}).get("identity")) == str(version_id)]
    require(len(versions) == 1
            and new_refs.intersection(versions[0].get("providerEvidenceRefs", [])),
            "Recovered public invoke was not attributed to the published source revision")
    return sorted(new_refs)


def accept(api, samples, secret, control_project):
    env = dict(os.environ, YAK_OPS_BASE_URL=api.base_url, YAK_OPS_PROJECT_ID=str(api.project_id),
               YAK_OPS_DATASET_ID=samples["dataset"]["id"],
               YAK_OPS_DATA_SERVICE_ID=samples["dataService"]["id"],
               YAK_OPS_DATA_SERVICE_API_KEY=secret)
    golden_dataset = source_evidence("phase4-real-env-dataset-evidence.mjs", env)
    golden_service = source_evidence("phase4-real-env-data-service-evidence.mjs", env)
    require(golden_dataset["query"]["result"]["returnedRows"] == 3, "Dataset sample row count mismatch")
    require(str(golden_service["invocationRecord"]["consumerId"]) == str(samples["consumerId"]),
            "Invocation did not preserve stable Consumer identity")
    require(golden_service["usageNormalization"]["state"] == "NORMALIZED", "Invocation Usage missing")
    require(golden_service["invocationRecord"]["rowCount"] == 3, "API sample row count mismatch")
    require(str(golden_dataset["queryPerformance"]["datasetVersionId"]) == samples["dataset"]["versionId"],
            "Query Performance did not preserve the published Dataset version")
    require(str(golden_service["invocationRecord"]["sourceRevisionId"]) == samples["dataService"]["revisionId"],
            "Invocation did not preserve the published source revision")
    details = {}
    for kind, sample, consumer_type, domain, identity, mode in [
        ("DATASET", samples["dataset"], "USER", "SECURITY_PRINCIPAL", api.user["userName"], "QUERY"),
        ("DATA_SERVICE", samples["dataService"], "DATA_SERVICE", "DATA_SERVICE_CONSUMER",
         samples["consumerId"], "API_INVOKE")]:
        key = f"{kind}:{sample['id']}"
        body = {"productKey": key, "consumerType": consumer_type, "sourceDomain": domain,
                "sourceIdentity": identity, "consumptionMode": mode}
        first = api.request("POST", "/api/v1/consumption/subscriptions", body)
        second = api.request("POST", "/api/v1/consumption/subscriptions", body)
        require(first["id"] == second["id"], "Duplicate subscription identity")
        detail = api.request("GET", f"/api/v1/consumption/products/{key}")
        require(detail["state"] == "FOUND", "Canonical source was not found")
        version_id = sample["versionId"] if kind == "DATASET" else sample["revisionId"]
        require(str(detail["product"]["activeVersion"]["identity"]) == version_id,
                "Canonical did not preserve the published source version")
        require(str(detail["product"]["producerRef"]["identity"]) == sample["nodeId"]
                and str(detail["product"]["assetRef"]["identity"]) == sample["assetId"],
                "Canonical source backlinks did not preserve stable identities")
        impact = api.request("GET", "/api/v1/consumption/impact", params={"productKey": key, "usageLimit": 200})
        require(impact["usageState"] == "READY", "Successful Usage Evidence missing")
        expected_ref = ("DATASET_QUERY_PERFORMANCE:query:" + golden_dataset["queryPerformance"]["queryId"]
                        if kind == "DATASET" else
                        "DATA_SERVICE_INVOCATION:invocation:" + str(golden_service["invocationRecord"]["id"]))
        assert_observed_usage(
            impact, consumer_type, domain, identity, mode, version_id, expected_ref)
        require(detail["navigation"].get("producerHref"), "Stable Producer backlink is missing")
        for section in ["quality", "security", "lineage"]:
            evidence = next(value for value in detail["governanceEvidence"] if value["sectionKey"] == section)
            require(evidence["state"] in ["READY", "EMPTY", "NOT_APPLICABLE"],
                    f"{key} {section} evidence cannot be read")
        nav = api.request("GET", f"/api/v1/consumption/navigation/assets/{sample['assetId']}")
        require(nav["state"] == "FOUND"
                and nav["canonicalHref"] == detail["navigation"]["canonicalHref"], "Asset backlink mismatch")
        details[key] = {"detail": detail, "impact": impact, "assetNavigation": nav,
                        "subscriptionId": first["id"], "duplicateSubscription": "PASSED"}
    # Exercise the actual public plane with neither console cookie nor project header.
    import requests
    denied = requests.get(api.base_url + samples["dataService"]["runtimePath"],
                          headers={"X-API-Key": "invalid-golden-sample-key"}, timeout=30)
    require(denied.status_code == 401, "Invalid API key was not rejected with HTTP 401")
    service_key = "DATA_SERVICE:" + samples["dataService"]["id"]
    service_consumer = ("DATA_SERVICE", "DATA_SERVICE_CONSUMER", samples["consumerId"])
    expected_revision = samples["dataService"]["revisionId"]
    baseline_refs = consumer_usage_refs(details[service_key]["impact"], *service_consumer)
    after_invalid = api.request(
        "GET", "/api/v1/consumption/impact",
        params={"productKey": service_key, "usageLimit": 200})
    assert_denied_did_not_create_usage(baseline_refs, after_invalid, *service_consumer)
    management = requests.get(api.base_url + "/api/v1/data-service/consumers", timeout=30)
    require(management.status_code == 401, "Anonymous service management was not rejected")
    # Disable only the dedicated source-managed sample through its owning authoring API.
    source_path = f"{NODES}/{samples['dataService']['nodeId']}/data-service/publication"
    version = details[service_key]["detail"]["product"]["activeVersion"]
    try:
        api.request("POST", source_path + "/offline")
        offline = api.request("GET", f"/api/v1/consumption/products/{service_key}")["product"]
        require(offline["lifecycle"] == "PUBLISHED" and offline["availability"] == "UNAVAILABLE"
                and offline["activeVersion"] == version, "Disable changed owning publication truth")
        unavailable = requests.get(api.base_url + samples["dataService"]["runtimePath"],
                                   headers={"X-API-Key": secret}, timeout=30)
        require(unavailable.status_code in (500, 503), "Disabled sample did not reject public invocation")
        disabled_http_status = unavailable.status_code
        after_offline = api.request(
            "GET", "/api/v1/consumption/impact",
            params={"productKey": service_key, "usageLimit": 200})
        assert_denied_did_not_create_usage(baseline_refs, after_offline, *service_consumer)
    finally:
        api.request("POST", source_path + "/online")
    recovered = requests.get(api.base_url + samples["dataService"]["runtimePath"],
                             headers={"X-API-Key": secret}, timeout=30)
    require(recovered.status_code == 200, "Sample public invocation did not recover")
    after_recovery = api.request(
        "GET", "/api/v1/consumption/impact",
        params={"productKey": service_key, "usageLimit": 200})
    new_refs = assert_recovered_usage(
        baseline_refs, after_recovery, *service_consumer, expected_revision)
    original_project = api.project_id
    try:
        api.project_id = control_project
        for key in details:
            hidden = api.request("GET", f"/api/v1/consumption/products/{key}")
            require(hidden["state"] == "NOT_FOUND", "Cross-project product identity leaked")
            kind, source_id = key.split(":", 1)
            hidden_asset = api.request("GET", "/api/v1/assets/source-lookup",
                                       params={"sourceType": kind, "sourceId": source_id})
            require(hidden_asset["state"] == "NOT_INDEXED", "Cross-project Asset identity leaked")
    finally:
        api.project_id = original_project
    return {"dataset": golden_dataset, "dataService": golden_service, "products": details,
            "invalidPublicApiKey": "PASSED", "anonymousManagement": "PASSED", "crossProject": "PASSED",
            "sourceDisableRecovery": {"state": "PASSED", "disabledInvocationHttpStatus": disabled_http_status,
                                      "recoveryHttpStatus": recovered.status_code,
                                      "recoveredUsageEvidenceRefs": sorted(new_refs)},
            "negativeInvocationUsage": "PASSED"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True, help="Physical sample bootstrap manifest")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--accept", action="store_true", help="Run real Query/Invoke and relationship checks")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not args.apply:
        print(json.dumps({"mode": "PLAN", "objects": ["Dataset", "Data Service", "Consumer", "API Key"],
                          "sampleKeyRerun": "ROTATE", "acceptance": "PARTIAL_F004"}))
        return
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    require(manifest.get("marker") == MARKER, "Unknown sample manifest")
    api = Api(os.environ.get("YAK_OPS_BASE_URL", manifest["baseUrl"]),
              required_env("YAK_OPS_USERNAME"), required_env("YAK_OPS_PASSWORD"))
    api.project_id = manifest["projectId"]
    project = api.request("GET", f"/yak-security/api/v1/project/{api.project_id}", scoped=False)
    require(project.get("projectName") == PROJECT_NAME and project.get("description") == MARKER,
            "Project does not belong to the Golden Sample")
    source = unique_match(records(api.request("GET", "/api/v1/data-source/all")),
                          lambda item: str(item.get("id")) == str(manifest["dataSourceId"]), "source")
    require(source and source.get("remark") == MARKER and source.get("dbType") == "MYSQL",
            "Datasource is not an owned sample")
    require(args.output is not None, "--output is required to persist provisioning progress")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    api.progress_path = args.output.with_suffix(".progress.local.json")
    api.provisioned_nodes = {}
    if api.progress_path.exists():
        progress = json.loads(api.progress_path.read_text(encoding="utf-8"))
        require(progress.get("marker") == MARKER
                and progress.get("projectId") == str(api.project_id), "Foreign provisioning checkpoint")
        api.provisioned_nodes = progress.get("nodes", {})
    samples = {"dataset": dataset(api, manifest["dataSourceId"]),
               "dataService": data_service(api, manifest["dataSourceId"])}
    samples["consumerId"], secret = consumer_key(api, samples["dataService"]["id"])
    for kind, name in [("DATASET", "dataset"), ("DATA_SERVICE", "dataService")]:
        samples[name]["assetId"] = str(reconcile(api, kind, samples[name]["id"])["assetId"])
    report = {"marker": MARKER, "repositoryCommit": current_commit(), "deploymentCommit": None,
              "deploymentArtifactSha256": os.environ.get("YAK_OPS_ARTIFACT_SHA256"),
              "projectId": str(api.project_id), "samples": samples, "f004Acceptance": "PARTIAL",
              "capturedAt": datetime.now(timezone.utc).isoformat()}
    if args.accept:
        report["acceptance"] = accept(api, samples, secret, manifest["controlProjectId"])
    encoded = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Golden consumption failed: {error}" if isinstance(error, ValueError)
              else f"Golden consumption failed: {type(error).__name__}; inspect the source service locally",
              file=sys.stderr)
        sys.exit(1)
