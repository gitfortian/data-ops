#!/usr/bin/env python3
"""Execute the physical-table Golden Sample checks; never certify all of Phase7."""

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import sys
import time

from bootstrap import Api, DATABASE, MARKER, SECTION_OWNERS, TABLES, required_env

STATES = {"OK", "EMPTY", "UNAVAILABLE", "NOT_APPLICABLE", "PERMISSION_DENIED"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_manifest(manifest):
    require(manifest.get("schemaVersion") == 1 and manifest.get("marker") == MARKER,
            "Unrecognized sample manifest")
    require(manifest.get("database") == DATABASE and manifest.get("projectId"),
            "Isolated source database and Project are required")
    assets = manifest.get("assets", [])
    require(len(assets) == len(TABLES) and {a.get("table") for a in assets} == set(TABLES),
            "Manifest must identify the three physical samples exactly once")
    require(len({a.get("id") for a in assets}) == len(TABLES), "Duplicate Asset identities")
    require(all(a.get("sourceType") == "METADATA" and a.get("sourceId") and a.get("assetKey")
                for a in assets), "Physical source identities are required")
    monitors = manifest.get("monitors", [])
    require(len(monitors) == 2 and
            {(m.get("table"), m.get("expectedResult")) for m in monitors} ==
            {("golden_orders", "PASSED"), ("golden_orders_bad", "NOT_PASSED")},
            "Manifest must distinguish normal and failing monitors")
    require(len({m.get("id") for m in monitors}) == 2, "Duplicate monitor identities")


def validate_section(section, kind):
    require(section.get("sectionType") == kind, f"Wrong section identity: {kind}")
    require(section.get("ownerDomain") == SECTION_OWNERS[kind], f"Wrong Truth Owner: {kind}")
    require(section.get("status") in STATES, f"Invalid five-state result: {kind}")
    if section["status"] != "OK":
        require(bool(section.get("reason")), f"Non-OK section needs an explanation: {kind}")
    if section["status"] in {"UNAVAILABLE", "PERMISSION_DENIED", "NOT_APPLICABLE"}:
        require(not section.get("evidence"), f"Unreadable section claimed evidence: {kind}")


def run_monitor(api, monitor):
    # Verify owning-domain coordinates before triggering any execution.
    detail = api.request("GET", f"/api/v1/data-quality/monitor/{monitor['id']}")
    require(detail.get("description") == MARKER and detail.get("databaseName") == DATABASE
            and detail.get("tableName") == monitor["table"]
            and str(detail.get("dataSourceId")) == str(api.sample_source_id),
            "Refusing to execute a monitor outside the fixture")
    submitted = api.request("POST", f"/api/v1/data-quality/monitor/{monitor['id']}/run")
    execution_no = submitted["executionNo"]
    deadline = time.monotonic() + 45
    while True:
        status = api.request("GET", f"/api/v1/data-quality/execution/{execution_no}/status")
        if status.get("executionStatus") in {"SUCCESS", "FAILED"}:
            break
        require(time.monotonic() < deadline, "Quality execution timed out")
        time.sleep(1)
    require(status["executionStatus"] == "SUCCESS", "Quality engine did not execute successfully")
    require(status.get("checkResult") == monitor["expectedResult"],
            f"Unexpected measured result for {monitor['table']}")
    return {"monitorId": monitor["id"], "executionNo": execution_no,
            "executionStatus": status["executionStatus"], "checkResult": status["checkResult"]}


def inspect_asset(api, asset, execution):
    lookup = api.request("GET", "/api/v1/assets/source-lookup", params={
        "sourceType": asset["sourceType"], "sourceId": asset["sourceId"]})
    require(lookup.get("state") == "FOUND" and str(lookup.get("assetId")) == asset["id"]
            and lookup.get("assetKey") == asset["assetKey"], "Stable source lookup changed identity")
    source = api.request("GET", f"/api/v1/assets/{asset['id']}/source-attributes")
    coordinates = (source.get("data") or {}).get("extra") or {}
    require(source.get("status") == "OK" and coordinates.get("databaseName") == DATABASE
            and coordinates.get("tableName") == asset["table"]
            and str(coordinates.get("dataSourceId")) == str(api.sample_source_id),
            "Asset source coordinates do not match the isolated fixture")
    sections = {}
    for kind in SECTION_OWNERS:
        section = api.request("GET", f"/api/v1/assets/{asset['id']}/sections/{kind}")
        validate_section(section, kind)
        sections[kind] = section
    require(sections["LIFECYCLE"]["status"] == "NOT_APPLICABLE",
            "Physical-table Lifecycle must respect the current Model-only scope")
    quality = sections["QUALITY"]
    if execution:
        require(quality["status"] == "OK", "Executed Quality sample has no readable section")
        latest = (quality.get("summary") or {}).get("latestExecution") or {}
        require(latest.get("executionNo") == execution["executionNo"]
                and latest.get("result") == execution["checkResult"],
                "Quality section does not describe the measured execution result")
        # Evidence and actions must reference the exact real execution, never inferred health.
        require(any(e.get("referenceId") == execution["executionNo"]
                    for e in quality.get("evidence", [])), "Quality evidence lost its execution identity")
        require(any(a.get("target") == f"/data-quality/execution/{execution['executionNo']}"
                    for a in quality.get("actions", [])), "Quality execution backlink is missing")
    else:
        require(quality["status"] == "EMPTY" and not quality.get("evidence"),
                "Unmonitored customer sample must remain explicitly empty")
    return {"asset": asset, "sourceLookup": lookup, "qualitySummary": quality.get("summary"),
            "sections": {kind: {key: section.get(key) for key in
                ("sectionType", "status", "ownerDomain", "reason", "actions", "evidence")}
                for kind, section in sections.items()}}


def inspect_project_isolation(api, manifest):
    control_id = manifest.get("controlProjectId")
    if not control_id:
        return {"status": "NOT_EXECUTED"}
    require(str(control_id) != str(api.project_id), "Control Project must be different")
    own_id = api.project_id
    api.project_id = control_id
    try:
        for asset in manifest["assets"]:
            lookup = api.request("GET", "/api/v1/assets/source-lookup", params={
                "sourceType": asset["sourceType"], "sourceId": asset["sourceId"]})
            require(lookup.get("state") == "NOT_INDEXED" and not lookup.get("assetId"),
                    "Source lookup leaked a different Project's Asset")
            api.request("GET", f"/api/v1/assets/{asset['id']}", expected_code=48001)
            for kind in SECTION_OWNERS:
                api.request("GET", f"/api/v1/assets/{asset['id']}/sections/{kind}", expected_code=48001)
    finally:
        api.project_id = own_id
    return {"status": "PASSED", "controlProjectId": str(control_id),
            "assetCount": len(manifest["assets"]), "detailAndSectionErrorCode": 48001,
            "scope": "Asset source lookup/detail/eight sections under root; restricted RBAC remains untested"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    validate_manifest(manifest)
    api = Api(os.environ.get("YAK_OPS_BASE_URL", manifest["baseUrl"]),
              required_env("YAK_OPS_USERNAME"), required_env("YAK_OPS_PASSWORD"))
    api.project_id = manifest["projectId"]
    api.sample_source_id = manifest["dataSourceId"]
    results = {m["table"]: run_monitor(api, m) for m in manifest["monitors"]}
    evidence = [inspect_asset(api, a, results.get(a["table"])) for a in manifest["assets"]]
    project_isolation = inspect_project_isolation(api, manifest)
    report = {"schemaVersion": 1, "marker": MARKER, "result": "PHYSICAL_SAMPLE_PASSED",
              "phase7": "PARTIAL", "pd001": "PARTIAL", "browserE2E": "NOT_EXECUTED",
              "capturedAt": datetime.now(timezone.utc).isoformat(),
              "repositoryCommit": manifest["repositoryCommit"],
              "deploymentCommit": manifest.get("deploymentCommit"),
              "deploymentArtifactSha256": manifest.get("deploymentArtifactSha256"),
              "projectId": api.project_id, "executions": results, "assets": evidence,
              "projectIsolation": project_isolation,
              "remaining": ["MODEL/METRIC/DATASET real samples", "F-007 MDM journey",
                            "restricted user RBAC", "provider failure isolation",
                            "browser journeys", "main/CI delivery confirmation"]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"result": report["result"], "phase7": report["phase7"],
                      "executions": results, "output": str(args.output)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        text = str(error) if isinstance(error, (ValueError, FileNotFoundError)) else type(error).__name__
        print(f"Golden Sample acceptance failed: {text}", file=sys.stderr)
        sys.exit(1)
