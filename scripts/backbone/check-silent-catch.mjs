#!/usr/bin/env node

/**
 * 静默 catch 检查（主心骨规矩 S11 的守门人之一）：
 * 扫出「catch 块里只有注释、没有任何处理」的吞错写法，并分桶定性。
 *
 * 背景：2026-09-21 M-2 事故（创建失败零提示）的代码就是
 *   } catch {
 *     // API 报错已由全局错误提示统一展示，这里只负责结束 loading
 *   }
 * biome 的 noEmptyBlockStatements 明确放行「加注释的空块」，拦不住这种；
 * 请求层测试（src/utils/request.test.tsx）保证原话拿得到，但管不住调用方吞掉。
 *
 * 分桶（避免一刀切把「有意委派」也判死，那只会让规则被整体关掉）：
 *   A. 委派全局：注释声明由全局错误层/表单/拦截器展示——计数放行；
 *   B. 降级展示：读路径或增强功能失败，产品已决策「不影响主流程」——计数放行；
 *   C. 良性忽略：JSON 解析兜底、剪贴板、可选增强失败等——计数放行；
 *   D. 可疑吞错：无注释或注释未解释去向——退出码 1，需人工确认。
 *
 * 判定块体：catch 后到第一个右花括号，块内只有空白和 // 行注释。
 * 退出码：0 = 无可疑项；1 = 存在可疑吞错。
 */

import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = process.cwd();
const SRC_DIRS = ['yak-ops-ui/src'];
const SKIP_DIRS = new Set(['node_modules', '.umi', '.umi-test', '.umi-production', 'dist', 'coverage']);
const EXTENSIONS = ['.ts', '.tsx'];

const DELEGATION_PATTERN =
  /全局|global|shared request|统一展示|统一拦截|拦截器|已由|errorHandler|interceptor|Form 自身|Form owns|form 自身|防 onOk|防onOk|request layer/i;
const DEGRADATION_PATTERN =
  /不影响|non-blocking|best.?effort|never block|must never|保留|退回|防环|幂等|已处理|已初始化|未试跑|未保存|保持默认|留空|source of truth|last known|next tick|older browsers|pointer capture|单次异常|不关闭|不崩溃/i;
const BENIGN_PATTERN =
  /JSON|parse|剪贴板|clipboard|navigator|会话|session ?storage|local ?storage|可选|增强|兜底|非 ?JSON|保留状态码|ignore|降级/i;

const walk = (dir) => {
  const entries = readdirSync(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    if (SKIP_DIRS.has(entry.name)) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...walk(full));
    } else if (EXTENSIONS.some((ext) => entry.name.endsWith(ext))) {
      files.push(full);
    }
  }
  return files;
};

const files = SRC_DIRS.filter((dir) => {
  try {
    readdirSync(join(ROOT, dir));
    return true;
  } catch {
    return false;
  }
}).flatMap((dir) => walk(join(ROOT, dir)));

const delegated = [];
const degraded = [];
const benign = [];
const suspicious = [];

for (const file of files) {
  const text = readFileSync(file, 'utf8');
  // 捕获 catch 之后到第一个右花括号的块体；块体内出现 { 或 ; 即视为有处理，跳过。
  const pattern = /catch\s*(?:\([^)]*\))?\s*\{([^{}]*)\}/g;
  let match;
  while ((match = pattern.exec(text)) !== null) {
    const body = match[1];
    const comments = body.match(/\/\/[^\n]*/g)?.join(' ') ?? '';
    const withoutComments = body.replace(/\/\/[^\n]*/g, '').trim();
    if (withoutComments.length > 0) continue; // 有实际处理，不管
    const line = text.slice(0, match.index).split('\n').length;
    const entry = { file: relative(ROOT, file), line, comment: comments.trim().slice(0, 60) };
    if (DELEGATION_PATTERN.test(comments)) {
      delegated.push(entry);
    } else if (DEGRADATION_PATTERN.test(comments)) {
      degraded.push(entry);
    } else if (BENIGN_PATTERN.test(comments)) {
      benign.push(entry);
    } else {
      suspicious.push(entry);
    }
  }
}

console.log(`委派全局/表单（放行）: ${delegated.length} 处`);
console.log(`降级展示（放行，产品已决策）: ${degraded.length} 处`);
console.log(`良性忽略（放行）: ${benign.length} 处`);
console.log(`可疑吞错（需人工确认）: ${suspicious.length} 处`);

if (suspicious.length === 0) {
  console.log('PASS: 未发现可疑的静默 catch');
  process.exit(0);
}

console.error(`\nFAIL: ${suspicious.length} 处 catch 未声明委派、也不属已知良性模式:\n`);
for (const v of suspicious) {
  console.error(`  ${v.file}:${v.line}  // ${v.comment || '（无注释）'}`);
}
console.error('\n规矩 S11：出错必须让用户看见。要么展示错误，要么带着原因继续抛出；');
console.error('确属委派全局的，请在注释里写明（如“已由全局错误提示展示”）即可放行。');
process.exit(1);
