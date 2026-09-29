package io.yak.ops.business.home.controller.v1;

import io.yak.framework.common.Result;
import io.yak.ops.business.home.asset.HomeAssetOverviewReader;
import io.yak.ops.business.home.asset.HomeAssetOverviewReader.OverviewResponse;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 首页数据资产与血缘总览接口。 */
@RestController
@RequestMapping("/api/v1/home/assets")
@ProjectScope
public class HomeAssetOverviewController {

  private final HomeAssetOverviewReader reader;

  public HomeAssetOverviewController(HomeAssetOverviewReader reader) {
    this.reader = reader;
  }

  @GetMapping("/overview")
  public Result<OverviewResponse> overview() {
    return Result.success(reader.overview());
  }
}
