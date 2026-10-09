import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Conservative repository-wide P2 Java symbol audit.
 *
 * "No direct textual reference" is NOT proof of dead code. Spring scanning,
 * ServiceLoader, FQCN configuration, REST routes, Mapper XML, reflection and
 * separate publications are all runtime entrypoints.
 *
 * This is a read-only reachability inventory; it NEVER automatically deletes.
 */
const JAVA_MAIN = /\/src\/main\/java\/.+\.java$/;
const JAVA_TEST = /\/src\/test\/java\/.+\.java$/;
const REFERENCE_TEXT = /\.(?:java|xml|json|ya?ml|properties|sql|mjs|cjs|ts|tsx|mf|gradle|kts|txt)$/i;
const IGNORED_ROOTS = ['docs/', '.zcode/', 'data-ops-ui/', '.github/'];
const DECLARED_TYPE = /\b(?:public|protected|private)?\s*(?:abstract\s+|final\s+|sealed\s+|non-sealed\s+)*(?:class|interface|enum|record|@interface)\s+([A-Za-z_$][\w$]*)\b/g;
const SPRING_OR_EXTERNAL = /@(?:RestController|Controller|ControllerAdvice|RestControllerAdvice|Component|Service|Repository|Configuration|SpringBootApplication|SpringBootConfiguration|Mapper|Entity|TableName|Bean|AutoService|Scheduled|EventListener|JsonTypeName|JsonSubTypes|ServletComponentScan|Enable\w+|Conditional\w+)\b/;
const RUNTIME_DESCRIPTOR = /META-INF\/services\/|META-INF\/spring\/|spring\.factories$|AutoConfiguration\.imports$/;
const DYNAMIC_HINT = /\b(?:Class\.forName|ServiceLoader\.load|ClassLoader|ReflectionUtils|Method\.invoke|Proxy\.newProxyInstance)\b/;
const LEGACY_RELEASE = /\/legacy\/data-job\//;

export function trackedPaths() {
  return execFileSync('git', ['ls-files', '-z'], {
    encoding: 'utf8',
    maxBuffer: 24 * 1024 * 1024,
  }).split(String.fromCharCode(0)).filter(Boolean);
}

export function declaredJavaTypes(source) {
  const packageName = source.match(/^\s*package\s+([\w.]+)\s*;/m)?.[1] ?? '';
  return [...source.matchAll(DECLARED_TYPE)].map(match => ({
    name: match[1], fqn: packageName ? packageName + '.' + match[1] : match[1],
  }));
}

export function retentionReasons(file, source) {
  const reasons = [];
  if (LEGACY_RELEASE.test(file)) reasons.push('independent-legacy-release');
  if (SPRING_OR_EXTERNAL.test(source)) reasons.push('framework-discovered');
  if (DYNAMIC_HINT.test(source)) reasons.push('reflective-loading');
  if (/\/(?:controller|mapper|api|spi|config|configuration)\//i.test(file))
    reasons.push('interface-or-wiring-path');
  if (/\/src\/main\/java\/.*(?:Mapper|Controller|Application|Configuration|AutoConfig|Listener|Provider|Factory|Plugin)\.java$/.test(file))
    reasons.push('runtime-entrypoint-name');
  return reasons;
}

export function decisionFor(references, reasons, ambiguous = false) {
  if (ambiguous) return 'AMBIGUOUS_NAME_REVIEW';
  if (reasons.length) return 'IMPLICIT_ENTRYPOINT_KEEP_UNTIL_PROVEN';
  if (references > 0) return 'REFERENCED_KEEP';
  return 'UNRESOLVED_NO_TEXT_MATCH_REVIEW';
}

let cached;
export function scanBackend() {
  if (cached) return cached;
  const tracked = trackedPaths();
  const prod = tracked.filter(f => JAVA_MAIN.test(f) && existsSync(f));
  const tests = tracked.filter(f => JAVA_TEST.test(f) && existsSync(f));
  const symbolOwners = new Map();
  const descriptors = tracked.filter(f => RUNTIME_DESCRIPTOR.test(f));
  const candidates = prod.map(file => {
    const source = readFileSync(file, 'utf8');
    const topType = path.basename(file, '.java');
    const declared = declaredJavaTypes(source).find(t => t.name === topType);
    const type = declared ?? { name: topType, fqn: null };
    const candidate = {
      file, name: topType, fqn: type.fqn,
      reasons: retentionReasons(file, source), referenceFiles: 0,
      exampleReferences: [], nameCollision: false,
    };
    const owners = symbolOwners.get(topType) ?? [];
    owners.push(candidate);
    symbolOwners.set(topType, owners);
    return candidate;
  });
  const names = new Set(symbolOwners.keys());
  const scanned = tracked.filter(file =>
    REFERENCE_TEXT.test(file) && !IGNORED_ROOTS.some(root => file.startsWith(root)) &&
    existsSync(file));
  for (const file of scanned) {
    const source = readFileSync(file, 'utf8');
    const mentioned = new Set();
    for (const match of source.matchAll(/[A-Za-z_$][\w$]*/g)) {
      if (names.has(match[0])) mentioned.add(match[0]);
    }
    for (const name of mentioned) {
      const owners = symbolOwners.get(name);
      for (const owner of owners) {
        if (owner.file === file) continue;
        owner.referenceFiles += 1;
        if (owner.exampleReferences.length < 3) owner.exampleReferences.push(file);
      }
    }
  }
  for (const owners of symbolOwners.values()) {
    for (const owner of owners) {
      owner.nameCollision = owners.length > 1;
      owner.decision = decisionFor(owner.referenceFiles, owner.reasons, owner.nameCollision);
    }
  }
  const disposition = {};
  for (const row of candidates) disposition[row.decision] = (disposition[row.decision] ?? 0) + 1;
  cached = {
    sourceFiles: prod.length,
    testFiles: tests.length,
    trackedReferenceFiles: scanned.length,
    runtimeDescriptors: descriptors.length,
    moduleRoots: [...new Set(prod.map(p =>
      p.includes('/src/main/java/') ? p.split('/src/main/java/')[0] : p))].sort(),
    disposition,
    candidates,
  };
  return cached;
}

export function printableReport(result = scanBackend()) {
  const lowReference = result.candidates
    .filter(c => c.decision === 'UNRESOLVED_NO_TEXT_MATCH_REVIEW')
    .sort((a, b) => a.file.localeCompare(b.file))
    .slice(0, 30)
    .map(c => ({ file: c.file, fqn: c.fqn, referenceFiles: c.referenceFiles,
      decision: c.decision }));
  const protectedSamples = result.candidates
    .filter(c => c.decision === 'IMPLICIT_ENTRYPOINT_KEEP_UNTIL_PROVEN')
    .slice(0, 12)
    .map(c => ({ file: c.file, reasons: c.reasons }));
  return {
    schema: 'backend-p2-audit-v1',
    safety: 'READ_ONLY; UNRESOLVED is NEVER authorization to delete',
    sourceFiles: result.sourceFiles, testFiles: result.testFiles,
    trackedReferenceFiles: result.trackedReferenceFiles,
    runtimeDescriptors: result.runtimeDescriptors,
    moduleRootCount: result.moduleRoots.length,
    dispositions: result.disposition, lowReference, protectedSamples,
  };
}

const invoked = process.argv[1] && path.resolve(process.argv[1]) ===
  fileURLToPath(import.meta.url);
if (invoked) {
  const report = printableReport();
  process.stdout.write(JSON.stringify(report, null, 2) + '\n');
}
