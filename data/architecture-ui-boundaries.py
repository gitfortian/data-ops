from pathlib import Path
import re
root=Path.cwd().resolve();base=root/'data-ops-ui/src'
files=list(base.rglob('*.ts'))+list(base.rglob('*.tsx'))
moves=[('pages/integration/sourceHandoff.ts','services/integration/sourceHandoff.ts'),('pages/data-quality/components/QualityStatus.tsx','components/quality/QualityStatus.tsx'),('pages/data-metadata/components/AssetExplorer.tsx','components/metadata/AssetExplorer.tsx'),('pages/data-metadata/components/AssetDetailDrawer.tsx','components/metadata/AssetDetailDrawer.tsx'),('pages/data-metadata/components/typeVisual.ts','components/metadata/typeVisual.ts')]
move_map={base/a:base/b for a,b in moves}
for p in files:
 s=p.read_text(encoding='utf-8')
 def rewrite(m):
  name=m[2]
  if not name.startswith(('@/', '.')):return m[0]
  candidate=(base/name[2:] if name.startswith('@/') else p.parent/name).resolve()
  for source,target in move_map.items():
   if candidate==source or str(candidate)==str(source.with_suffix('')):
    return m[1]+"@/"+str(target.relative_to(base).with_suffix('')).replace('\\','/')+m[3]
  return m[0]
 new=re.sub(r"(from\s+['\"])([^'\"]+)(['\"])",rewrite,s)
 if p in move_map and 'data-metadata' in str(p):new=new.replace("'../collect/constants'", "'@/services/metadata/presentation'")
 if p in move_map and p.name=='QualityStatus.tsx':new=new.replace("'../types'", "'@/services/data-quality/types'")
 if new!=s:p.write_text(new,encoding='utf-8')
for source,target in move_map.items():
 assert source.is_relative_to(root) and target.is_relative_to(root)
 target.parent.mkdir(parents=True,exist_ok=True);target.write_text(source.read_text(encoding='utf-8'),encoding='utf-8');source.unlink()
p=base/'services/metadata/presentation.ts';p.write_text("/** Backend LocalDateTime projection formatting, shared by metadata consumers. */\nexport const formatMetadataTime = (value?: string | null): string =>\n  value ? String(value).replace('T', ' ').slice(0, 19) : '-';\n",encoding='utf-8')
p=base/'pages/data-metadata/collect/constants.ts';s=p.read_text(encoding='utf-8');s=re.sub(r'export const formatMetadataTime = [\s\S]*?;\n',"export { formatMetadataTime } from '@/services/metadata/presentation';\n",s,count=1);p.write_text(s,encoding='utf-8')
