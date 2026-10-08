import { fileURLToPath } from 'node:url';
import path from 'node:path';

function requireResult(result, expected, label) {
  if (result !== expected) throw new Error(label + ': expected ' + expected + ', got ' + result);
}

/** Evaluates the fixed required Architecture gate, including planned skips. */
export function verifyArchitectureGate({
  impact, full, expectBackend, expectFrontend, backend, frontend, distribution,
}) {
  requireResult(impact, 'success', 'Impact and static contracts');
  if (full !== 'true' && full !== 'false') throw new Error('Missing verified full/scoped plan');
  if (full === 'true') {
    requireResult(backend, 'success', 'Full backend');
    requireResult(frontend, 'success', 'Full frontend');
    requireResult(distribution, 'success', 'Full distribution');
    return 'full';
  }

  for (const [label, expected, actual] of [
    ['Backend', expectBackend, backend],
    ['Frontend', expectFrontend, frontend],
  ]) {
    if (expected !== 'true' && expected !== 'false') {
      throw new Error(label + ' expected scope is missing');
    }
    requireResult(actual, expected === 'true' ? 'success' : 'skipped', label);
  }
  requireResult(distribution, 'skipped', 'Scoped distribution');
  return 'scoped';
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const mode = verifyArchitectureGate({
      impact: process.env.PLAN,
      full: process.env.FULL,
      expectBackend: process.env.EXPECT_BACKEND,
      expectFrontend: process.env.EXPECT_FRONTEND,
      backend: process.env.BACKEND,
      frontend: process.env.FRONTEND,
      distribution: process.env.DISTRIBUTION,
    });
    console.log('Architecture gate passed with ' + mode + ' acceptance.');
  } catch (error) {
    console.error('::error::' + error.message);
    process.exitCode = 1;
  }
}
