package io.yak.ops.business.mdm.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.mdm.api.MdmCollectApi;
import io.yak.ops.business.mdm.application.MdmCollectService;
import io.yak.ops.business.mdm.application.MdmCollectService.CollectStatus;
import io.yak.ops.business.mdm.controller.v1.vo.MdmCollectLinkVO;
import io.yak.ops.common.constant.mdm.MdmPermissionCode;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionVO;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 采集落地链路与状态(R1,sync 零改动复用). */
@Tag(name = "主数据采集落地接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mdm/collect")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MdmPermissionCode.READ)
public class MdmCollectController {

  private final MdmCollectService service;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "生成采集落地任务(幂等:已有链路直接返回)")
  @RequiresPermission(MdmPermissionCode.CREATE)
  @PostMapping("/links")
  public Result<MdmCollectLinkVO> generate(
      @Valid @RequestBody MdmCollectApi.LinkRequest request, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        MdmCollectLinkVO.from(
            service.generateLandingTask(
                request.sourceId(), request.sinkDatasourceId(), operator)));
  }

  @Operation(summary = "采集链路状态(含最近成功采集反查)")
  @GetMapping("/links")
  public Result<List<CollectStatus>> listStatus(
      @RequestParam(value = "entityId", required = false) Long entityId) {
    return Result.success(service.listStatus(entityId));
  }

  @Operation(summary = "手动触发一次落地运行(异步,返回受理的实例)")
  @RequiresPermission(MdmPermissionCode.CREATE)
  @PostMapping("/links/{sourceId}/run")
  public Result<OfflineJobExecutionVO> run(@PathVariable("sourceId") Long sourceId) {
    return Result.success(service.run(sourceId));
  }
}
