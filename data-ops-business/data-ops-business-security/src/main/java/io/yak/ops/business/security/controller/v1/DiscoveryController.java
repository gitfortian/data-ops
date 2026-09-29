package io.yak.ops.business.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.security.application.DiscoveryService;
import io.yak.ops.business.security.domain.DiscoverableField;
import io.yak.ops.common.bean.po.security.DsecDiscoveryRulePO;
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

/** 敏感数据发现规则与扫描(票据 74)。 */
@Tag(name = "数据安全-敏感发现接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-security/discovery-rules")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SecurityPermissionCode.READ)
public class DiscoveryController {

  private final DiscoveryService service;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "发现规则分页查询")
  @PostMapping("/page")
  public Result<PagingData<DsecDiscoveryRulePO>> page(@Valid @RequestBody PageQuery query) {
    return Result.success(
        PagingData.from(service.page(query.pageNo(), query.pageSize(), query.keyword())));
  }

  @Operation(summary = "发现规则详情")
  @GetMapping("/{id}")
  public Result<DsecDiscoveryRulePO> get(@PathVariable("id") Long id) {
    return Result.success(service.get(id));
  }

  @Operation(summary = "创建发现规则")
  @RequiresPermission(SecurityPermissionCode.CREATE)
  @PostMapping
  public Result<DsecDiscoveryRulePO> create(@Valid @RequestBody CreateRequest body,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(service.create(body.code(), body.name(), body.matchType(), body.pattern(),
        body.levelId(), body.categoryId(), body.enabled(), body.description(), operator));
  }

  @Operation(summary = "编辑发现规则(编码不可改)")
  @RequiresPermission(SecurityPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<DsecDiscoveryRulePO> update(@PathVariable("id") Long id,
      @Valid @RequestBody UpdateRequest body) {
    return Result.success(service.update(id, body.name(), body.matchType(), body.pattern(),
        body.levelId(), body.categoryId(), body.enabled(), body.description()));
  }

  @Operation(summary = "删除发现规则")
  @RequiresPermission(SecurityPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "扫描字段生成候选标签,返回命中数")
  @RequiresPermission(SecurityPermissionCode.CREATE)
  @PostMapping("/scan")
  public Result<Integer> scan(@Valid @RequestBody ScanRequest body, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(service.scan(body.fields(), operator));
  }

  public record PageQuery(int pageNo, int pageSize, String keyword) {}

  public record CreateRequest(@NotBlank String code, @NotBlank String name,
      @NotBlank String matchType, String pattern, Long levelId, Long categoryId, Boolean enabled,
      String description) {}

  public record UpdateRequest(@NotBlank String name, @NotBlank String matchType, String pattern,
      Long levelId, Long categoryId, Boolean enabled, String description) {}

  public record ScanRequest(List<DiscoverableField> fields) {}
}
