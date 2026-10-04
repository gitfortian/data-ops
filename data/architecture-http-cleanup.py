from pathlib import Path
import re
for rel in ['data-ops-ui/src/pages/settings/services/computeEnvironments.ts','data-ops-ui/src/pages/create/index.tsx']:
 p=Path(rel);s=p.read_text(encoding='utf-8');s=s.replace("import { request } from '@umijs/max';", "import request from '@/utils/request';").replace("import { history, request, useLocation } from '@umijs/max';", "import request from '@/utils/request';\nimport { history, useLocation } from '@umijs/max';");p.write_text(s,encoding='utf-8')
# Prove demo clients have no consumers before removing unused example generation artifacts.
root=Path('data-ops-ui'); candidates=list((root/'src/services/swagger').rglob('*'))+list((root/'src/services/ant-design-pro').rglob('*'))+[root/'config/oneapi.json']
for p in root.rglob('*.ts*'):
 if 'node_modules' in p.parts or '.umi' in p.parts or 'dist' in p.parts or p in candidates:continue
 s=p.read_text(encoding='utf-8',errors='ignore')
 if 'services/swagger' in s or 'services/ant-design-pro' in s:raise SystemExit('Example consumer requires migration: '+str(p))
for p in candidates:
 if p.is_file():p.unlink()
p=root/'package.json';import json
obj=json.loads(p.read_text(encoding='utf-8'));obj['scripts'].pop('openapi',None);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
