from pathlib import Path
p=Path('data-ops-business/data-ops-business-lineage/src/test/java/io/yak/ops/business/lineage/architecture/LineageDependencyBoundaryTest.java');s=p.read_text(encoding='utf-8');s=s.replace('Set.of("io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled"),\n          "config/LineagePersistenceConfiguration.java",\n          Set.of(\n              "io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration",\n              "io.yak.ops.business.datasource.config.DataSourceProperties"));','Set.of("io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled"));');p.write_text(s,encoding='utf-8')
p=Path('data-ops-business/data-ops-business-quality/src/test/java/io/yak/ops/business/quality/architecture/QualityDependencyBoundaryTest.java');s=p.read_text(encoding='utf-8').replace('            "config/QualityConfiguration.java",\n            Set.of(\n                "io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration"),\n','');p.write_text(s,encoding='utf-8')
p=Path('data-ops-business/data-ops-business-lineage/src/test/java/io/yak/ops/business/lineage/architecture/LineagePublicApiBoundaryTest.java');s=p.read_text(encoding='utf-8').replace('&& !normalized.contains("/target/");','&& !normalized.contains("/target/")\n        && !normalized.contains("/.git/")\n        && !normalized.contains("/data/");');p.write_text(s,encoding='utf-8')
# Latest operation guard for Modeling and Data Service load paths.
p=Path('data-ops-ui/src/pages/modeling/detail.tsx');s=p.read_text(encoding='utf-8');s="import { useLatestOperation } from '@/hooks/useLatestOperation';\n"+s
s=s.replace('  const modelId = params.id;', '  const modelId = params.id;\n  const beginStructureLoad = useLatestOperation(modelId);')
a=s.index('  const loadStructure = useCallback');b=s.index('  /** 重新导入',a);seg=s[a:b]
seg=seg.replace('    setLoading(true);','    const isCurrent = beginStructureLoad();\n    setLoading(true);',1)
seg=seg.replace('      setStructure(data);','      if (!isCurrent()) return;\n      setStructure(data);')
seg=seg.replace('            if (columns?.length)', '            if (!isCurrent()) return;\n            if (columns?.length)')
seg=seg.replace('                setRows(discovered.rows);','                if (!isCurrent()) return;\n                setRows(discovered.rows);')
seg=seg.replace('              setRows(discovered.rows);','              if (!isCurrent()) return;\n              setRows(discovered.rows);')
seg=seg.replace('setRows(initialRows);','if (!isCurrent()) return;\n                setRows(initialRows);')
seg=seg.replace('    } catch (error) {\n      message.error', '    } catch (error) {\n      if (!isCurrent()) return;\n      message.error').replace('      setLoading(false);','      if (isCurrent()) setLoading(false);')
seg=seg.replace('[modelId, discoverStandards]', '[modelId, discoverStandards, beginStructureLoad]')
s=s[:a]+seg+s[b:];p.write_text(s,encoding='utf-8')
p=Path('data-ops-ui/src/pages/development/data-development/components/data-service/DataServiceNodeEditor.tsx');s=p.read_text(encoding='utf-8');s="import { useLatestOperation } from '@/hooks/useLatestOperation';\n"+s
s=s.replace('  const metadataContext =', '  const beginLoad = useLatestOperation(node.id);\n  const metadataContext =',1)
a=s.index('  const load = useCallback');b=s.index('  useEffect',a);seg=s[a:b]
seg=seg.replace('    setLoading(true);','    const isCurrent = beginLoad();\n    setLoading(true);').replace('      const next = applyContext(await getDevelopmentDataServiceNode(node.id));','      const raw = await getDevelopmentDataServiceNode(node.id);\n      if (!isCurrent()) return;\n      const next = applyContext(raw);')
seg=seg.replace('    } catch (error) {','    } catch (error) {\n      if (!isCurrent()) return;').replace('      setLoading(false);','      if (isCurrent()) setLoading(false);').replace('[applyContext, loadPublicationState, node.id]', '[applyContext, loadPublicationState, node.id, beginLoad]')
s=s[:a]+seg+s[b:];p.write_text(s,encoding='utf-8')
