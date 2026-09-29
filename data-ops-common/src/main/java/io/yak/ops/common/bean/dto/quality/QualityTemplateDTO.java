package io.yak.ops.common.bean.dto.quality;

import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;

/** 系统规则模板查询请求。 */
public final class QualityTemplateDTO {
  private QualityTemplateDTO() {}

  public record Query(String keyword, String dimension, RuleScope scope) {}
}
