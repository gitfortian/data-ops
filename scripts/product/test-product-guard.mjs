#!/usr/bin/env node

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  acceptanceFromPrBody,
  extractBusinessModules,
  extractTopLevelNavigationGroups,
  parseDecisionImplementation,
  parseDecisionStatus,
  parseFeatureStatus,
  parseYesNo,
  readField,
  sectionBody,
} from "./product-guard-lib.mjs";

const template = readFileSync(".github/pull_request_template.md", "utf8");

const classification = sectionBody(template, "Change Classification");
assert.equal(readField(classification, "Change Type"), "");
assert.equal(readField(classification, "Product Behavior Changed"), "");

const blankAcceptance = acceptanceFromPrBody(template);
assert.deepEqual(blankAcceptance, { scenario: "", evidence: "" });

const filled = `
## Change Classification
- Change Type: PRODUCT
- Product Behavior Changed: Yes
- If No, Why:

## Product Impact
- User: Data Engineer
- Capability: Development & Orchestration
- User Journey: J3
- Problem: The user cannot publish a governed result.
- Expected Outcome: The user can publish and consume the result.

## Truth & Ownership
- Truth Owner: dataset

## Acceptance
- Scenario: A published result becomes consumable through the governed path.
- Evidence: API result plus persisted binding and audit event.
`;

assert.equal(parseYesNo(readField(sectionBody(filled, "Change Classification"), "Product Behavior Changed")), true);
assert.equal(readField(sectionBody(filled, "Product Impact"), "User"), "Data Engineer");
assert.deepEqual(acceptanceFromPrBody(filled), {
  scenario: "A published result becomes consumable through the governed path.",
  evidence: "API result plus persisted binding and audit event.",
});

const navigation = readFileSync("data-ops-ui/src/config/navigation.ts", "utf8");
const topGroups = extractTopLevelNavigationGroups(navigation);
assert(topGroups.has("integration"));
assert(topGroups.has("development"));
assert(topGroups.has("modeling"));
assert(topGroups.has("data-asset"));
assert(topGroups.has("data-analysis"));
assert(topGroups.has("system"));
assert(!topGroups.has("resources"));
assert(!topGroups.has("workflow"));
assert(!topGroups.has("semantic"));
assert(!topGroups.has("governance"));
assert(!topGroups.has("approval"));
assert.equal(topGroups.size, 6);

const businessPom = readFileSync("data-ops-business/pom.xml", "utf8");
const modules = extractBusinessModules(businessPom);
assert(modules.has("datasource"));
assert(modules.has("workflow"));
assert(modules.has("dataset"));
assert(modules.has("metadata"));

const previousModules = extractBusinessModules(
  businessPom.replaceAll("data-ops-business-", "yak-ops-business-"),
);
assert.deepEqual(previousModules, modules);
const expandedModules = extractBusinessModules(
  `${businessPom}<module>data-ops-business-new-capability</module>`,
);
assert.deepEqual([...expandedModules].filter((name) => !previousModules.has(name)), ["new-capability"]);


assert.equal(parseDecisionStatus("Status: ACCEPTED\nImplementation: PARTIAL"), "ACCEPTED");
assert.equal(parseDecisionImplementation("Status: ACCEPTED\nImplementation: PARTIAL"), "PARTIAL");
assert.equal(parseFeatureStatus("Status: IMPLEMENTING"), "IMPLEMENTING");

console.log("PASS: Product Guard parser self-tests");
