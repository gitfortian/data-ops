package io.yak.ops.business.quality.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.controller.v1.converter.QualityExecutionWorkspaceConverter;
import io.yak.ops.business.quality.execution.QualityExecutionReader;
import io.yak.ops.business.quality.workspace.QualityExecutionLogProjector;
import io.yak.ops.common.bean.dto.quality.QualityExecutionWorkspaceDTO;
import io.yak.ops.common.bean.vo.quality.QualityExecutionWorkspaceVO;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据质量执行工作台")
@RestController
@ConditionalOnQualityEnabled
@RequestMapping("/api/v1/data-quality/execution/workspace")
@RequiresPermission(QualityPermissionCode.EXECUTION_READ)
@ProjectScope
public class QualityExecutionWorkspaceController {
  private final QualityExecutionReader reader;
  private final QualityExecutionLogProjector logProjector;
  private final QualityExecutionWorkspaceConverter converter;

  public QualityExecutionWorkspaceController(
      QualityExecutionReader reader,
      QualityExecutionLogProjector logProjector,
      QualityExecutionWorkspaceConverter converter) {
    this.reader = reader;
    this.logProjector = logProjector;
    this.converter = converter;
  }

  @Operation(summary = "分页查询监控执行记录")
  @PostMapping("/page")
  public Result<QualityExecutionWorkspaceVO.ExecutionPage> page(
      @Valid @RequestBody(required = false)
          QualityExecutionWorkspaceDTO.PageRequest request) {
    var query = converter.query(request);
    return Result.success(converter.page(reader.page(query), query));
  }

  @Operation(summary = "分页查询规则执行记录")
  @PostMapping("/rule/page")
  public Result<QualityExecutionWorkspaceVO.RuleExecutionPage> pageRules(
      @Valid @RequestBody(required = false)
          QualityExecutionWorkspaceDTO.PageRequest request) {
    var query = converter.query(request);
    return Result.success(converter.pageRules(reader.pageRules(query), query));
  }

  @Operation(summary = "查询执行工作台详情")
  @GetMapping("/{executionNo}")
  public Result<QualityExecutionWorkspaceVO.ExecutionDetail> detail(
      @PathVariable String executionNo) {
    return Result.success(converter.detail(reader.require(executionNo)));
  }

  @Operation(summary = "查询执行结构化日志")
  @GetMapping("/{executionNo}/logs")
  public Result<QualityExecutionWorkspaceVO.LogView> logs(
      @PathVariable String executionNo) {
    return Result.success(
        converter.logs(logProjector.project(reader.require(executionNo))));
  }
}
