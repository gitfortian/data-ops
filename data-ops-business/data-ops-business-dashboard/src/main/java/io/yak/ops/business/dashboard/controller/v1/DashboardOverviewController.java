package io.yak.ops.business.dashboard.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.dashboard.DashboardService;
import io.yak.ops.business.dashboard.controller.v1.converter.DashboardOverviewViewConverter;
import io.yak.ops.business.dashboard.controller.v1.vo.DashboardOverviewView;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bounded Dashboard overview HTTP adapter. */
@Tag(name = "BI 仪表盘接口")
@RestController
@ProjectScope
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/dashboards")
public class DashboardOverviewController {

  private final DashboardService dashboardService;
  private final DashboardOverviewViewConverter converter;

  @Operation(summary = "查询 Dashboard 轻量总览")
  @GetMapping("/overview")
  public Result<DashboardOverviewView> overview(
      @RequestParam(value = "limit", defaultValue = "4") int limit) {
    return Result.success(converter.convert(dashboardService.overview(limit)));
  }
}
