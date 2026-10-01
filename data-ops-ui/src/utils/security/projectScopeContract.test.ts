import { readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';

import {
  PROJECT_REQUEST_RULES,
  resolveProjectRequestMode,
} from './projectContext';

/**
 * 后端 `@ProjectScope` ⇄ 前端 `PROJECT_REQUEST_RULES` 自动对齐守卫（ticket 127）。
 *
 * 为什么要机器守而不是靠人记：T1/T1b 两次 `/api/...` 前缀未登记 → 前端不发项目头 →
 * 后端 `PROJECT_REQUIRED` 判 999，都不是逻辑 bug，而是**两种语言各有一份清单**。本测试
 * 直接读后端 controller 源码，让清单不可能再各说各话（读源码比对已有
 * `navigationMenuContract.test.ts` 先例）。
 */

const BUSINESS_ROOT = path.resolve(__dirname, '../../../../data-ops-business');

/** 路径常量住在 common/core，索引必须覆盖整仓，否则 `AlertConstants.API_PREFIX` 解不出来。 */
const REPO_ROOT = path.resolve(BUSINESS_ROOT, '..');

const INDEX_IGNORED_DIRS = new Set(['target', 'node_modules', '.git', 'dist', '.umi', 'docs']);

type BackendScope = 'PROJECT_REQUIRED' | 'PROJECT_OPTIONAL' | 'LEGACY_GLOBAL';

type Endpoint = {
  /** 完整路径；方法级为 类前缀 + 方法后缀。 */
  url: string;
  scope: BackendScope;
  owner: string;
};

type ParsedControllers = {
  /** 只有标注（或继承）了 `@ProjectScope` 的端点。 */
  endpoints: Endpoint[];
  /** 全部已声明的后端路径（含无 `@ProjectScope` 的），供反向垃圾检查使用。 */
  declaredPaths: Set<string>;
  /** 解析不出来的映射表达式，供“扫描规模”断言之外的自诊断。 */
  unresolvedMappings: string[];
};

const SCOPE_PATTERN = /@ProjectScope\(ProjectMigrationMode\.(\w+)\)/;
const CLASS_MAPPING_PATTERN = /RequestMapping\s*\(([^)]*)\)/;
const ANY_MAPPING_PATTERN = /\w*Mapping\s*\(([^)]*)\)/g;

const normalize = (url: string) => url.replace(/\/+$/, '') || '/';

/** 文件名 → 源码路径，用于按需解析 `SomeConstants.API_PREFIX` 这类拼接映射。 */
const javaFileIndex = new Map<string, string>();

const walkJava = (dir: string, files: string[]): string[] => {
  if (!statSync(dir, { throwIfNoEntry: false })?.isDirectory()) return files;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (!INDEX_IGNORED_DIRS.has(entry.name)) walkJava(full, files);
      continue;
    }
    if (!entry.name.endsWith('.java')) continue;
    // 测试夹具里的同名常量/控制器会把解析带偏，只认生产源码。
    if (full.includes(`${path.sep}src${path.sep}test${path.sep}java`)) continue;
    if (!javaFileIndex.has(entry.name)) javaFileIndex.set(entry.name, full);
    files.push(full);
  }
  return files;
};

const allJavaFiles = walkJava(REPO_ROOT, []);

const controllerSources = (): string[] =>
  allJavaFiles.filter((file) => {
    if (!file.startsWith(BUSINESS_ROOT)) return false;
    const relative = file.slice(BUSINESS_ROOT.length).replace(/\\/g, '/');
    return relative.endsWith('Controller.java');
  });

const constantCache = new Map<string, string | undefined>();

const resolveConstant = (reference: string): string | undefined => {
  const key = reference.trim();
  if (constantCache.has(key)) return constantCache.get(key);
  const [owner, name = owner] = key.split('.');
  const file = javaFileIndex.get(`${owner}.java`);
  let value: string | undefined;
  if (file) {
    const literal = new RegExp(`String\\s+${name}\\s*=\\s*"([^"]+)"`).exec(readFileSync(file, 'utf8'));
    value = literal?.[1];
  }
  constantCache.set(key, value);
  return value;
};

