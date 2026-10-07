package io.yak.ops.business.agent.runtime;

import io.yak.ops.business.agent.domain.GovernanceTarget;

/** Task-specific expression; it neither reads sources nor expands tool permissions. */
final class QualityTroubleshootingPrompt {
  private QualityTroubleshootingPrompt() {}

  static boolean appliesTo(GovernanceTarget target) {
    return target != null && target.qualityExecutionNo() != null;
  }

  static final String INSTRUCTIONS = """
      历史质量排查任务：帮助用户完成下一步人工核对。围绕固定 execution_no，按以下顺序回答：
      1. 本次执行事实：引用本轮证据，说明对象、读取时点和结果。关键状态/数值先调用 verify_governance_facts；
         字段路径使用实际返回的 JSON，如 checkResult、rules[0].result、rules[0].metricValue、rules[0].expectedValue。
         无法核验的字段明确缺失，不填数值。API、文本或事实核验有截断/上限时，只说明可见范围。
      2. 优先核对的规则：保留 PASSED/NOT_PASSED/ERROR/RUNNING/NOT_RUN 的区别。
         实际值的业务含义需要模板语义；没有语义时只保留原值，不能换算为异常行数。
         全部可见规则 PASSED 只说明本次检查，不代表整表健康；WAITING/RUNNING 说明尚未结束。
      3. 待验证假设与证据缺口：逐项写清假设及需要补充的证据。当前公开证据没有错误类别、SQL、
         失败样本、冻结失败策略或上游状态。ERROR 只能确认执行异常，具体原因需原详情核对；
         NOT_RUN 只能确认未执行，不能确认本次 STOP 触发原因，也不能判定通过或不通过。
      4. 人工检查步骤：每项说明在哪里检查、检查什么、看到什么后再判断。先从原执行详情核对规则、
         实际值和期望值。缺少入口或样本时说明需表维护人员核对，不能编造按钮/链接或宣称已检查。
         关注规则或业务背景不明确时使用 request_clarification；用户口述标为“用户提供的背景”，
         不升级为源事实，不用澄清补造历史字段。澄清后仍围绕同一次执行。
      5. 源页面下一步：通过本轮证据回链返回执行详情。需要修改规则时，请用户在源页面选择当前监控，
         再独立发起规则建议任务；不由监控名称推导 ID，不在此轮读取当前定义替换历史、取样或执行修复。
      证据不足时明确停在“需要人工核对”；无法读取源证据时只说明状态/缺口和下一步，不生成根因。
      """;

  static final String NEXT_STEP = """

      排查范围与下一步：请通过本轮证据回链返回本次执行详情，人工核对规则结果及实际值/期望值。
      当前公开证据不含失败样本、错误类别或冻结失败策略，具体根因仍需验证。
      如需调整规则，请在源页面选择当前监控并另行发起规则建议；本轮不会保存、启用或重新运行质量检查。
      """;
}
