from pathlib import Path
import re
p=Path('data-ops-ui/src/pages/modeling/detail.tsx'); s=p.read_text(encoding='utf-8')
fields={'tableName':('string',"''"),'tableComment':('string',"''"),'rows':('ColumnDraft[]','[]'),'primaryKey':('string[]','[]'),'indexes':('IndexDraft[]','[]'),'partitionEnabled':('boolean','false'),'partitionType':('string | undefined','undefined'),'partitionColumns':('string[]','[]'),'partitionExpression':('string',"''"),'properties':('PropertyDraft[]','[]'),'dirty':('boolean','false')}
names=[]
for field in fields:
 setter='set'+field[0].upper()+field[1:]
 s,n=re.subn(r'  const \['+field+', '+setter+r'\] = useState[^;]*;\n','',s); assert n==1,(field,n)
 names.extend([field,setter])
s="import { useModelStructureDraft } from './editor/useModelStructureDraft';\n"+s
s=s.replace('  const params = useParams', '  const { '+', '.join(names)+' } = useModelStructureDraft();\n  const params = useParams',1)
p.write_text(s,encoding='utf-8')
body="import { useCallback, useReducer } from 'react';\nimport type { Dispatch, SetStateAction } from 'react';\nimport type { ColumnDraft, IndexDraft, PropertyDraft } from './structureRules';\n\nexport interface StructureDraft {\n"+''.join(f'  {k}: {v[0]};\n' for k,v in fields.items())+"}\nconst initialDraft: StructureDraft = {\n"+''.join(f'  {k}: {v[1]},\n' for k,v in fields.items())+"};\n\nexport type DraftAction = { [K in keyof StructureDraft]: { field: K; value: SetStateAction<StructureDraft[K]> } }[keyof StructureDraft];\n\nexport function structureDraftReducer(state: StructureDraft, action: DraftAction): StructureDraft {\n  const previous = state[action.field];\n  const value = typeof action.value === 'function' ? action.value(previous as never) : action.value;\n  return Object.is(previous, value) ? state : { ...state, [action.field]: value };\n}\n\n/** Structure draft is local to one editor; publication and remote snapshots remain separate. */\nexport function useModelStructureDraft() {\n  const [draft, dispatch] = useReducer(structureDraftReducer, initialDraft);\n"
for field, (type_,_) in fields.items():
 setter='set'+field[0].upper()+field[1:]
 body+=f"  const {setter}: Dispatch<SetStateAction<{type_}>> = useCallback(value => dispatch({{ field: '{field}', value }}), []);\n"
body+='  return { ...draft, '+', '.join('set'+k[0].upper()+k[1:] for k in fields)+' };\n}\n'
(p.parent/'editor/useModelStructureDraft.ts').write_text(body,encoding='utf-8')
