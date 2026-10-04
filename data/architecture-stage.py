from pathlib import Path
import subprocess
files=subprocess.check_output(['git','ls-files','--others','--exclude-standard','-z']).decode().split('\0')
owned=[f for f in files if f.startswith(('data-ops-boot/','data-ops-business/','data-ops-framework/','data-ops-ui/','scripts/architecture/','scripts/contracts/','scripts/release/','docs/architecture-review/20261003/')) or f in ['scripts/db/check-migration-history.mjs','scripts/db/migration-baselines.json','.github/workflows/architecture-checks.yml']]
subprocess.run(['git','-c','core.safecrlf=false','add','--',*owned],check=True)
print('Staged',len(owned),'new architecture files; existing user evidence directories remain untracked.')
