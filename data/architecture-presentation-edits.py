from pathlib import Path
import re
base=Path('data-ops-ui/src');p=base/'pages/data-service/utils.ts';s=p.read_text(encoding='utf-8');m=re.search(r'export const dataServiceDetailUrl = [\s\S]*?;\n',s);assert m
(base/'services/data-service/navigation.ts').write_text(m[0],encoding='utf-8');s=s.replace(m[0],"export { dataServiceDetailUrl } from '@/services/data-service/navigation';\n");p.write_text(s,encoding='utf-8')
p=base/'pages/development/data-development/components/data-service/DataServiceNodeEditor.tsx';s=p.read_text(encoding='utf-8').replace("'@/pages/data-service/utils'", "'@/services/data-service/navigation'");p.write_text(s,encoding='utf-8')
# Shared page presentation is reusable across Security and SQL execution surfaces.
p=base/'pages/data-security/shared.tsx';s=p.read_text(encoding='utf-8');a=s.index('/** 数据安全各页');b=s.index('export const LEVEL_RANK_COLORS',a);segment=s[a:b]
target=base/'components/ui/PagePresentation.tsx';target.write_text("import type { ReactNode } from 'react';\n\n"+segment,encoding='utf-8')
s="export { PageHeader, StatCard } from '@/components/ui/PagePresentation';\n"+s[:a]+s[b:];s=s.replace("import type { ReactNode } from 'react';\n",'');p.write_text(s,encoding='utf-8')
p=base/'pages/data-source/sql-executions/index.tsx';s=p.read_text(encoding='utf-8').replace("'@/pages/data-security/shared'", "'@/components/ui/PagePresentation'");p.write_text(s,encoding='utf-8')
