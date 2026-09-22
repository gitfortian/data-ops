package io.yak.ops.business.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.security.application.SecurityLevelService;
import io.yak.ops.common.bean.po.security.DsecSecurityLevelPO;
import io.yak.ops.common.constant.security.SecurityPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 安全等级维护(票据 71)。 */
@Tag(name = "数据安全-等级接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-security/levels")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SecurityPermissionCode.READ)
public class SecurityLevelController {

  private final SecurityLevelService service;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "等级分页查询")
  @PostMapping("/page")
  public Result<PagingData<DsecSecurityLevelPO>> page(@Valid @RequestBody PageQuery query) {
    return Result.success(PagingData.from(
        service.page(query.pageNo(), query.pageSize(), query.keyword(), query.status())));
  }

  @Operation(summary = "启用中的等级列表(下拉用)")
  @GetMapping("/active")
  public Result<List<DsecSecurityLevelPO>> listActive() {
    return Result.success(service.findAllActive());
  }

  @Operation(summary = "等级详情")
  @GetMapping("/{id}")
  public Result<DsecSecurityLevelPO> get(@PathVariable("id") Long id) {
    return Result.success(service.get(id));
  }

  @Operation(summary = "创建等级")
  @RequiresPermission(SecurityPermissionCode.CREATE)
  @PostMapping
  public Result<DsecSecurityLevelPO> create(@Valid @RequestBody CreateRequest body,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        service.create(body.code(), body.name(), body.rankNo(), body.stdSecurityId(),
            body.description(), operator));
  }

  @Operation(summary = "编辑等级(编码不可改)")
  @RequiresPermission(SecurityPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<DsecSecurityLevelPO> update(@PathVariable("id") Long id,
      @Valid @RequestBody UpdateRequest body) {
    return Result.success(
        service.update(id, body.name(), body.rankNo(), body.stdSecurityId(), body.description()));
  }

  @Operation(summary = "状态流转")
  @RequiresPermission(SecurityPermissionCode.UPDATE)
  @PostMapping("/{id}/status")
  public Result<Boolean> changeStatus(@PathVariable("id") Long id,
      @Valid @RequestBody StatusRequest body) {
    service.changeStatus(id, body.status());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "删除等级(被分级标签引用时阻断)")
  @RequiresPermission(SecurityPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }

  public record PageQuery(int pageNo, int pageSize, String keyword, String status) {}

  public record CreateRequest(@NotBlank String code, @NotBlank String name, Integer rankNo,
      Long stdSecurityId, String description) {}

  public record UpdateRequest(@NotBlank String name, Integer rankNo, Long stdSecurityId,
      String description) {}

  public record StatusRequest(@NotBlank String status) {}
}
