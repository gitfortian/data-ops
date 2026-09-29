package io.yak.ops.business.mdm.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.mdm.api.MdmEntityApi;
import io.yak.ops.business.mdm.application.MdmEntityService;
import io.yak.ops.business.mdm.controller.v1.dto.MdmEntityQueryDTO;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.common.constant.mdm.MdmPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Master data entity management (ticket 51). */
@Tag(name = "主数据实体接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mdm/entities")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MdmPermissionCode.READ)
public class MdmEntityController {

  private final MdmEntityService service;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "实体分页查询")
  @PostMapping("/page")
  public Result<PagingData<MdmEntity>> page(@Valid @RequestBody MdmEntityQueryDTO query) {
    return Result.success(
        PagingData.from(
            service.page(
                query.getPageNo(), query.getPageSize(), query.getKeyword(), query.getStatus())));
  }

  @Operation(summary = "实体详情")
  @GetMapping("/{id}")
  public Result<MdmEntity> get(@PathVariable("id") Long id) {
    return Result.success(service.get(id));
  }

  @Operation(summary = "创建实体(编码创建后不可改)")
  @RequiresPermission(MdmPermissionCode.CREATE)
  @PostMapping
  public Result<MdmEntity> create(
      @Valid @RequestBody MdmEntityApi.CreateRequest request, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        service.create(
            request.code(),
            request.name(),
            request.owner(),
            request.description(),
            operator));
  }

  @Operation(summary = "编辑实体(编码不可改)")
  @RequiresPermission(MdmPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<Boolean> update(
      @PathVariable("id") Long id, @Valid @RequestBody MdmEntityApi.UpdateRequest request) {
    service.update(id, request.name(), request.owner(), request.description());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "状态流转(草稿→生效→停用→生效)")
  @RequiresPermission(MdmPermissionCode.UPDATE)
  @PostMapping("/{id}/status")
  public Result<Boolean> changeStatus(
      @PathVariable("id") Long id, @Valid @RequestBody MdmEntityApi.StatusRequest request) {
    service.changeStatus(id, request.toStatus());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "删除实体(被引用时阻断)")
  @RequiresPermission(MdmPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }
}
