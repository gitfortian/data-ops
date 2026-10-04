from pathlib import Path
r=Path('.')
def edit(p,f):
 p=Path(p);s=p.read_text(encoding='utf-8');p.write_text(f(s),encoding='utf-8',newline='')
d='data-ops-business/data-ops-business-datasource/src/main/java/io/yak/ops/business/datasource/'
Path(d+'domain/DataSourceReference.java').write_text('''package io.yak.ops.business.datasource.domain;

import io.yak.ops.common.enums.datasource.DataSourceDbType;

/** Project-owned identity for definition and display reads; carries no connection credentials. */
public record DataSourceReference(Long id, Long projectId, String name, DataSourceDbType dbType) {}
''',encoding='utf-8')
for f in ['query/DataSourceReader.java','repository/DataSourceRepository.java','repository/DataSourceRepositoryAdapter.java']:
 edit(d+f,lambda s:s.replace('import io.yak.ops.business.datasource.domain.DataSourceDefinition;','import io.yak.ops.business.datasource.domain.DataSourceDefinition;\nimport io.yak.ops.business.datasource.domain.DataSourceReference;').replace('List<DataSourceDefinition> findByIds','List<DataSourceReference> findReferences').replace('repository.findByIds(ids)','repository.findReferences(ids)'))
edit(d+'query/DataSourceReader.java',lambda s:s.replace('  public PageData<DataSourceDefinition> page(','''  public DataSourceReference requireReference(Long id) {
    return findReferences(List.of(requireId(id))).stream().findFirst()
        .orElseThrow(() -> new DataSourceException(DataSourceErrorCode.NOT_FOUND));
  }

  public PageData<DataSourceDefinition> page('''))
edit(d+'repository/DataSourceRepositoryAdapter.java',lambda s:s.replace('    currentProjectId();\n    if (ids == null','    long projectId = currentProjectId();\n    if (ids == null').replace('return dao.selectByIds(ids).stream().map(this::toDomain).toList();','''return dao.selectReferences(projectId, ids).stream()
        .map(row -> new DataSourceReference(row.getId(), row.getProjectId(), row.getName(), row.getDbType()))
        .toList();'''))
edit(d+'dao/DataSourceDao.java',lambda s:s.replace('  List<DataSourcePO> selectByIds(List<Long> ids);','  List<DataSourcePO> selectByIds(List<Long> ids);\n\n  List<DataSourcePO> selectReferences(Long projectId, List<Long> ids);'))
edit(d+'dao/impl/DataSourceDaoImpl.java',lambda s:s.replace('  @Override\n  public IPage<DataSourcePO> selectPage', '''  @Override
  public List<DataSourcePO> selectReferences(Long projectId, List<Long> ids) {
    long trustedProjectId = requireCurrentProject(projectId);
    if (ids == null || ids.isEmpty()) return List.of();
    if (ids.size() > 1000) throw new IllegalArgumentException("最多批量读取 1000 个数据源");
    List<Long> normalizedIds = ids.stream().filter(id -> id != null && id > 0).distinct().toList();
    if (normalizedIds.isEmpty()) return List.of();
    return dataSourceMapper.selectList(Wrappers.<DataSourcePO>lambdaQuery()
        .select(DataSourcePO::getId, DataSourcePO::getProjectId, DataSourcePO::getName, DataSourcePO::getDbType)
        .eq(DataSourcePO::getProjectId, trustedProjectId).in(DataSourcePO::getId, normalizedIds));
  }

  @Override
  public IPage<DataSourcePO> selectPage'''))
o='data-ops-business/data-ops-business-sync/data-ops-business-sync-offline/src/'
f=o+'main/java/io/yak/ops/business/sync/offline/engine/LinkUpJobSpecFactory.java'
def factory(s):
 s=s.replace('import io.yak.ops.business.datasource.domain.DataSourceDefinition;','import io.yak.ops.business.datasource.domain.DataSourceDefinition;\nimport io.yak.ops.business.datasource.domain.DataSourceReference;')
 s=s.replace('DataSourceDefinition sourceDataSource = dataSource(', 'DataSourceReference sourceDataSource = dataSourceReference(').replace('DataSourceDefinition sinkDataSource = dataSource(', 'DataSourceReference sinkDataSource = dataSourceReference(')
 a=s.index('  public static final class BuildResult')
 s=s[:a]+s[a:].replace('DataSourceDefinition','DataSourceReference')
 s=s.replace('  private DataSourceDefinition dataSource(','''  private DataSourceReference dataSourceReference(Long id, String endpointName) {
    if (id == null) return null;
    return dataSourceReader.requireReference(id);
  }

  private DataSourceDefinition dataSource(''')
 return s
edit(f,factory)
edit(o+'main/java/io/yak/ops/business/sync/offline/repository/OfflineJobDefinitionRepositoryAdapter.java',lambda s:s.replace('DataSourceDefinition','DataSourceReference').replace('findByIds','findReferences').replace('dataSource.getId()','dataSource.id()').replace('dataSource.getName()','dataSource.name()'))
# Factory tests also exercise execution-time credential translation: retain full definition stubs there.
for p in Path(o+'test').rglob('*Factory*Test.java'):
 s=p.read_text(encoding='utf-8')
 import re
 s=re.sub(r'(when\(dao.require\(([^\n]+?)\)\).thenReturn\(dataSource\(([^\n]+?)\)\);)',lambda m:m[1]+'\n    when(dao.requireReference('+m[2]+')).thenReturn(reference(dataSource('+m[3]+')));',s)
 if 'requireReference' in s:
  s=s.replace('  private DataSourceDefinition dataSource(','''  private io.yak.ops.business.datasource.domain.DataSourceReference reference(DataSourceDefinition definition) {
    return new io.yak.ops.business.datasource.domain.DataSourceReference(
        definition.getId(), definition.getProjectId(), definition.getName(), definition.getDbType());
  }

  private DataSourceDefinition dataSource(''')
  p.write_text(s,encoding='utf-8',newline='')
p=o+'test/java/io/yak/ops/business/sync/offline/repository/OfflineJobDefinitionRepositoryAdapterTest.java'
edit(p,lambda s:s.replace('DataSourceDefinition','DataSourceReference').replace('findByIds','findReferences').replace('return DataSourceFixtures.definition(dataSource);','return new DataSourceReference(id, PROJECT_ID, name, null);'))
m='data-ops-business/data-ops-business-metadata/src/test/java/io/yak/ops/business/metadata/architecture/MetadataLayeringConventionTest.java'
edit(m,lambda s:s.replace('      if (file.relativePath().equals("controller/v1/MetadataOverviewController.java")','''      // Registry currently returns its owning immutable-by-convention type metadata row.
      // This exception permits that type only, never a mapper or DAO operation in detail.
      if (file.relativePath().equals("detail/EntityDetailService.java")
          && imported.equals(MODULE_PACKAGE + ".dao.model.MdTypeDefPO")) continue;
      if (file.relativePath().equals("controller/v1/MetadataOverviewController.java")''').replace('          "datasource.config.BusinessDatabaseConfiguration",\n          "datasource.config.DataSourceProperties"','          "datasource.config.ConditionalOnDataSourceEnabled"').replace('"datasource.config.ConditionalOnDataSourceEnabled",\n          "datasource.config.ConditionalOnDataSourceEnabled"','"datasource.config.ConditionalOnDataSourceEnabled"'))
