from pathlib import Path
import subprocess
files=subprocess.check_output(['git','ls-files','--others','--exclude-standard','-z']).decode().split('\0')
selected=[f for f in files if f.startswith('data-ops-ui/') and f.endswith(('.ts','.tsx','.mjs','.cjs')) and not f.endswith('httpContracts.generated.ts')]
selected+=['data-ops-ui/src/services/workflow/instances.ts']
subprocess.run(['node','data-ops-ui/node_modules/prettier/bin-prettier.js','--write',*selected],check=True)
