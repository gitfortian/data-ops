package io.yak.ops.business.digitalscreen.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.digitalscreen.application.DigitalScreenApplicationService;
import io.yak.ops.business.digitalscreen.controller.v1.converter.DigitalScreenRequestConverter;
import io.yak.ops.business.digitalscreen.controller.v1.converter.DigitalScreenViewConverter;
import io.yak.ops.business.digitalscreen.controller.v1.dto.DigitalScreenRequests.CreateDigitalScreenRequest;
import io.yak.ops.business.digitalscreen.controller.v1.dto.DigitalScreenRequests.UpdateDigitalScreenRequest;
import io.yak.ops.business.digitalscreen.controller.v1.vo.DigitalScreenViews.DigitalScreenVO;
import io.yak.ops.business.digitalscreen.controller.v1.vo.DigitalScreenViews.DigitalScreenVersionSummaryVO;
import io.yak.ops.business.digitalscreen.controller.v1.vo.DigitalScreenViews.DigitalScreenVersionVO;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Digital Screen management HTTP adapter. Viewer routes expose immutable project-owned snapshots. */
@Tag(name = "数字化大屏接口")
@RestController
@Validated
@ProjectScope
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/digital-screens")
public class DigitalScreenController {

  private final DigitalScreenApplicationService service;
  private final DigitalScreenRequestConverter requestConverter;
  private final DigitalScreenViewConverter viewConverter;

  @Operation(summary = "查询数字化大屏列表")
  @GetMapping
  public Result<List<DigitalScreenVO>> list() {
    return Result.success(service.list().stream().map(viewConverter::screen).toList());
  }

  @Operation(summary = "查询数字化大屏当前 Draft")
  @GetMapping("/{screenId}")
  public Result<DigitalScreenVO> get(@PathVariable("screenId") long screenId) {
    return Result.success(viewConverter.screen(service.get(screenId)));
  }

  @Operation(summary = "查询数字化大屏当前已发布快照")
  @GetMapping("/{screenId}/published")
  public Result<DigitalScreenVersionVO> published(@PathVariable("screenId") long screenId) {
    return Result.success(viewConverter.version(service.published(screenId)));
  }

  @Operation(summary = "查询数字化大屏发布版本历史")
  @GetMapping("/{screenId}/versions")
  public Result<List<DigitalScreenVersionSummaryVO>> versions(
      @PathVariable("screenId") long screenId) {
    DigitalScreenVO screen = viewConverter.screen(service.get(screenId));
    int currentVersionNo = screen.publishedVersionNo() == null ? 0 : screen.publishedVersionNo();
    return Result.success(service.versions(screenId).stream()
        .map(version -> viewConverter.versionSummary(version, currentVersionNo))
        .toList());
  }

  @Operation(summary = "查询数字化大屏指定发布版本")
  @GetMapping("/{screenId}/versions/{versionNo}")
  public Result<DigitalScreenVersionVO> version(
      @PathVariable("screenId") long screenId,
      @PathVariable("versionNo") int versionNo) {
    return Result.success(viewConverter.version(service.version(screenId, versionNo)));
  }

  @Operation(summary = "创建数字化大屏")
  @PostMapping
  public Result<DigitalScreenVO> create(
      @Valid @RequestBody CreateDigitalScreenRequest request) {
    return Result.success(viewConverter.screen(service.create(requestConverter.create(request))));
  }

  @Operation(summary = "保存数字化大屏 Draft")
  @PutMapping("/{screenId}")
  public Result<DigitalScreenVO> update(
      @PathVariable("screenId") long screenId,
      @Valid @RequestBody UpdateDigitalScreenRequest request) {
    return Result.success(viewConverter.screen(service.update(screenId, requestConverter.update(request))));
  }

  @Operation(summary = "把当前 Draft 发布为新的不可变版本")
  @PostMapping("/{screenId}/publish")
  public Result<DigitalScreenVO> publish(@PathVariable("screenId") long screenId) {
    return Result.success(viewConverter.screen(service.publish(screenId)));
  }

  @Operation(summary = "取消发布数字化大屏，历史版本继续保留")
  @PostMapping("/{screenId}/offline")
  public Result<DigitalScreenVO> offline(@PathVariable("screenId") long screenId) {
    return Result.success(viewConverter.screen(service.offline(screenId)));
  }

  @Operation(summary = "回滚历史版本并追加发布为新的版本")
  @PostMapping("/{screenId}/versions/{versionNo}/rollback")
  public Result<DigitalScreenVO> rollback(
      @PathVariable("screenId") long screenId,
      @PathVariable("versionNo") int versionNo) {
    return Result.success(viewConverter.screen(service.rollback(screenId, versionNo)));
  }

  @Operation(summary = "复制数字化大屏 Draft，复制品不继承发布历史")
  @PostMapping("/{screenId}/duplicate")
  public Result<DigitalScreenVO> duplicate(@PathVariable("screenId") long screenId) {
    return Result.success(viewConverter.screen(service.duplicate(screenId)));
  }

  @Operation(summary = "删除数字化大屏及其发布版本历史")
  @DeleteMapping("/{screenId}")
  public Result<Boolean> delete(@PathVariable("screenId") long screenId) {
    service.delete(screenId);
    return Result.success(Boolean.TRUE);
  }
}
