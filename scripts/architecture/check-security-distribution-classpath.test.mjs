import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  ARTIFACTS, expectedBinaryOwners, validateDistribution,
} from './check-security-distribution-classpath.mjs';

const digest = 'a'.repeat(64);
const first = 'io/yak/framework/security/common/dto/PageParamDTO.class';
const second = 'io/yak/framework/security/common/po/UserPO.class';
const third = 'io/yak/framework/security/authentication/SaTokenAuthenticationManager.class';
const sample = new Map([
  [first, 'contract'], [second, 'persistence'], [third, 'runtime'],
]);

function fixture() {
  const nested = Object.values(ARTIFACTS)
    .map(name => 'BOOT-INF/lib/' + name + '-1.0.0.jar');
  const class_owners = Object.fromEntries([...sample].map(([name, owner]) =>
    [name, ['BOOT-INF/lib/' + ARTIFACTS[owner] + '-1.0.0.jar']]));
  return { boot_sha256: digest, release_sha256: digest, nested, class_owners };
}

test('the live binary manifest reuses migrated source-owner inventories', () => {
  const expected = expectedBinaryOwners();
  assert.ok(expected.size >= 125, 'must cover more than the original 75+36 owner types');
  assert.equal(expected.get(first), 'contract');
  assert.equal(expected.get(second), 'persistence');
  assert.equal(expected.get(third), 'runtime');
  assert.ok([...expected].every(([name, owner]) =>
    name.endsWith('.class') && ARTIFACTS[owner]));
});

test('release package accepts exactly one relocated compiled owner', () => {
  assert.deepEqual(validateDistribution(fixture(), sample), []);
});

test('release package refuses missing or shadowing owners', () => {
  const missing = fixture();
  missing.class_owners[first] = [];
  assert.match(validateDistribution(missing, sample).join('\n'), /missing, duplicated or wrong/);
  const oldStarterCopy = fixture();
  oldStarterCopy.class_owners[first].push('BOOT-INF/lib/data-security-spring-boot-starter-1.0.0.jar');
  assert.match(validateDistribution(oldStarterCopy, sample).join('\n'), /Duplicate Security FQCN/);
  const wrong = fixture();
  wrong.class_owners[second] = ['BOOT-INF/lib/data-security-spring-boot-starter-1.0.0.jar'];
  assert.match(validateDistribution(wrong, sample).join('\n'), /wrong binary owner/);
});

test('release package refuses missing modules and duplicate versions', () => {
  const missing = fixture();
  missing.nested = missing.nested.filter(name => !name.includes('security-runtime'));
  assert.match(validateDistribution(missing, sample).join('\n'), /exactly one data-ops-platform-security-runtime/);
  const duplicate = fixture();
  duplicate.nested.push('BOOT-INF/lib/data-ops-platform-security-contract-1.0.1.jar');
  assert.match(validateDistribution(duplicate, sample).join('\n'), /exactly one data-ops-platform-security-contract/);
});

test('release package refuses tampered dist jar, empty inventory and hidden duplicate', () => {
  const tampered = fixture();
  tampered.release_sha256 = 'b'.repeat(64);
  assert.match(validateDistribution(tampered, sample).join('\n'), /differs from the tested Boot/);
  const empty = fixture();
  empty.class_owners = {};
  assert.match(validateDistribution(empty, sample).join('\n'), /inventory is empty/);
  const hidden = fixture();
  hidden.class_owners['io/yak/framework/security/hidden/SomeClass.class'] = [
    'BOOT-INF/lib/foo.jar', 'BOOT-INF/lib/bar.jar',
  ];
  assert.match(validateDistribution(hidden, sample).join('\n'), /Duplicate Security FQCN/);
});

test('the concrete release probe reads nested jars and final tar without network', () => {
  const source = readFileSync('scripts/architecture/probe-security-distribution.py', 'utf8');
  assert.match(source, /with tarfile\.open\(dist_path, "r:gz"\)/);
  assert.match(source, /libs\/yak-ops-api\.jar/);
  assert.match(source, /zipfile\.ZipFile\(io\.BytesIO\(boot_jar\.read\(name\)\)\)/);
  assert.match(source, /hashlib\.sha256/);
});
