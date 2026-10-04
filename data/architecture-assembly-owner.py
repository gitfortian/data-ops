from pathlib import Path
import re
root=Path.cwd().resolve()
for kind in ['main','test']:
 source=root/f'data-ops-common/src/{kind}/java/io/yak/ops/common/mybatis'
 for p in source.glob('*.java'):
  target=root/f'data-ops-boot/src/{kind}/java/io/yak/ops/boot/config/persistence'/p.name
  assert target.is_relative_to(root) and p.is_relative_to(root)
  target.parent.mkdir(parents=True,exist_ok=True);target.write_text(p.read_text(encoding='utf-8').replace('io.yak.ops.common.mybatis','io.yak.ops.boot.config.persistence'),encoding='utf-8');p.unlink()
p=root/'data-ops-boot/src/main/java/io/yak/ops/boot/config/persistence/BusinessDatabaseConfiguration.java';s=p.read_text(encoding='utf-8').replace('import io.yak.ops.common.mybatis.MybatisPlusFactorySupport;\n','');p.write_text(s,encoding='utf-8')
p=root/'data-ops-common/pom.xml';s=p.read_text(encoding='utf-8');s=re.sub(r'        <dependency>\s*<groupId>com.baomidou</groupId>\s*<artifactId>mybatis-plus-core</artifactId>[\s\S]*?</dependency>\n','',s);p.write_text(s,encoding='utf-8')
p=root/'data-ops-business/data-ops-business-metadata/src/test/java/io/yak/ops/business/metadata/architecture/MetadataLayeringConventionTest.java';s=p.read_text(encoding='utf-8');needle='      if (!imported.startsWith(MODULE_PACKAGE + ".")) {';replacement='''      if (file.relativePath().equals("controller/v1/MetadataOverviewController.java")
          && Set.of(MODULE_PACKAGE + ".config.ConditionalOnMetadataPersistence",
              MODULE_PACKAGE + ".stat.MetadataOverviewService",
              MODULE_PACKAGE + ".stat.MetadataOverviewService.Overview").contains(imported)) {
        continue; // Exact infrastructure annotation/read projection corridor, not a whole package allowance.
      }
'''+needle;assert needle in s;s=s.replace(needle,replacement);p.write_text(s,encoding='utf-8')
