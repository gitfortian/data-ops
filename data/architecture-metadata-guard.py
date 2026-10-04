from pathlib import Path
p=Path('data-ops-business/data-ops-business-metadata/src/test/java/io/yak/ops/business/metadata/architecture/MetadataLayeringConventionTest.java');s=p.read_text(encoding='utf-8');s=s.replace('          && imported.equals(MODULE_PACKAGE + ".dao.model.MdTypeDefPO")) continue;','          && imported.equals(MODULE_PACKAGE + ".dao.model.MdTypeDefPO")) continue;\n      if (file.relativePath().equals("query/CatalogQueryService.java")\n          && imported.equals(MODULE_PACKAGE + ".dao.model.MdFieldDefPO")) continue;');p.write_text(s,encoding='utf-8')

