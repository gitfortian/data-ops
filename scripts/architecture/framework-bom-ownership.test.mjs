import test from 'node:test';
import assert from 'node:assert/strict';
import { THIRD_PARTY, verifyBomOwnership, verifyParity, readCurrent } from './framework-bom-ownership.mjs';

const { bom, parent } = readCurrent();

test('A8.1b real BOM owns six Framework third-party dependency versions', () => {
  assert.equal(THIRD_PARTY.length, 6);
  assert.deepEqual(verifyBomOwnership(bom), []);
});
test('A8.1b real BOM preserves old Framework parent managed versions', () => {
  assert.deepEqual(verifyParity(bom, parent), []);
});
test('A8.1b forbids imported Framework parent POM reintroduction', () => {
  const added = bom.replace('</dependencies>',
    '<dependency><groupId>io.github.weifuwan</groupId><artifactId>data-ops-framework-parent</artifactId>'
    + '<version>0.1.0</version><type>pom</type><scope>import</scope></dependency></dependencies>');
  assert.match(verifyBomOwnership(added).join('\n'), /must not import or manage/);
});
test('A8.1b rejects missing, duplicated, or scoped third-party management entry', () => {
  const row = bom.match(/<dependency>\s*<groupId>com\.alibaba<\/groupId>\s*<artifactId>druid<\/artifactId>\s*<version>\$\{druid\.version\}<\/version>\s*<\/dependency>/)?.[0];
  assert.ok(row, 'Druid must be explicitly owned in BOM');
  assert.match(verifyBomOwnership(bom.replace(row, '')).join('\n'), /expected exactly one/);
  assert.match(verifyBomOwnership(bom.replace(row, row + row)).join('\n'), /expected exactly one/);
  assert.match(verifyBomOwnership(bom.replace(row, row.replace('</dependency>',
    '<scope>provided</scope></dependency>'))).join('\n'), /wrong version reference or scope/);
});
test('A8.1b rejects property drift or hard-coded version in managed row', () => {
  assert.match(verifyBomOwnership(bom.replace('3.5.16', '3.5.99')).join('\n'), /expected 3\.5\.16/);
  assert.match(verifyBomOwnership(bom.replace('$' + '{springdoc.version}', '2.6.0')).join('\n'),
    /wrong version reference/);
});
test('A8.1b detects transitional parent/BOM parity drift', () => {
  const drift = parent.replace('<checker-qual.version>3.37.0</checker-qual.version>',
    '<checker-qual.version>9.9.9</checker-qual.version>');
  assert.match(verifyParity(bom, drift).join('\n'), /parity mismatch/);
});
test('A8.1b ignores XML comments and does not prematurely delete Common contracts', () => {
  const commented = bom.replace('</dependencies>', '<!-- <dependency><groupId>io.github.weifuwan</groupId>'
    + '<artifactId>data-ops-framework-parent</artifactId></dependency> --></dependencies>');
  assert.deepEqual(verifyBomOwnership(commented), []);
  assert.ok(bom.includes('<artifactId>data-common</artifactId>'));
});
