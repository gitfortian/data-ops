package io.yak.ops.business.metric.controller.v1;

import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metric.api.MetricExplanationQueryApi;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.yak.ops.business.metric.api.MetricDraftQueryApi;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metrics")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetricPermissionCode.READ)
public class MetricExplanationController {
  private final MetricExplanationQueryApi query;
  private final MetricDraftQueryApi drafts;
  @PostMapping("/draft-context")
  public Result<MetricDraftQueryApi.Context> draftContext(@RequestBody MetricDraftQueryApi.Input input) {
    return Result.success(drafts.prepare(input));
  }
  @GetMapping("/{id}/versions/{version}/explanation-context")
  public Result<MetricExplanationQueryApi.Context> context(@PathVariable("id") long id, @PathVariable("version") int version) {
    return Result.success(query.require(id, version));
  }
  @GetMapping("/{id}/versions/{version}/snapshot-explanation-context")
  public Result<MetricExplanationQueryApi.Context> snapshot(@PathVariable("id") long id, @PathVariable("version") int version) {
    return Result.success(query.requireSnapshot(id, version));
  }
}
