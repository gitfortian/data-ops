package io.yak.ops.business.quality.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.controller.v1.converter.QualityTemplateConverter;
import io.yak.ops.business.quality.template.QualityTemplateReader;
import io.yak.ops.common.bean.vo.quality.QualityTemplateVO;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据质量规则模板")
@RestController
@ConditionalOnQualityEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-quality/template")
@RequiresPermission(QualityPermissionCode.TEMPLATE_READ)
@ProjectScope(ProjectMigrationMode.LEGACY_GLOBAL)
public class QualityTemplateController {
  private final QualityTemplateReader reader;
  private final QualityTemplateConverter converter;

  @Operation(summary = "查询规则模板")
  @GetMapping
  public Result<QualityTemplateVO.ListView> list(
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "dimension", required = false) String dimension,
      @RequestParam(value = "scope", required = false) RuleScope scope) {
    return Result.success(
        converter.templateList(
            reader.list(
                converter.templateQuery(keyword, dimension, scope))));
  }

  @Operation(summary = "查询规则模板目录统计")
  @GetMapping("/summary")
  public Result<QualityTemplateVO.CatalogSummary> summary() {
    return Result.success(converter.catalogSummary(reader.catalog()));
  }

  @Operation(summary = "查询规则模板详情")
  @GetMapping("/{id}")
  public Result<QualityTemplateVO.Template> detail(@PathVariable long id) {
    return Result.success(converter.template(reader.require(id)));
  }
}
