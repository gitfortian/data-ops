package io.yak.ops.business.mdm.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.mdm.api.MdmApprovalApi;
import io.yak.ops.business.mdm.application.MdmApprovalService;
import io.yak.ops.business.mdm.application.MdmEntityService;
import io.yak.ops.business.mdm.domain.approval.MdmApprovalStatus;
import io.yak.ops.business.mdm.domain.approval.MdmChange;
import io.yak.ops.business.mdm.domain.approval.MdmChangeType;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.record.MdmRecordVersion;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主数据审批端点(R4,接审批中心):MDM 侧只提供提单/撤回/查询;
 * 通过与拒绝在审批中心待办完成,终态经 MdmChangeApprovalHandler 回调生效。
 */
@Tag(name = "主数据审批")
@RestController
@RequestMapping("/api/v1/mdm/approval")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class MdmApprovalController {

  private final MdmApprovalService service;
  private final CurrentUserProvider currentUserProvider;
  private final MdmEntityService entityService;

  public MdmApprovalController(
      MdmApprovalService service,
      CurrentUserProvider currentUserProvider,
      MdmEntityService entityService) {
    this.service = service;
    this.currentUserProvider = currentUserProvider;
    this.entityService = entityService;
  }

  @Operation(summary = "提交变更申请(自动生成审批中心 MDM_CHANGE 单)")
  @RequiresPermission("mdm:create")
  @PostMapping("/submit")
  public Result<ChangeVO> submit(
      @RequestBody MdmApprovalApi.SubmitRequest request,
      HttpServletRequest httpRequest) {
    String applicant = currentUserProvider.getCurrentUser(httpRequest);
    MdmChange created = service.submit(
        request.entityId(), request.masterId(),
        MdmChangeType.valueOf(request.changeType()),
        request.changeContent(), request.approvalLevel(), applicant);
    return Result.success(ChangeVO.from(created));
  }

  @Operation(summary = "撤回变更申请(转审批中心撤销,仅发起人可撤)")
  @RequiresPermission("mdm:update")
  @PutMapping("/{id}/withdraw")
  public Result<Void> withdraw(
      @PathVariable("id") Long id,
      HttpServletRequest httpRequest) {
    service.withdraw(id, currentUserProvider.getCurrentUser(httpRequest));
    return Result.success(null);
  }

  @Operation(summary = "审批详情")
  @RequiresPermission("mdm:read")
  @GetMapping("/{id}")
  public Result<ChangeVO> detail(@PathVariable("id") Long id) {
    return Result.success(ChangeVO.from(service.get(id)));
  }

  @Operation(summary = "审批列表(分页,可按状态/申请人筛选)")
  @RequiresPermission("mdm:read")
  @GetMapping
  public Result<PagingData<ChangeVO>> page(
      @RequestParam(value = "entityId", required = false) Long entityId,
      @RequestParam(value = "applicant", required = false) String applicant,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
    MdmApprovalStatus parsedStatus =
        status != null ? MdmApprovalStatus.valueOf(status) : null;
    PageData<MdmChange> page = service.page(entityId, applicant, parsedStatus, pageNo, pageSize);
    // 变更单只存 entityId;批量补一次实体名,列表不再显示裸 #id(MD5-01)。
    Map<Long, MdmEntity> entitiesById =
        page.records().stream()
            .map(MdmChange::entityId)
            .distinct()
            .collect(Collectors.toUnmodifiableMap(
                Function.identity(), this::findEntityOrNull));
    return Result.success(
        PagingData.from(
            page.map(change -> {
              MdmEntity entity = entitiesById.get(change.entityId());
              return ChangeVO.from(
                  change,
                  entity == null ? null : entity.name(),
                  entity == null ? null : entity.code());
            })));
  }

  /** 实体可能已被删除,查不到时名字留空,由前端回退 #id。 */
  private MdmEntity findEntityOrNull(Long entityId) {
    try {
      return entityService.get(entityId);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  @Operation(summary = "版本快照历史(按 master_id,全量属性快照供 v(n-1)/v(n) diff)")
  @RequiresPermission("mdm:read")
  @GetMapping("/entity/{entityId}/version/{masterId}")
  public Result<List<VersionVO>> versions(
      @PathVariable("entityId") Long entityId,
      @PathVariable("masterId") String masterId) {
    return Result.success(
        service.listVersions(entityId, masterId).stream().map(VersionVO::from).toList());
  }

  @Operation(summary = "变更申请历史(按 master_id,含在途/终态)")
  @RequiresPermission("mdm:read")
  @GetMapping("/entity/{entityId}/changes/{masterId}")
  public Result<List<ChangeVO>> changeHistory(
      @PathVariable("entityId") Long entityId,
      @PathVariable("masterId") String masterId) {
    return Result.success(
        service.listVersionHistory(entityId, masterId).stream()
            .map(ChangeVO::from).toList());
  }

  /** 变更审批视图。 */
  public record ChangeVO(
      Long id,
      Long entityId,
      String entityName,
      String entityCode,
      String masterId,
      String changeType,
      String changeContent,
      int approvalLevel,
      String approvalStatus,
      String applicant,
      String approver,
      String approvalComment,
      LocalDateTime approvalTime,
      Long instanceId,
      LocalDateTime createTime) {

    public static ChangeVO from(MdmChange c) {
      return from(c, null, null);
    }

    public static ChangeVO from(MdmChange c, String entityName, String entityCode) {
      return new ChangeVO(
          c.id(), c.entityId(), entityName, entityCode, c.masterId(),
          c.changeType().name(), c.changeContent(), c.approvalLevel(),
          c.approvalStatus().name(),
          c.applicant(), c.approver(), c.approvalComment(),
          c.approvalTime(), c.instanceId(), c.createTime());
    }
  }

  /** 记录版本快照视图。 */
  public record VersionVO(
      Long id,
      Long entityId,
      String masterId,
      int version,
      String attributes,
      String status,
      Long changeId,
      String operator,
      LocalDateTime createTime) {

    public static VersionVO from(MdmRecordVersion v) {
      return new VersionVO(
          v.id(), v.entityId(), v.masterId(), v.version(), v.attributes(),
          v.status().name(), v.changeId(), v.operator(), v.createTime());
    }
  }
}
