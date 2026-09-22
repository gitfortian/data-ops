package io.yak.ops.business.home.controller.v1;

import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.home.resource.HomeResourceCenterReader;
import io.yak.ops.business.home.resource.HomeResourceCenterReader.OverviewResponse;
import io.yak.ops.common.constant.resource.ResourcePermissionCode;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 首页文件资源总览接口。 */
@RestController
@RequestMapping("/api/v1/home/resources")
@ProjectScope
@RequiresPermission(ResourcePermissionCode.READ)
public class HomeResourceCenterController {

  private final HomeResourceCenterReader reader;

  public HomeResourceCenterController(HomeResourceCenterReader reader) {
    this.reader = reader;
  }

  @GetMapping("/overview")
  public Result<OverviewResponse> overview() {
    return Result.success(reader.overview());
  }
}
