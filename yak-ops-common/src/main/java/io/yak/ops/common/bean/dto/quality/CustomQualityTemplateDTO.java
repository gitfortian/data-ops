package io.yak.ops.common.bean.dto.quality;

import io.yak.ops.common.enums.quality.QualityEnums.CheckMethod;
import io.yak.ops.common.enums.quality.QualityEnums.CheckType;
import io.yak.ops.common.enums.quality.QualityEnums.ComparisonOperator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 自定义数据质量规则模板请求契约。 */
public final class CustomQualityTemplateDTO {
  private CustomQualityTemplateDTO() {}

  public record Query(String keyword, String dimension, Long folderId) {}

  public record SaveFolderRequest(
      @NotBlank @Size(max = 100) String name,
      Long parentId) {}

  public record SaveTemplateRequest(
      @NotBlank @Size(max = 100) String name,
      @Size(max = 500) String description,
      @NotBlank @Size(max = 40) String dimension,
      Long folderId,
      @Size(max = 1000) String setFlag,
      CheckType checkType,
      CheckMethod checkMethod,
      @NotBlank @Size(max = 20000) String customSql,
      ComparisonOperator defaultOperator,
      @NotNull BigDecimal defaultThreshold,
      BigDecimal defaultThresholdEnd) {}

  public record CopyTemplateRequest(
      @NotBlank @Size(max = 100) String name,
      Long folderId) {}
}
