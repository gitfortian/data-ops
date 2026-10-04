from pathlib import Path
import re, subprocess, json

root=Path.cwd().resolve()
files=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard'],text=True).splitlines()
unused=root/'data-ops-business/data-ops-business-semantic/src/main/java/io/yak/ops/business/semantic/repository/SemanticProcessFieldRepository.java'
unused.write_text(unused.read_text(encoding='utf-8').replace('import io.yak.ops.common.bean.po.modeling.ModelingColumnMappingPO;\n',''),encoding='utf-8')
source=root/'data-ops-common/src/main/java/io/yak/ops/common/bean/po'
mapping={}
for p in source.rglob('*.java'):
    suffix=p.relative_to(source)
    domain=suffix.parts[0]
    if domain=='sync':
        sub=suffix.parts[1]
        module=f'data-ops-business/data-ops-business-sync/data-ops-business-sync-{sub}'
        package=f'io.yak.ops.business.sync.{sub}.dao.model'
    else:
        module='data-ops-business/data-ops-business-'+('data-development' if domain=='development' else domain)
        package=f'io.yak.ops.business.{domain}.dao.model'
    target=root/module/'src/main/java'/Path(*package.split('.'))/p.name
    assert target.is_relative_to(root) and (root/module/'pom.xml').is_file()
    old_package=re.search(r'^package ([^;]+);',p.read_text(encoding='utf-8'),re.M)[1]
    old=old_package+'.'+p.stem
    new=package+'.'+p.stem
    mapping[old]=new
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(p.read_text(encoding='utf-8').replace('package '+old_package+';','package '+package+';'),encoding='utf-8')
    p.unlink()
for name in files:
    p=root/name
    if not p.is_file() or p.suffix not in ('.java','.xml','.md'):continue
    if name.startswith(('data/','docs/architecture-review/')):continue
    if p.suffix=='.md' and p.name not in ('ARCHITECTURE.md','DEPENDENCIES.md'):continue
    text=p.read_text(encoding='utf-8')
    new=text
    for old,target in mapping.items():new=new.replace(old,target)
    if new!=text:p.write_text(new,encoding='utf-8')

p=root/'data-ops-boot/src/main/java/io/yak/ops/boot/config/persistence/BusinessDatabaseConfiguration.java'
text=p.read_text(encoding='utf-8')
text=re.sub(r'        factory.setTypeAliasesPackage\([\s\S]*?\);', '        factory.setTypeAliasesPackage("io.yak.ops.business.**.dao.model");',text)
p.write_text(text,encoding='utf-8')
p=root/'scripts/architecture/legacy-shared-persistence.json'
p.write_text(json.dumps({'purpose':'All domain persistence types now live with their owner. Shared PO types/imports are prohibited.','files':[],'imports':[]},indent=2)+'\n',encoding='utf-8')
