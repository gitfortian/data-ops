package io.yak.ops.business.semantic.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.semantic.api.LayerStdBindingReader.StdBindingStats;
import io.yak.ops.business.semantic.controller.v1.vo.WarehouseLayerVO;
import io.yak.ops.business.semantic.layer.SemanticLayerService;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Warehouse layer config management (ticket 37). */
@Tag(name = "业务语义数仓分层接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/semantic/layers")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SemanticPermissionCode.READ)
public class SemanticLayerController {

  private final SemanticLayerService service;
  private final CurrentUserProvider currentUserProvider;

  /** 创建请求：编码必填。 */
  @Data
  public static class LayerCreateRequest {
    @NotBlank(message = "分层编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{1,32}$", message = "分层编码仅允许字母、数字和下划线,1~32 位")
        String code;
    @NotBlank(message = "分层名称不能为空")
        @Size(max = 64, message = "分层名称不能超过 64 个字符")
        String name;
    @NotBlank(message = "库名不能为空")
        @Size(max = 128, message = "库名不能超过 128 个字符") String databaseName;
    @NotNull(message = "数据源不能为空") Long datasourceId;
    Long stdNamingId;
    @Size(max = 256, message = "默认分区不能超过 256 个字符") String defaultPartition;
    @Size(max = 64, message = "存储格式不能超过 64 个字符") String storageFormat;
    Integer lifecycleDays;
    @Size(max = 512, message = "描述不能超过 512 个字符") String description;
    Integer sortOrder;
    /** 是否强制字段落标(M2-5);空=强制。 */
    Boolean stdMandatory;
  }

  /** 编辑请求：编码不可改,不接收 code 字段。 */
  @Data
  public static class LayerUpdateRequest {
    @NotBlank(message = "分层名称不能为空")
        @Size(max = 64, message = "分层名称不能超过 64 个字符")
        String name;
    @NotBlank(message = "库名不能为空")
        @Size(max = 128, message = "库名不能超过 128 个字符") String databaseName;
    @NotNull(message = "数据源不能为空") Long datasourceId;
    Long stdNamingId;
    @Size(max = 256, message = "默认分区不能超过 256 个字符") String defaultPartition;
    @Size(max = 64, message = "存储格式不能超过 64 个字符") String storageFormat;
    Integer lifecycleDays;
    @Size(max = 512, message = "描述不能超过 512 个字符") String description;
    Integer sortOrder;
    /** 空=保持现状。 */
    Boolean stdMandatory;
  }

  /** 状态请求。 */
  @Data
  public static class LayerStatusRequest {
    @NotBlank(message = "状态不能为空")
        @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "状态必须为 ENABLED 或 DISABLED")
        String status;
  }

  /** stats 为该层落标统计(M2-5 观察期),无建模域/无数据时为 null,前端按"无数据"展示。 */
  private static WarehouseLayerVO toView(
      WarehouseLayer layer, long modelCount, StdBindingStats stats) {
    WarehouseLayerVO view = new WarehouseLayerVO();
    view.setId(layer.id());
    view.setCode(layer.code());
    view.setName(layer.name());
    view.setDatabaseName(layer.databaseName());
    view.setDatasourceId(layer.datasourceId());
    view.setStdNamingId(layer.stdNamingId());
    view.setDefaultPartition(layer.defaultPartition());
    view.setStorageFormat(layer.storageFormat());
    view.setLifecycleDays(layer.lifecycleDays());
    view.setDescription(layer.description());
    view.setSortOrder(layer.sortOrder());
    view.setStatus(layer.status());
    view.setStdMandatory(layer.stdMandatory());
    view.setPreset(layer.preset());
    view.setModelCount(modelCount);
    if (stats != null) {
      view.setStdColumnTotal(stats.columnTotal());
      view.setStdBoundColumns(stats.stdBoundColumns());
    }
    return view;
  }

  @Operation(summary = "全部分层配置（含被引用模型数、各层落标统计）")
  @GetMapping
  public Result<List<WarehouseLayerVO>> list() {
    Map<String, Long> modelCounts = service.modelCounts();
    Map<String, StdBindingStats> bindingStats = service.bindingStats();
    return Result.success(
        service.list().stream()
            .map(
                layer ->
                    toView(
                        layer,
                        modelCounts.getOrDefault(layer.code(), 0L),
                        bindingStats.get(layer.code())))
            .toList());
  }

  @Operation(summary = "创建分层")
  @RequiresPermission(SemanticPermissionCode.CREATE)
  @PostMapping
  public Result<WarehouseLayerVO> create(
      @Valid @RequestBody LayerCreateRequest request, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    WarehouseLayer created =
        service.create(
            request.getCode(), request.getName(), request.getDatabaseName(),
            request.getDatasourceId(), request.getStdNamingId(),
            request.getDefaultPartition(), request.getStorageFormat(),
            request.getLifecycleDays(), request.getDescription(), request.getSortOrder(),
            request.getStdMandatory(), operator);
    return Result.success(toView(created, 0L, null));
  }

  @Operation(summary = "编辑分层（编码不可改）")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<WarehouseLayerVO> update(
      @PathVariable("id") Long id, @Valid @RequestBody LayerUpdateRequest request) {
    WarehouseLayer updated =
        service.update(
            id, request.getName(), request.getDatabaseName(), request.getDatasourceId(),
            request.getStdNamingId(), request.getDefaultPartition(), request.getStorageFormat(),
            request.getLifecycleDays(), request.getDescription(), request.getSortOrder(),
            request.getStdMandatory());
    return Result.success(
        toView(
            updated,
            service.modelCounts().getOrDefault(updated.code(), 0L),
            service.bindingStats().get(updated.code())));
  }

  @Operation(summary = "启用/停用分层")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PostMapping("/{id}/status")
  public Result<Boolean> changeStatus(
      @PathVariable("id") Long id, @Valid @RequestBody LayerStatusRequest request) {
    service.changeStatus(id, request.getStatus());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "删除分层（默认分层不可删；自定义分层被模型引用时阻断）")
  @RequiresPermission(SemanticPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "初始化默认分层（幂等：ODS/DWD/DWS/ADS）")
  @RequiresPermission(SemanticPermissionCode.CREATE)
  @PostMapping("/initialize")
  public Result<Integer> initialize(HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(service.initializeDefaults(operator));
  }
}
