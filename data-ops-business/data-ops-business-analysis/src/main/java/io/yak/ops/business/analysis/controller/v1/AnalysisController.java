package io.yak.ops.business.analysis.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.analysis.AnalysisService;
import io.yak.ops.business.analysis.controller.v1.converter.AnalysisRequestConverter;
import io.yak.ops.business.analysis.controller.v1.converter.AnalysisViewConverter;
import io.yak.ops.business.analysis.controller.v1.dto.AnalysisRequests.SaveAnalysisRequest;
import io.yak.ops.business.analysis.controller.v1.vo.AnalysisViews;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BI 图表分析接口")
@Validated
@RestController
@ProjectScope
@RequestMapping("/api/v1/analyses")
public class AnalysisController {

  private final AnalysisService service;
  private final AnalysisRequestConverter requestConverter;
  private final AnalysisViewConverter viewConverter;

  public AnalysisController(
      AnalysisService service,
      AnalysisRequestConverter requestConverter,
      AnalysisViewConverter viewConverter) {
    this.service = service;
    this.requestConverter = requestConverter;
    this.viewConverter = viewConverter;
  }

  @Operation(summary = "查询可复用 Analysis 列表")
  @GetMapping
  public Result<List<AnalysisViews.Analysis>> list() {
    return Result.success(viewConverter.toViews(service.list()));
  }

  @Operation(summary = "查询 Analysis 详情")
  @GetMapping("/{analysisId}")
  public Result<AnalysisViews.Analysis> get(@PathVariable("analysisId") long analysisId) {
    return Result.success(viewConverter.toView(service.get(analysisId)));
  }

  @Operation(summary = "创建可复用 Analysis")
  @PostMapping
  public Result<AnalysisViews.Analysis> create(@Valid @RequestBody SaveAnalysisRequest request) {
    return Result.success(viewConverter.toView(service.create(requestConverter.toCommand(request))));
  }

  @Operation(summary = "更新 Analysis 定义")
  @PutMapping("/{analysisId}")
  public Result<AnalysisViews.Analysis> update(
      @PathVariable("analysisId") long analysisId,
      @Valid @RequestBody SaveAnalysisRequest request) {
    return Result.success(
        viewConverter.toView(service.update(analysisId, requestConverter.toCommand(request))));
  }

  @Operation(summary = "删除 Analysis")
  @DeleteMapping("/{analysisId}")
  public Result<Boolean> delete(@PathVariable("analysisId") long analysisId) {
    service.delete(analysisId);
    return Result.success(Boolean.TRUE);
  }
}
