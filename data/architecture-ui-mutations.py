from pathlib import Path
for rel in ['data-service/DataServiceNodeEditor.tsx','dataset/DatasetNodeEditor.tsx']:
 p=Path('data-ops-ui/src/pages/development/data-development/components')/rel;s=p.read_text(encoding='utf-8')
 marker='  const metadataContext = useSqlMetadataContext(node.id);'
 s=s.replace(marker,'''  const beginQuery = useLatestOperation(`${currentProject?.id ?? ""}:${node.id}`);
  const beginSave = useLatestOperation(`${currentProject?.id ?? ""}:${node.id}`);
  const beginPublish = useLatestOperation(`${currentProject?.id ?? ""}:${node.id}`);
  const beginOnline = useLatestOperation(`${currentProject?.id ?? ""}:${node.id}`);
'''+marker)
 for name,begin,flag in [('runQuery','beginQuery','Running'),('save','beginSave','Saving'),('publish','beginPublish','Publishing'),('goOnline','beginOnline','GoingOnline')]:
  start=s.find('  const '+name+' = async ')
  if start<0:continue
  end=s.index('\n  };',start)+len('\n  };');c=s[start:end]
  c=c.replace('    set'+flag+'(true);','    const isCurrent = '+begin+'();\n    set'+flag+'(true);',1)
  # Guard the resolved request before any editor updates; multiline awaited calls close at indentation 6.
  import re
  c=re.sub(r'(      (?:const \w+ = )?await [^;]+;)',r'\1\n      if (!isCurrent()) return;',c)
  c=c.replace('    } catch (error) {','    } catch (error) {\n      if (!isCurrent()) return;').replace('      set'+flag+'(false);','      if (isCurrent()) set'+flag+'(false);')
  s=s[:start]+c+s[end:]
 # Each object starts with fresh loading flags, even while its previous operation finishes elsewhere.
 s=s.replace('    setLoading(true);\n    setLoadError', '    setLoading(true);\n    setRunning(false);\n    setSaving(false);\n    setPublishing(false);\n    setLoadError',1)
 p.write_text(s,encoding='utf-8')
# Retain historical source sections in owning tests, retire the mutation tool completely.
p=Path('scripts/db/update-flyway-contract-tests.py');p.write_text('''#!/usr/bin/env python3
"""Retired one-shot conversion used for the migration consolidation.

Owning Maven contract tests now verify the consolidated Source sections directly.
Applied baselines are immutable; add forward migrations and run the history guard.
"""
if __name__ == "__main__":
    raise SystemExit("Retired conversion: run owning Maven tests and node scripts/db/check-migration-history.mjs.")
''',encoding='utf-8')
# Pin the newly introduced runtime baseline too.
p=Path('scripts/db/migration-baselines.json');import json,hashlib
history=json.loads(p.read_text(encoding='utf-8'))
f='data-ops-business/data-ops-business-job/src/main/resources/db/migration/yak-job-runtime/V1__job_runtime_terminal_evidence.sql'
if not Path(f).exists():
 f=next(str(x).replace('\\','/') for x in Path('data-ops-business/data-ops-business-job/src/main/resources').rglob('V1*.sql'))
history[f]=hashlib.sha256(Path(f).read_text(encoding='utf-8').replace('\r\n','\n').encode()).hexdigest()
p.write_text(json.dumps(history,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
