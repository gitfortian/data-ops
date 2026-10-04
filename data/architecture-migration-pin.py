from pathlib import Path
import json,hashlib,subprocess
root=Path.cwd();files=subprocess.check_output(['git','ls-files'],text=True).splitlines()
history={f:hashlib.sha256((root/f).read_text(encoding='utf-8').encode()).hexdigest() for f in files if '/migration/' in f and Path(f).suffix=='.sql' and not f.startswith('data-ops-framework-legacy/')}
(root/'scripts/db/migration-baselines.json').write_text(json.dumps(history,indent=2)+'\n',encoding='utf-8')
p=root/'scripts/db/update-flyway-contract-tests.py'
s=p.read_text(encoding='utf-8').replace('if __name__ == "__main__":\n    main()','if __name__ == "__main__":\n    raise SystemExit("Retired one-shot conversion. Run the owning Maven contract tests and node scripts/db/check-migration-history.mjs instead.")')
p.write_text(s,encoding='utf-8')
print(len(history))
