package io.yak.ops.business.asset.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.asset.health.HealthRecomputeService;
import io.yak.ops.common.bean.po.asset.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.common.enums.asset.AssetStatus;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 资产状态机(ticket 96,design 6.2):预检 → 上架(带 token,可带风险)/ 下架(必填原因)/
 * 忽略(仅 PENDING/OFFLINE,48016)。批量整单原子,一次操作一条审计。
 */
@Service
@RequiredArgsConstructor
public class AssetLifecycleService {

  /** 可上架的当前状态:待上架 / 重新上架 / 忽略中(上架即回归治理). 审批发起同口径. */
  public static final Set<String> PUBLISHABLE =
      Set.of(AssetStatus.PENDING.name(), AssetStatus.OFFLINE.name(), AssetStatus.IGNORED.name());
  /** 可忽略的状态(design 6.1):已上架须先下架(48016)。 */
  private static final Set<String> IGNORABLE =
      Set.of(AssetStatus.PENDING.name(), AssetStatus.OFFLINE.name());

  private final CurrentProject currentProject;
  private final AssetItemMapper itemMapper;
  private final PrecheckTokenService tokenService;
  private final BusinessAuditService auditService;
  private final HealthRecomputeService healthRecompute;

  /** 单资产缺口视图。gaps=阻断项;advisories=建议项(定级等,不阻断)。 */
  public record AssetGapView(
      Long assetId, String assetKey, String name, String status,
      List<String> gaps, List<String> advisories, Map<String, String> defaults) {}

  public record PrecheckResult(String token, long expiresIn, boolean allClear,
      List<AssetGapView> items) {}

  /** 预检:负责人/描述/目录为阻断缺口;定级仅建议。返回缺口清单 + 5 分钟 token。 */
  public PrecheckResult precheck(List<Long> assetIds, String operator) {
    Long projectId = currentProject.requireProjectId();
    List<AssetItemPO> items = loadForUpdate(projectId, assetIds);
    List<AssetGapView> views = new ArrayList<>();
    boolean allClear = true;
    for (AssetItemPO po : items) {
      requirePublishable(po);
      List<String> gaps = new ArrayList<>();
      Map<String, String> defaults = new LinkedHashMap<>();
      if (!StringUtils.hasText(po.getOwner())) {
        gaps.add("OWNER_MISSING");
        defaults.put("owner", operator);
      }
      if (!StringUtils.hasText(po.getDescription())) {
        gaps.add("DESCRIPTION_MISSING");
      }
      if (po.getDirectoryId() == null) {
        gaps.add("DIRECTORY_MISSING");
      }
      List<String> advisories = new ArrayList<>();
      if (!StringUtils.hasText(po.getSecurityLevelCode())) {
        advisories.add("SECURITY_LEVEL_SUGGESTED");
      }
      allClear &= gaps.isEmpty();
      views.add(new AssetGapView(po.getId(), po.getAssetKey(), po.getName(), po.getStatus(),
          gaps, advisories, defaults));
    }
    return new PrecheckResult(tokenService.issue(projectId, assetIds),
        PrecheckTokenService.WINDOW_SECONDS, allClear, views);
  }

