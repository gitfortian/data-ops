package io.yak.ops.business.metadata.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metadata.controller.v1.dto.MetamodelRequests;
import io.yak.ops.business.metadata.metamodel.MetamodelAdminService;
import io.yak.ops.business.metadata.metamodel.MetamodelAdminService.FieldView;
import io.yak.ops.business.metadata.metamodel.MetamodelAdminService.SlotBoard;
import io.yak.ops.business.metadata.metamodel.MetamodelAdminService.TypeView;
import io.yak.ops.common.constant.metadata.MetadataPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 元模型自省与配置 API（ticket 129）。
 *
 * <p>{@code GET /types} 是前端目录列、详情面板、类型 facet 的<b>唯一</b>数据来源：
 * 新插一行 {@code type_def} 后该类型自动出现在检索面，不改一行前端代码（ticket 132）。
 *
 * <p>这里是 {@code LEGACY_GLOBAL} 而目录/搜索接口是 {@code PROJECT_REQUIRED}：元模型是
 * <b>全局</b>配置（类型定义不属于任何项目），而目录行按项目隔离。两者同前缀不冲突——
 * 前端对 {@code /api/v1/metadata} 一律带项目头，固有全局接口容忍多带的头（quality 模块
 * 的模板接口与此同形）。
 */
@Tag(name = "元数据-元模型")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metadata")
@ProjectScope(ProjectMigrationMode.LEGACY_GLOBAL)
@RequiresPermission(MetadataPermissionCode.READ)
public class MetadataTypeController {

  private final MetamodelAdminService metamodelService;

  @Operation(summary = "类型清单（含字段定义）：前端渲染与检索配置的唯一来源")
  @GetMapping("/types")
  public Result<List<TypeView>> types(
      @RequestParam(value = "category", required = false) String category,
      @RequestParam(value = "includeFields", defaultValue = "true") boolean includeFields) {
    return Result.success(metamodelService.listTypes(category, includeFields));
  }

  @Operation(summary = "单个类型定义")
  @GetMapping("/types/{typeName}")
  public Result<TypeView> type(
      @PathVariable("typeName") String typeName,
      @RequestParam(value = "includeFields", defaultValue = "true") boolean includeFields) {
    return Result.success(metamodelService.getType(typeName, includeFields));
  }

  @Operation(summary = "槽位占用与余量：7 个生成列是建表时定死的容量")
  @GetMapping("/slots")
  public Result<SlotBoard> slots() {
    return Result.success(metamodelService.slotBoard());
  }

  @Operation(summary = "新增类型（ENTITY 必须声明 key_prefix/fqn_pattern/lineage_asset_type）")
  @PostMapping("/types")
  @RequiresPermission(MetadataPermissionCode.UPDATE)
  public Result<TypeView> createType(@Valid @RequestBody MetamodelRequests.TypeDefDTO dto) {
    return Result.success(metamodelService.createType(dto));
  }

  @Operation(summary = "修改类型（错配置进不了库，校验见 MetamodelValidationService）")
  @PutMapping("/types/{typeName}")
  @RequiresPermission(MetadataPermissionCode.UPDATE)
  public Result<TypeView> updateType(
      @PathVariable("typeName") String typeName,
      @Valid @RequestBody MetamodelRequests.TypeDefDTO dto) {
    return Result.success(metamodelService.updateType(typeName, dto));
  }

  @Operation(summary = "废弃类型（永不物理删：历史目录行还要能解析）")
  @DeleteMapping("/types/{typeName}")
  @RequiresPermission(MetadataPermissionCode.DELETE)
  public Result<TypeView> archiveType(@PathVariable("typeName") String typeName) {
    return Result.success(metamodelService.archiveType(typeName));
  }

  @Operation(summary = "给类型加一个扩展字段（searchable/facetable 必须有落点）")
  @PostMapping("/types/{typeName}/fields")
  @RequiresPermission(MetadataPermissionCode.UPDATE)
  public Result<FieldView> createField(
      @PathVariable("typeName") String typeName,
      @Valid @RequestBody MetamodelRequests.FieldDefDTO dto) {
    return Result.success(metamodelService.createField(typeName, dto));
  }

  @Operation(summary = "修改扩展字段定义")
  @PutMapping("/types/{typeName}/fields/{fieldName}")
  @RequiresPermission(MetadataPermissionCode.UPDATE)
  public Result<FieldView> updateField(
      @PathVariable("typeName") String typeName,
      @PathVariable("fieldName") String fieldName,
      @Valid @RequestBody MetamodelRequests.FieldDefDTO dto) {
    return Result.success(metamodelService.updateField(typeName, fieldName, dto));
  }
}
