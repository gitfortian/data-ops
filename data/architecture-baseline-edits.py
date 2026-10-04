from pathlib import Path
import subprocess, json

root = Path.cwd()
for p in (root / 'data-ops-boot/src/test').rglob('*.java'):
    text = p.read_text(encoding='utf-8').replace('V1__boot_security_baseline.sql', 'V2__boot_security_baseline.sql')
    if p.name in ('DataServiceAccessMenuMigrationTest.java', 'DataServiceApiCallMenuMigrationTest.java'):
        text = text.replace('Files.readString(migrationSource())', 'migrationSource()')
    p.write_text(text, encoding='utf-8')

files = subprocess.check_output(['git','ls-files'],text=True).splitlines()
shared_files = [p for p in files if p.startswith('data-ops-common/src/main/java/') and '/bean/po/' in p and p.endswith('.java')]
imports = []
import re
for name in files:
    p=root/name
    if '/src/main/java/' not in name or not p.is_file() or not name.endswith('.java'): continue
    for m in re.finditer(r'^import (io\.yak\.ops\.common\.bean\.po\.[^;]+);',p.read_text(encoding='utf-8'),re.M): imports.append(name+':'+m[1])
(root/'scripts/architecture/legacy-shared-persistence.json').write_text(json.dumps({'purpose':'Existing shared persistence references only. Remove entries as owners migrate; new PO types/imports are prohibited.', 'files':shared_files,'imports':sorted(imports)},indent=2)+'\n',encoding='utf-8')
