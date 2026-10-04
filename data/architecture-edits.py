from pathlib import Path
import re
import importlib.util

root = Path.cwd()
module = root / 'data-ops-business/data-ops-business-sync/data-ops-business-sync-offline'
for p in (module / 'src/main/java').rglob('*.java'):
    text = p.read_text(encoding='utf-8')
    text = text.replace('io.yak.ops.common.bean.po.datasource.DataSourcePO', 'io.yak.ops.business.datasource.domain.DataSourceDefinition')
    text = text.replace('DataSourcePO', 'DataSourceDefinition')
    text = text.replace('io.yak.ops.business.datasource.dao.DataSourceDao', 'io.yak.ops.business.datasource.query.DataSourceReader')
    text = text.replace('DataSourceDao', 'DataSourceReader').replace('dataSourceDao', 'dataSourceReader')
    text = text.replace('dataSourceReader.selectById(', 'dataSourceReader.require(').replace('dataSourceReader.selectByIds(', 'dataSourceReader.findByIds(')
    if text != p.read_text(encoding='utf-8'): p.write_text(text, encoding='utf-8')

for p in (module / 'src/test/java').rglob('*.java'):
    text = p.read_text(encoding='utf-8')
    if 'DataSourcePO' not in text or p.name == 'DataSourceFixtures.java': continue
    # PO construction remains a test fixture, but consumer calls use domain values.
    text = text.replace('io.yak.ops.business.datasource.dao.DataSourceDao', 'io.yak.ops.business.datasource.query.DataSourceReader')
    text = text.replace('DataSourceDao', 'DataSourceReader').replace('dataSourceDao', 'dataSourceReader')
    if 'DataSourceReader' in text:
        text = text.replace('.selectByIds(', '.findByIds(')
        if 'RepositoryAdapterTest' not in p.name: text = text.replace('.selectById(', '.require(')
    # Factory-returned fixtures are immutable domain definitions.
    text = re.sub(r'(private\s+)DataSourcePO(\s+dataSource\([^)]*\)\s*\{)([\s\S]*?)(\n  \})',
        lambda m: m[1]+'DataSourceDefinition'+m[2]+re.sub(r'return (\w+);', r'return DataSourceFixtures.definition(\1);', m[3])+m[4], text)
    if 'RepositoryAdapterTest' in p.name:
        text = re.sub(r'DataSourcePO (source|sink) = dataSource', r'DataSourceDefinition \1 = dataSource', text)
    # Execution contexts accept the datasource-owned domain snapshot, not PO.
    text = re.sub(r'new ExecutionContext\(([^,]+),([^,]+),([^,]+),\s*(dataSource),',
        r'new ExecutionContext(\1,\2,\3, DataSourceFixtures.definition(\4),', text)
    if 'DataSourceDefinition' in text and 'import io.yak.ops.business.datasource.domain.DataSourceDefinition;' not in text:
        text = text.replace('\nimport ', '\nimport io.yak.ops.business.datasource.domain.DataSourceDefinition;\nimport ', 1)
    if 'DataSourceFixtures.' in text and 'import io.yak.ops.business.sync.offline.engine.DataSourceFixtures;' not in text:
        text = text.replace('\nimport ', '\nimport io.yak.ops.business.sync.offline.engine.DataSourceFixtures;\nimport ', 1)
    p.write_text(text, encoding='utf-8')

# The isolation contract applies to the schema source section, not later additive sections.
spec = importlib.util.spec_from_file_location('migration_edits', root / 'scripts/db/update-flyway-contract-tests.py')
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)
p = root / helper.F_ISOLATION
text = p.read_text(encoding='utf-8').replace('String sql = readMigration(', 'String sql = section(readMigration(').replace('"yak-security/db/migration/V1__security_framework_baseline.sql");', '"yak-security/db/migration/V1__security_framework_baseline.sql"), "V1__init_yak_security.sql");')
if 'private static String section(' not in text:
    i = text.rfind('}')
    text = text[:i] + helper.sec('  ') + text[i:]
p.write_text(text, encoding='utf-8')
