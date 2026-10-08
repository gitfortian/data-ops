import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const BUSINESS_ROOT = 'data-ops-business';

/**
 * Fail closed: unknown or cross-cutting paths trigger the complete reactor,
 * frontend and distribution rather than silently removing acceptance coverage.
 */
export function fullPlan(reason) {
  return {
    mode: 'full',
    reason,
    full: true,
    backend: true,
    frontend: true,
    modules: '',
    jestTargets: '',
  };
}

export function readBusinessModules(root = ROOT) {
  const dir = path.join(root, BUSINESS_ROOT);
  return new Set(fs.readdirSync(dir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory()
      && entry.name.startsWith('data-ops-business-')
      && fs.existsSync(path.join(dir, entry.name, 'pom.xml')))
    .map((entry) => entry.name));
}

/** Reverse reactor dependency graph, including test-scope consumers. */
export function readReverseDependencies(modules, root = ROOT) {
  const consumers = new Map([...modules].map((module) => [module, new Set()]));
  for (const module of modules) {
    const xml = fs.readFileSync(path.join(root, BUSINESS_ROOT, module, 'pom.xml'), 'utf8');
    for (const match of xml.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)) {
      const artifactId = match[1].match(/<artifactId>([^<]+)<\/artifactId>/)?.[1];
      if (artifactId && consumers.has(artifactId)) {
        consumers.get(artifactId).add(module);
      }
    }
  }
  return consumers;
}

function addConsumers(module, reverseDependencies, selected) {
  const queue = [module];
  while (queue.length > 0) {
    const current = queue.shift();
    if (selected.has(current)) continue;
    selected.add(current);
    for (const consumer of reverseDependencies.get(current) ?? []) {
      if (!selected.has(consumer)) queue.push(consumer);
    }
  }
}

const SERVICE_PAGE_TESTS = {
  semantic: ['src/pages/semantic', 'src/pages/metric'],
  metric: ['src/pages/metric'],
  metadata: ['src/pages/data-metadata'],
  'data-asset': ['src/pages/data-asset'],
  'data-service': ['src/pages/data-service'],
  consumption: ['src/pages/data-analysis/consumption'],
  'data-development': ['src/pages/development/data-development'],
  dataset: ['src/pages/data-analysis/dataset'],
  modeling: ['src/pages/modeling'],
  quality: ['src/pages/data-quality'],
  security: ['src/pages/data-security'],
  mdm: ['src/pages/mdm'],
};

/**
 * @param {string[]} changedPaths Git-reported paths, not shell arguments.
 * @param {Set<string>} modules Existing Maven business artifactId/directory names.
 * @param {Map<string,Set<string>>} reverseDependencies Actual Maven dependency graph.
 */
