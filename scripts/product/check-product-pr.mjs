#!/usr/bin/env node

/**
 * Product-impact guard for pull requests.
 *
 * Only PRs that change business/runtime product behavior are required to fill
 * product metadata. Documentation/process-only PRs remain lightweight.
 */

import { existsSync, readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";

const eventPath = process.env.GITHUB_EVENT_PATH;
if (!eventPath || !existsSync(eventPath)) {
  console.log("SKIP: no GitHub pull-request event; product PR metadata check is CI-only");
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
  changedFiles = output.split("\n").map((x) => x.trim()).filter(Boolean);
} catch (error) {
  console.error("FAIL: unable to inspect changed files for product impact");
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
  console.log("PASS: no product-behavior files changed; detailed Product Impact fields not required");
  process.exit(0);
}

const body = (pr.body ?? "").replace(/\r/g, "");
const lines = body.split("\n");

const readField = (label) => {
  const prefixes = [`${label}:`, `- ${label}:`, `* ${label}:`];
  for (const line of lines) {
    const trimmed = line.trim();
    const prefix = prefixes.find((candidate) =>
      trimmed.toLowerCase().startsWith(candidate.toLowerCase()),
    );
    if (!prefix) continue;
    const value = trimmed.slice(prefix.length).trim();
    if (!value || /^(n\/a|na|none|todo|tbd|-)$/i.test(value)) return "";
    return value;
  }
  return "";
};

const sectionBody = (heading) => {
  const headingIndex = lines.findIndex(
    (line) => line.trim().toLowerCase() === `## ${heading}`.toLowerCase(),
  );
  if (headingIndex < 0) return "";
  const collected = [];
  for (let i = headingIndex + 1; i < lines.length; i += 1) {
    if (/^##\s+/.test(lines[i])) break;
    collected.push(lines[i]);
  }
  return collected
    .join("\n")
    .replace(/<!--([\s\S]*?)-->/g, "")
    .replace(/[-*]\s*\[[ xX]\]/g, "")
    .trim();
};

const requiredFields = [
  "User",
  "Capability",
  "User Journey",
  "Problem",
  "Expected Outcome",
  "Truth Owner",
];

const missingFields = requiredFields.filter((field) => !readField(field));
const acceptance = sectionBody("Acceptance");

if (missingFields.length || !acceptance) {
  console.error("FAIL: product behavior changed but PR Product Impact is incomplete.");
  if (missingFields.length) {
    console.error("Missing non-empty fields:");
    for (const field of missingFields) console.error(`  - ${field}`);
  }
  if (!acceptance) console.error("  - Acceptance must contain a real E2E outcome");
  console.error("\nChanged product files include:");
  for (const file of productFiles.slice(0, 20)) console.error(`  ${file}`);
  process.exit(1);
}

console.log(
  `PASS: Product Impact complete for ${productFiles.length} product-behavior file(s)`,
);
