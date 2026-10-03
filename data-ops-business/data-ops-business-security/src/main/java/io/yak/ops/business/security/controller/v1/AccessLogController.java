package io.yak.ops.business.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.security.application.AccessLogService;
import io.yak.ops.business.security.dao.model.DsecAccessLogPO;
import io.yak.ops.common.constant.security.SecurityPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 数据访问审计日志查询(票据 77)。 */
@Tag(name = "数据安全-访问审计接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-security/access-logs")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SecurityPermissionCode.READ)
public class AccessLogController {

  private final AccessLogService service;

  @Operation(summary = "访问日志分页查询")
  @PostMapping("/page")
  public Result<PagingData<DsecAccessLogPO>> page(@Valid @RequestBody PageQuery query) {
    return Result.success(PagingData.from(service.page(query.pageNo(), query.pageSize(),
        query.actor(), query.decision(), query.resourceKey(), query.start(), query.end())));
  }

  @Operation(summary = "访问热点主体 Top")
  @PostMapping("/top-actors")
  public Result<List<Map<String, Object>>> topActors() {
    return Result.success(service.topActors());
  }

  public record PageQuery(int pageNo, int pageSize, String actor, String decision,
      String resourceKey, LocalDateTime start, LocalDateTime end) {}
}
