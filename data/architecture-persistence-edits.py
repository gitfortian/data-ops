from pathlib import Path
import re

root = Path.cwd().resolve()
source = root / 'data-ops-business/data-ops-business-datasource/src/main/java/io/yak/ops/business/datasource/config'
target = root / 'data-ops-boot/src/main/java/io/yak/ops/boot/config/persistence'
assert source.is_relative_to(root) and target.is_relative_to(root)
target.mkdir(parents=True, exist_ok=True)
for name in ('BusinessDatabaseConfiguration.java', 'BusinessDatabaseProperties.java'):
    p = source / name
    text = p.read_text(encoding='utf-8').replace('package io.yak.ops.business.datasource.config;', 'package io.yak.ops.boot.config.persistence;')
    text = text.replace('"io.yak.ops.common.bean.po.job"', '"io.yak.ops.common.bean.po.job,"')
    (target / name).write_text(text, encoding='utf-8')
    p.unlink()

for p in (root / 'data-ops-business').rglob('src/main/java/**/*.java'):
    text = p.read_text(encoding='utf-8')
    original = text
    text = re.sub(r'^import io\.yak\.ops\.business\.datasource\.config\.BusinessDatabaseConfiguration;\n', '', text, flags=re.M)
    text = re.sub(r'^@Import\((?:io\.yak\.ops\.business\.datasource\.config\.)?BusinessDatabaseConfiguration\.class\)\n', '', text, flags=re.M)
    if 'BusinessDatabaseConfiguration' in text: raise RuntimeError(str(p))
    # These modules consume the shared database beans, not Datasource's properties.
    if p.name != 'DataSourceConfiguration.java' and 'import io.yak.ops.business.datasource.config.DataSourceProperties;' in text:
        if text.count('DataSourceProperties') == 2:
            text = text.replace('import io.yak.ops.business.datasource.config.DataSourceProperties;\n', '').replace('@EnableConfigurationProperties(DataSourceProperties.class)\n', '')
    for annotation in ('Import', 'EnableConfigurationProperties'):
        if '@'+annotation not in text:
            text = re.sub(r'^import org\.springframework\.[^;]+\.'+annotation+r';\n', '', text, flags=re.M)
    if text != original: p.write_text(text, encoding='utf-8')

# Production docs describe the new assembly owner; prior migration numbers are source-section provenance.
for p in (root / 'data-ops-business').rglob('*.md'):
    if p.name not in ('ARCHITECTURE.md', 'DEPENDENCIES.md'): continue
    text = p.read_text(encoding='utf-8')
    original = text
    text = text.replace('@Import(BusinessDatabaseConfiguration.class)', 'Boot `config.persistence.BusinessDatabaseConfiguration` 应用装配')
    text = text.replace('datasource 模块提供 yakBusinessDataSource', 'Boot 应用装配提供 yakBusinessDataSource')
    text = text.replace('V2031__register_lifecycle_menu.sql', 'V2__boot_security_baseline.sql（Source: V2031__register_lifecycle_menu.sql）')
    text = text.replace('V2032__register_data_asset_menu.sql', 'V2__boot_security_baseline.sql（Source: V2032__register_data_asset_menu.sql）')
    if p.parent.name == 'data-ops-business-mdm' and p.name == 'ARCHITECTURE.md':
        text = text.replace('## 迁移所有权(规划,合入后不可再编辑)', '## 迁移所有权\n\n当前可执行迁移为 `V1__mdm_baseline.sql`。下表版本号是合并文件 `-- Source:` 段的历史出处，不是独立迁移文件。新的结构变化使用前向增量迁移，不再重新生成已发布基线。')
    if p.parent.name == 'data-ops-business-modeling' and p.name == 'DEPENDENCIES.md':
        text = text.replace('建模 V18 迁移', '建模 `V1__modeling_baseline.sql` 中历史 V18 Source 段')
    if text != original: p.write_text(text, encoding='utf-8')
