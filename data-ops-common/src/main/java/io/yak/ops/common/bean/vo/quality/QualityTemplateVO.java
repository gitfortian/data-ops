package io.yak.ops.common.bean.vo.quality;

import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import java.util.List;
import java.util.Map;

/** 系统规则模板响应契约。 */
public final class QualityTemplateVO {
  private QualityTemplateVO() {}

  public record Template(
      Long id,
      String code,
      String name,
      String description,
      RuleType ruleType,
      RuleScope scope,
      String dimension,
      String parameterSchema,
      boolean builtin,
      boolean enabled,
      long ruleCount,
      int sortOrder) {}

  public record Summary(long total, Map<String, Long> dimensions) {}

  public record CatalogSummary(
      long systemTotal,
      long customTotal,
      Map<String, Long> systemDimensions,
      Map<String, Long> customDimensions) {}

  public record ListView(List<Template> records, Summary summary) {}
}
