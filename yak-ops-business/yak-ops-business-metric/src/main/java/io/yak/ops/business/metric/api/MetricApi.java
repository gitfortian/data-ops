package io.yak.ops.business.metric.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 指标管理 API 契约。 */
public final class MetricApi {

  private MetricApi() {}

  public record CreateRequest(
      @NotBlank(message = "指标名称不能为空")
      @Size(max = 128, message = "指标名称不能超过 128 个字符")
      String metricName,
      @Size(max = 64, message = "指标编码不能超过 64 个字符")
      String metricCode,
      Long domainId,
      Long processId,
      @NotBlank(message = "指标类型不能为空")
      String metricType,
      Long caliberId,
      String calRule,
      @Size(max = 512, message = "度量表达式不能超过 512 个字符")
      String measureExpr,
      @Size(max = 1024, message = "过滤条件不能超过 1024 个字符")
      String filterExpr,
      String dimModelIds,
      Long refMetricId,
      @Size(max = 1024, message = "维度限定不能超过 1024 个字符")
      String dimConstraint,
      /** 结构化限定条件(JSON 数组 [{field,op,value}],仅派生指标;非空时后端自动组装 measure/filter)。 */
      @Size(max = 2048, message = "限定条件不能超过 2048 个字符")
      String qualifiersJson,
      Long modelId,
      String statDimensions,
      String statPeriod,
      Long unitId,
      @Size(max = 512, message = "业务口径描述不能超过 512 个字符")
      String businessDesc,
      @Size(max = 64, message = "负责人不能超过 64 个字符")
      String owner,
      /** 复合指标子指标列表。 */
      List<CompositionItem> compositions) {}

  public record CompositionItem(
      Long subMetricId,
      @NotBlank(message = "运算方式不能为空")
      String operator,
      String expression,
      int sortOrder) {}

  public record UpdateRequest(
      @NotBlank(message = "指标名称不能为空")
      @Size(max = 128, message = "指标名称不能超过 128 个字符")
      String metricName,
      Long domainId,
      Long processId,
      @NotBlank(message = "指标类型不能为空")
      String metricType,
      Long caliberId,
      String calRule,
      @Size(max = 512, message = "度量表达式不能超过 512 个字符")
      String measureExpr,
      @Size(max = 1024, message = "过滤条件不能超过 1024 个字符")
      String filterExpr,
      String dimModelIds,
      Long refMetricId,
      @Size(max = 1024, message = "维度限定不能超过 1024 个字符")
      String dimConstraint,
      /** 结构化限定条件(JSON 数组 [{field,op,value}],仅派生指标;非空时后端自动组装 measure/filter)。 */
      @Size(max = 2048, message = "限定条件不能超过 2048 个字符")
      String qualifiersJson,
      Long modelId,
      String statDimensions,
      String statPeriod,
      Long unitId,
      @Size(max = 512, message = "业务口径描述不能超过 512 个字符")
      String businessDesc,
      @Size(max = 64, message = "负责人不能超过 64 个字符")
      String owner,
      int expectedVersion,
      List<CompositionItem> compositions) {}

  public record StatusRequest(@NotBlank(message = "状态不能为空") String status) {}
}
