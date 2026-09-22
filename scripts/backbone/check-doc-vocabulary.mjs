#!/usr/bin/env node

/**
 * 文档词汇检查（01 主心骨 S10「一个词一个含义」的守门人之一）：
 * 扫出 docs/v1 正文里已裁定口径的违禁词——防止修过的错二次复发。
 *
 * 背景：「数仓分层可配置、预置 ODS/DWD/DWS/ADS 四层」是 01 v0.4→v0.6 两轮
 * review 才钉住的口径；04 图一把「五层表结构」又写回去一次（v0.1），
 * 说明光靠人记不住——违禁词进 CI。
 *
 * 规则：
 *   - 只查 docs/v1/*.md 的正文（「修订记录」节之前），修订历史保留原词；
 *   - 命中违禁词退出码 1，输出文件、行号与建议口径。
 *
 * 新增违禁词：在 BANNED_TERMS 里加一行（词 + 正确口径 + 原因）。
 */

import { readdirSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";

const ROOT = process.cwd();
const DOCS_DIR = "docs/v1";

const BANNED_TERMS = [
  {
    term: "五层",
    correct: "数仓分层（可配置，预置 ODS/DWD/DWS/ADS 四层）",
    reason: "分层可配置、预置四层；语义迁移模板实测只 INSERT 四层，且每层带定标强制开关（01 v0.4/v0.6 两轮裁定）",
  },
];

const files = readdirSync(join(ROOT, DOCS_DIR))
  .filter((name) => name.endsWith(".md") && !name.includes("review结果"))
  .map((name) => join(ROOT, DOCS_DIR, name));

const violations = [];

for (const file of files) {
  const text = readFileSync(file, "utf8");
  // 只查正文：修订记录节属于历史档案，保留当时的原词
  const body = text.split(/^##\s*修订记录/m)[0];
  for (const rule of BANNED_TERMS) {
    const lines = body.split("\n");
    lines.forEach((line, index) => {
      if (line.includes(rule.term)) {
        violations.push({
          file: relative(ROOT, file),
          line: index + 1,
          ...rule,
        });
      }
    });
  }
}

if (violations.length === 0) {
  console.log("PASS: 未发现违禁词");
  process.exit(0);
}

console.error(`FAIL: 发现 ${violations.length} 处违禁词:\n`);
for (const v of violations) {
  console.error(`  ${v.file}:${v.line}  「${v.term}」`);
  console.error(`    正确口径: ${v.correct}`);
  console.error(`    原因: ${v.reason}\n`);
}
process.exit(1);
