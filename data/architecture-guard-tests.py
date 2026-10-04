from pathlib import Path
p=Path('data-ops-business/data-ops-business-datasource/src/test/java/io/yak/ops/business/datasource/dao/impl/DataSourceDaoImplCredentialTest.java')
s=p.read_text(encoding='utf-8');s=s.replace('  @Test\n  void storesCiphertext', '''  @Test
  void referenceReadSelectsOnlyIdentityColumnsWithoutDecryptingCredentials() {
    // Deliberately invalid ciphertext would throw if this path tried to decrypt a credential.
    DataSourcePO projected = row(42L, "ENC:invalid", "ENC:invalid");
    when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(projected));
    assertThat(dao(BASE64_KEY).selectReferences(7L, List.of(42L))).containsExactly(projected);
    ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).selectList(query.capture());
    assertThat(query.getValue().getSqlSelect()).isEqualTo("id,project_id,name,db_type");
    assertThat(query.getValue().getSqlSegment()).contains("project_id", "id IN");
  }

  @Test
  void storesCiphertext''');p.write_text(s,encoding='utf-8')
p=Path('scripts/architecture/import-boundaries.mjs');p.write_text('''/** Resolve explicit, nested/static and wildcard imports against the actual source ownership map. */
export function importedClasses(name, classes) {
  if (name.endsWith('.*')) {
    const prefix = name.slice(0, -1);
    return [...classes.entries()].filter(([key]) => key.startsWith(prefix)).map(([, value]) => value);
  }
  let target = name;
  while (target.includes('.') && !classes.has(target)) target = target.slice(0, target.lastIndexOf('.'));
  return classes.has(target) ? [classes.get(target)] : [];
}

export function crossesPersistenceBoundary(source, name, classes) {
  return source.module?.startsWith('data-ops-business-') &&
    /\\.(dao|mapper)(\\.|$)|\\.repository\\.impl(\\.|$)/.test(name) &&
    importedClasses(name, classes).some(target => target.module?.startsWith('data-ops-business-') && target.module !== source.module);
}
''',encoding='utf-8')
p=Path('scripts/architecture/check-boundaries.mjs');s=p.read_text(encoding='utf-8');s="import { crossesPersistenceBoundary } from './import-boundaries.mjs';\n"+s;start=s.index('    let target = name;');end=s.index('    if (name.startsWith',start);s=s[:start]+'''    if (crossesPersistenceBoundary(j, name, classes)) violations.push(`${j.file}: cross-domain persistence ${name}`);
    if ((j.file.startsWith('data-ops-spi/') || /data-ops-plugin-[^/]+-api\\/src\\/main\\/java\\//.test(j.file)) &&
        /^(org\\.springframework\\.|com\\.baomidou\\.|org\\.apache\\.ibatis\\.|io\\.yak\\.ops\\.business\\.)/.test(name))
      violations.push(`${j.file}: plugin contract exposes infrastructure ${name}`);
'''+s[end:];p.write_text(s,encoding='utf-8')
p=Path('scripts/architecture/import-boundaries.test.mjs');p.write_text('''import test from 'node:test';
import assert from 'node:assert/strict';
import { crossesPersistenceBoundary } from './import-boundaries.mjs';
const source = { module: 'data-ops-business-sync-offline' };
const classes = new Map([['io.yak.ops.business.datasource.dao.DataSourceDao', { module: 'data-ops-business-datasource' }],
  ['io.yak.ops.business.sync.offline.dao.DefinitionDao', source]]);
test('explicit, wildcard and static persistence imports cannot bypass domain ownership', () => {
  for (const name of ['io.yak.ops.business.datasource.dao.DataSourceDao', 'io.yak.ops.business.datasource.dao.*',
    'io.yak.ops.business.datasource.dao.DataSourceDao.PageQuery', 'io.yak.ops.business.datasource.dao.DataSourceDao.*'])
    assert.equal(crossesPersistenceBoundary(source, name, classes), true, name);
  assert.equal(crossesPersistenceBoundary(source, 'io.yak.ops.business.sync.offline.dao.*', classes), false);
  assert.equal(crossesPersistenceBoundary({ module: 'data-ops-boot' }, 'io.yak.ops.business.datasource.dao.*', classes), false);
});
''',encoding='utf-8')
p=Path('.github/workflows/architecture-checks.yml');s=p.read_text(encoding='utf-8').replace('      - run: node scripts/architecture/check-boundaries.mjs','      - run: node --test scripts/architecture/*.test.mjs\n      - run: node scripts/architecture/check-boundaries.mjs');p.write_text(s,encoding='utf-8')
