#!/usr/bin/env node

/**
 * Product baseline guard.
 *
 * Keeps the repository's product truth readable and structurally complete.
 * This deliberately checks only durable, low-noise invariants.
 */

import { existsSync, readFileSync } from "node:fs";

const REQUIRED_FILES = [
  "PRODUCT_STYLE.md",
  "AGENTS.md",
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
];

const missing = REQUIRED_FILES.filter((file) => !existsSync(file));
if (missing.length > 0) {
  console.error("FAIL: product baseline is incomplete:");
  for (const file of missing) console.error(`  missing: ${file}`);
  process.exit(1);
}

const emptyOrUntitled = [];
for (const file of REQUIRED_FILES) {
  const text = readFileSync(file, "utf8").trim();
  if (!text || !/^#\s+\S+/m.test(text)) emptyOrUntitled.push(file);
}
if (emptyOrUntitled.length > 0) {
  console.error("FAIL: product baseline documents must be non-empty and have an H1:");
  for (const file of emptyOrUntitled) console.error(`  invalid: ${file}`);
  process.exit(1);
}

const glossary = readFileSync("docs/product/PRODUCT_GLOSSARY.md", "utf8");
const rows = glossary
  .split("\n")
  .filter((line) => /^\|[^-].*\|$/.test(line.trim()))
  .slice(1);

const terms = new Map();
const duplicateTerms = [];
const missingOwners = [];

for (const row of rows) {
  const cells = row
    .split("|")
    .slice(1, -1)
    .map((cell) => cell.trim());

  if (cells.length < 3 || cells[0] === "术语") continue;
  const term = cells[0];
  const owner = cells[2];

  if (!term) continue;
  if (terms.has(term)) duplicateTerms.push(term);
  terms.set(term, true);
  if (!owner) missingOwners.push(term);
}

if (duplicateTerms.length || missingOwners.length) {
  if (duplicateTerms.length) {
    console.error("FAIL: duplicate product glossary terms:");
    for (const term of duplicateTerms) console.error(`  ${term}`);
  }
  if (missingOwners.length) {
    console.error("FAIL: product glossary terms without an Owner:");
    for (const term of missingOwners) console.error(`  ${term}`);
  }
  process.exit(1);
}

console.log(`PASS: product baseline complete (${REQUIRED_FILES.length} files, ${terms.size} glossary terms)`);
