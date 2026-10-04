#!/usr/bin/env python3
"""Provision isolated source fixtures through existing owning-domain APIs."""

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from datetime import datetime, timezone

DATABASE = "yak_golden_sample"
MARKER = "yak-golden-sample-v1"
PROJECT_NAME = "Golden Sample 治理验收"
SOURCE_NAME = "Golden Sample MySQL"
TABLES = ("golden_orders", "golden_orders_bad", "golden_customers")
SECTION_OWNERS = {
    "OVERVIEW": "ASSET", "TECHNICAL_METADATA": "METADATA", "QUALITY": "QUALITY",
    "SECURITY": "SECURITY", "LINEAGE": "LINEAGE", "USAGE": "FEDERATED",
    "LIFECYCLE": "LIFECYCLE", "GOVERNANCE": "ASSET",
}


def required_env(name):
    value = os.environ.get(name)
    if not value:
        raise ValueError(f"Required environment variable: {name}")
    return value


def records(page):
    if isinstance(page, list):
        return page
    for key in ("bizData", "records"):
        if isinstance(page, dict) and isinstance(page.get(key), list):
            total = page.get("total", page.get("totalCount", (page.get("pagination") or {}).get("total")))
            if total is not None and int(total) > len(page[key]):
                raise ValueError("Incomplete record page; narrow the scope before provisioning")
            return page[key]
    raise ValueError("API did not expose an explicit record list")


def unique_match(items, predicate, label):
    matches = [item for item in items if predicate(item)]
    if len(matches) > 1:
        raise ValueError(f"Ambiguous {label}; refusing to choose by display name")
    return matches[0] if matches else None


class Api:
    def __init__(self, base_url, username, password):
        import requests
        self.session = requests.Session()
        self.base_url = base_url.rstrip("/")
        self.project_id = None
        self.request("POST", "/yak-security/api/v1/account/login",
                     {"userName": username, "pw": password}, scoped=False)
        self.user = self.request("GET", "/yak-security/api/v1/account/current", scoped=False)
        if not isinstance(self.user, dict) or not self.user.get("id"):
            raise ValueError("Authenticated user identity is unavailable")

    def request(self, method, path, body=None, scoped=True, params=None, expected_code=200):
        headers = {}
        if scoped:
            if not self.project_id:
                raise ValueError("Project identity required before a business request")
            headers["X-YAK-SECURITY-PROJECT-ID"] = str(self.project_id)
        response = self.session.request(method, self.base_url + path, json=body,
                                        params=params, headers=headers, timeout=30,
                                        allow_redirects=False)
        # Do not echo bodies, cookies, connection strings or server error text.
        if response.status_code != 200:
            raise ValueError(f"{method} {path}: HTTP {response.status_code}")
        payload = response.json()
        if not isinstance(payload, dict) or payload.get("code") != expected_code:
            code = payload.get("code") if isinstance(payload, dict) else "INVALID_ENVELOPE"
            raise ValueError(f"{method} {path}: application code {code}")
        return payload.get("data")


def ensure_project(api, dept_id, project_name=PROJECT_NAME):
    projects = records(api.request("POST", "/yak-security/api/v1/project/page",
                                   {"page": 1, "size": 200, "projectName": project_name}, scoped=False))
    existing = unique_match(projects, lambda item: item.get("projectName") == project_name, "sample project")
    if existing:
        if existing.get("description") != MARKER:
            raise ValueError("Sample project name is occupied by an unowned project")
        return existing
    if dept_id is None:
        raise ValueError("--dept-id is required to create the dedicated sample project")
    return api.request("POST", "/yak-security/api/v1/project", {
        "projectName": project_name, "description": MARKER, "deptId": dept_id,
        "ownerIdList": [api.user["id"]], "userIdList": [], "running": True,
    }, scoped=False)


def seed_source(host, port, username, password):
    import pymysql
    connection = pymysql.connect(host=host, port=port, user=username, password=password,
                                  charset="utf8mb4", connect_timeout=10, autocommit=True)
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT table_name, table_comment FROM information_schema.tables "
                           "WHERE table_schema=%s", (DATABASE,))
            existing = cursor.fetchall()
            if any(name not in TABLES or comment != MARKER for name, comment in existing):
                raise ValueError("Source database contains unowned tables; initialization refused")
            source = Path(__file__).with_name("source.sql").read_text(encoding="utf-8")
            source = "\n".join(line for line in source.splitlines() if not line.lstrip().startswith("--"))
            for statement in source.split(";"):
                if statement.strip():
                    cursor.execute(statement)
            counts = {}
            for name in TABLES:
                cursor.execute(f"SELECT COUNT(*) FROM `{DATABASE}`.`{name}`")
                counts[name] = cursor.fetchone()[0]
            return counts
    finally:
        connection.close()


