/**
 * Read-only architecture inventory for the actual Maven reactor and production Java imports.
 * This is an audit, not a substitute for Maven's effective-POM or the existing boundary gate.
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

export function parsePom(xml) {
  const source = xml.replace(/<!--[\s\S]*?-->/g, '');
  const parent = source.match(/<parent>([\s\S]*?)<\/parent>/)?.[1] ?? '';
  const body = source.replace(/<parent>[\s\S]*?<\/parent>/, '');
  const firstTag = (text, tag) => text.match(new RegExp('<' + tag + '>([^<]+)</' + tag + '>'))?.[1]?.trim() ?? null;
  const modulesBlock = body.match(/<modules>([\s\S]*?)<\/modules>/)?.[1] ?? '';
  const dependenciesBody = body
    .replace(/<dependencyManagement>[\s\S]*?<\/dependencyManagement>/g, '')
    .replace(/<build>[\s\S]*?<\/build>/g, '')
    .replace(/<profiles>[\s\S]*?<\/profiles>/g, '');
  // An inherited project groupId must not be mistaken for the first dependency's groupId.
  const identityBody = dependenciesBody.replace(/<dependencies>[\s\S]*?<\/dependencies>/g, '');
  const dependencies = [...dependenciesBody.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)]
    .map((m) => ({
      groupId: firstTag(m[1], 'groupId'),
      artifactId: firstTag(m[1], 'artifactId'),
      scope: firstTag(m[1], 'scope') ?? 'compile',
      optional: firstTag(m[1], 'optional') === 'true',
    }))
    .filter((d) => d.artifactId);
  return {
    groupId: firstTag(identityBody, 'groupId') ?? firstTag(parent, 'groupId'),
    artifactId: firstTag(identityBody, 'artifactId'),
    packaging: firstTag(identityBody, 'packaging') ?? 'jar',
    modules: [...modulesBlock.matchAll(/<module>([^<]+)<\/module>/g)].map((m) => m[1].trim()),
    dependencies,
  };
}

export function moduleRole(directory) {
  if (directory === 'data-ops-dist') return 'distribution';
  if (directory === 'data-ops-boot') return 'boot';
  if (directory === 'data-ops-ui') return 'frontend';
  if (directory === 'data-ops-framework' || directory.startsWith('data-ops-framework/')) return 'framework';
  if (directory.startsWith('data-ops-business/')) return 'business-domain';
  if (directory.startsWith('data-ops-plugins/')) return 'plugin';
  if (['data-ops-core', 'data-ops-common', 'data-ops-spi', 'data-ops-bom'].includes(directory)) return 'foundation';
  return 'aggregation';
}

export function readReactor(root = REPO_ROOT) {
  const byDirectory = new Map();
  const visit = (pomRel) => {
    const normalized = path.posix.normalize(pomRel);
    if (normalized.startsWith('../') || normalized.startsWith('/') || byDirectory.has(path.posix.dirname(normalized))) {
      throw new Error('Repeated or escaping reactor module: ' + normalized);
    }
    const filename = path.join(root, normalized);
    if (!fs.existsSync(filename)) throw new Error('Missing reactor POM: ' + normalized);
    const parsed = parsePom(fs.readFileSync(filename, 'utf8'));
    const directory = path.posix.dirname(normalized);
    if (!parsed.artifactId) throw new Error('Missing artifactId: ' + normalized);
    const entry = { directory, artifactId: parsed.artifactId, groupId: parsed.groupId,
      packaging: parsed.packaging, role: moduleRole(directory), dependencies: parsed.dependencies };
    byDirectory.set(directory, entry);
    for (const child of parsed.modules) {
      visit(path.posix.join(directory, child, 'pom.xml'));
    }
  };
  visit('pom.xml');
  return [...byDirectory.values()].sort((a, b) => a.directory.localeCompare(b.directory));
}

export function buildMavenEdges(modules) {
  const exact = new Map(modules.map((m) => [m.groupId + ':' + m.artifactId, m]));
  const byArtifact = new Map();
  for (const module of modules) {
    const found = byArtifact.get(module.artifactId) ?? [];
    found.push(module);
    byArtifact.set(module.artifactId, found);
  }
  const edges = [];
  for (const module of modules) {
    for (const dependency of module.dependencies) {
      const artifactCandidates = byArtifact.get(dependency.artifactId) ?? [];
      const target = exact.get(dependency.groupId + ':' + dependency.artifactId)
        ?? (artifactCandidates.length === 1 && (!dependency.groupId || dependency.groupId.includes('$'))
          ? artifactCandidates[0] : null);
      if (!target) continue;
      edges.push({ from: module.directory, to: target.directory,
        scope: dependency.scope, optional: dependency.optional });
    }
  }
  return edges.sort((a, b) => (a.from + ':' + a.to + ':' + a.scope)
    .localeCompare(b.from + ':' + b.to + ':' + b.scope));
}

function ownerForFile(filename, modulesByPath) {
  return modulesByPath.find((m) => filename.startsWith(m.directory + '/'));
}

export function scanJavaImports(root, modules, trackedFiles = null) {
  const files = trackedFiles ?? execFileSync('git', ['ls-files', '-z'], { cwd: root, encoding: 'utf8' })
    .split('\0').filter(Boolean);
  const owned = [...modules].filter((m) => m.directory !== '.' && m.packaging !== 'pom')
    .sort((a, b) => b.directory.length - a.directory.length);
  const sourceFiles = files.filter((f) => f.endsWith('.java') && f.includes('/src/main/java/'))
    .map((f) => ({ filename: f, owner: ownerForFile(f, owned) }))
    .filter((f) => f.owner && fs.existsSync(path.join(root, f.filename)));
  const classes = new Map();
  for (const item of sourceFiles) {
    const source = fs.readFileSync(path.join(root, item.filename), 'utf8');
    const pkg = source.match(/^\s*package\s+([\w.]+)\s*;/m)?.[1];
    if (pkg) classes.set(pkg + '.' + path.basename(item.filename, '.java'),
      { directory: item.owner.directory, filename: item.filename });
  }
  const edgeMap = new Map();
  for (const item of sourceFiles) {
    const source = fs.readFileSync(path.join(root, item.filename), 'utf8');
    for (const match of source.matchAll(/^\s*import\s+(?:static\s+)?([\w.*]+)\s*;/gm)) {
      const expression = match[1];
      const targets = [];
      if (expression.endsWith('.*')) {
        const prefix = expression.slice(0, -1);
        const enclosing = classes.get(expression.slice(0, -2));
        if (enclosing) targets.push(enclosing);
        else for (const [name, target] of classes) if (name.startsWith(prefix)) targets.push(target);
      } else {
        let key = expression;
        while (key.includes('.') && !classes.has(key)) key = key.slice(0, key.lastIndexOf('.'));
        if (classes.has(key)) targets.push(classes.get(key));
      }
      for (const target of targets) {
        if (target.directory === item.owner.directory) continue;
        const key = item.owner.directory + '\0' + target.directory;
        let entry = edgeMap.get(key);
        if (!entry) {
          entry = { from: item.owner.directory, to: target.directory, importCount: 0, samples: [] };
          edgeMap.set(key, entry);
        }
        entry.importCount += 1;
        if (entry.samples.length < 3) entry.samples.push(item.filename + ' → ' + expression);
      }
    }
  }
  return { productionJavaFiles: sourceFiles.length, resolvedClasses: classes.size,
    edges: [...edgeMap.values()].sort((a, b) => (a.from + a.to).localeCompare(b.from + b.to)) };
}

export function audit(root = REPO_ROOT, trackedFiles = null) {
  const reactor = readReactor(root);
  const imports = scanJavaImports(root, reactor, trackedFiles);
  return {
    note: 'Source-level import and declared POM edges only; reflection, runtime injection, effective POM and dynamic execution need separate review.',
    moduleCount: reactor.length,
    businessModuleCount: reactor.filter((m) => m.role === 'business-domain' && m.packaging !== 'pom').length,
    productionJavaFiles: imports.productionJavaFiles,
    resolvedClasses: imports.resolvedClasses,
    modules: reactor.map(({ dependencies, ...m }) => m),
    mavenEdges: buildMavenEdges(reactor),
    javaImportEdges: imports.edges,
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = audit();
  if (process.argv.includes('--json')) {
    process.stdout.write(JSON.stringify(result, null, 2) + '\n');
  } else {
    const mavenProduction = result.mavenEdges.filter((e) => e.scope !== 'test');
    console.log('DataOps module topology (read-only, declared/static evidence)');
    console.log('Reactor modules: ' + result.moduleCount
      + ', leaf business modules: ' + result.businessModuleCount
      + ', Java files: ' + result.productionJavaFiles);
    console.log('Internal Maven edges: ' + mavenProduction.length
      + ' production/provided/runtime, ' + (result.mavenEdges.length - mavenProduction.length) + ' test');
    console.log('Distinct Java import edges: ' + result.javaImportEdges.length);
    console.log('Top Maven consumers (direct edge counts):');
    const consumerCounts = new Map();
    for (const edge of mavenProduction) consumerCounts.set(edge.to, (consumerCounts.get(edge.to) ?? 0) + 1);
    for (const [name, count] of [...consumerCounts].sort((a, b) => b[1] - a[1]).slice(0, 12)) {
      console.log('  ' + String(count).padStart(3) + '  ' + name);
    }
    console.log('For reproducible edges: node scripts/architecture/module-topology.mjs --json');
  }
}
