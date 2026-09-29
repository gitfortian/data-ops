package io.yak.ops.business.mdm.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.mdm.api.MdmServiceApi;
import io.yak.ops.business.mdm.application.MdmServiceService;
import io.yak.ops.business.mdm.application.MdmServiceService.RecordView;
import io.yak.ops.business.mdm.domain.subscription.MdmSubscription;
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

/** 主数据服务端点(ticket 59):查询 API + 订阅管理。 */
@Tag(name = "主数据服务")
@RestController
@RequestMapping("/api/v1/mdm/service")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class MdmServiceController {

  private final MdmServiceService service;
  private final CurrentUserProvider currentUserProvider;

  public MdmServiceController(
      MdmServiceService service, CurrentUserProvider currentUserProvider) {
    this.service = service;
    this.currentUserProvider = currentUserProvider;
  }

  // ==== 查询 API ====

  @Operation(summary = "按 master_id 查询单条 ACTIVE 记录")
  @RequiresPermission("mdm:read")
  @GetMapping("/entity/{entityId}/record/{masterId}")
  public Result<RecordView> getRecord(
      @PathVariable("entityId") Long entityId,
      @PathVariable("masterId") String masterId) {
    return Result.success(service.getRecordByMasterId(entityId, masterId));
  }

  @Operation(summary = "条件搜索主数据记录(分页,仅 ACTIVE)")
  @RequiresPermission("mdm:read")
  @PostMapping("/entity/{entityId}/search")
  public Result<PagingData<RecordView>> search(
      @PathVariable("entityId") Long entityId,
      @RequestBody(required = false) MdmServiceApi.SearchRequest request) {
    int pageNo = request != null && request.pageNo() != null ? request.pageNo() : 1;
    int pageSize = request != null && request.pageSize() != null ? request.pageSize() : 20;
    String keyword = request != null ? request.keyword() : null;
    return Result.success(
        PagingData.from(service.searchRecords(entityId, keyword, pageNo, pageSize)));
  }

  // ==== 订阅管理 ====

  @Operation(summary = "按实体列出全部订阅")
  @RequiresPermission("mdm:read")
  @GetMapping("/subscription/entity/{entityId}")
  public Result<List<SubscriptionVO>> listSubscriptions(@PathVariable("entityId") Long entityId) {
    return Result.success(
        service.listSubscriptions(entityId).stream().map(this::toVO).toList());
  }

  @Operation(summary = "新建订阅")
  @RequiresPermission("mdm:create")
  @PostMapping("/subscription")
  public Result<SubscriptionVO> createSubscription(
      @RequestBody MdmServiceApi.SubscriptionSaveRequest request, HttpServletRequest httpRequest) {
    MdmSubscription created = service.createSubscription(
        request.entityId(), request.subscriberCode(), request.subscriberName(),
        request.notifyMode(), currentUserProvider.getCurrentUser(httpRequest));
    return Result.success(toVO(created));
  }

  @Operation(summary = "更新订阅")
  @RequiresPermission("mdm:update")
  @PutMapping("/subscription/{id}")
  public Result<Void> updateSubscription(
      @PathVariable("id") Long id,
      @RequestBody MdmServiceApi.SubscriptionUpdateRequest request) {
    service.updateSubscription(id, request.subscriberName(), request.notifyMode());
    return Result.success(null);
  }

  @Operation(summary = "切换订阅状态")
  @RequiresPermission("mdm:update")
  @PutMapping("/subscription/{id}/status")
  public Result<Void> setSubscriptionStatus(
      @PathVariable("id") Long id,
      @RequestBody MdmServiceApi.SubscriptionStatusRequest request) {
    service.setSubscriptionStatus(id, request.status());
    return Result.success(null);
  }

  @Operation(summary = "删除订阅")
  @RequiresPermission("mdm:delete")
  @DeleteMapping("/subscription/{id}")
  public Result<Void> deleteSubscription(@PathVariable("id") Long id) {
    service.deleteSubscription(id);
    return Result.success(null);
  }

  private SubscriptionVO toVO(MdmSubscription s) {
    return new SubscriptionVO(
        s.id(), s.entityId(), s.subscriberCode(), s.subscriberName(),
        s.notifyMode(), s.status(), s.createdBy(), s.createTime(),
        service.subscriberReachable(s.subscriberCode()));
  }

  /**
   * 订阅视图。{@code reachable}=订阅方编码能否解析成平台用户;false 表示这条订阅收得到记录
   * 变更事件但站内信送不出去(编码写错或用户已停用),页面必须显式提示,不能假装订阅成功。
   */
  public record SubscriptionVO(
      Long id,
      Long entityId,
      String subscriberCode,
      String subscriberName,
      String notifyMode,
      String status,
      String createdBy,
      LocalDateTime createTime,
      boolean reachable) {}
}
