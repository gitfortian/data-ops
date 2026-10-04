from pathlib import Path
import re
root=Path.cwd()
p=root/'data-ops-ui/config/config.ts'
s=p.read_text(encoding='utf-8'); start=s.index('  /**\n   * @name openAPI'); end=s.index('\n  mock:',start)
s=s[:start]+ '  // API contracts are maintained in services beside the owning backend domain.\n'+s[end:]; s=s.replace('import { join } from "node:path";\n',''); p.write_text(s,encoding='utf-8')
for name in ['workflow/instances.ts','workflow/schedules.ts','taskCatalog/index.ts','realtime-sync/legacy.ts']:
 p=root/'data-ops-ui/src/services'/name
 s=p.read_text(encoding='utf-8').replace("import { request } from '@umijs/max';", "import request from '@/utils/request';")
 p.write_text(s,encoding='utf-8')
p=root/'data-ops-business/data-ops-business-job/src/test/java/io/yak/ops/business/job/adapter/plugin/SqlTaskExecutorAdapterTest.java'
s=p.read_text(encoding='utf-8').replace('TaskExecution completed = awaitTerminal(started.executionId());\n\n    assertTrue(completed.successful());\n    assertEquals(42L, executedProjectId.get());','TaskExecution completed;\n    projectHolder.set(new ProjectContext(42L, "Project A"));\n    try {\n      completed = awaitTerminal(started.executionId());\n    } finally {\n      projectHolder.remove();\n    }\n\n    assertTrue(completed.successful());\n    assertEquals(42L, executedProjectId.get());')
p.write_text(s,encoding='utf-8')
p=root/'data-ops-ui/src/pages/modeling/detail.tsx'
s=p.read_text(encoding='utf-8')
# Isolate structure types and physical type rules from UI state and rendering.
start=s.index('interface ColumnDraft');end=s.index('/** 当前高亮行 key')
segment=s[start:end]
exports=['ColumnDraft','IndexDraft','PropertyDraft','CODE_PATTERN','MODEL_PUBLISH_FLOW_CODE','MODEL_PUBLISH_BIZ_TYPE','PUBLISH_APPROVAL_POLL_MS','TYPE_DEFAULTS','typeSpecOf','resolveColumnType','PARTITION_TYPES_BY_DIALECT','AGGREGATE_FUNC_OPTIONS','AGGREGATE_LAYERS']
for name in exports:
 segment=re.sub(r'(?m)^(interface|const) '+name+r'\b',r'export \1 '+name,segment)
folder=p.parent/'editor';folder.mkdir(exist_ok=True)
(folder/'structureRules.ts').write_text("import type { ModelingTypeOption } from '@/services/modeling/types';\n\n"+segment,encoding='utf-8')
s=s[:start]+s[end:]
s="import { " + ', '.join(exports) + " } from './editor/structureRules';\n"+s
s=s.replace('useCallback, useEffect, useMemo, useRef, useState','createContext, useContext, useCallback, useEffect, useMemo, useRef, useState')
a=s.index('/** 当前高亮行 key');b=s.index('/** 可拖拽排序行',a)
s=s[:a]+"const RowInteractionContext = createContext<{ highlight?: number; dragging: ReadonlySet<number> }>({ dragging: new Set() });\n\n"+s[b:]
s=s.replace("  const rowKey = props['data-row-key'];", "  const interaction = useContext(RowInteractionContext);\n  const rowKey = props['data-row-key'];")
s=s.replace('numericKey === _highlightRowKey','numericKey === interaction.highlight').replace('_multiDragActive && _multiDragSelectedKeys.has(numericKey)','interaction.dragging.has(numericKey)')
a=s.index('let draftSeq = 0;');b=s.index('/** 模型详情',a)
helpers=s[a:b].replace('let draftSeq = 0;','const draftSeq = useRef(0);').replace('draftSeq += 1;','draftSeq.current += 1;').replace('return draftSeq;','return draftSeq.current;')
s=s[:a]+s[b:]
s=s.replace('const ModelingModelDetail: React.FC = () => {','const ModelingModelDetail: React.FC = () => {\n'+helpers)
s=s.replace('    _multiDragActive = false;\n    _multiDragSelectedKeys = new Set<number>();','    setDraggingKeys(new Set());').replace('      _multiDragActive = true;\n      _multiDragSelectedKeys = selectedKeys;','      setDraggingKeys(selectedKeys);')
s=s.replace('    _highlightRowKey = row.key;\n','').replace('      _highlightRowKey = undefined;\n','')
s=s.replace('  const [highlightRowKey, setHighlightRowKey] = useState<number>();','  const [highlightRowKey, setHighlightRowKey] = useState<number>();\n  const [draggingKeys, setDraggingKeys] = useState<ReadonlySet<number>>(new Set());')
s=s.replace('<DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>','<RowInteractionContext.Provider value={{ highlight: highlightRowKey, dragging: draggingKeys }}>\n              <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd} onDragCancel={() => { setDraggingKeys(new Set()); setDragOverlayRows([]); }}>')
s=s.replace('</DndContext>','</DndContext>\n              </RowInteractionContext.Provider>')
p.write_text(s,encoding='utf-8')