def ensure_source(api, host, port, username, password):
    sources = records(api.request("GET", "/api/v1/data-source/all"))
    existing = unique_match(sources, lambda item: item.get("name") == SOURCE_NAME, "sample datasource")
    if existing:
        if existing.get("remark") != MARKER:
            raise ValueError("Sample datasource name is occupied by unowned configuration")
        return existing
    api.request("POST", "/api/v1/data-source", {
        "name": SOURCE_NAME, "dbType": "MYSQL", "environment": "TEST", "remark": MARKER,
        "connectionParams": json.dumps({"host": host, "port": port, "database": DATABASE,
                                        "username": username, "password": password}),
    })
    created = unique_match(records(api.request("GET", "/api/v1/data-source/all")),
                           lambda item: item.get("name") == SOURCE_NAME and item.get("remark") == MARKER,
                           "created datasource")
    if not created:
        raise ValueError("Created datasource identity could not be resolved")
    return created


def harvest(api, source_id):
    jobs = records(api.request("POST", "/api/v1/metadata/collect-jobs/page",
                               {"pageNo": 1, "pageSize": 100, "keyword": MARKER}))
    job = unique_match(jobs, lambda item: item.get("jobCode") == "golden_sample_v1", "sample harvest")
    if job:
        if (str(job.get("dataSourceId")) != str(source_id) or job.get("databaseName") != DATABASE
                or job.get("jobName") != MARKER or job.get("tablePattern") != "golden_%"):
            raise ValueError("Existing sample harvest points to a different source")
    else:
        job = api.request("POST", "/api/v1/metadata/collect-jobs", {
            "jobCode": "golden_sample_v1", "jobName": MARKER,
            "providerType": "HARVESTED", "typeName": "table", "dataSourceId": source_id,
            "databaseName": DATABASE, "tablePattern": "golden_%", "collectColumns": True,
        })
    dry_run = api.request("POST", f"/api/v1/metadata/collect-jobs/{job['id']}/dry-run")
    if (dry_run.get("status") != "SUCCESS" or dry_run.get("cntPartialFailed", 0)
            or dry_run.get("cntTotal", 0) < len(TABLES)):
        raise ValueError(f"Harvest dry-run did not pass: {dry_run.get('status')}")
    result = api.request("POST", f"/api/v1/metadata/collect-jobs/{job['id']}/run")
    if (result.get("status") != "SUCCESS" or result.get("cntPartialFailed", 0)
            or result.get("cntTotal", 0) < len(TABLES)):
        raise ValueError(f"Harvest did not pass: {result.get('status')}")
    return {"jobId": str(job["id"]), "runId": str(result["runId"]), "status": result["status"]}


def ensure_monitors(api, source_id):
    registered = records(api.request("POST", "/api/v1/data-quality/table-asset/page",
                                     {"current": 1, "pageSize": 100, "dataSourceId": source_id}))
    missing = [name for name in TABLES if not any(item.get("tableName") == name and
               item.get("databaseName") == DATABASE for item in registered)]
    if missing:
        api.request("POST", "/api/v1/data-quality/table-asset/register", {
            "dataSourceId": source_id, "dataSourceName": SOURCE_NAME, "databaseName": DATABASE,
            "tables": [{"tableName": name} for name in missing],
        })
    templates = records(api.request("GET", "/api/v1/data-quality/template"))
    template = unique_match(templates, lambda item: item.get("code") == "COLUMN_NOT_NULL", "not-null template")
    if not template:
        raise ValueError("Required built-in COLUMN_NOT_NULL template is missing")
    monitors = records(api.request("POST", "/api/v1/data-quality/monitor/page",
                                   {"current": 1, "pageSize": 100, "dataSourceId": source_id}))
    output = []
    for table, expected in (("golden_orders", "PASSED"), ("golden_orders_bad", "NOT_PASSED")):
        name = f"Golden Sample {table} 非空检查"
        monitor = unique_match(monitors, lambda item: item.get("name") == name, "sample monitor")
        if monitor:
            if monitor.get("tableName") != table or monitor.get("description") != MARKER:
                raise ValueError("Existing sample monitor has different ownership or target")
        else:
            monitor = api.request("POST", "/api/v1/data-quality/monitor", {
                "name": name, "description": MARKER, "dataSourceId": source_id,
                "dataSourceName": SOURCE_NAME, "databaseName": DATABASE, "tableName": table,
                "owner": api.user["userName"], "enabled": True,
                "settings": {"runMode": "MANUAL", "scheduleEnabled": False,
                             "ruleFailureAction": "CONTINUE", "notifyEnabled": False,
                             "notifyChannel": "MESSAGE", "alertLevel": "WARNING"},
                "rules": [{"templateId": template["id"], "name": "订单数量非空",
                           "columnName": "quantity", "operator": "GTE", "threshold": 100, "enabled": True}],
            })
        output.append({"id": str(monitor["id"]), "table": table, "expectedResult": expected})
    return output


