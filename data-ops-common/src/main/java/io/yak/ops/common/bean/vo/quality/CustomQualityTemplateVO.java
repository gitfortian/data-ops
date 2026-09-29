package io.yak.ops.common.bean.vo.quality;

import io.yak.ops.common.annotation.quality.QualityDateTimeFormat;
import io.yak.ops.common.enums.quality.QualityEnums.CheckMethod;
import io.yak.ops.common.enums.quality.QualityEnums.CheckType;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 自定义规则模板响应契约。 */
public final class CustomQualityTemplateVO {
  private CustomQualityTemplateVO() {}

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
      int sortOrder,
      Long folderId,
      String folderName,
      String templateSql,
      String setFlag,
      CheckType checkType,
      CheckMethod checkMethod,
      String createdBy,
      @QualityDateTimeFormat LocalDateTime createdAt,
      @QualityDateTimeFormat LocalDateTime updatedAt) {}

  public record Summary(
      long total,
      long systemTotal,
      long customTotal,
      Map<String, Long> dimensions) {}

  public record ListView(List<Template> records, Summary summary) {}

  public record Folder(
      Long id,
      Long parentId,
      String name,
      int sortOrder,
      long templateCount,
      long childCount,
      @QualityDateTimeFormat LocalDateTime createdAt,
      @QualityDateTimeFormat LocalDateTime updatedAt) {}
}
