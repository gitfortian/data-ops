package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.modeling.api.ModelingImportApi;
import io.yak.ops.business.modeling.importer.ReverseImportService;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reverse import (ticket 08): datasource tables → modeling models. */
@Tag(name = "数据建模逆向导入接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/import")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingReverseImportController {

  private final ReverseImportService importService;
  private final CurrentUserProvider currentUserProvider;

  public record TableBrowseView(
      String database, String schema, String name, String type, String remarks) {}

  public record ColumnPreviewView(
      String name,
      String typeName,
      Integer size,
      Integer scale,
      boolean nullable,
      boolean primaryKey,
      String remarks,
      /** 表级备注(冗余到每列,方便前端取值)。 */
      String tableRemarks) {}

  @Operation(summary = "浏览/搜索数据源表")
  @GetMapping("/tables")
  public Result<List<TableBrowseView>> listTables(
      @RequestParam("datasourceId") Long datasourceId,
      @RequestParam(value = "keyword", required = false) String keyword) {
    List<CatalogTable> tables = importService.listTables(datasourceId, keyword);
    return Result.success(
        tables.stream()
            .map(
                table ->
                    new TableBrowseView(
                        table.database(), table.schema(), table.name(), table.type(),
                        table.remarks()))
            .toList());
  }

  @Operation(summary = "预览一张表的列结构")
  @PostMapping("/preview")
  public Result<List<ColumnPreviewView>> preview(
      @Valid @RequestBody ModelingImportApi.PreviewRequest request) {
    List<CatalogColumn> columns =
        importService.previewColumns(request.datasourceId(), request.database(), request.table());
    // 从表列表中查找目标表备注
    String tableRemarks = importService.listTables(request.datasourceId(), request.table())
        .stream()
        .filter(t -> request.table().equalsIgnoreCase(t.name()))
        .findFirst()
        .map(CatalogTable::remarks)
        .orElse(null);
    String tableRemarksFinal = tableRemarks;
    List<ColumnPreviewView> result = new java.util.ArrayList<>(
        columns.stream()
            .map(
                column ->
                    new ColumnPreviewView(
                        column.name(), column.typeName(), column.size(), column.scale(),
                        column.nullable(), column.primaryKey(), column.remarks(), tableRemarksFinal))
            .toList());
    // 自动追加技术字段:已存在同名源列则跳过
    java.util.Set<String> names = result.stream().map(ColumnPreviewView::name)
        .collect(java.util.stream.Collectors.toSet());
    if (!names.contains("process_time")) {
      result.add(new ColumnPreviewView("process_time", "DATETIME", null, null, false, false, "数据处理时间", tableRemarksFinal));
    }
    if (!names.contains("event_time")) {
      result.add(new ColumnPreviewView("event_time", "DATETIME", null, null, false, false, "业务事件时间", tableRemarksFinal));
    }
    return Result.success(result);
  }

  @Operation(summary = "批量导入为模型（每表独立事务；编码冲突跳过）")
  @RequiresPermission(ModelingPermissionCode.CREATE)
  @PostMapping
  public Result<ModelingImportApi.ImportResult> importTables(
      @Valid @RequestBody ModelingImportApi.ImportRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(importService.importTables(request, operator));
  }
}