def reconcile_samples(api):
    previous = unique_match(api.request("GET", "/api/v1/assets/reconcile/status"),
                            lambda item: item.get("sourceType") == "METADATA", "Metadata provider")
    previous_at = ((previous or {}).get("lastRun") or {}).get("at")
    api.request("POST", "/api/v1/assets/reconcile", {"sourceTypes": ["METADATA"]})
    deadline = time.monotonic() + 45
    while True:
        statuses = api.request("GET", "/api/v1/assets/reconcile/status")
        metadata = unique_match(statuses, lambda item: item.get("sourceType") == "METADATA", "Metadata provider")
        run = (metadata or {}).get("lastRun", {})
        if run.get("at") and run["at"] != previous_at:
            if run.get("error"):
                raise ValueError("Metadata Asset reconcile failed; inspect the provider locally")
            break
        if time.monotonic() > deadline:
            raise ValueError("Timed out waiting for Metadata Asset reconcile")
        time.sleep(1)
    assets = records(api.request("GET", "/api/v1/assets", params={
        "sourceType": "METADATA", "keyword": "golden_", "pageNo": 1, "pageSize": 100}))
    result = []
    for table in TABLES:
        matches = []
        for asset in assets:
            # Source-owned attributes prove exact physical coordinates, not just a name.
            detail = api.request("GET", f"/api/v1/assets/{asset['id']}/source-attributes")
            attributes = (detail.get("data") or {}).get("extra") or {}
            if (str(attributes.get("dataSourceId")) == str(api.sample_source_id)
                    and attributes.get("databaseName") == DATABASE and attributes.get("tableName") == table):
                matches.append(asset)
        asset = unique_match(matches, lambda item: True, f"physical asset {table}")
        if not asset:
            raise ValueError(f"Exact physical asset identity is unavailable: {table}")
        result.append({"id": str(asset["id"]), "assetKey": asset["assetKey"],
                       "sourceType": asset["sourceType"], "sourceId": str(asset["sourceId"]), "table": table})
    return result


def current_commit():
    result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=Path(__file__).resolve().parents[3],
                            capture_output=True, text=True, check=True)
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Create isolated samples using existing APIs")
    parser.add_argument("--dept-id", type=int, help="Existing department for the dedicated sample Project")
    parser.add_argument("--output", type=Path, help="Write a credential-free sample manifest")
    args = parser.parse_args()
    if not args.apply:
        print(json.dumps({"mode": "PLAN", "project": PROJECT_NAME, "sourceDatabase": DATABASE,
                          "tables": TABLES, "operations": ["source seed", "project", "datasource",
                          "harvest", "physical Quality targets", "two monitors", "Asset reconcile"],
                          "notProvisioned": ["Model", "Metric", "Dataset", "MDM", "restricted role"]},
                         ensure_ascii=False, indent=2))
        return
    base_url = os.environ.get("YAK_OPS_BASE_URL", "http://localhost:8080")
    api = Api(base_url, required_env("YAK_OPS_USERNAME"), required_env("YAK_OPS_PASSWORD"))
    host = os.environ.get("YAK_GOLDEN_MYSQL_HOST", "127.0.0.1")
    port = int(os.environ.get("YAK_GOLDEN_MYSQL_PORT", "3306"))
    username = required_env("YAK_GOLDEN_MYSQL_USERNAME")
    password = required_env("YAK_GOLDEN_MYSQL_PASSWORD")
    counts = seed_source(host, port, username, password)
    project = ensure_project(api, args.dept_id)
    control_project = ensure_project(api, args.dept_id, PROJECT_NAME + " 对照")
    api.project_id = project["id"]
    source = ensure_source(api, os.environ.get("YAK_GOLDEN_JDBC_HOST", host), port, username, password)
    api.sample_source_id = source["id"]
    api.request("POST", f"/api/v1/data-source/{source['id']}/connect-test")
    harvest_result = harvest(api, source["id"])
    monitors = ensure_monitors(api, source["id"])
    assets = reconcile_samples(api)
    manifest = {"schemaVersion": 1, "marker": MARKER, "baseUrl": base_url,
                "repositoryCommit": current_commit(), "capturedAt": datetime.now(timezone.utc).isoformat(),
                "deploymentCommit": None,
                "deploymentArtifactSha256": os.environ.get("YAK_OPS_ARTIFACT_SHA256"),
                "projectId": str(project["id"]),
                "controlProjectId": str(control_project["id"]),
                "dataSourceId": str(source["id"]), "database": DATABASE, "rowCounts": counts,
                "harvest": harvest_result, "monitors": monitors, "assets": assets,
                "coverage": {"physicalTable": "PROVISIONED", "model": "NOT_PROVISIONED",
                             "metric": "NOT_PROVISIONED", "dataset": "NOT_PROVISIONED",
                             "mdm": "NOT_PROVISIONED", "permissions": "NOT_PROVISIONED",
                             "projectIsolation": "PROVISIONED", "faultIsolation": "NOT_EXECUTED"}}
    encoded = json.dumps(manifest, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Configuration/server exception details can carry connection secrets.
        if isinstance(error, (ValueError, FileNotFoundError)):
            print(f"Golden Sample bootstrap failed: {error}", file=sys.stderr)
        else:
            print(f"Golden Sample bootstrap failed: {type(error).__name__}; inspect the source service locally",
                  file=sys.stderr)
        sys.exit(1)
