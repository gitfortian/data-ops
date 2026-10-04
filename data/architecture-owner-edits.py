from pathlib import Path
import subprocess

root=Path.cwd().resolve()
tracked=subprocess.check_output(['git','ls-files'],text=True).splitlines()
for p in (root/'data-ops-business').rglob('src/main/java/**/*.java'):
    text=p.read_text(encoding='utf-8')
    if 'DataSourceProperties' in text and p.parent.name != 'config' and 'import io.yak.ops.business.datasource.config.DataSourceProperties;' not in text:
        text=text.replace('\nimport ', '\nimport io.yak.ops.business.datasource.config.DataSourceProperties;\nimport ',1)
        p.write_text(text,encoding='utf-8')

for domain in ('mdm','asset','metric'):
    source=root/f'data-ops-common/src/main/java/io/yak/ops/common/bean/po/{domain}'
    target=root/f'data-ops-business/data-ops-business-{domain}/src/main/java/io/yak/ops/business/{domain}/dao/model'
    assert source.is_relative_to(root) and target.is_relative_to(root)
    target.mkdir(parents=True,exist_ok=True)
    for p in source.glob('*.java'):
        text=p.read_text(encoding='utf-8').replace(f'package io.yak.ops.common.bean.po.{domain};',f'package io.yak.ops.business.{domain}.dao.model;')
        (target/p.name).write_text(text,encoding='utf-8')
        p.unlink()
    for name in tracked:
        p=root/name
        if not p.is_file() or p.suffix not in ('.java','.md','.xml'): continue
        text=p.read_text(encoding='utf-8')
        new=text.replace(f'io.yak.ops.common.bean.po.{domain}',f'io.yak.ops.business.{domain}.dao.model')
        if new!=text: p.write_text(new,encoding='utf-8')

source=root/'data-ops-business/data-ops-business-mdm/src/main/java/io/yak/ops/business/mdm/dao/MdmDedupKeyRow.java'
target=root/'data-ops-business/data-ops-business-mdm/src/main/java/io/yak/ops/business/mdm/domain/clean/MdmDedupKey.java'
text=source.read_text(encoding='utf-8').replace('package io.yak.ops.business.mdm.dao;', 'package io.yak.ops.business.mdm.domain.clean;').replace('MdmDedupKeyRow','MdmDedupKey')
target.write_text(text,encoding='utf-8')
source.unlink()
for p in (root/'data-ops-business/data-ops-business-mdm').rglob('*.java'):
    if 'target' in p.parts: continue
    text=p.read_text(encoding='utf-8')
    new=text.replace('io.yak.ops.business.mdm.dao.MdmDedupKeyRow','io.yak.ops.business.mdm.domain.clean.MdmDedupKey').replace('MdmDedupKeyRow','MdmDedupKey')
    if new!=text:p.write_text(new,encoding='utf-8')
