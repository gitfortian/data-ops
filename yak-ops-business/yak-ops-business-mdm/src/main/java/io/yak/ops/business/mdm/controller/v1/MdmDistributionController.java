package io.yak.ops.business.mdm.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.mdm.api.MdmDistributionApi;
import io.yak.ops.business.mdm.application.MdmDistributionPublishService;
import io.yak.ops.business.mdm.application.MdmDistributionService;
import io.yak.ops.business.mdm.application.MdmDistributionService.DistributionResult;
import io.yak.ops.business.mdm.domain.distribution.MdmDistribution;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionMode;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionStatus;
import io.yak.ops.common.constant.mdm.MdmPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 主数据分发配置端点(ticket 58):实体详情页"分发配置"Tab。 */
@Tag(name = "主数据分发")
@RestController
@RequestMapping("/api/v1/mdm/distribution")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class MdmDistributionController {

  private final MdmDistributionService service;
  private final MdmDistributionPublishService publishService;
  private final CurrentUserProvider currentUserProvider;

  public MdmDistributionController(
      MdmDistributionService service,
      MdmDistributionPublishService publishService,
      CurrentUserProvider currentUserProvider) {
    this.service = service;
    this.publishService = publishService;
    this.currentUserProvider = currentUserProvider;
  }

  @Operation(summary = "按实体列出全部分发配置")
  @RequiresPermission("mdm:read")
  @GetMapping("/entity/{entityId}")
  public Result<List<DistributionVO>> list(@PathVariable("entityId") Long entityId) {
    return Result.success(
        service.listByEntity(entityId).stream().map(DistributionVO::from).toList());
  }

  @Operation(summary = "分发配置详情")
  @RequiresPermission("mdm:read")
  @GetMapping("/{id}")
  public Result<DistributionVO> detail(@PathVariable("id") Long id) {
    return Result.success(DistributionVO.from(service.get(id)));
  }

  @Operation(summary = "新建分发配置")
  @RequiresPermission("mdm:create")
  @PostMapping
  public Result<DistributionVO> create(
      @RequestBody MdmDistributionApi.DistributionSaveRequest request,
      HttpServletRequest httpRequest) {
    MdmDistribution created =
        service.create(
            request.entityId(),
            request.targetSystem(),
            request.targetName(),
            MdmDistributionMode.valueOf(request.distributeMode()),
            request.distributeFreq(),
            request.distributeScope(),
            currentUserProvider.getCurrentUser(httpRequest));
    return Result.success(DistributionVO.from(created));
  }

  @Operation(summary = "更新分发配置")
  @RequiresPermission("mdm:update")
  @PutMapping("/{id}")
  public Result<Void> update(
      @PathVariable("id") Long id,
      @RequestBody MdmDistributionApi.DistributionUpdateRequest request) {
    MdmDistributionMode mode =
        request.distributeMode() == null
            ? null : MdmDistributionMode.valueOf(request.distributeMode());
    service.update(id, request.targetName(), mode, request.distributeFreq(), request.distributeScope());
    return Result.success(null);
  }

  @Operation(summary = "切换分发配置状态(生效/停用)")
  @RequiresPermission("mdm:update")
  @PutMapping("/{id}/status")
  public Result<Void> setStatus(
      @PathVariable("id") Long id,
      @RequestBody MdmDistributionApi.StatusToggleRequest request) {
    service.setStatus(id, MdmDistributionStatus.valueOf(request.status()));
    return Result.success(null);
  }

  @Operation(summary = "删除分发配置")
  @RequiresPermission("mdm:delete")
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return Result.success(null);
  }

  @Operation(summary = "手动执行分发(R5:API 模式=推送至数据服务发布态,返回可供数与 API 路径)")
  @RequiresPermission("mdm:update")
  @PostMapping("/{id}/execute")
  public Result<DistributionResult> execute(
      @PathVariable("id") Long id, HttpServletRequest httpRequest) {
    return Result.success(service.execute(id, currentUserProvider.getCurrentUser(httpRequest)));
  }

  @Operation(summary = "分发 API 发布态(未发布时 published=false,数据服务未启用时 available=false)")
  @RequiresPermission("mdm:read")
  @GetMapping("/{id}/publication")
  public Result<PublicationVO> publication(@PathVariable("id") Long id) {
    MdmDistribution config = service.get(id);
    return Result.success(
        PublicationVO.from(config, publishService.currentView(config), publishService.available()));
  }

  /** 分发 API 发布态视图(路径/启停/鉴权模式透出,引导用户去数据服务页配 API Key)。 */
  public record PublicationVO(
      boolean available,
      boolean published,
      Long apiId,
      String name,
      String path,
      Boolean enabled,
      String authMode) {

    static PublicationVO from(
        MdmDistribution config,
        java.util.Optional<io.yak.ops.business.dataservice.query.DataServiceView> view,
        boolean available) {
      return new PublicationVO(
          available,
          view.isPresent(),
          view.map(io.yak.ops.business.dataservice.query.DataServiceView::id).orElse(null),
          view.map(io.yak.ops.business.dataservice.query.DataServiceView::name)
              .orElse("主数据分发-" + config.entityId() + "-" + config.targetSystem()),
          view.map(io.yak.ops.business.dataservice.query.DataServiceView::path).orElse(null),
          view.map(io.yak.ops.business.dataservice.query.DataServiceView::enabled).orElse(null),
          view.map(io.yak.ops.business.dataservice.query.DataServiceView::authMode).orElse(null));
    }
  }

  /** 分发配置视图。 */
  public record DistributionVO(
      Long id,
      Long entityId,
      String targetSystem,
      String targetName,
      String distributeMode,
      String distributeFreq,
      String distributeScope,
      String status,
      LocalDateTime lastDistributeTime,
      Integer lastDistributeCount,
      Integer lastDistributeFail,
      LocalDateTime createTime) {

    public static DistributionVO from(MdmDistribution d) {
      return new DistributionVO(
          d.id(), d.entityId(), d.targetSystem(), d.targetName(),
          d.mode() == null ? null : d.mode().name(),
          d.frequency(), d.scope(),
          d.status() == null ? null : d.status().name(),
          d.lastDistributeTime(), d.lastDistributeCount(), d.lastDistributeFail(),
          d.createTime());
    }
  }
}
