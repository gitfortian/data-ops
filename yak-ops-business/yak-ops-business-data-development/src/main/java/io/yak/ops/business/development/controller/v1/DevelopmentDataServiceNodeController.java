package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDefinition.ParameterContract;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDefinition.ResponseFieldContract;
import io.yak.ops.business.development.domain.DevelopmentDataServiceRevision;
import io.yak.ops.business.development.domain.DevelopmentDataServiceRevisionSummary;
import io.yak.ops.business.development.service.DevelopmentDataServiceNodeService;
import io.yak.ops.business.development.service.DevelopmentDataServiceNodeService.DataServiceNodeContext;
import io.yak.ops.business.development.service.DevelopmentDataServiceNodeService.PreviewResult;
import io.yak.ops.business.development.service.DevelopmentDataServiceNodeService.SaveDraftCommand;
import io.yak.ops.common.constant.development.DataDevelopmentPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Data Service Node authoring API. Publish creates an immutable Data Development revision only. */
@Tag(name = "数据开发 Data Service Node 接口")
@RestController
@RequestMapping("/api/v1/data-development/nodes")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataDevelopmentPermissionCode.READ)
public class DevelopmentDataServiceNodeController {

  private final DevelopmentDataServiceNodeService service;

  public DevelopmentDataServiceNodeController(DevelopmentDataServiceNodeService service) {
    this.service = service;
  }

  @Operation(summary = "查询 Data Service Node 草稿与发布历史")
  @GetMapping("/{nodeId}/data-service")
  public Result<DataServiceNodeContext> get(@PathVariable("nodeId") long nodeId) {
    return Result.success(service.get(nodeId));
  }

  @Operation(summary = "执行当前查询 SQL，并返回结果数据、请求参数与响应字段")
  @RequiresPermission(DataDevelopmentPermissionCode.EXECUTE)
  @PostMapping("/{nodeId}/data-service/preview")
  public Result<PreviewResult> preview(
      @PathVariable("nodeId") long nodeId,
      @Valid @RequestBody PreviewRequest request) {
    return Result.success(service.preview(
        nodeId,
        request.dataSourceId(),
        request.sql(),
        request.maxRows(),
        request.timeoutSeconds(),
        request.parameterValues()));
  }

  @Operation(summary = "保存独立 Data Service Node 草稿")
  @RequiresPermission(DataDevelopmentPermissionCode.EDIT)
  @PutMapping("/{nodeId}/data-service/draft")
  public Result<DataServiceNodeContext> saveDraft(
      @PathVariable("nodeId") long nodeId,
      @Valid @RequestBody SaveDraftRequest request) {
    return Result.success(service.saveDraft(
        nodeId,
        new SaveDraftCommand(
            request.dataSourceId(),
            request.sql(),
            request.serviceName(),
            request.path(),
            request.method(),
            request.parameters() == null
                ? List.of()
                : request.parameters().stream()
                    .map(DevelopmentDataServiceNodeController::toParameterContract)
                    .toList(),
            request.responseFields() == null
                ? List.of()
                : request.responseFields().stream()
                    .map(DevelopmentDataServiceNodeController::toResponseFieldContract)
                    .toList(),
            request.maxRows(),
            request.timeoutSeconds(),
            request.description(),
            request.paginationEnabled(),
            request.autoParseParameters(),
            request.baseRevision())));
  }

  @Operation(summary = "发布不可变 Data Service Node Revision")
  @RequiresPermission(DataDevelopmentPermissionCode.PUBLISH)
  @PostMapping("/{nodeId}/data-service/publish")
  public Result<DevelopmentDataServiceRevision> publish(
      @PathVariable("nodeId") long nodeId,
      @Valid @RequestBody PublishRequest request) {
    return Result.success(service.publish(nodeId, request.expectedDraftRevision()));
  }

  @Operation(summary = "查询 Data Service Node 发布历史")
  @GetMapping("/{nodeId}/data-service/revisions")
  public Result<List<DevelopmentDataServiceRevisionSummary>> revisions(
      @PathVariable("nodeId") long nodeId) {
    return Result.success(service.listRevisions(nodeId));
  }

  @Operation(summary = "查询指定 Data Service Node Revision")
  @GetMapping("/{nodeId}/data-service/revisions/{revisionNo}")
  public Result<DevelopmentDataServiceRevision> revision(
      @PathVariable("nodeId") long nodeId,
      @PathVariable("revisionNo") int revisionNo) {
    return Result.success(service.getRevision(nodeId, revisionNo));
  }

  private static ParameterContract toParameterContract(ParameterRequest request) {
    return new ParameterContract(
        request.name(), request.type(), request.required(), request.description(), request.example());
  }

  private static ResponseFieldContract toResponseFieldContract(ResponseFieldRequest request) {
    return new ResponseFieldContract(
        request.name(), request.type(), request.nullable(), request.description(), request.example());
  }

  public record PreviewRequest(
      @NotNull @Min(1) Long dataSourceId,
      @NotBlank @Size(max = 1_000_000) String sql,
      @Min(1) @Max(10_000) Integer maxRows,
      @Min(1) @Max(3_600) Integer timeoutSeconds,
      Map<String, Object> parameterValues) {}

  public record SaveDraftRequest(
      @NotNull @Min(1) Long dataSourceId,
      @NotBlank @Size(max = 1_000_000) String sql,
      @NotBlank @Size(max = 200) String serviceName,
      @NotBlank @Size(max = 255) String path,
      @Size(max = 16) String method,
      List<@Valid ParameterRequest> parameters,
      List<@Valid ResponseFieldRequest> responseFields,
      @Min(1) @Max(10_000) Integer maxRows,
      @Min(1) @Max(3_600) Integer timeoutSeconds,
      @Size(max = 2_000) String description,
      Boolean paginationEnabled,
      Boolean autoParseParameters,
      @Min(0) Long baseRevision) {}

  public record ParameterRequest(
      @NotBlank @Size(max = 128) String name,
      @NotBlank @Size(max = 32) String type,
      boolean required,
      @Size(max = 1_000) String description,
      @Size(max = 1_000) String example) {}

  public record ResponseFieldRequest(
      @NotBlank @Size(max = 128) String name,
      @NotBlank @Size(max = 32) String type,
      boolean nullable,
      @Size(max = 1_000) String description,
      @Size(max = 1_000) String example) {}

  public record PublishRequest(@Min(1) long expectedDraftRevision) {}
}