  /** 上架:token 必带(48005);预检有缺口且未 acceptRisk 拒绝(48004)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int publish(List<Long> assetIds, String token, boolean acceptRisk, String operator) {
    Long projectId = currentProject.requireProjectId();
    tokenService.validate(projectId, assetIds, token);
    List<AssetItemPO> items = loadForUpdate(projectId, assetIds);
    List<String> risks = new ArrayList<>();
    for (AssetItemPO po : items) {
      requirePublishable(po);
      for (String gap : blockingGaps(po)) {
        risks.add(po.getId() + ":" + gap);
      }
    }
    if (!risks.isEmpty() && !acceptRisk) {
      throw new AssetException(AssetErrorCode.PRECHECK_FAILED, String.join(", ", risks));
    }
    LocalDateTime now = LocalDateTime.now();
    for (AssetItemPO po : items) {
      po.setStatus(AssetStatus.PUBLISHED.name());
      if (po.getFirstListedAt() == null) {
        po.setFirstListedAt(now);
      }
      po.setLastListedAt(now);
      po.setUpdatedBy(operator);
      po.setUpdateTime(now);
      itemMapper.updateById(po);
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("count", assetIds.size());
    if (!risks.isEmpty()) {
      // 带风险上架:风险项必须留在审计 detail(design 6.2)
      payload.put("riskItems", String.join(", ", risks));
    }
    audit("ASSET_PUBLISH", "Publish assets", assetIds, payload, AuditEventType.RESOURCE_UPDATED,
        "上架 " + items.size() + " 个资产" + (risks.isEmpty() ? "" : "(带风险项 " + risks.size() + ")"),
        operator);
    healthRecompute.recomputeItems(projectId, assetIds);
    return items.size();
  }

  /** 下架:原因必填(48013),仅 PUBLISHED 可下(48003)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int offline(List<Long> assetIds, String reason, String operator) {
    if (!StringUtils.hasText(reason)) {
      throw new AssetException(AssetErrorCode.OFFLINE_REASON_REQUIRED);
    }
    Long projectId = currentProject.requireProjectId();
    List<AssetItemPO> items = loadForUpdate(projectId, assetIds);
    LocalDateTime now = LocalDateTime.now();
    for (AssetItemPO po : items) {
      if (!AssetStatus.PUBLISHED.name().equals(po.getStatus())) {
        throw new AssetException(AssetErrorCode.ILLEGAL_STATE_OPERATION,
            "仅已上架资产可下架,id=" + po.getId() + " 当前 " + po.getStatus());
      }
      po.setStatus(AssetStatus.OFFLINE.name());
      po.setLastOfflineAt(now);
      po.setLastOfflineReason(reason.trim());
      po.setUpdatedBy(operator);
      po.setUpdateTime(now);
      itemMapper.updateById(po);
    }
    audit("ASSET_OFFLINE", "Take assets offline", assetIds,
        Map.of("count", assetIds.size(), "reason", reason.trim()),
        AuditEventType.RESOURCE_UPDATED, "下架 " + items.size() + " 个资产", operator);
    return items.size();
  }

  /** 忽略(对账抑制态):仅 PENDING/OFFLINE 可忽略(48016);对账不复活,指纹变化自动回 PENDING。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int ignore(List<Long> assetIds, String operator) {
    Long projectId = currentProject.requireProjectId();
    List<AssetItemPO> items = loadForUpdate(projectId, assetIds);
    for (AssetItemPO po : items) {
      if (!IGNORABLE.contains(po.getStatus())) {
        throw new AssetException(AssetErrorCode.NOT_IGNORABLE,
            "id=" + po.getId() + " 当前 " + po.getStatus());
      }
      po.setStatus(AssetStatus.IGNORED.name());
      po.setUpdatedBy(operator);
      po.setUpdateTime(LocalDateTime.now());
      itemMapper.updateById(po);
    }
    audit("ASSET_IGNORE", "Ignore assets", assetIds, Map.of("count", assetIds.size()),
        AuditEventType.RESOURCE_UPDATED, "忽略 " + items.size() + " 个资产", operator);
    return items.size();
  }

  // ---------- internal ----------

  private static List<String> blockingGaps(AssetItemPO po) {
    List<String> gaps = new ArrayList<>();
    if (!StringUtils.hasText(po.getOwner())) {
      gaps.add("OWNER");
    }
    if (!StringUtils.hasText(po.getDescription())) {
      gaps.add("DESCRIPTION");
    }
    if (po.getDirectoryId() == null) {
      gaps.add("DIRECTORY");
    }
    return gaps;
  }

  private static void requirePublishable(AssetItemPO po) {
    if (!PUBLISHABLE.contains(po.getStatus())) {
      throw new AssetException(AssetErrorCode.ILLEGAL_STATE_OPERATION,
          "id=" + po.getId() + " 当前状态 " + po.getStatus() + " 不可上架");
    }
  }

  /** 批量整单加载:任一 id 不在本项目/已删即 48001(原子语义)。 */
  private List<AssetItemPO> loadForUpdate(Long projectId, List<Long> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) {
      throw new AssetException(AssetErrorCode.INVALID_ARGUMENT, "assetIds 不能为空");
    }
    List<Long> distinct = assetIds.stream().distinct().toList();
    List<AssetItemPO> items = itemMapper.selectList(new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, projectId)
        .in(AssetItemPO::getId, distinct)
        .eq(AssetItemPO::getDeleted, false));
    if (items.size() != distinct.size()) {
      Set<Long> found = new HashSet<>();
      items.forEach(po -> found.add(po.getId()));
      List<Long> missing = distinct.stream().filter(id -> !found.contains(id)).toList();
      throw new AssetException(AssetErrorCode.ASSET_NOT_FOUND, "ids=" + missing);
    }
    return items;
  }

  private void audit(String code, String action, List<Long> assetIds, Map<String, Object> payload,
      AuditEventType type, String message, String operator) {
    AuditOperationHandle handle = auditService.start(new AuditOperationRequest(
        code, action, "ASSET",
        String.valueOf(assetIds.get(0)), "batch:" + assetIds.size(), "APPLICATION", payload));
    AuditTransactions.completeOnCommit(handle, type, message,
        Map.of("assetIds", assetIds.toString()), null);
  }
}
