package io.yak.ops.business.lifecycle.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.lifecycle.dao.mapper.LifecyclePolicyMapper;
import io.yak.ops.business.lifecycle.dao.mapper.LifecyclePolicyVersionMapper;
import io.yak.ops.business.lifecycle.exception.LifecycleException;
import io.yak.ops.business.lifecycle.dao.model.LifecyclePolicyPO;
import io.yak.ops.business.lifecycle.dao.model.LifecyclePolicyVersionPO;
import io.yak.ops.common.enums.PublishState;
import io.yak.ops.common.enums.lifecycle.LifecycleErrorCode;
import io.yak.ops.common.util.AuditDiffs;
import io.yak.ops.common.version.VersionDigests;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TTL 策略发布/版本/回滚（W1-1，多版本契约 C2/C3）。
 *
 * <p>口径：主表列=可编辑草稿；{@code yak_lc_policy_version}=发布全量快照(append-only)；
 * 消费方（绑定/监控/下发）经 {@link #effective} 只读发布快照内容。
 * 发布幂等=草稿快照与最新发布版本**语义相等**（回填行的 checksum 口径可能异于 Java 计算值，
 * 因此等值判断不依赖 checksum）。
 */
@Service
@RequiredArgsConstructor
public class TtlPolicyVersionService {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final TypeReference<LinkedHashMap<String, Object>> PAYLOAD_TYPE =
      new TypeReference<>() {};

  private final CurrentProject currentProject;
  private final LifecyclePolicyMapper policyMapper;
  private final LifecyclePolicyVersionMapper versionMapper;
  private final BusinessAuditService auditService;

  /** 发布结果：version=当前发布版本，appended=本次是否产生了新版本行。 */
  public record PublishResult(VersionSummary version, boolean appended) {}

  /** 版本行摘要（GET /policies/{id}/versions）。 */
  public record VersionSummary(
      Long id, int versionNo, String checksum, String createdBy,
      LocalDateTime createTime, boolean current) {}

  /** 版本详情：payload 为快照内容 Map（前端 diff 数据源）。 */
  public record VersionDetailView(VersionSummary version, Map<String, Object> payload) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public PublishResult publish(Long id, String operator) {
    LifecyclePolicyPO po = requirePolicy(id);
    return publishLoaded(po, draftSnapshot(po), operator, "TTL_POLICY_PUBLISH", "发布");
  }

  /** 下线=退出生效（保留发布指针，可再发布/回滚）。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void offline(Long id, String operator) {
    LifecyclePolicyPO po = requirePolicy(id);
    if (!PublishState.PUBLISHED.name().equals(po.getPublishState())) {
      throw new LifecycleException(LifecycleErrorCode.INVALID_RETENTION, "仅已发布策略可下线");
    }
    po.setPublishState(PublishState.OFFLINE.name());
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    policyMapper.updateById(po);
    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        "TTL_POLICY_OFFLINE", "Offline TTL policy", "TTL_POLICY",
        String.valueOf(po.getId()), po.getPolicyCode(), "APPLICATION",
        Map.of("id", po.getId())));
    AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
        "下线 TTL 策略 " + po.getPolicyCode(),
        Map.of("id", po.getId(), "versionNo", Objects.requireNonNullElse(po.getLatestVersionNo(), 0)),
        null);
  }

  public List<VersionSummary> versions(Long id) {
    LifecyclePolicyPO po = requirePolicy(id);
    List<LifecyclePolicyVersionPO> rows = versionMapper.selectList(
        new LambdaQueryWrapper<LifecyclePolicyVersionPO>()
            .eq(LifecyclePolicyVersionPO::getPolicyId, po.getId())
            .orderByDesc(LifecyclePolicyVersionPO::getVersionNo));
    List<VersionSummary> views = new ArrayList<>();
    rows.forEach(row -> views.add(toSummary(row, po.getPublishedVersionId())));
    return views;
  }

  public VersionDetailView versionDetail(Long id, int versionNo) {
    LifecyclePolicyPO po = requirePolicy(id);
    LifecyclePolicyVersionPO row = requireVersion(po.getId(), versionNo);
    return new VersionDetailView(toSummary(row, po.getPublishedVersionId()), parsePayload(row));
  }

  /** 回滚=用 v{n} 内容覆盖草稿并立即发布（追加式 activate，不抹历史）。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public PublishResult rollback(Long id, int versionNo, String operator) {
    LifecyclePolicyPO po = requirePolicy(id);
    LifecyclePolicyVersionPO target = requireVersion(po.getId(), versionNo);
    Map<String, Object> restored = parsePayload(target);
    Map<String, Object> beforeDraft = draftSnapshot(po);

    applySnapshot(po, restored);
    po.setDraftRevision(Objects.requireNonNullElse(po.getDraftRevision(), 0) + 1);
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    policyMapper.updateById(po);

    PublishResult result =
        publishLoaded(po, restored, operator, "TTL_POLICY_ROLLBACK", "回滚");
    // 回滚把当前草稿覆盖为历史内容——审计里同时留被丢弃的草稿，保证可追溯。
    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        "TTL_POLICY_ROLLBACK_DRAFT", "Rollback overwrote draft", "TTL_POLICY",
        String.valueOf(po.getId()), po.getPolicyCode(), "APPLICATION",
        Map.of("discardedDraft", beforeDraft)));
    AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
        "回滚覆盖了回滚前的草稿内容 " + po.getPolicyCode(),
        Map.of("id", po.getId(),
            "diff", AuditDiffs.diff(beforeDraft, restored)),
        null);
    return result;
  }

  // ---------- 消费方读路径（生效内容 = 发布快照覆盖草稿列） ----------

  /** 单条生效策略；未发布/已下线返回 null（消费方按"无策略"处理）。 */
  public LifecyclePolicyPO effective(LifecyclePolicyPO po) {
    if (!isPublishedActive(po) || po.getPublishedVersionId() == null) {
      return null;
    }
    LifecyclePolicyVersionPO row = versionMapper.selectById(po.getPublishedVersionId());
    if (row == null) {
      return null;
    }
    return overlay(po, parsePayload(row));
  }

  /** 发布态且启用开关开启才可生效。 */
  private boolean isPublishedActive(LifecyclePolicyPO po) {
    return PublishState.PUBLISHED.matches(po.getPublishState())
        && !"DISABLED".equals(po.getStatus());
  }

  /** 当前发布快照内容（视图层判定"有未发布修改"用）；未发布返回 null。 */
  public Map<String, Object> publishedSnapshot(LifecyclePolicyPO po) {
    if (po.getPublishedVersionId() == null) {
      return null;
    }
    LifecyclePolicyVersionPO row = versionMapper.selectById(po.getPublishedVersionId());
    return row == null ? null : parsePayload(row);
  }

  public boolean hasPendingDraft(LifecyclePolicyPO po) {
    Map<String, Object> published = publishedSnapshot(po);
    return published == null || !snapshotsEqual(draftSnapshot(po), published);
  }

  // ---------- internal ----------

  private PublishResult publishLoaded(
      LifecyclePolicyPO po, Map<String, Object> snapshot,
      String operator, String auditType, String auditLabelCn) {
    Map<String, Object> beforePublished = publishedSnapshot(po);
    LifecyclePolicyVersionPO latest = versionMapper.selectOne(
        new LambdaQueryWrapper<LifecyclePolicyVersionPO>()
            .eq(LifecyclePolicyVersionPO::getPolicyId, po.getId())
            .orderByDesc(LifecyclePolicyVersionPO::getVersionNo)
            .last("LIMIT 1"));

    LifecyclePolicyVersionPO versionRow;
    boolean appended = false;
    if (latest != null && snapshotsEqual(snapshot, parsePayload(latest))) {
      versionRow = latest; // 幂等：内容未变不追加版本，仅恢复发布态
    } else {
      int versionNo = versionMapper.nextVersionNo(po.getId());
      String payloadJson = VersionDigests.canonicalJson(snapshot);
      versionRow = new LifecyclePolicyVersionPO();
      versionRow.setPolicyId(po.getId());
      versionRow.setProjectId(po.getProjectId());
      versionRow.setVersionNo(versionNo);
      versionRow.setPayloadJson(payloadJson);
      versionRow.setChecksum(VersionDigests.sha256Hex(payloadJson));
      versionRow.setSourceDraftRevision(po.getDraftRevision());
      versionRow.setCreatedBy(operator);
      versionMapper.insert(versionRow);
      appended = true;
    }

    po.setPublishState(PublishState.PUBLISHED.name());
    po.setPublishedVersionId(versionRow.getId());
    po.setLatestVersionNo(versionRow.getVersionNo());
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    policyMapper.updateById(po);

    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        auditType, auditLabelCn + " TTL policy", "TTL_POLICY",
        String.valueOf(po.getId()), po.getPolicyCode(), "APPLICATION",
        Map.of("versionNo", versionRow.getVersionNo())));
    AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
        auditLabelCn + " TTL 策略 " + po.getPolicyCode() + " v" + versionRow.getVersionNo(),
        Map.of("id", po.getId(),
            "versionNo", versionRow.getVersionNo(),
            "appended", appended,
            "diff", AuditDiffs.diff(beforePublished, snapshot)),
        null);
    return new PublishResult(toSummary(versionRow, versionRow.getId()), appended);
  }

  private LifecyclePolicyPO requirePolicy(Long id) {
    LifecyclePolicyPO po = policyMapper.selectOne(new LambdaQueryWrapper<LifecyclePolicyPO>()
        .eq(LifecyclePolicyPO::getProjectId, currentProject.requireProjectId())
        .eq(LifecyclePolicyPO::getId, id)
        .eq(LifecyclePolicyPO::getDeleted, false));
    if (po == null) {
      throw new LifecycleException(LifecycleErrorCode.POLICY_NOT_FOUND, "id=" + id);
    }
    return po;
  }

  private LifecyclePolicyVersionPO requireVersion(Long policyId, int versionNo) {
    LifecyclePolicyVersionPO row = versionMapper.selectOne(
        new LambdaQueryWrapper<LifecyclePolicyVersionPO>()
            .eq(LifecyclePolicyVersionPO::getPolicyId, policyId)
            .eq(LifecyclePolicyVersionPO::getVersionNo, versionNo));
    if (row == null) {
      throw new LifecycleException(LifecycleErrorCode.POLICY_VERSION_NOT_FOUND,
          "policyId=" + policyId + ", versionNo=" + versionNo);
    }
    return row;
  }

  /** 快照字段清单=策略可编辑内容全集。 */
  static Map<String, Object> draftSnapshot(LifecyclePolicyPO po) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("policyCode", po.getPolicyCode());
    snapshot.put("policyName", po.getPolicyName());
    snapshot.put("scopeType", po.getScopeType());
    snapshot.put("layerCode", po.getLayerCode());
    snapshot.put("partitionGranularity", po.getPartitionGranularity());
    snapshot.put("hotDays", po.getHotDays());
    snapshot.put("coldDays", po.getColdDays());
    snapshot.put("destroyDays", po.getDestroyDays());
    snapshot.put("remark", po.getRemark());
    return snapshot;
  }

  @SuppressWarnings("unchecked")
  private static void applySnapshot(LifecyclePolicyPO po, Map<String, Object> snapshot) {
    po.setPolicyName((String) snapshot.get("policyName"));
    po.setPartitionGranularity((String) snapshot.get("partitionGranularity"));
    po.setHotDays(asInt(snapshot.get("hotDays")));
    po.setColdDays(asInt(snapshot.get("coldDays")));
    po.setDestroyDays(asInt(snapshot.get("destroyDays")));
    po.setRemark((String) snapshot.get("remark"));
  }

  private static Integer asInt(Object value) {
    return value == null ? null : ((Number) value).intValue();
  }

  private static Map<String, Object> parsePayload(LifecyclePolicyVersionPO row) {
    try {
      return JSON.readValue(row.getPayloadJson(), PAYLOAD_TYPE);
    } catch (Exception e) {
      throw new IllegalStateException(
          "策略版本快照反序列化失败 versionId=" + row.getId(), e);
    }
  }

  /** 语义等值：值统一转字符串比较（Integer/Long/null 口径差异在此吸收）。 */
  static boolean snapshotsEqual(Map<String, Object> a, Map<String, Object> b) {
    return normalize(a).equals(normalize(b));
  }

  private static Map<String, String> normalize(Map<String, Object> in) {
    Map<String, String> out = new LinkedHashMap<>();
    in.forEach((key, value) -> out.put(key, value == null ? null : String.valueOf(value)));
    return out;
  }

  private LifecyclePolicyPO overlay(LifecyclePolicyPO po, Map<String, Object> snapshot) {
    LifecyclePolicyPO effective = new LifecyclePolicyPO();
    org.springframework.beans.BeanUtils.copyProperties(po, effective);
    applySnapshot(effective, snapshot);
    return effective;
  }

  private VersionSummary toSummary(LifecyclePolicyVersionPO row, Long publishedVersionId) {
    return new VersionSummary(row.getId(), row.getVersionNo(), row.getChecksum(),
        row.getCreatedBy(), row.getCreateTime(), Objects.equals(row.getId(), publishedVersionId));
  }
}
