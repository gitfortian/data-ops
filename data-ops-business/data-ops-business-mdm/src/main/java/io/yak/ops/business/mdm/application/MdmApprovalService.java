package io.yak.ops.business.mdm.application;

import io.yak.framework.common.PageData;
import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.approval.api.ApprovalSubmitCommand;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.mdm.domain.approval.MdmApprovalStatus;
import io.yak.ops.business.mdm.domain.approval.MdmChange;
import io.yak.ops.business.mdm.domain.approval.MdmChangeType;
import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import io.yak.ops.business.mdm.domain.clean.CleanJson;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.record.MdmRecordVersion;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmAttributeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmChangeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordVersionRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 主数据变更审批(R4,接通用审批中心,MDM 不自建推进引擎):
 * 提交 = 落 {@code yak_mdm_change}(业务真相)+ 发起审批中心 MDM_CHANGE 单
 * (两级推进/审批人配置/在途唯一/仅发起人可撤销均由中心负责);
 * 终态生效走 {@link MdmChangeEffectService}(handler 只依赖仓储,避免
 * ApprovalService → Registry → handler → 本服务的 bean 循环)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MdmApprovalService {

  public static final String BIZ_TYPE = "MDM_CHANGE";
  static final String PAYLOAD_CHANGE_ID = "changeId";
  static final String PAYLOAD_ENTITY_ID = "entityId";
  static final String PAYLOAD_ENTITY_CODE = "entityCode";
  static final String PAYLOAD_MASTER_ID = "masterId";
  static final String PAYLOAD_CHANGE_TYPE = "changeType";
  /** Clear reviewed overrides and resume source refresh for the listed fields. */
  public static final String USE_SOURCE_FIELDS = "$useSource";

  /** 中心 payload 上限 64KB,留出包装字段余量。 */
  private static final int MAX_CHANGE_CONTENT_LENGTH = 60_000;

  private final MdmChangeRepository changeRepository;
  private final MdmRecordVersionRepository versionRepository;
  private final MdmEntityService entityService;
  private final MdmAttributeRepository attributeRepository;
  private final BusinessAuditService auditService;
  private final ApprovalApi approvalApi;

  // ==== 变更申请(提交即进审批中心,不再有 MDM 侧 approve/reject) ====

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MdmChange submit(
      Long entityId, String masterId, MdmChangeType changeType,
      String changeContent, Integer approvalLevel, String applicant) {
    MdmEntity entity = entityService.get(entityId);
    validateSubmission(entityId, masterId, changeType, changeContent);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_CHANGE_SUBMIT",
                "Submit change request",
                "MDM_CHANGE",
                null,
                masterId,
                "APPLICATION",
                Map.of("entityId", String.valueOf(entityId),
                    "changeType", changeType.name())));
    try {
      int level = (approvalLevel != null && approvalLevel == MdmChange.LEVEL_TWO)
          ? MdmChange.LEVEL_TWO : MdmChange.LEVEL_ONE;
      MdmChange inserted = changeRepository.insert(
          new MdmChange(
              null, entityId, masterId, changeType, changeContent, level,
              MdmApprovalStatus.PENDING, applicant, null, null, null, null, null, null));
      ApprovalInstanceView instance = approvalApi.submit(new ApprovalSubmitCommand(
          ApprovalFlowCodes.MDM_CHANGE, BIZ_TYPE, String.valueOf(inserted.id()),
          "主数据变更申请:" + entity.code() + " · " + changeType.name(),
          buildPayload(entity, inserted), applicant));
      MdmChange attached = inserted.withInstance(instance.id());
      if (!changeRepository.update(attached)) {
        throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "关联审批单失败");
      }
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_CREATED, "Change submitted",
          Map.of(), "Change submitted");
      return attached;
    } catch (RuntimeException exception) {
      audit.failure("MDM_CHANGE_SUBMIT_FAILED", exception);
      throw exception;
    }
  }

  /** 申请人撤回:操作人必须服务端可信上下文取得,撤销动作转中心(校验仅发起人可撤)。 */
  public void withdraw(Long changeId, String operator) {
    MdmChange change = requirePending(changeId, "撤回");
    if (change.instanceId() == null) {
      throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "变更未关联审批单,无法撤回");
    }
    approvalApi.cancel(change.instanceId(), operator, "MDM 申请人撤回");
  }

  // ==== 查询 ====

  public MdmChange get(Long id) {
    return changeRepository.findById(id)
        .orElseThrow(() -> new MdmException(MdmErrorCode.APPROVAL_FAILED,
            "变更不存在: " + id));
  }

  public PageData<MdmChange> page(
      Long entityId, String applicant, MdmApprovalStatus status, int pageNo, int pageSize) {
    return changeRepository.page(entityId, applicant, status, pageNo, Math.min(pageSize, 200));
  }

  /** 版本历史:按 master_id 的全量快照序列(v(n-1)/v(n) diff 数据源)。 */
  public List<MdmRecordVersion> listVersions(Long entityId, String masterId) {
    return versionRepository.listByMaster(entityId, masterId);
  }

  /** 变更申请历史(按 master_id,含在途/终态)。 */
  public List<MdmChange> listVersionHistory(Long entityId, String masterId) {
    return changeRepository.listByMaster(entityId, masterId);
  }

  /** 待审批数(总览卡片)。 */
  public long countPending(Long entityId) {
    return changeRepository.countPending(entityId);
  }

  // ==== 提单校验 ====

  private MdmChange requirePending(Long changeId, String action) {
    MdmChange change = get(changeId);
    if (change.approvalStatus() != MdmApprovalStatus.PENDING) {
      throw new MdmException(MdmErrorCode.APPROVAL_FAILED,
          "只能" + action + " PENDING 状态变更,当前: " + change.approvalStatus());
    }
    return change;
  }

  private void validateSubmission(
      Long entityId, String masterId, MdmChangeType changeType, String changeContent) {
    if (!StringUtils.hasText(masterId)) {
      throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "master_id 不能为空");
    }
    if (changeType == null) {
      throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "变更类型不能为空");
    }
    // review P0-1.5:CREATE 无生效路径、MERGE 走清洗合并专用链路,均不允许经变更审批提单
    if (changeType != MdmChangeType.UPDATE && changeType != MdmChangeType.DELETE) {
      throw new MdmException(
          MdmErrorCode.CHANGE_TYPE_UNSUPPORTED,
          MdmErrorCode.CHANGE_TYPE_UNSUPPORTED.getMessage() + ": " + changeType.name());
    }
    if (!StringUtils.hasText(changeContent)) {
      throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "变更内容不能为空");
    }
    if (changeContent.length() > MAX_CHANGE_CONTENT_LENGTH) {
      throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "变更内容超长");
    }
    validateUpdateFields(entityId, changeType, changeContent);
    boolean inFlight = changeRepository.listByMaster(entityId, masterId).stream()
        .anyMatch(c -> c.approvalStatus() == MdmApprovalStatus.PENDING);
    if (inFlight) {
      throw new MdmException(MdmErrorCode.CHANGE_IN_FLIGHT);
    }
  }

  /** Only declared non-PK attributes may change; PK edits would split the row from master_id. */
  private void validateUpdateFields(Long entityId, MdmChangeType changeType, String changeContent) {
    if (changeType != MdmChangeType.UPDATE) {
      return;
    }
    Map<String, Object> content = CleanJson.readObject(changeContent);
    Object resetValue = content.get(USE_SOURCE_FIELDS);
    Set<String> resetFields;
    if (!content.containsKey(USE_SOURCE_FIELDS)) {
      resetFields = Set.of();
    } else if (resetValue instanceof List<?> values
        && values.stream().allMatch(String.class::isInstance)) {
      resetFields = values.stream().map(String.class::cast).collect(Collectors.toSet());
      if (resetFields.size() != values.size()) {
        throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "$useSource 属性不能重复");
      }
    } else {
      throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "$useSource 必须是属性编码数组");
    }
    Set<String> patchFields = content.keySet().stream()
        .filter(key -> !USE_SOURCE_FIELDS.equals(key)).collect(Collectors.toSet());
    if (patchFields.stream().anyMatch(resetFields::contains)) {
      throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "同一属性不能同时修改并恢复来源");
    }
    if (patchFields.isEmpty() && resetFields.isEmpty()) {
      throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "变更内容至少包含一个属性修改或来源恢复");
    }
    List<io.yak.ops.business.mdm.domain.attribute.MdmAttribute> attributes =
        attributeRepository.listByEntity(entityId);
    Set<String> allowed = attributes.stream().map(attribute -> attribute.code())
        .collect(Collectors.toSet());
    Set<String> requested = new java.util.HashSet<>(patchFields);
    requested.addAll(resetFields);
    if (!allowed.containsAll(requested)) {
      requested.removeAll(allowed);
      throw new MdmException(MdmErrorCode.APPROVAL_FAILED,
          "变更引用了未定义属性: " + String.join(", ", requested));
    }
    Set<String> pkCodes = attributes.stream()
        .filter(attribute -> attribute.type() == MdmAttributeType.PK)
        .map(attribute -> attribute.code())
        .collect(Collectors.toSet());
    for (String key : requested) {
      if (pkCodes.contains(key)) {
        throw new MdmException(
            MdmErrorCode.PK_CHANGE_FORBIDDEN,
            MdmErrorCode.PK_CHANGE_FORBIDDEN.getMessage() + ": " + key);
      }
    }
  }

  private String buildPayload(MdmEntity entity, MdmChange change) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(PAYLOAD_CHANGE_ID, change.id());
    payload.put(PAYLOAD_ENTITY_ID, change.entityId());
    payload.put(PAYLOAD_ENTITY_CODE, entity.code());
    payload.put(PAYLOAD_MASTER_ID, change.masterId());
    payload.put(PAYLOAD_CHANGE_TYPE, change.changeType().name());
    payload.put("changeContent", change.changeContent());
    return CleanJson.write(payload);
  }
}
