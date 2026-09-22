#!/usr/bin/env node

import { existsSync, readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import {
  acceptanceFromPrBody,
  parseYesNo,
  readField,
  sectionBody,
} from "./product-guard-lib.mjs";

const eventPath = process.env.GITHUB_EVENT_PATH;
if (!eventPath || !existsSync(eventPath)) {
  console.log("SKIP: no GitHub event file; PR product-impact check is pull-request only");
  process.exit(0);
}

const event = JSON.parse(readFileSync(eventPath, "utf8"));
const pr = event.pull_request;
if (!pr) {
  console.log("SKIP: event is not a pull request");
  process.exit(0);
}

const baseRef = process.env.GITHUB_BASE_REF || pr.base?.ref;
if (!baseRef) {
  console.error("FAIL: cannot determine pull request base ref");
  process.exit(1);
}

let changedFiles = [];
try {
  const output = execFileSync(
    "git",
    ["diff", "--name-only", `origin/${baseRef}...HEAD`],
    { encoding: "utf8" },
  );
  changedFiles = output.split("\n").map((value) => value.trim()).filter(Boolean);
} catch (error) {
  console.error("FAIL: unable to inspect changed files");
  console.error(error?.message ?? error);
  process.exit(1);
}

const PRODUCT_PATHS = [
  /^yak-ops-business\/[^/]+\/src\/main\//,
  /^yak-ops-ui\/src\//,
  /^yak-ops-core\/src\/main\//,
  /^yak-ops-spi\/src\/main\//,
  /^yak-ops-common\/src\/main\//,
  /^yak-ops-boot\/src\/main\//,
];

const productFiles = changedFiles.filter((file) =>
  PRODUCT_PATHS.some((pattern) => pattern.test(file)),
);

if (productFiles.length === 0) {
  console.log("PASS: no product-behavior source paths changed");
  process.exit(0);
}

const body = pr.body ?? "";
const classification = sectionBody(body, "Change Classification");
const changeType = readField(classification, "Change Type").toUpperCase();
const behaviorChanged = parseYesNo(readField(classification, "Product Behavior Changed"));
const noBehaviorReason = readField(classification, "If No, Why");

const ALLOWED_CHANGE_TYPES = new Set(["PRODUCT", "TECHNICAL", "DOCS", "OPS"]);

if (!ALLOWED_CHANGE_TYPES.has(changeType)) {
  console.error("FAIL: changed product source paths require Change Type = PRODUCT / TECHNICAL / DOCS / OPS");
  process.exit(1);
}

if (behaviorChanged === undefined) {
  console.error("FAIL: changed product source paths require Product Behavior Changed: Yes / No");
  process.exit(1);
}

if (behaviorChanged === false) {
  if (changeType === "PRODUCT") {
    console.error("FAIL: Change Type PRODUCT conflicts with Product Behavior Changed: No");
    process.exit(1);
  }
  if (!noBehaviorReason) {
    console.error("FAIL: non-product behavior change must explain 'If No, Why'");
    process.exit(1);
  }

  console.log(
    `PASS: ${changeType} change declared no product behavior change for ${productFiles.length} product source file(s)`,
  );
  process.exit(0);
}

if (changeType !== "PRODUCT") {
  console.error("FAIL: Product Behavior Changed: Yes requires Change Type: PRODUCT");
  process.exit(1);
}

const impact = sectionBody(body, "Product Impact");
const ownership = sectionBody(body, "Truth & Ownership");

const required = [
  ["User", impact],
  ["Capability", impact],
  ["User Journey", impact],
  ["Problem", impact],
  ["Expected Outcome", impact],
  ["Truth Owner", ownership],
];

const missing = required
  .filter(([label, section]) => !readField(section, label))
  .map(([label]) => label);

const acceptance = acceptanceFromPrBody(body);
if (!acceptance.scenario) missing.push("Acceptance / Scenario");
if (!acceptance.evidence) missing.push("Acceptance / Evidence");

if (missing.length) {
  console.error("FAIL: product behavior changed but PR Product Impact is incomplete:");
  for (const field of missing) console.error(`  - ${field}`);
  console.error("\nChanged product files include:");
  for (const file of productFiles.slice(0, 20)) console.error(`  ${file}`);
  process.exit(1);
}

console.log(
  `PASS: Product Impact and E2E Acceptance declared for ${productFiles.length} product source file(s)`,
);
