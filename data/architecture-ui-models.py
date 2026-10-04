from pathlib import Path
import re
root=Path.cwd().resolve()
# Cleansing serialization belongs to the local model, independent from Ant Design forms.
p=root/'data-ops-ui/src/pages/mdm/cleansing/index.tsx';s=p.read_text(encoding='utf-8')
a=s.index('interface MatchFieldRow');b=s.index('type RuleTypeFilter',a);types=s[a:b]
a2=s.index('const parseExpr');b2=s.index('/** 主数据清洗:',a2);rules=s[a2:b2]
names=['MatchFieldRow','MappingRow','DefaultRow','RuleFormValues','parseExpr','mappingRows','defaultRows','buildRuleExpr','transformChips','changedAttrs']
segment=types+rules
for name in names:segment=re.sub(r'(?m)^(interface|const) '+name+r'\b',r'export \1 '+name,segment)
p.with_name('cleanRuleModel.ts').write_text("import type { MdmMatchType, MdmCleanRuleType, MdmCleanRuleRecord } from '@/services/mdm/types';\n\n"+segment,encoding='utf-8')
s=s[:a2]+s[b2:];s=s[:a]+s[b:];s="import { "+', '.join(names)+" } from './cleanRuleModel';\n"+s;p.write_text(s,encoding='utf-8')
# Data Service draft hydration and parameter interpretation are independent from editor UI.
p=root/'data-ops-ui/src/pages/development/data-development/components/data-service/DataServiceNodeEditor.tsx';s=p.read_text(encoding='utf-8')
a=s.index('const normalizeId');b=s.index('export default function',a);segment=s[a:b]
names=['normalizeId','safeArray','parseNamedParameters','mergeParameters','normalizeContext']
for name in names:segment=segment.replace('const '+name+' ', 'export const '+name+' ',1)
imports="import type { DevelopmentId, DevelopmentResourceNode } from '../../types';\nimport type { DevelopmentDataServiceDefinition, DevelopmentDataServiceNodeContext, DevelopmentDataServiceParameter } from '../../data-service-node-service';\n\n"
p.with_name('dataServiceDraftModel.ts').write_text(imports+segment,encoding='utf-8');s="import { "+', '.join(names)+" } from './dataServiceDraftModel';\n"+s[:a]+s[b:];p.write_text(s,encoding='utf-8')
# Field mappings expose pure planning functions; row IDs are allocated by each mounted editor.
p=root/'data-ops-ui/src/pages/integration/batch-link-up/detail/components/FieldMappingSection.tsx';s=p.read_text(encoding='utf-8')
a=s.index('let mappingRowSeed');b=s.index('const ManualFieldEditor',a);segment=s[a:b]
segment=re.sub(r'let mappingRowSeed = 0;\n\n','',segment)
segment=re.sub(r'const createMappingKey = \(index: number\) => \{[\s\S]*?\};\n\n','',segment)
segment=segment.replace('  value: FieldMappingValue[],\n): FieldMappingRow[]', '  value: FieldMappingValue[],\n  createMappingKey: (index: number) => string,\n): FieldMappingRow[]')
segment=segment.replace('  targetColumns: DataSourceColumnOption[],\n): FieldMappingRow[]', '  targetColumns: DataSourceColumnOption[],\n  createMappingKey: (index: number) => string,\n): FieldMappingRow[]')
names=['normalizeFieldName','getColumnLabel','getFieldType','parseManualFields','rowsToMappingValue','mappingValueToRows','mappingValueSignature','buildSameNameMappings','buildPositionMappings']
for name in names:segment=segment.replace('const '+name+' ', 'export const '+name+' ',1)
value=re.search(r'export interface FieldMappingValue \{[\s\S]*?\n\}',s)[0];row=re.search(r'interface FieldMappingRow \{[\s\S]*?\n\}',s)[0].replace('interface','export interface',1)
model="import type { DataSourceColumnOption } from '../hooks/useDataSourceColumns';\n\n"+value+'\n'+row+'\n\n'+segment
p.with_name('fieldMappingModel.ts').write_text(model,encoding='utf-8')
s=s[:a]+s[b:]
s=s.replace(value,'export type { FieldMappingValue } from "./fieldMappingModel";').replace(row.replace('export interface','interface'),'')
imports=['FieldMappingValue','FieldMappingRow']+[n for n in names if n not in ('mappingValueToRows','buildSameNameMappings','buildPositionMappings')]
s='import { '+', '.join(imports)+", mappingValueToRows as planRows, buildSameNameMappings as planSameNames, buildPositionMappings as planPositions } from './fieldMappingModel';\n"+s
needle='}: FieldMappingSectionProps) {'
s=s.replace(needle,needle+'''
  const rowSequence = useRef(0);
  const rowPrefix = useId();
  const createMappingKey = useCallback((index: number) => `${rowPrefix}-${++rowSequence.current}-${index}`, [rowPrefix]);
  const mappingValueToRows = useCallback((value: FieldMappingValue[]) => planRows(value, createMappingKey), [createMappingKey]);
  const buildSameNameMappings = useCallback((source: DataSourceColumnOption[], target: DataSourceColumnOption[]) => planSameNames(source, target, createMappingKey), [createMappingKey]);
  const buildPositionMappings = useCallback((source: DataSourceColumnOption[], target: DataSourceColumnOption[]) => planPositions(source, target, createMappingKey), [createMappingKey]);
''',1)
p.write_text(s,encoding='utf-8')