/** 按顶层逗号切分：引号内、`{}`/`()` 内的逗号不是分隔符，所以 `{sessionId}` 不会被撕开。 */
const splitTopLevel = (input: string): string[] => {
  const segments: string[] = [];
  let current = '';
  let depth = 0;
  let inQuote = false;
  for (const char of input) {
    if (char === '"') inQuote = !inQuote;
    if (!inQuote && (char === '{' || char === '(')) depth += 1;
    if (!inQuote && (char === '}' || char === ')')) depth -= 1;
    if (!inQuote && depth === 0 && char === ',') {
      segments.push(current);
      current = '';
      continue;
    }
    current += char;
  }
  segments.push(current);
  return segments.map((segment) => segment.trim()).filter(Boolean);
};

const splitStringConcatenation = (input: string): string[] => {
  const segments: string[] = [];
  let current = '';
  let inQuote = false;
  for (let index = 0; index < input.length; index += 1) {
    const char = input[index];
    if (char === '"' && input[index - 1] !== '\\') inQuote = !inQuote;
    if (!inQuote && char === '+') {
      segments.push(current);
      current = '';
      continue;
    }
    current += char;
  }
  segments.push(current);
  return segments;
};

const literalOf = (token: string): string | undefined => {
  if (!token.startsWith('"')) return undefined;
  const closing = token.lastIndexOf('"');
  return closing > 0 ? token.slice(1, closing) : token.slice(1);
};

/**
 * 从映射注解的参数里取出全部路径。
 *
 * 三种真实写法都必须支持：数组别名 `{"/a", "/b"}`、常量拼接
 * `DataSourceConstants.API_PREFIX + "/plugin/config"`、以及 `value = ` / 裸字面量。
 * 只留以 `/` 开头的结果，`produces = "application/json"` 之类自然被排除。
 */
const pathsFromArgs = (args: string, owner: string, unresolved: string[]): string[] => {
  const paths: string[] = [];
  for (const rawSegment of splitTopLevel(args)) {
    let segment = rawSegment;
    const assignment = segment.indexOf('=');
    if (assignment >= 0) {
      const key = segment.slice(0, assignment).trim();
      if (key && key !== 'value' && key !== 'path') continue;
      segment = segment.slice(assignment + 1).trim();
    }
    const arrayLiteral = /^\{([\s\S]*)\}$/.exec(segment);
    const candidates = arrayLiteral ? splitTopLevel(arrayLiteral[1]) : [segment];
    for (const candidate of candidates) {
      let url = '';
      let broken = false;
      for (const token of splitStringConcatenation(candidate)) {
        const trimmed = token.trim();
        if (!trimmed) continue;
        const literal = literalOf(trimmed);
        if (literal !== undefined) {
          url += literal;
          continue;
        }
        const constant = resolveConstant(trimmed);
        if (constant === undefined) {
          unresolved.push(`${owner}: ${trimmed}`);
          broken = true;
          break;
        }
        url += constant;
      }
      if (!broken && url.startsWith('/')) paths.push(url);
    }
  }
  return paths;
};

/**
 * 按“注解块 → 声明行”切分。
 *
 * 归属单位是**方法**：类级 `@ProjectScope` 只是方法未标注时的默认值（后端 advice 的真实语义），
 * 裸类路径本身不可路由。把裸类路径当端点，会把"类声明 GLOBAL、个别子路径 REQUIRED"
 * 这类正常写法误判成冲突。仅当一个类没有任何映射方法时才退回类级条目。
 */
