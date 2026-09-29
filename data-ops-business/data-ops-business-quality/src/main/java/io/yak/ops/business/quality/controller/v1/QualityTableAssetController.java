package io.yak.ops.business.quality.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.business.quality.asset.QualityTableAssetManager;
import io.yak.ops.business.quality.asset.QualityTableAssetReader;
import io.yak.ops.business.quality.asset.QualityTableCandidateReader;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.controller.v1.converter.QualityTableAssetConverter;
import io.yak.ops.common.bean.dto.quality.QualityTableAssetDTO;
import io.yak.ops.common.bean.vo.quality.QualityTableAssetVO;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据质量注册表")
@Validated
@RestController
@ConditionalOnQualityEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-quality/table-asset")
@RequiresPermission(QualityPermissionCode.MONITOR_READ)
@ProjectScope
public class QualityTableAssetController {
  private final QualityTableAssetReader reader;
  private final QualityTableCandidateReader candidateReader;
  private final QualityTableAssetManager manager;
  private final QualityTableAssetConverter converter;

  @Operation(summary = "分页查询已注册数据表")
  @PostMapping("/page")
  public Result<QualityTableAssetVO.Page> page(
      @Valid @RequestBody QualityTableAssetDTO.PageRequest request) {
    var query = converter.query(request);
    return Result.success(converter.page(reader.page(query), query));
  }

  @Operation(summary = "从数据源插件分页查询可注册数据表")
  @GetMapping("/candidates")
  public Result<QualityTableAssetVO.CandidatePage> candidates(
      @RequestParam long dataSourceId,
      @RequestParam(value = "databaseName", required = false) String databaseName,
      @RequestParam(value = "schemaName", required = false) String schemaName,
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(defaultValue = "1") @Min(1) int current,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
    return Result.success(
        converter.candidates(
            candidateReader.candidates(
                dataSourceId,
                databaseName,
                schemaName,
                keyword,
                current,
                pageSize)));
  }

  @Operation(summary = "批量注册数据表")
  @PostMapping("/register")
  @RequiresPermission(QualityPermissionCode.MONITOR_CREATE)
  public Result<QualityTableAssetVO.RegisterResult> register(
      @Valid @RequestBody QualityTableAssetDTO.RegisterRequest request,
      Principal principal) {
    return Result.success(
        converter.register(
            manager.register(
                converter.command(request),
                operator(principal))));
  }

  @Operation(summary = "取消注册数据表")
  @DeleteMapping("/{id}")
  @RequiresPermission(QualityPermissionCode.MONITOR_DELETE)
  public Result<Boolean> unregister(@PathVariable long id) {
    return Result.success(manager.unregister(id));
  }

  private static String operator(Principal principal) {
    return principal == null
            || principal.getName() == null
            || principal.getName().isBlank()
        ? "system"
        : principal.getName();
  }
}
