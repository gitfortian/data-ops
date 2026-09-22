#!/usr/bin/env node

import { existsSync, readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import {
  extractBusinessModules,
  extractTopLevelNavigationGroups,
  parseDecisionStatus,
  readField,
  sectionBody,
} from "./product-guard-lib.mjs";

const eventPath = process.env.GITHUB_EVENT_PATH;
if (!eventPath || !existsSync(eventPath)) {
  console.log("SKIP: no GitHub event file; product-surface check is pull-request only");
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

const git = (...args) =>
  execFileSync("git", args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });

const showBase = (path) => {
  try {
    return git("show", `origin/${baseRef}:${path}`);
  } catch {
    return "";
  }
};

const currentBusinessPom = existsSync("yak-ops-business/pom.xml")
  ? readFileSync("yak-ops-business/pom.xml", "utf8")
  : "";
const baseBusinessPom = showBase("yak-ops-business/pom.xml");

const currentNav = existsSync("yak-ops-ui/src/config/navigation.ts")
  ? readFileSync("yak-ops-ui/src/config/navigation.ts", "utf8")
  : "";
const baseNav = showBase("yak-ops-ui/src/config/navigation.ts");

const baseModules = extractBusinessModules(baseBusinessPom);
const currentModules = extractBusinessModules(currentBusinessPom);
const newModules = [...currentModules].filter((name) => !baseModules.has(name));

const baseGroups = extractTopLevelNavigationGroups(baseNav);
const currentGroups = extractTopLevelNavigationGroups(currentNav);
const newGroups = [...currentGroups].filter((name) => !baseGroups.has(name));

if (newModules.length === 0 && newGroups.length === 0) {
  console.log("PASS: no new business Maven module or top-level navigation domain");
  process.exit(0);
}

const body = pr.body ?? "";
const surface = sectionBody(body, "Product Surface Change");

const requiredFields = [
  "Product Decision",
  "Capability",
  "User Journey",
  "Why existing surface cannot carry this",
];

const missing = requiredFields.filter((field) => !readField(surface, field));
if (missing.length) {
  console.error("FAIL: product surface expanded without complete Product Surface Change metadata:");
  for (const field of missing) console.error(`  - ${field}`);
  if (newModules.length) console.error(`New business modules: ${newModules.join(", ")}`);
  if (newGroups.length) console.error(`New top-level navigation groups: ${newGroups.join(", ")}`);
  process.exit(1);
}

const rawDecision = readField(surface, "Product Decision");
const match = rawDecision.match(/^(?:docs\/product\/decisions\/)?(PD-[A-Za-z0-9._-]+\.md)$/);
if (!match) {
  console.error("FAIL: Product Decision must reference PD-*.md in docs/product/decisions/");
  process.exit(1);
}

const decisionPath = `docs/product/decisions/${match[1]}`;
const baseDecision = showBase(decisionPath);
if (!baseDecision) {
  console.error(
    `FAIL: ${decisionPath} must already exist on base branch ${baseRef}; decide first, implement later`,
  );
  process.exit(1);
}

if (parseDecisionStatus(baseDecision) !== "ACCEPTED") {
  console.error(
    `FAIL: ${decisionPath} is not ACCEPTED on base branch ${baseRef}`,
  );
  process.exit(1);
}

console.log(
  `PASS: product surface expansion authorized by accepted ${decisionPath} (new modules=${newModules.length}, new top-level groups=${newGroups.length})`,
);
