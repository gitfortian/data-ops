package io.yak.ops.business.mdm.api;

import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleExpr;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request contracts of the master data cleansing (ticket 56/57). */
public final class MdmCleanApi {

  private MdmCleanApi() {}

  /** 创建去重规则(向后兼容):实体 + 名称 + 表达式(字段 + 组合条件) + 排序。 */
  public record RuleSaveRequest(
      @NotNull(message = "实体不能为空") Long entityId,
      @NotBlank(message = "规则名称不能为空")
          @Size(max = 128, message = "规则名称不能超过 128 个字符")
          String ruleName,
      @Valid @NotNull(message = "规则表达式不能为空") MdmCleanRuleExpr ruleExpr,
      Integer sortOrder) {}

  /** 通用规则创建(ticket 57):支持 DEDUP/STANDARDIZE/COMPLETE,ruleExpr 为原始 JSON。 */
  public record TypedRuleSaveRequest(
      @NotNull(message = "实体不能为空") Long entityId,
      MdmCleanRuleType ruleType,
      @NotBlank(message = "规则名称不能为空")
          @Size(max = 128, message = "规则名称不能超过 128 个字符")
          String ruleName,
      @NotBlank(message = "规则表达式不能为空") String ruleExpr,
      Integer sortOrder) {}

  /** 通用规则更新(ticket 57):名称 + 表达式 JSON + 排序。 */
  public record TypedRuleUpdateRequest(
      @NotBlank(message = "规则名称不能为空")
          @Size(max = 128, message = "规则名称不能超过 128 个字符")
          String ruleName,
      @NotBlank(message = "规则表达式不能为空") String ruleExpr,
      Integer sortOrder) {}

  /** 编辑去重规则:名称 + 表达式 + 排序(编码/实体/类型不可改)。 */
  public record RuleUpdateRequest(
      @NotBlank(message = "规则名称不能为空")
          @Size(max = 128, message = "规则名称不能超过 128 个字符")
          String ruleName,
      @Valid @NotNull(message = "规则表达式不能为空") MdmCleanRuleExpr ruleExpr,
      Integer sortOrder) {}

  /** 启停去重规则。 */
  public record EnabledRequest(boolean enabled) {}

  /** 去重发现:实体 + 规则 + 分页。 */
  public record DedupRunRequest(
      @NotNull(message = "实体不能为空") Long entityId,
      @NotNull(message = "去重规则不能为空") Long ruleId,
      Integer pageNo,
      Integer pageSize) {}

  /**
   * 忽略重复组(R7):组键按去重发现返回的原样回传,服务端不做任何归一,
   * 否则忽略条目与 SQL 聚合键对不上,会变成静音失效的忽略。
   */
  public record DedupIgnoreRequest(
      @NotNull(message = "实体不能为空") Long entityId,
      @NotNull(message = "去重规则不能为空") Long ruleId,
      @NotNull(message = "重复组键不能为空") String matchKey,
      String matchBasis,
      @Size(max = 255, message = "忽略原因不能超过 255 个字符") String reason) {}

  /** 合并预览:选主记录 + 被合并记录列表。 */
  public record MergePreviewRequest(
      @NotNull(message = "实体不能为空") Long entityId,
      @NotNull(message = "请选择保留的主记录") Long masterRecordId,
      @NotEmpty(message = "请选择至少一条被合并记录") List<Long> mergedRecordIds) {}

  /** 合并执行:实体 + 触发规则(可空) + 主记录 + 被合并记录。 */
  public record MergeExecuteRequest(
      @NotNull(message = "实体不能为空") Long entityId,
      Long ruleId,
      @NotNull(message = "请选择保留的主记录") Long masterRecordId,
      @NotEmpty(message = "请选择至少一条被合并记录") List<Long> mergedRecordIds) {}

  /** 标准化/补全执行(ticket 57)。 */
  public record TransformApplyRequest(
      @NotNull(message = "实体不能为空") Long entityId,
      @NotNull(message = "规则不能为空") Long ruleId) {}
}
