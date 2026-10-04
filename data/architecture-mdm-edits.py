from pathlib import Path
p=Path('data-ops-business/data-ops-business-mdm/src/main/java/io/yak/ops/business/mdm/application/MdmCleanService.java');s=p.read_text(encoding='utf-8')
a=s.index('  /** 按规则类型分发表达式');b=s.index('  // ==== 通用规则校验',a)
transform=s[a:b].replace('private Map<String, Object> applyTransformExpr','static Map<String, Object> apply')
s=s[:a]+s[b:]
a=s.index('    switch (ruleType) {',s.index('  private void validateGenericRule'));b=s.index('\n  /**\n   * 去重发现',a)
validators=s[a:b]
# Separate the repository-backed policy entry from the expression-only rules.
end=validators.index('\n  private void validateDedupExpr')
validation_body=validators[:end].replace('    switch (ruleType)', '    switch (ruleType)')
methods=validators[end:]
methods=methods.replace('private void validate','private static void validate')
policy='package io.yak.ops.business.mdm.application;\n\n'+ '\n'.join(x for x in s.splitlines() if x.startswith('import ') and any(v in x for v in ['domain.clean.','MdmException','MdmErrorCode','DedupSql','java.util.']))+'\n\n/** Pure cleansing expression validation and transformation; persistence and audit stay in the use case. */\nfinal class CleanRulePolicy {\n  private CleanRulePolicy() {}\n\n'+transform+'  static void validate(MdmCleanRuleType ruleType, String ruleExprJson, Set<String> validCodes, Set<String> pkCodes) {\n'+validation_body+methods+'}\n'
s=s[:a]+'    CleanRulePolicy.validate(ruleType, ruleExprJson, validCodes, pkCodes);\n  }\n'+s[b:]
s=s.replace('applyTransformExpr(rule,','CleanRulePolicy.apply(rule,')
p.write_text(s,encoding='utf-8');p.with_name('CleanRulePolicy.java').write_text(policy,encoding='utf-8')
