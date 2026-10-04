from pathlib import Path
import re
# Resource parameter policy owns validation; existing service constructor remains compatible.
p=Path('data-ops-framework/data-security/src/main/java/io/yak/framework/security/service/impl/UserResourceServiceImpl.java');s=p.read_text(encoding='utf-8')
a=s.index('  /**\n   * 校验资源控制层级参数');b=s.index('  /**\n   * 批量保存用户资源权限关系',a)
segment=s[a:b].replace('private void checkParam','void validate').replace('checkParam(', 'validate(')
imports='\n'.join(line for line in s.splitlines() if line.startswith('import ') and ('.dto.resource.' in line or any(n in line for n in ['ResultCode;','ControlLevelCode;','ShowLevelCode;','ProjectBriefVO;','ResourceTypeVO;','YakSecurityException;','ProjectService;','ResourceTypeService;'])))
policy='package io.yak.framework.security.service.impl;\n\n'+imports+'\n\n/** Validates resource hierarchy and assignment inputs before reads or commands. */\nfinal class ResourceGrantPolicy {\n  private final ProjectService projectService;\n  private final ResourceTypeService resourceTypeService;\n\n  ResourceGrantPolicy(ProjectService projectService, ResourceTypeService resourceTypeService) {\n    this.projectService = projectService;\n    this.resourceTypeService = resourceTypeService;\n  }\n\n'+segment+'}\n'
s=s[:a]+s[b:];s=s.replace('checkParam(', 'grantPolicy.validate(')
s=s.replace('  private final ResourceExtend resourceExtend;', '  private final ResourceExtend resourceExtend;\n\n  private final ResourceGrantPolicy grantPolicy;')
s=s.replace('    this.resourceExtend = resourceExtend;', '    this.resourceExtend = resourceExtend;\n    this.grantPolicy = new ResourceGrantPolicy(projectService, resourceTypeService);')
p.write_text(s,encoding='utf-8');p.with_name('ResourceGrantPolicy.java').write_text(policy,encoding='utf-8')
# Aggregate classification has one owner shared by both SQL parser modes.
folder=Path('data-ops-business/data-ops-business-data-development/src/main/java/io/yak/ops/business/development/service')
first=(folder/'SqlColumnLineageParser.java').read_text(encoding='utf-8')
match=re.search(r'  private static final Set<String> AGGREGATE_FUNCTIONS = Set\.of\([\s\S]*?\);',first); assert match
body=match[0].replace('private static final','static final')
(folder/'SqlExpressionClassification.java').write_text('package io.yak.ops.business.development.service;\n\nimport java.util.Set;\nimport java.util.Locale;\n\n/** One classification contract for baseline and derived SQL lineage. */\nfinal class SqlExpressionClassification {\n  private SqlExpressionClassification() {}\n'+body+'\n  static boolean aggregate(String name) {\n    return name != null && AGGREGATE_FUNCTIONS.contains(name.toUpperCase(Locale.ROOT));\n  }\n}\n',encoding='utf-8')
for name in ['SqlColumnLineageParser.java','DerivedAwareSqlColumnLineageParser.java']:
 p=folder/name;s=p.read_text(encoding='utf-8');s=re.sub(r'  private static final Set<String> AGGREGATE_FUNCTIONS = Set\.of\([\s\S]*?\);\n','',s)
 s=s.replace('name != null && AGGREGATE_FUNCTIONS.contains(name.toUpperCase(Locale.ROOT))','SqlExpressionClassification.aggregate(name)');p.write_text(s,encoding='utf-8')
