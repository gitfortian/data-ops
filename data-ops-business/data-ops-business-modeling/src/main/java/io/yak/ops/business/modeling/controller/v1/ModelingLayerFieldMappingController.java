package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.mapping.LayerFieldMappingService;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
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

/** Layer-field mappings (ticket 43): standard field x layer landings. */
@Tag(name = "数据建模字段分层映射接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingLayerFieldMappingController {

  private final LayerFieldMappingService service;
  private final CurrentUserProvider currentUserProvider;

  /** upsert 请求。 */
  @Data
  public static class LayerMappingUpsertRequest {
    @NotNull(message = "模型不能为空")
    Long modelId;

    @NotNull(message = "标准字段不能为空")
    Long processFieldId;

    @NotNull(message = "分层不能为空")
    Long layerId;

    @NotBlank(message = "落地字段名不能为空")
    @Size(max = 128, message = "落地字段名不能超过 128 个字符")
    String layerFieldName;

    @Size(max = 64, message = "落地类型不能超过 64 个字符")
    String layerDataType;

    @Size(max = 256, message = "来源字段不能超过 256 个字符")
    String sourceField;

    @Size(max = 1024, message = "转换表达式不能超过 1024 个字符")
    String transformExpr;
  }

  @Operation(summary = "按模型列出分层映射（展示名经 SPI 解析）")
  @GetMapping("/models/{modelId}/layer-mappings")
  public Result<List<LayerFieldMappingService.LayerFieldMappingView>> listByModel(
      @PathVariable("modelId") Long modelId) {
    return Result.success(service.listByModel(modelId));
  }

  @Operation(summary = "按标准字段查看各层落地（45 血缘数据基础）")
  @GetMapping("/layer-mappings/by-field/{processFieldId}")
  public Result<List<LayerFieldMappingService.LayerFieldMappingView>> listByField(
      @PathVariable("processFieldId") Long processFieldId) {
    return Result.success(service.listByProcessField(processFieldId));
  }

  @Operation(summary = "补录/修正一条分层映射")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PutMapping("/models/{modelId}/layer-mappings")
  public Result<Boolean> upsert(
      @PathVariable("modelId") Long modelId,
      @Valid @RequestBody LayerMappingUpsertRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    service.upsert(
        modelId,
        request.getProcessFieldId(),
        request.getLayerId(),
        request.getLayerFieldName(),
        request.getLayerDataType(),
        request.getSourceField(),
        request.getTransformExpr(),
        operator);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "删除一条分层映射")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @DeleteMapping("/layer-mappings/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(Boolean.TRUE);
  }
}
