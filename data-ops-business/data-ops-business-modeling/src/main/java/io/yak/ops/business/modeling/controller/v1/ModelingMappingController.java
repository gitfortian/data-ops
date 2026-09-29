package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.mapping.MappingService;
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

/** Column source mappings (ticket 19). */
@Tag(name = "数据建模来源映射接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/models/{modelId}/mappings")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingMappingController {

  private final MappingService mappingService;
  private final CurrentUserProvider currentUserProvider;

  /** 映射设置请求。 */
  @Data
  public static class MappingSetRequest {
    @NotNull(message = "源数据源不能为空")
    Long sourceDatasourceId;

    @NotBlank(message = "源库不能为空")
    @Size(max = 128, message = "源库不能超过 128 个字符")
    String sourceDatabase;

    @NotBlank(message = "源表不能为空")
    @Size(max = 128, message = "源表不能超过 128 个字符")
    String sourceTable;

    @NotBlank(message = "源字段不能为空")
    @Size(max = 128, message = "源字段不能超过 128 个字符")
    String sourceColumn;

    @Size(max = 1024, message = "转换表达式不能超过 1024 个字符")
    String transformExpr;
  }

  /** 表达式校验请求。 */
  @Data
  public static class ExpressionCheckRequest {
    @Size(max = 1024, message = "表达式不能超过 1024 个字符")
    String expression;
  }

  /** 表达式校验结果。 */
  public record ExpressionCheckView(boolean valid, String message) {}

  @Operation(summary = "模型的来源映射清单（含未映射字段标识）")
  @GetMapping
  public Result<List<MappingService.MappingView>> list(@PathVariable("modelId") Long modelId) {
    return Result.success(mappingService.list(modelId));
  }

  @Operation(summary = "设置/更新目标列的来源映射")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PutMapping("/{targetColumn}")
  public Result<Boolean> setMapping(
      @PathVariable("modelId") Long modelId,
      @PathVariable("targetColumn") String targetColumn,
      @Valid @RequestBody MappingSetRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    mappingService.setMapping(
        modelId,
        targetColumn,
        request.getSourceDatasourceId(),
        request.getSourceDatabase(),
        request.getSourceTable(),
        request.getSourceColumn(),
        request.getTransformExpr(),
        operator);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "清空目标列映射")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @DeleteMapping("/{targetColumn}")
  public Result<Boolean> clearMapping(
      @PathVariable("modelId") Long modelId, @PathVariable("targetColumn") String targetColumn) {
    return Result.success(mappingService.clearMapping(modelId, targetColumn));
  }

  @Operation(summary = "批量清空模型全部映射")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @DeleteMapping
  public Result<Integer> clearAll(@PathVariable("modelId") Long modelId) {
    return Result.success(mappingService.clearAll(modelId));
  }

  @Operation(summary = "转换表达式语法校验")
  @PostMapping("/validate-expression")
  public Result<ExpressionCheckView> validateExpression(
      @Valid @RequestBody ExpressionCheckRequest request) {
    String error = mappingService.validateExpression(request.getExpression());
    return Result.success(new ExpressionCheckView(error == null, error));
  }
}
