package io.yak.ops.business.home.controller.v1;

import io.yak.framework.common.Result;
import io.yak.ops.business.home.cockpit.HomeCockpitReader;
import io.yak.ops.business.home.cockpit.HomeCockpitReader.CockpitResponse;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 首页头部摘要接口。 */
@RestController
@RequestMapping("/api/v1/home/cockpit")
@ProjectScope
public class HomeCockpitController {

  private final HomeCockpitReader reader;

  public HomeCockpitController(HomeCockpitReader reader) {
    this.reader = reader;
  }

  @GetMapping
  public Result<CockpitResponse> cockpit() {
    return Result.success(reader.cockpit());
  }
}
