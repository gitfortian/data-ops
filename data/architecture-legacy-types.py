from pathlib import Path
import subprocess
p=Path('data-ops-ui/src/types/legacyApi.d.ts');p.parent.mkdir(exist_ok=True)
p.write_bytes(subprocess.check_output(['git','show','HEAD:data-ops-ui/src/services/ant-design-pro/typings.d.ts']))
p=Path('docs/data-asset/design.md');p.write_bytes(subprocess.check_output(['git','show','HEAD:docs/data-asset/design.md']))
