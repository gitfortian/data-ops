#!/usr/bin/env node

/**
 * LEGACY EVIDENCE CHECK
 *
 * 该脚本只检查 docs/v1 历史材料内部是否重新出现曾经裁定过的旧词。
 * docs/v1 已是 Evidence；本脚本不属于 Product Guard，也不定义当前产品词汇。
 *
 * 当前跨产品词汇以 docs/product/PRODUCT_GLOSSARY.md 和 ACCEPTED Decisions 为准。
 */

import { readdirSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";

const ROOT = process.cwd();
const DOCS_DIR = "docs/v1";

console.warn("LEGACY: checking historical docs/v1 vocabulary only; this is not Product Truth.");

const BANNED_TERMS = [
  {
    term: "五层",
    correct: "历史裁定口径：数仓分层可配置，预置 ODS/DWD/DWS/ADS 四层",
  },
];

let entries;
try {
  entries = readdirSync(join(ROOT, DOCS_DIR));
} catch {
  console.log("SKIP: docs/v1 not present");
  process.exit(0);
}

const files = entries
  .filter((name) => name.endsWith(".md") && !name.includes("review结果"))
  .map((name) => join(ROOT, DOCS_DIR, name));

const violations = [];
for (const file of files) {
  const text = readFileSync(file, "utf8");
  const body = text.split(/^##\s*修订记录/m)[0];
  for (const rule of BANNED_TERMS) {
    body.split("\n").forEach((line, index) => {
      if (line.includes(rule.term)) {
        violations.push({ file: relative(ROOT, file), line: index + 1, ...rule });
      }
    });
  }
}

if (violations.length === 0) {
  console.log("PASS: no known legacy vocabulary regression in docs/v1");
  process.exit(0);
}

console.warn(`INFO: found ${violations.length} historical vocabulary regression(s):`);
for (const item of violations) {
  console.warn(`  ${item.file}:${item.line} 「${item.term}」 -> ${item.correct}`);
}
console.warn("Do not use this result to override current Product Glossary or accepted Product Decisions.");
process.exit(0);
