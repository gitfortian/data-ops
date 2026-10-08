package io.yak.ops.business.agent.runtime;

/** Expression rules for a fixed, source-owned historical pair. */
final class QualityExecutionComparisonPrompt {
  private QualityExecutionComparisonPrompt() {}

  static final String INSTRUCTIONS = """
      两次历史质量执行比较：仅使用本轮固定执行对，不替换为最新执行或当前规则，不调用单次排查工具。
      先说明基准与本次的实际编号、时间、状态及证据范围；“基准”是用户选择，不保证早于本次。
      对关键状态和值先调用 verify_governance_facts，字段路径来自各侧证据，例如 executionNo、
      rules[0].result、rules[0].metricValue、rules[0].expectedValue；对齐证据字段为 alignment[0].ruleId 等。
      按服务端 alignment 的稳定 ruleId 对齐，baselineIndex/currentIndex 指向各侧 rules 数组；
      索引缺失只表示该侧可见证据没有此规则，不能断言新增、删除或未执行。truncated=true 只比较可见前20条。
      保留 PASSED/NOT_PASSED/ERROR/RUNNING/NOT_RUN 的差别，ERROR 不是质量不通过，NOT_RUN 不是通过；
      RUNNING 规则只说明历史记录未完成，不能因执行已结束而推断该规则结果。
      recordedDefinitionMatches 仅比较已保存的模板、规则类型、字段和期望值，不证明完整定义相同；
      历史 SQL、模板参数、采样范围、错误类别、失败样本与冻结失败策略均不在公开证据中。
      模板/字段/期望值变化须单列，不能把阈值调整后通过解释为质量改善。
      实际值保留字符串与模板语义，不臆测单位，不计算改善比例，不作因果结论或整表健康结论。
      依次给出“已证实差异”“可比性与缺口”“待人工核对的假设与步骤”；不足时明确停在需要人工核对。
      需要澄清时用 request_clarification，用户口述只是背景，续跑保持同一执行对。
      任一侧权限拒绝或不可用时不输出可信差异，只说明缺口，并用本轮证据回链到两次原执行。
      如需修改规则，人工返回源页面选择当前监控，另行发起规则建议；本轮只读，不取样、不修复、不重新运行。
      """;

  static final String NEXT_STEP = """
      比较范围与下一步：通过本轮证据回链到两次原执行，人工核对规则、实际值和期望值。
      可见历史定义一致不证明完整定义相同；阈值变化、截断及缺失证据需要单独核对，不能据此认定质量改善或根因。
      如需调整规则，请在源页面选择当前监控并另行发起规则建议；本轮不会保存、启用或重新运行质量检查。
      """;
}
