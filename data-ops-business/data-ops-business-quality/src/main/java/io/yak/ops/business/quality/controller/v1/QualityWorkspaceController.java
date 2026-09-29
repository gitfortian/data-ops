package io.yak.ops.business.quality.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.controller.v1.converter.QualityWorkspaceConverter;
import io.yak.ops.business.quality.workspace.QualityWorkspaceReader;
import io.yak.ops.common.bean.vo.quality.QualityWorkspaceVO;
import io.yak.ops.core.project.ProjectScope;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据质量监控工作台")
@RestController
@ConditionalOnQualityEnabled
@RequestMapping("/api/v1/data-quality/monitor")
@RequiresPermission(QualityPermissionCode.MONITOR_READ)
@ProjectScope
public class QualityWorkspaceController {
  private final QualityWorkspaceReader reader;
  private final QualityWorkspaceConverter converter;

  public QualityWorkspaceController(
      QualityWorkspaceReader reader,
      QualityWorkspaceConverter converter) {
    this.reader = reader;
    this.converter = converter;
  }

  @Operation(summary = "查询质量监控工作台")
  @GetMapping("/{id}/workspace")
  public Result<QualityWorkspaceVO.MonitorWorkspace> workspace(
      @PathVariable long id) {
    return Result.success(converter.workspace(reader.workspace(id)));
  }

  @Operation(summary = "查询质量监控报告")
  @GetMapping("/{id}/report")
  public Result<QualityWorkspaceVO.MonitorReport> report(
      @PathVariable long id,
      @RequestParam(value = "date", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return Result.success(converter.report(reader.report(id, date)));
  }

  @Operation(summary = "查询质量监控操作日志")
  @GetMapping("/{id}/operation-log")
  public Result<QualityWorkspaceVO.OperationLogPage> operationLog(
      @PathVariable long id,
      @RequestParam(value = "current", required = false) Integer current,
      @RequestParam(value = "pageSize", required = false) Integer pageSize) {
    return Result.success(
        converter.operationLogs(reader.operationLogs(id, current, pageSize)));
  }
}
