from pathlib import Path
b=Path('data-ops-business/data-ops-business-sync/data-ops-business-sync-offline/src/main/java/io/yak/ops/business/sync/offline')
for rel in ['engine/plan/OfflineExecutionPlanFactory.java','definition/OfflineDefinitionSupport.java']:
 p=b/rel;s=p.read_text(encoding='utf-8').replace('DataSourceDefinition','DataSourceReference')
 if rel.startswith('definition'):s=s.replace('dataSource.getId()','dataSource.id()').replace('dataSource.getDbType()','dataSource.dbType()')
 p.write_text(s,encoding='utf-8')
p=Path('data-ops-business/data-ops-business-modeling/src/main/java/io/yak/ops/business/modeling/derive/MetricDraftPlanner.java');s=p.read_text(encoding='utf-8').replace('import java.util.*;','import java.util.ArrayList;\nimport java.util.LinkedHashSet;\nimport java.util.List;\nimport java.util.Locale;\nimport java.util.Set;');p.write_text(s,encoding='utf-8')
