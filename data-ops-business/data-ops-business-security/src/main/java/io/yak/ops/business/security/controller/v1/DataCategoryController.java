package io.yak.ops.business.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.security.application.DataCategoryService;
import io.yak.ops.common.bean.po.security.DsecDataCategoryPO;
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

/** 数据分类维护(票据 72)。 */
@Tag(name = "数据安全-分类接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-security/categories")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SecurityPermissionCode.READ)
public class DataCategoryController {

  private final DataCategoryService service;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "分类分页查询")
  @PostMapping("/page")
  public Result<PagingData<DsecDataCategoryPO>> page(@Valid @RequestBody PageQuery query) {
    return Result.success(
        PagingData.from(service.page(query.pageNo(), query.pageSize(), query.keyword())));
  }

  @Operation(summary = "全部分类(树/下拉用)")
  @GetMapping("/all")
  public Result<List<DsecDataCategoryPO>> listAll() {
    return Result.success(service.listAll());
  }

  @Operation(summary = "分类详情")
  @GetMapping("/{id}")
  public Result<DsecDataCategoryPO> get(@PathVariable("id") Long id) {
    return Result.success(service.get(id));
  }

  @Operation(summary = "创建分类")
  @RequiresPermission(SecurityPermissionCode.CREATE)
  @PostMapping
  public Result<DsecDataCategoryPO> create(@Valid @RequestBody CreateRequest body,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(service.create(body.code(), body.name(), body.parentCode(),
        body.sortOrder(), body.description(), operator));
  }

  @Operation(summary = "编辑分类(编码不可改)")
  @RequiresPermission(SecurityPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<DsecDataCategoryPO> update(@PathVariable("id") Long id,
      @Valid @RequestBody UpdateRequest body) {
    return Result.success(service.update(id, body.name(), body.sortOrder(), body.description()));
  }

  @Operation(summary = "删除分类(有子级或被引用时阻断)")
  @RequiresPermission(SecurityPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }

  public record PageQuery(int pageNo, int pageSize, String keyword) {}

  public record CreateRequest(@NotBlank String code, @NotBlank String name, String parentCode,
      Integer sortOrder, String description) {}

  public record UpdateRequest(@NotBlank String name, Integer sortOrder, String description) {}
}
