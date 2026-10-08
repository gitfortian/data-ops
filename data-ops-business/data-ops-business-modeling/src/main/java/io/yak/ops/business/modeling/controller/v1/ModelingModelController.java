package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.modeling.api.ModelingModelApi;
import io.yak.ops.business.modeling.api.ModelingStructureApi;
import io.yak.ops.business.modeling.approval.ModelPublishApprovalService;
import io.yak.ops.business.modeling.catalog.ModelCatalogService;
import io.yak.ops.business.modeling.controller.v1.converter.ModelViewConverter;
import io.yak.ops.business.modeling.controller.v1.dto.ModelingModelQueryDTO;
import io.yak.ops.business.modeling.controller.v1.vo.ModelVO;
import io.yak.ops.business.modeling.controller.v1.vo.ModelVersionVO;
import io.yak.ops.business.modeling.ddl.DdlService;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureDialectCatalog;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.modeling.structure.ValidationIssue;
import io.yak.ops.business.modeling.version.ModelVersionService;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelVersionSummary;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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

/** Warehouse modeling catalog: physical model CRUD. */
@Tag(name = "数据建模模型接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/models")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingModelController {

  private final ModelCatalogService service;
  private final ModelStructureService structureService;
  private final DdlService ddlService;
  private final ModelVersionService versionService;
  private final ModelPublishApprovalService publishApprovalService;
  private final ModelViewConverter viewConverter;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "创建物理模型")
  @RequiresPermission(ModelingPermissionCode.CREATE)
  @PostMapping
  public Result<ModelVO> create(
      @Valid @RequestBody ModelingModelApi.CreateRequest request, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        viewConverter.toView(
            service.create(
                request.name(), request.code(), request.dialect(), request.description(),
                operator, request.directoryId(), request.layerCode(), request.processId(),
                request.sourceDatasourceId(), request.sourceDatabase(), request.sourceTable(),
                request.domainId())));
  }

  @Operation(summary = "分页查询物理模型（支持分层/业务过程/状态筛选，列表含名称解析）")
  @PostMapping("/page")
  public Result<PagingData<ModelVO>> page(@Valid @RequestBody ModelingModelQueryDTO query) {
    var page =
        service.page(
            query.getPageNo(),
            query.getPageSize(),
            query.getKeyword(),
            query.getDirectoryId(),
            query.getTagIds(),
            query.getLayerCode(),
            query.getProcessId(),
            query.getProcessIds(),
            query.getStatus(),
            query.getDomainId());
    return Result.success(
        viewConverter.page(
            page, service.processNames(), service.layerDatabaseNames(), service.domainNames()));
  }

  @Operation(summary = "模型详情")
  @GetMapping("/{id}")
  public Result<ModelVO> get(@PathVariable("id") Long id) {
    return Result.success(toViewWithNames(service.get(id)));
  }

  @Operation(summary = "更新模型基础信息（除编码外均可编辑）")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<ModelVO> updateBasics(
      @PathVariable("id") Long id,
      @Valid @RequestBody ModelingModelApi.UpdateRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    service.updateBasics(id, request, operator);
    return Result.success(toViewWithNames(service.get(id)));
  }

  /** 单条响应带名称解析(详情/编辑后回显):业务过程、业务域、分层目标库。 */
  private ModelVO toViewWithNames(Model model) {
    return viewConverter.toView(
        model, service.processNames(), service.layerDatabaseNames(), service.domainNames());
  }

  @Operation(summary = "写入字段导入方式(血缘追溯)")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PutMapping("/{id}/import-lineage")
  public Result<Void> assignImportLineage(
      @PathVariable("id") Long id,
      @Valid @RequestBody ModelingModelApi.AssignImportLineageRequest request,
      HttpServletRequest httpRequest) {
    service.assignImportLineage(
        id, request.importMode(), request.sourceModelId(),
        currentUserProvider.getCurrentUser(httpRequest));
    return Result.success(null);
  }

  @Operation(summary = "查询模型表结构")
  @GetMapping("/{id}/structure")
  public Result<StructureView> getStructure(@PathVariable("id") Long id) {
    return Result.success(structureService.get(id));
  }

  @Operation(summary = "读取模型编辑上下文与条件保存指纹")
  @RequiresPermission(ModelingPermissionCode.READ)
  @GetMapping("/{id}/structure/edit-context")
  public Result<ModelStructureService.EditContext> editContext(@PathVariable("id") Long id) {
    return Result.success(structureService.editContext(id));
  }

  @Operation(summary = "条件保存模型编辑草稿并返回同事务编辑基线")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PutMapping("/{id}/structure/edit-context")
  public Result<ModelStructureService.EditContext> saveEditContext(
      @PathVariable("id") Long id,
      @Valid @RequestBody ModelingStructureApi.SaveStructureRequest request,
      HttpServletRequest httpRequest) {
    return Result.success(structureService.saveEditContext(id, request,
        currentUserProvider.getCurrentUser(httpRequest), httpRequest.getHeader("If-Match")));
  }

  @Operation(summary = "生成建库脚本（仅生成，不在平台内执行）")
  @GetMapping("/{id}/ddl")
  public Result<DdlService.DdlView> generateDdl(@PathVariable("id") Long id) {
    return Result.success(ddlService.generate(id));
  }

  @Operation(summary = "按方言查询数据类型目录")
  @GetMapping("/{id}/structure/type-catalog")
  public Result<List<StructureDialectCatalog.TypeSpec>> typeCatalog(@PathVariable("id") Long id) {
    return Result.success(StructureDialectCatalog.types(structureService.dialectOf(id)));
  }

  @Operation(summary = "表结构方言校验（ERROR 阻断保存，WARNING 仅提示）")
  @PostMapping("/{id}/structure/validate")
  public Result<List<ValidationIssue>> validateStructure(
      @PathVariable("id") Long id,
      @Valid @RequestBody ModelingStructureApi.SaveStructureRequest request) {
    return Result.success(structureService.validate(id, request));
  }

  @Operation(summary = "保存模型表结构（表基础信息 + 字段全量替换）")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PutMapping("/{id}/structure")
  public Result<StructureView> saveStructure(
      @PathVariable("id") Long id,
      @Valid @RequestBody ModelingStructureApi.SaveStructureRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    String expected = httpRequest.getHeader("If-Match");
    return Result.success(expected == null ? structureService.save(id, request, operator)
        : structureService.save(id, request, operator, expected));
  }

  @Operation(summary = "设置模型所属目录")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PutMapping("/{id}/directory")
  public Result<Boolean> assignDirectory(
      @PathVariable("id") Long id,
      @Valid @RequestBody ModelingModelApi.AssignDirectoryRequest request,
      HttpServletRequest httpRequest) {
    service.assignDirectory(id, request.directoryId(), currentUserProvider.getCurrentUser(httpRequest));
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "设置模型标签")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PutMapping("/{id}/tags")
  public Result<Boolean> assignTags(
      @PathVariable("id") Long id,
      @Valid @RequestBody ModelingModelApi.AssignTagsRequest request) {
    service.assignTags(id, request.tagIds());
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "删除物理模型（移入回收站）")
  @RequiresPermission(ModelingPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    service.delete(id, currentUserProvider.getCurrentUser(httpRequest));
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "回收站分页查询")
  @PostMapping("/deleted/page")
  public Result<PagingData<ModelVO>> pageDeleted(@Valid @RequestBody ModelingModelQueryDTO query) {
    return Result.success(
        viewConverter.page(
            service.listDeleted(query.getPageNo(), query.getPageSize(), query.getKeyword())));
  }

  @Operation(summary = "从回收站恢复模型")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PostMapping("/{id}/restore")
  public Result<Boolean> restore(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    service.restore(id, currentUserProvider.getCurrentUser(httpRequest));
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "彻底删除回收站模型（不可恢复）")
  @RequiresPermission(ModelingPermissionCode.DELETE)
  @DeleteMapping("/{id}/purge")
  public Result<Boolean> purge(@PathVariable("id") Long id) {
    service.purge(id);
    return Result.success(Boolean.TRUE);
  }

  // ---- 版本管理 ----

  @Operation(summary = "发布当前结构为新版本")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PostMapping("/{id}/publish")
  public Result<ModelVersionVO> publish(
      @PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    ModelVersionService.PublishResult result = versionService.publish(id, operator);
    return Result.success(toVersionVO(result.version()));
  }

  @Operation(summary = "提交发布审批(需已配置 MODEL_PUBLISH 流程;批准后自动发布)")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PostMapping("/{id}/publish-approval")
  public Result<ApprovalInstanceView> submitPublishApproval(
      @PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(publishApprovalService.submit(id, operator));
  }

  @Operation(summary = "列出版本历史")
  @GetMapping("/{id}/versions")
  public Result<List<ModelVersionVO>> listVersions(@PathVariable("id") Long id) {
    List<ModelVersionSummary> versions = versionService.listVersions(id);
    return Result.success(versions.stream().map(this::toVersionVO).toList());
  }

  @Operation(summary = "查看某版本结构详情")
  @GetMapping("/{id}/versions/{versionNo}")
  public Result<ModelVersionService.VersionDetailView> getVersionDetail(
      @PathVariable("id") Long id, @PathVariable("versionNo") int versionNo) {
    return Result.success(versionService.getVersionDetail(id, versionNo));
  }

  @Operation(summary = "回滚第一步：用指定版本覆盖当前草稿（置 DRAFT，不发布）；需再次发布才生效")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  @PostMapping("/{id}/versions/{versionNo}/rollback")
  public Result<Boolean> rollback(
      @PathVariable("id") Long id,
      @PathVariable("versionNo") int versionNo,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    versionService.rollback(id, versionNo, operator);
    return Result.success(true);
  }

  private ModelVersionVO toVersionVO(ModelVersionSummary v) {
    return new ModelVersionVO(
        v.id(), v.modelId(), v.versionNo(), v.columnCount(),
        v.checksum(), v.publishedBy(), v.publishTime());
  }

  private ModelVersionVO toVersionVO(io.yak.ops.business.modeling.domain.ModelVersion v) {
    return new ModelVersionVO(
        v.id(), v.modelId(), v.versionNo(), v.columnCount(),
        v.checksum(), v.publishedBy(), v.publishTime());
  }
}
