package io.yak.ops.business.home.controller.v1;

import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.home.quality.HomeQualityOverviewReader;
import io.yak.ops.business.home.quality.HomeQualityOverviewReader.OverviewResponse;
import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 首页数据质量总览接口。 */
@RestController
@RequestMapping("/api/v1/home/quality")
@ProjectScope
@RequiresPermission(QualityPermissionCode.EXECUTION_READ)
public class HomeQualityOverviewController {

  private final HomeQualityOverviewReader reader;

  public HomeQualityOverviewController(HomeQualityOverviewReader reader) {
    this.reader = reader;
  }

  @GetMapping("/overview")
  public Result<OverviewResponse> overview() {
    return Result.success(reader.overview());
  }
}
