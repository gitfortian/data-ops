package io.yak.ops.business.home.controller.v1;

import io.yak.framework.common.Result;
import io.yak.ops.business.home.datacenter.HomeDataCenterReader;
import io.yak.ops.business.home.datacenter.HomeDataCenterReader.OverviewResponse;
import io.yak.ops.business.home.datacenter.HomeDataCenterReader.RecentResponse;
import io.yak.ops.business.home.datacenter.HomeDataCenterReader.ScheduleResponse;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 首页数据中心接口。 */
@RestController
@RequestMapping("/api/v1/home/data-center")
@ProjectScope
public class HomeDataCenterController {

  private final HomeDataCenterReader reader;

  public HomeDataCenterController(HomeDataCenterReader reader) {
    this.reader = reader;
  }

  @GetMapping("/overview")
  public Result<OverviewResponse> overview(
      @RequestParam(defaultValue = "7d") String period) {
    return Result.success(reader.overview(period));
  }

  @GetMapping("/recent")
  public Result<RecentResponse> recent() {
    return Result.success(reader.recent());
  }

  @GetMapping("/schedule")
  public Result<ScheduleResponse> schedules(
      @RequestParam(defaultValue = "7d") String period) {
    return Result.success(reader.schedules(period));
  }
}
