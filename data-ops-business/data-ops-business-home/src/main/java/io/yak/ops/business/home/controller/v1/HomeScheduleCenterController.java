package io.yak.ops.business.home.controller.v1;

import io.yak.framework.common.Result;
import io.yak.ops.business.home.schedule.HomeScheduleCenterReader;
import io.yak.ops.business.home.schedule.HomeScheduleCenterReader.CalendarResponse;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 首页调度中心接口。 */
@RestController
@RequestMapping("/api/v1/home/schedule-center")
@ProjectScope
public class HomeScheduleCenterController {

  private final HomeScheduleCenterReader reader;

  public HomeScheduleCenterController(HomeScheduleCenterReader reader) {
    this.reader = reader;
  }

  @GetMapping("/calendar")
  public Result<CalendarResponse> calendar(
      @RequestParam(required = false) String month) {
    return Result.success(reader.calendar(month));
  }
}
