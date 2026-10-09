import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const script = readFileSync('scripts/architecture/verify-security-legacy-consumer.sh', 'utf8');
const consumer = readFileSync('scripts/architecture/fixtures/SecurityLegacyConsumer.java', 'utf8');
const workflow = readFileSync('.github/workflows/architecture-checks.yml', 'utf8');

test('legacy ABI compiles against immutable original main 0.1.0 Starter artifact', () => {
  assert.match(script, /\^\[0-9a-f\]\{40\}\$/);
  assert.match(script, /git -C "\$repo" worktree add --detach "\$legacy" "\$base_sha"/);
  assert.match(script, /-pl data-ops-framework\/data-security -am package/);
  assert.match(script, /data-security-spring-boot-starter-0\.1\.0\.jar/);
  assert.match(script, /javac --release 21 -cp "\$legacy_jar"/);
  assert.match(script, /trap cleanup EXIT/);
});

test('unchanged legacy class runs exclusively from NEW Boot package classpath', () => {
  assert.match(script, /java -cp "\$tmp\/consumer:\$tmp\/current\/classes:\$tmp\/current\/libs\/\*" SecurityLegacyConsumer/);
  assert.match(script, /BOOT-INF\/lib\//);
  assert.match(script, /BOOT-INF\/classes\//);
  assert.doesNotMatch(script, /java -cp "[^"\n]*\$legacy_jar/);
  assert.match(consumer, /implements AuthenticationManager/);
  assert.match(consumer, /new PageParamDTO\(\)/);
  assert.match(consumer, /new User\(\)/);
  assert.match(consumer, /PermissionDefinition\.of/);
  assert.match(consumer, /@YakPermission/);
  assert.match(consumer, /Legacy Security consumer linked and executed unchanged/);
});

test('ABI runtime belongs to existing release job, never a standalone workflow', () => {
  assert.match(workflow, /name: Distribution source and artifact consistency/);
  // The runtime-only test is conditional on the #461 PR, not main push.
  assert.match(workflow, /a8-integration:/);
});
