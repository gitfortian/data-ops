#!/usr/bin/env node

/**
 * Product-surface guard.
 *
 * New top-level navigation domains and new business Maven modules are allowed,
 * but they must be explicit product decisions instead of accidental growth.
 */

import { existsSync, readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";

const eventPath = process.env.GITHUB_EVENT_PATH;
if (!eventPath || !existsSync(eventPath)) {
  console.log("SKIP: no GitHub pull-request event; product surface check is CI-only");
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

const body = (pr.body ?? "").replace(/\r/g, "");

const git = (...args) =>
  execFileSync("git", args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });

const showBase = (path) => {
  try {
    return git("show", `origin/${baseRef}:${path}`);
  } catch {
    return "";
  }
};

const extractBusinessModules = (pom) =>
  new Set(
    [...pom.matchAll(/<module>yak-ops-business-([^<]+)<\/module>/g)]
      .map((match) => match[1].trim())
      .filter(Boolean),
  );

const extractTopLevelNavigationGroups = (source) => {
  const start = source.indexOf("export const navigationGroups");
  if (start < 0) return new Set();
  const end = source.indexOf("];", start);
  const block = end < 0 ? source.slice(start) : source.slice(start, end);

  const ids = new Set();
  for (const line of block.split("\n")) {
    if (!line.includes("{") || line.includes("parentGroupId:")) continue;
    const id = line.match(/\bid:\s*['"]([^'"]+)['"]/)?.[1];
    if (id) ids.add(id);
  }
  return ids;
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
  console.log("PASS: no new business module or top-level navigation domain");
  process.exit(0);
}

const sectionStart = body.search(/^##\s+Product Surface Change\s*$/mi);
let section = "";
if (sectionStart >= 0) {
  const rest = body.slice(sectionStart).split("\n").slice(1);
  const collected = [];
  for (const line of rest) {
    if (/^##\s+/.test(line)) break;
    collected.push(line);
  }
  section = collected.join("\n");
}

const readSectionField = (label) => {
  const prefixes = [`${label}:`, `- ${label}:`, `* ${label}:`];
  for (const line of section.split("\n")) {
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

const requiredFields = [
  "Capability",
  "User Journey",
  "Why existing surface cannot carry this",
];

const missing = requiredFields.filter((field) => !readSectionField(field));

if (missing.length) {
  console.error("FAIL: product surface expanded without an explicit Product Surface Change decision.");
  if (newModules.length) console.error(`New business modules: ${newModules.join(", ")}`);
  if (newGroups.length) console.error(`New top-level navigation groups: ${newGroups.join(", ")}`);
  console.error("Missing PR fields:");
  for (const field of missing) console.error(`  - ${field}`);
  process.exit(1);
}

console.log(
  `PASS: product surface expansion declared (modules=${newModules.length}, top-level groups=${newGroups.length})`,
);
