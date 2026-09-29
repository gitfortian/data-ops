package io.yak.ops.business.mdm.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.mdm.api.MdmAttributeApi;
import io.yak.ops.business.mdm.application.MdmAttributeService;
import io.yak.ops.business.mdm.controller.v1.converter.MdmAttributeViewConverter;
import io.yak.ops.business.mdm.controller.v1.vo.MdmAttributeVO;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.common.constant.mdm.MdmPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Master data attribute management (ticket 52). */
@Tag(name = "主数据属性接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mdm/entities/{entityId}/attributes")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MdmPermissionCode.READ)
public class MdmAttributeController {

  private final MdmAttributeService service;
  private final MdmAttributeViewConverter viewConverter;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "实体属性列表(标准引用展示名服务端解析)")
  @GetMapping
  public Result<List<MdmAttributeVO>> list(@PathVariable("entityId") Long entityId) {
    List<MdmAttribute> attributes = service.list(entityId);
    Set<Long> refIds = new HashSet<>();
    for (MdmAttribute attribute : attributes) {
      addIfPresent(refIds, attribute.stdTypeId());
      addIfPresent(refIds, attribute.stdUnitId());
      addIfPresent(refIds, attribute.stdSecurityId());
    }
    Map<Long, String> labels = service.standardLabels(refIds);
    return Result.success(viewConverter.toViews(attributes, labels));
  }

  @Operation(summary = "创建属性(标准引用必须来自数据标准)")
  @RequiresPermission(MdmPermissionCode.CREATE)
  @PostMapping
  public Result<MdmAttributeVO> create(
      @PathVariable("entityId") Long entityId,
      @Valid @RequestBody MdmAttributeApi.CreateRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    MdmAttribute created =
        service.create(
            entityId,
            request.code(),
            request.name(),
            request.toType(),
            request.dataType(),
            request.stdTypeId(),
            request.stdUnitId(),
            request.stdCodeSetCode(),
            request.stdSecurityId(),
            request.required(),
            request.businessDesc(),
            request.sortOrder(),
            operator);
    Set<Long> refIds = new HashSet<>();
    addIfPresent(refIds, created.stdTypeId());
    return Result.success(viewConverter.toView(created, service.standardLabels(refIds)));
  }

  @Operation(summary = "编辑属性(编码不可改)")
  @RequiresPermission(MdmPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<Boolean> update(
      @PathVariable("id") Long id,
      @Valid @RequestBody MdmAttributeApi.UpdateRequest request) {
    service.update(
        id,
        request.name(),
        request.toType(),
        request.dataType(),
        request.stdTypeId(),
        request.stdUnitId(),
        request.stdCodeSetCode(),
        request.stdSecurityId(),
        request.required(),
        request.businessDesc(),
        request.sortOrder());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "删除属性(被引用时阻断)")
  @RequiresPermission(MdmPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }

  private static void addIfPresent(Set<Long> ids, Long id) {
    if (id != null) {
      ids.add(id);
    }
  }
}