export function classifyChangedPaths(changedPaths, modules, reverseDependencies = new Map()) {
  if (!Array.isArray(changedPaths) || changedPaths.length === 0) {
    return fullPlan('No reliable changed-file evidence');
  }
  const selectedModules = new Set();
  const jestTargets = new Set();
  let frontend = false;
  let backend = false;

  for (const file of changedPaths) {
    if (typeof file !== 'string' || !file
        || file.startsWith('/') || file.startsWith('../') || file.includes('/../')
        || file.includes('\n') || file.includes('\r') || file.includes('\\')) {
      return fullPlan('Untrusted or unrecognized path');
    }

    if (file === 'pom.xml' || file === 'mvnw' || file === 'mvnw.cmd'
        || file.startsWith('.mvn/')
        || file.startsWith('.github/')
        || file.startsWith('scripts/')
        || file.startsWith('data-ops-framework/')
        || file.startsWith('data-ops-bom/')
        || file.startsWith('data-ops-common/')
        || file.startsWith('data-ops-spi/')
        || file.startsWith('data-ops-core/')
        || file.startsWith('data-ops-plugins/')
        || file.startsWith('data-ops-boot/')
        || file.startsWith('data-ops-dist/')) {
      return fullPlan('Shared infrastructure, migration, release, script or workflow changed: ' + file);
    }

    if (file.startsWith('docs/') || file === 'README.md'
        || file === 'CODE_STYLE.md' || file === 'PRODUCT_STYLE.md'
        || file === '.gitignore' || file === 'LICENSE') {
      continue;
    }

    const business = file.match(/^data-ops-business\/(data-ops-business-[^/]+)\/(.+)$/);
    if (business) {
      const [, module, relative] = business;
      if (!modules.has(module)) return fullPlan('Unknown business module: ' + module);
      if (relative === 'pom.xml' || relative.endsWith('/pom.xml')
          || relative.includes('/db/migration/')
          || relative.startsWith('src/main/resources/db/')
          || /^src\/main\/java\/.*\/api\//.test(relative)) {
        return fullPlan('Maven, migration or published Java API changed: ' + file);
      }
      if (relative.endsWith('.md')) continue;
      if (!relative.startsWith('src/')) {
        return fullPlan('Unclassified business-module source: ' + file);
      }
      backend = true;
      if (relative.startsWith('src/main/')) {
        addConsumers(module, reverseDependencies, selectedModules);
      } else {
        selectedModules.add(module);
      }
      continue;
    }

    if (file.startsWith('data-ops-business/')) {
      // Aggregator POM, module addition and new/untracked directories are high risk.
      return fullPlan('Business reactor layout changed: ' + file);
    }

    if (file.startsWith('data-ops-ui/')) {
      if (file === 'data-ops-ui/package.json'
          || file === 'data-ops-ui/yarn.lock'
          || file.startsWith('data-ops-ui/config/')
          || file.startsWith('data-ops-ui/scripts/')
          || file.startsWith('data-ops-ui/src/components/')
          || file.startsWith('data-ops-ui/src/utils/')
          || file.startsWith('data-ops-ui/src/layouts/')
          || file.startsWith('data-ops-ui/src/models/')
          || file.startsWith('data-ops-ui/src/access')) {
        return fullPlan('Shared frontend / build configuration changed: ' + file);
      }

      const page = file.match(/^data-ops-ui\/src\/pages\/([^/]+)\/(.+)$/);
      if (page) {
        frontend = true;
        jestTargets.add('src/pages/' + page[1]);
        if (page[1] === 'semantic') jestTargets.add('src/pages/metric');
        if (page[1] === 'metric') jestTargets.add('src/pages/semantic');
        continue;
      }

      const service = file.match(/^data-ops-ui\/src\/services\/([^/]+)\/(.+)$/);
      if (service && SERVICE_PAGE_TESTS[service[1]]) {
        frontend = true;
        for (const target of SERVICE_PAGE_TESTS[service[1]]) jestTargets.add(target);
        jestTargets.add('src/services/' + service[1]);
        continue;
      }

      if (file.startsWith('data-ops-ui/src/locales/')) {
        frontend = true;
        jestTargets.add('src/locales');
        continue;
      }
      return fullPlan('Unknown frontend or shared service surface: ' + file);
    }

    return fullPlan('Unclassified repository path: ' + file);
  }

  const sortedModules = [...selectedModules].sort();
  return {
    mode: backend || frontend ? 'scoped' : 'docs-only',
    reason: backend || frontend ? 'Changed module and affected consumers only' : 'Documentation only',
    full: false,
    backend,
    frontend,
    modules: sortedModules.map((module) => BUSINESS_ROOT + '/' + module).join(','),
    jestTargets: [...jestTargets].sort().join(','),
  };
}

function changedFilesForPullRequest(baseSha) {
  if (!/^[0-9a-f]{40}$/.test(baseSha ?? '')) {
    throw new Error('Missing validated pull-request base SHA');
  }
  // GitHub checks out a synthetic PR merge commit. Comparing the original
  // base SHA against that merge result includes PR changes conservatively.
  const raw = execFileSync('git', [
    'diff', '--name-only', '--diff-filter=ACDMRT', '-z', baseSha, 'HEAD',
  ], { cwd: ROOT, encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 });
  return raw.split('\0').filter(Boolean);
}

export function planForEvent(event, changedPaths, modules, reverseDependencies) {
  if (event !== 'pull_request') return fullPlan('Main / manual full acceptance');
  return classifyChangedPaths(changedPaths, modules, reverseDependencies);
}

function main() {
  const event = process.env.GITHUB_EVENT_NAME ?? 'workflow_dispatch';
  let changedPaths = [];
  let plan;
  try {
    if (event === 'pull_request') {
      changedPaths = changedFilesForPullRequest(process.env.PR_BASE_SHA);
    }
    const modules = readBusinessModules();
    const reverse = readReverseDependencies(modules);
    plan = planForEvent(event, changedPaths, modules, reverse);
  } catch (error) {
    console.error('Impact detection failed; fail-safe to full CI:', error);
    plan = fullPlan('Diff or module graph unavailable');
  }

  const fields = {
    full: String(plan.full),
    backend: String(plan.backend),
    frontend: String(plan.frontend),
    modules: plan.modules,
    jest_targets: plan.jestTargets,
    mode: plan.mode,
  };
  if (process.env.GITHUB_OUTPUT) {
    fs.appendFileSync(process.env.GITHUB_OUTPUT,
      Object.entries(fields).map(([key, value]) => key + '=' + value).join('\n') + '\n');
  }
  console.log(JSON.stringify({ ...plan, changedFiles: changedPaths }, null, 2));
  if (process.env.GITHUB_STEP_SUMMARY) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,
      '### Architecture CI impact plan\n\n' +
      '| Field | Value |\n|---|---|\n' +
      Object.entries({ mode: plan.mode, reason: plan.reason,
        backendModules: plan.modules || '—',
        frontendTargets: plan.jestTargets || '—' })
        .map(([key, value]) => '| ' + key + ' | ' + String(value).replaceAll('|', '\\|') + ' |')
        .join('\n') + '\n');
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main();
}
