package io.yak.ops.business.quality.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.controller.v1.converter.QualityOverviewConverter;
import io.yak.ops.business.quality.workspace.QualityOverviewReader;
import io.yak.ops.common.bean.vo.quality.QualityOverviewVO;
import io.yak.ops.core.project.ProjectScope;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据质量总览")
@RestController
@ConditionalOnQualityEnabled
@RequestMapping("/api/v1/data-quality/overview")
@RequiresPermission(QualityPermissionCode.EXECUTION_READ)
@ProjectScope
public class QualityOverviewController {

  private final QualityOverviewReader reader;
  private final QualityOverviewConverter converter;

  public QualityOverviewController(
      QualityOverviewReader reader,
      QualityOverviewConverter converter) {
    this.reader = reader;
    this.converter = converter;
  }

  @Operation(summary = "查询数据质量总览")
  @GetMapping
  public Result<QualityOverviewVO.Overview> overview(
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate startDate,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate endDate) {
    return Result.success(
        converter.overview(reader.analytics(startDate, endDate)));
  }
}
