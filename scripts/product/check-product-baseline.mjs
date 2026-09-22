#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import {
  parseDecisionImplementation,
  parseDecisionStatus,
  parseFeatureStatus,
} from "./product-guard-lib.mjs";

const REQUIRED_FILES = [
  "AGENTS.md",
  "PRODUCT_STYLE.md",
  "docs/product/README.md",
  "docs/product/PRODUCT_VISION.md",
  "docs/product/PRODUCT_PRINCIPLES.md",
  "docs/product/CAPABILITY_MAP.md",
  "docs/product/USER_JOURNEYS.md",
  "docs/product/PRODUCT_GLOSSARY.md",
  "docs/product/FEATURE_SPEC_TEMPLATE.md",
  "docs/product/PRODUCT_CHANGE_PROCESS.md",
  "docs/product/DOCUMENT_GOVERNANCE.md",
  "docs/product/LEGACY_DOC_INDEX.md",
  "docs/product/PRODUCT_GUARD.md",
  "docs/product/decisions/README.md",
  "docs/product/decisions/DECISION_TEMPLATE.md",
  "docs/product/features/README.md",
  ".github/pull_request_template.md",
  ".github/ISSUE_TEMPLATE/feature.md",
  ".github/workflows/product-guard.yml",
  "scripts/product/product-guard-lib.mjs",
  "scripts/product/check-product-baseline.mjs",
  "scripts/product/check-product-pr.mjs",
  "scripts/product/check-product-surface.mjs",
  "scripts/product/test-product-guard.mjs",
];

const fail = (message, details = []) => {
  console.error(`FAIL: ${message}`);
  for (const detail of details) console.error(`  - ${detail}`);
  process.exit(1);
};

const missing = REQUIRED_FILES.filter((file) => !existsSync(file));
if (missing.length) fail("product governance baseline is incomplete", missing);

const invalidDocs = REQUIRED_FILES
  .filter((file) => file.endsWith(".md"))
  .filter((file) => {
    const text = readFileSync(file, "utf8").trim();
    return !text || !/^#\s+\S+/m.test(text);
  });
if (invalidDocs.length) fail("governance markdown must be non-empty and have an H1", invalidDocs);

const glossary = readFileSync("docs/product/PRODUCT_GLOSSARY.md", "utf8");
const rows = glossary
  .split("\n")
  .filter((line) => /^\|[^-].*\|$/.test(line.trim()))
  .slice(1);

const terms = new Map();
const duplicateTerms = [];
const ownerlessTerms = [];

for (const row of rows) {
  const cells = row.split("|").slice(1, -1).map((cell) => cell.trim());
  if (cells.length < 3 || cells[0] === "术语") continue;
  const [term, , owner] = cells;
  if (!term) continue;
  if (terms.has(term)) duplicateTerms.push(term);
  terms.set(term, true);
  if (!owner) ownerlessTerms.push(term);
}

if (duplicateTerms.length) fail("duplicate Product Glossary terms", duplicateTerms);
if (ownerlessTerms.length) fail("Product Glossary terms without Owner", ownerlessTerms);

const DECISION_STATUS = new Set(["PROPOSED", "ACCEPTED", "SUPERSEDED", "REJECTED"]);
const IMPLEMENTATION_STATUS = new Set(["NOT_STARTED", "PARTIAL", "DONE"]);
const decisionsDir = "docs/product/decisions";
const decisionFiles = readdirSync(decisionsDir)
  .filter((name) => name.endsWith(".md"))
  .filter((name) => !["README.md", "DECISION_TEMPLATE.md"].includes(name));

for (const name of decisionFiles) {
  const text = readFileSync(join(decisionsDir, name), "utf8");
  const status = parseDecisionStatus(text);
  const implementation = parseDecisionImplementation(text);
  if (!DECISION_STATUS.has(status)) fail(`invalid Product Decision status in ${name}`);
  if (!IMPLEMENTATION_STATUS.has(implementation)) fail(`invalid Product Decision implementation state in ${name}`);
}

const FEATURE_STATUS = new Set(["DRAFT", "APPROVED", "IMPLEMENTING", "SHIPPED", "SUPERSEDED"]);
const featuresDir = "docs/product/features";
const featureFiles = readdirSync(featuresDir)
  .filter((name) => name.endsWith(".md"))
  .filter((name) => name !== "README.md");

for (const name of featureFiles) {
  const text = readFileSync(join(featuresDir, name), "utf8");
  const status = parseFeatureStatus(text);
  if (!FEATURE_STATUS.has(status)) fail(`invalid Feature Spec status in ${name}`);
}

const workflow = readFileSync(".github/workflows/product-guard.yml", "utf8");
if (workflow.includes("scripts/backbone/")) {
  fail("Product Guard must not use legacy docs/v1 backbone checks");
}

console.log(
  `PASS: product governance baseline complete (${REQUIRED_FILES.length} required files, ${terms.size} glossary terms, ${decisionFiles.length} decisions, ${featureFiles.length} feature specs)`,
);
