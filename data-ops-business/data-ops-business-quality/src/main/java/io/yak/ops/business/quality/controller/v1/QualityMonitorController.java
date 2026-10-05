package io.yak.ops.business.quality.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.controller.v1.converter.QualityMonitorConverter;
import io.yak.ops.business.quality.execution.QualityExecutionManager;
import io.yak.ops.business.quality.monitor.QualityMonitorManager;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.common.bean.dto.quality.QualityMonitorDTO;
import io.yak.ops.common.bean.vo.quality.QualityMonitorVO;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import java.security.Principal;
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

@Tag(name = "数据质量监控")
@RestController
@ConditionalOnQualityEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-quality/monitor")
@RequiresPermission(QualityPermissionCode.MONITOR_READ)
@ProjectScope
public class QualityMonitorController {
  private final QualityMonitorReader reader;
  private final QualityMonitorManager manager;
  private final QualityExecutionManager executionManager;
  private final QualityMonitorConverter converter;
  private final io.yak.ops.business.quality.monitor.QualitySuggestionQueryAdapter suggestions;

  @Operation(summary = "分页查询质量监控")
  @PostMapping("/page")
  public Result<QualityMonitorVO.Page> page(
      @Valid @RequestBody(required = false) QualityMonitorDTO.PageRequest request) {
    var query = converter.query(request);
    return Result.success(converter.page(reader.page(query), query));
  }

  @Operation(summary = "按数据表查询监控摘要")
  @GetMapping("/table-summary")
  public Result<List<QualityMonitorVO.TableSummary>> tableSummary(
      @RequestParam long dataSourceId,
      @RequestParam(value = "databaseName", required = false) String databaseName,
      @RequestParam(value = "schemaName", required = false) String schemaName) {
    return Result.success(
        converter.tableSummaries(
            reader.tableSummaries(dataSourceId, databaseName, schemaName)));
  }

  @Operation(summary = "工作台告警卡:近 24h 质量告警数 + 最近事件(只读,上限 50)")
  @GetMapping("/alert-overview")
  public Result<QualityMonitorVO.AlertOverview> alertOverview(
      @RequestParam(value = "limit", defaultValue = "10") int limit) {
    return Result.success(converter.alertOverview(reader.alertOverview(limit)));
  }

  @Operation(summary = "查询质量监控详情")
  @GetMapping("/{id}")
  public Result<QualityMonitorVO.Detail> detail(@PathVariable long id) {
    return Result.success(converter.detail(reader.require(id)));
  }

  public record EditorSnapshot(QualityMonitorVO.Detail monitor, QualityMonitorVO.Settings settings,
      String definition) {}

  @GetMapping("/{id}/editor-snapshot")
  public Result<EditorSnapshot> editableSnapshot(@PathVariable long id) {
    var value = reader.editableSnapshot(id);
    return Result.success(new EditorSnapshot(converter.detail(value.monitor()),
        converter.settings(value.settings()), value.definition()));
  }

  @Operation(summary = "读取当前可编辑定义的指纹")
  @GetMapping("/{id}/definition")
  public Result<String> definition(@PathVariable long id) {
    return Result.success(reader.definition(id));
  }

  @Operation(summary = "查询质量监控运行设置")
  @GetMapping("/{id}/settings")
  public Result<QualityMonitorVO.Settings> settings(@PathVariable long id) {
    return Result.success(converter.settings(reader.settings(id)));
  }

  @Operation(summary = "创建质量监控")
  @PostMapping
  @RequiresPermission(QualityPermissionCode.MONITOR_CREATE)
  public Result<QualityMonitorVO.Detail> create(
      @Valid @RequestBody QualityMonitorDTO.SaveRequest request) {
    return Result.success(
        converter.detail(manager.create(converter.command(request))));
  }

  @Operation(summary = "更新质量监控")
  @PutMapping("/{id}")
  @RequiresPermission(QualityPermissionCode.MONITOR_UPDATE)
  public Result<QualityMonitorVO.Detail> update(
      @PathVariable long id,
      @Valid @RequestBody QualityMonitorDTO.SaveRequest request) {
    var command = converter.command(request);
    if (request.expectedDefinition() != null) suggestions.validateEditedFields(command);
    return Result.success(
        converter.detail(manager.update(id, command, request.expectedDefinition())));
  }

  @Operation(summary = "删除质量监控")
  @DeleteMapping("/{id}")
  @RequiresPermission(QualityPermissionCode.MONITOR_DELETE)
  public Result<Boolean> delete(@PathVariable long id) {
    return Result.success(manager.delete(id));
  }

  @Operation(summary = "手动运行质量监控")
  @PostMapping("/{id}/run")
  @RequiresPermission(QualityPermissionCode.MONITOR_RUN)
  public Result<QualityMonitorVO.Run> run(
      @PathVariable long id,
      Principal principal) {
    return Result.success(
        converter.run(executionManager.run(id, operator(principal))));
  }

  private static String operator(Principal principal) {
    return principal == null
            || principal.getName() == null
            || principal.getName().isBlank()
        ? "system"
        : principal.getName();
  }
}
