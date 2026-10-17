package io.yak.ops.business.development.controller.v1;

import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.development.handoff.DwdFrozenExecutionAttempt;
import io.yak.ops.business.development.handoff.DwdFrozenExecutionDispatchCoordinator;
import io.yak.ops.common.constant.development.DataDevelopmentPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DR-07-gated DWD frozen SQL entry. OFF unless BOTH flags are explicitly true.
 * Even with flags, no dispatch occurs without a Security-owned target write
 * authorization bean. No browser-supplied SQL or editable draft is accepted.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-development/dwd-frozen-executions")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataDevelopmentPermissionCode.EXECUTE)
@ConditionalOnProperty(prefix = "data-ops.dwd",
    name = {"binding-write-authorized", "execution-write-authorized"},
    havingValue = "true", matchIfMissing = false)
public class DwdFrozenExecutionController {
  private final DwdFrozenExecutionDispatchCoordinator dispatch;

  public record SubmitRequest(long bindingId, String idempotencyKey, boolean confirmed) {}

  @PostMapping
  public Result<DwdFrozenExecutionAttempt> submit(@RequestBody SubmitRequest request) {
    if (request == null) throw new IllegalArgumentException("执行请求不能为空");
    return Result.success(dispatch.submit(
        request.bindingId(), request.idempotencyKey(), request.confirmed()));
  }

  @GetMapping("/{attemptId}")
  public Result<DwdFrozenExecutionAttempt> get(@PathVariable long attemptId) {
    return Result.success(dispatch.read(attemptId));
  }
}
