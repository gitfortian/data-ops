package io.yak.ops.business.quality.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.controller.v1.converter.QualityExecutionConverter;
import io.yak.ops.business.quality.execution.QualityExecutionReader;
import io.yak.ops.common.bean.dto.quality.QualityExecutionDTO;
import io.yak.ops.common.bean.vo.quality.QualityExecutionVO;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据质量执行记录")
@RestController
@ConditionalOnQualityEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-quality/execution")
@ProjectScope
public class QualityExecutionController {
  private final QualityExecutionReader reader;
  private final QualityExecutionConverter converter;

  @Operation(summary = "分页查询执行记录")
  @PostMapping("/page")
  @RequiresPermission(QualityPermissionCode.EXECUTION_READ)
  public Result<QualityExecutionVO.Page> page(
      @Valid @RequestBody(required = false) QualityExecutionDTO.PageRequest request) {
    var query = converter.query(request);
    return Result.success(converter.page(reader.page(query), query));
  }

  @Operation(summary = "查询执行状态")
  @GetMapping("/{executionNo}/status")
  @RequiresPermission(QualityPermissionCode.MONITOR_RUN)
  public Result<QualityExecutionVO.Status> status(
      @PathVariable String executionNo) {
    return Result.success(
        converter.status(reader.requireSummary(executionNo)));
  }

  @Operation(summary = "查询执行详情")
  @GetMapping("/{executionNo}")
  @RequiresPermission(QualityPermissionCode.EXECUTION_READ)
  public Result<QualityExecutionVO.Detail> detail(
      @PathVariable String executionNo) {
    return Result.success(converter.detail(reader.require(executionNo)));
  }
}