const parseController = (
  source: string,
  owner: string,
  declaredPaths: Set<string>,
  unresolved: string[],
): Endpoint[] => {
  if (!/@RestController|@Controller\b/.test(source)) return [];

  const classEntries: Endpoint[] = [];
  const methodEntries: Endpoint[] = [];
  let classScope: BackendScope | undefined;
  let classPath = '';
  let block: string[] = [];

  for (const rawLine of source.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) continue;
    if (line.startsWith('@')) {
      block.push(line);
      continue;
    }
    if (line.startsWith('//') || line.startsWith('/*') || line.startsWith('*')) continue;

    const isClass = /^(public\s+)?(final\s+|abstract\s+)*class\s/.test(line);
    const isMethod = /^(public|protected)\s/.test(line) && line.includes('(');
    const joined = block.join('\n');
    block = [];

    if (!isClass && !isMethod) continue;

    if (isClass) {
      const mapping = CLASS_MAPPING_PATTERN.exec(joined);
      if (!mapping) continue;
      const classPaths = pathsFromArgs(mapping[1], owner, unresolved);
      if (!classPaths.length) continue;
      classPath = classPaths[0];
      declaredPaths.add(normalize(classPath));
      classScope = SCOPE_PATTERN.exec(joined)?.[1] as BackendScope | undefined;
      if (classScope) classEntries.push({ url: classPath, scope: classScope, owner });
      continue;
    }

    // 方法声明：带映射注解才算端点；无 `@ProjectScope` 时继承类级（后端 advice 的真实语义）。
    const suffixes = [...joined.matchAll(ANY_MAPPING_PATTERN)].flatMap(([, args]) =>
      pathsFromArgs(args, `${owner}#${line.slice(0, 48)}`, unresolved),
    );
    if (!suffixes.length) continue;
    const scope = (SCOPE_PATTERN.exec(joined)?.[1] ?? classScope) as BackendScope | undefined;
    // 类上没有 @RequestMapping 时，方法路径本身就是绝对路径。
    const urls = classPath
      ? suffixes.map((suffix) => `${classPath}${suffix}`)
      : suffixes.filter((suffix) => suffix.startsWith('/api/'));
    for (const url of urls) {
      declaredPaths.add(normalize(url));
      if (!scope) continue;
      methodEntries.push({
        url: url.replace(/\/+$/, '') || classPath,
        scope,
        owner: `${owner}#${line.slice(0, 48)}`,
      });
    }
  }

  return methodEntries.length > 0 ? methodEntries : classEntries;
};

const parseControllers = (): ParsedControllers => {
  const declaredPaths = new Set<string>();
  const unresolvedMappings: string[] = [];
  const endpoints = controllerSources().flatMap((file) =>
    parseController(
      readFileSync(file, 'utf8'),
      file.slice(BUSINESS_ROOT.length + 1).replace(/\\/g, '/'),
      declaredPaths,
      unresolvedMappings,
    ),
  );
  return { endpoints, declaredPaths, unresolvedMappings };
};

const { endpoints, declaredPaths, unresolvedMappings } = parseControllers();

describe('Backend @ProjectScope ↔ frontend PROJECT_REQUEST_RULES contract', () => {
  it('scans a non-trivial backend surface', () => {
    // 解析器一旦失效，下面两条断言会“空集合恒真”，所以先锁住扫描规模。
    expect(endpoints.length).toBeGreaterThan(80);
    expect(
      endpoints.filter((endpoint) => endpoint.scope === 'PROJECT_REQUIRED').length,
    ).toBeGreaterThan(70);
    // 常量拼接的映射解析不了就会静默漏检，这里显式暴露而不是吞掉。
    expect(unresolvedMappings).toEqual([]);
  });

  it('sends a project header for every PROJECT_REQUIRED endpoint', () => {
    const missing = endpoints
      .filter((endpoint) => endpoint.scope === 'PROJECT_REQUIRED')
      .filter((endpoint) => resolveProjectRequestMode(endpoint.url) === 'LEGACY_GLOBAL')
      .map((endpoint) => `${normalize(endpoint.url)}  <-  ${endpoint.owner}`);

    expect(missing).toEqual([]);
  });

  it('keeps LEGACY_GLOBAL endpoints out of the project-required sweep', () => {
    const swallowed = endpoints
      .filter((endpoint) => endpoint.scope === 'LEGACY_GLOBAL')
      .filter((endpoint) => resolveProjectRequestMode(endpoint.url) !== 'LEGACY_GLOBAL')
      .map(
        (endpoint) =>
          `${normalize(endpoint.url)} resolves ${resolveProjectRequestMode(endpoint.url)}`
          + `  <-  ${endpoint.owner}`,
      );

    expect(swallowed).toEqual([]);
  });

  it('declares no frontend rule that no backend endpoint or known API family uses', () => {
    // 反向垃圾检查：规则前缀若落在任何存活 controller 之外，说明对应模块早已改名/下线，
    // 留着只会误导下一个人。比对的是**全部**已声明路径，不是只有 @ProjectScope 的那些。
    const orphanRules = PROJECT_REQUEST_RULES.filter(
      ({ prefix }) =>
        prefix !== '/'
        && ![...declaredPaths].some(
          (url) => url.startsWith(normalize(prefix)),
        ),
    ).map(({ prefix }) => prefix);

    expect(orphanRules).toEqual([]);
  });
});
