package io.yak.ops.business.quality.template;

import io.yak.ops.common.enums.quality.QualityEnums.CheckMethod;
import io.yak.ops.common.enums.quality.QualityEnums.CheckType;
import io.yak.ops.common.enums.quality.QualityEnums.ComparisonOperator;
import java.math.BigDecimal;

/** Typed commands for custom quality templates and folders. */
public final class CustomTemplateCommand {
  private CustomTemplateCommand() {}

  public record Save(
      String name,
      String description,
      String dimension,
      Long folderId,
      String customSql,
      String setFlag,
      CheckType checkType,
      CheckMethod checkMethod,
      ComparisonOperator defaultOperator,
      BigDecimal defaultThreshold,
      BigDecimal defaultThresholdEnd) {}

  public record Copy(String name, Long folderId) {}

  public record FolderSave(Long parentId, String name) {}
}
