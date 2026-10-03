package io.yak.ops.business.asset.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.ItemQueryDTO;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.ManualRegisterDTO;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.common.enums.asset.AssetStatus;
import io.yak.ops.common.enums.asset.AssetSourceType;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/** 资产台账 CRUD + 手工登记 + 统一负责人(ticket 91)。台账是目录卡片而非事实源(D1)。 */
@Service
@RequiredArgsConstructor
public class AssetAppService {

  private static final Set<String> DELETABLE_STATUS =
      Set.of(AssetStatus.OFFLINE.name(), AssetStatus.SOURCE_GONE.name());

  /**
   * 手工登记无源域对象(MANUAL 不参与对账),不存在"源域仍存在"的误删风险;未上架即可撤销登记。
   * 已上架仍须先下架(保留下架原因留痕)。
   */
  private static final Set<String> MANUAL_DELETABLE_STATUS =
      Set.of(AssetStatus.PENDING.name(), AssetStatus.OFFLINE.name(), AssetStatus.IGNORED.name());

  private final CurrentProject currentProject;
  private final AssetItemMapper itemMapper;
  private final BusinessAuditService auditService;

  /** 台账视图(360° 分区聚合在 ticket 97 扩展)。 */
  public record AssetView(
      Long id,
      String assetKey,
      String sourceType,
      String sourceId,
      String assetType,
      String name,
      String description,
      String layerCode,
      String domainCode,
      Long directoryId,
      String owner,
      String status,
      String securityLevelCode,
      Integer healthScore,
      String healthGrade,
      Integer viewCount30d,
      String accessUri,
      LocalDateTime sourceUpdatedAt,
      LocalDateTime firstListedAt,
      LocalDateTime lastListedAt,
      LocalDateTime lastOfflineAt,
      String lastOfflineReason,
      LocalDateTime reconciledAt,
      LocalDateTime createTime,
      LocalDateTime updateTime) {}

  /**
   * 台账分页/搜索(ticket 97):多选筛选取交集,标签经 tag_rel 反查;
   * 默认按 design §6.5 固定公式排序(健康×活跃度加权,零跨域调用),切列时放弃公式分。
   */
  public PageData<AssetView> page(ItemQueryDTO query) {
    Long projectId = currentProject.requireProjectId();
    List<String> assetTypes = upperList(query.getAssetTypes());
    List<String> layerCodes = upperList(query.getLayerCodes());
    List<String> grades = upperList(query.getGrades());
    LambdaQueryWrapper<AssetItemPO> wrapper = new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, projectId)
        .eq(AssetItemPO::getDeleted, false)
        .eq(StringUtils.hasText(query.getStatus()), AssetItemPO::getStatus, query.getStatus())
        .in(!CollectionUtils.isEmpty(query.getStatuses()), AssetItemPO::getStatus,
            query.getStatuses())
        .eq(StringUtils.hasText(query.getAssetType()), AssetItemPO::getAssetType,
            query.getAssetType())
        .in(!assetTypes.isEmpty(), AssetItemPO::getAssetType, assetTypes)
        .in(!layerCodes.isEmpty(), AssetItemPO::getLayerCode, layerCodes)
        .in(!grades.isEmpty(), AssetItemPO::getHealthGrade, grades)
        .eq(StringUtils.hasText(query.getSourceType()), AssetItemPO::getSourceType,
            query.getSourceType())
        .eq(StringUtils.hasText(query.getOwner()), AssetItemPO::getOwner, query.getOwner())
        .eq(query.getDirectoryId() != null, AssetItemPO::getDirectoryId, query.getDirectoryId())
        .and(StringUtils.hasText(query.getKeyword()), w -> w
            .like(AssetItemPO::getName, query.getKeyword())
            .or().like(AssetItemPO::getAssetKey, query.getKeyword()));
    List<Long> tagIds = query.getTagIds() == null ? List.of()
        : query.getTagIds().stream().filter(Objects::nonNull).distinct().toList();
    if (!tagIds.isEmpty()) {
      wrapper.inSql(AssetItemPO::getId,
          "SELECT asset_id FROM yak_asset_tag_rel WHERE project_id = " + projectId
              + " AND tag_id IN (" + tagIds.stream().map(String::valueOf)
                  .collect(Collectors.joining(",")) + ")");
    }
    applySort(wrapper, query.getSortBy());
    Page<AssetItemPO> result = itemMapper.selectPage(new Page<>(query.getPageNo(),
        query.getPageSize()), wrapper);
    List<AssetView> views = result.getRecords().stream().map(AssetAppService::toView).toList();
    return new PageData<>(views, result.getTotal(), result.getPages(),
        (int) result.getCurrent(), (int) result.getSize());
  }

  static final String DEFAULT_SCORE_ORDER =
      "ORDER BY COALESCE(health_score,0)*0.6 + LEAST(40, LOG2(1+COALESCE(view_count_30d,0))*8)*0.4"
          + " DESC, id DESC";

  private void applySort(LambdaQueryWrapper<AssetItemPO> wrapper, String sortBy) {
    if (!StringUtils.hasText(sortBy)) {
      wrapper.last(DEFAULT_SCORE_ORDER);
      return;
    }
    switch (sortBy.trim().toUpperCase()) {
      case "TIME" -> wrapper.orderByDesc(AssetItemPO::getUpdateTime).orderByDesc(AssetItemPO::getId);
      case "VIEWS" -> wrapper.orderByDesc(AssetItemPO::getViewCount30d)
          .orderByDesc(AssetItemPO::getId);
      case "NAME" -> wrapper.orderByAsc(AssetItemPO::getName).orderByAsc(AssetItemPO::getId);
      default -> throw new AssetException(AssetErrorCode.INVALID_ARGUMENT,
          "排序仅支持 TIME/VIEWS/NAME(默认健康×活跃度)");
    }
  }

  private static List<String> upperList(List<String> values) {
    return values == null ? List.of()
        : values.stream().filter(StringUtils::hasText).map(v -> v.trim().toUpperCase()).toList();
  }

  public AssetView get(Long id) {
    return toView(requireItem(id));
  }

  /** 手工登记:键=manual:{code}(code 空自动生成),登记即 PENDING,owner 默认当前用户。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public AssetView registerManual(ManualRegisterDTO dto, String operator) {
    Long projectId = currentProject.requireProjectId();
    String code;
    if (StringUtils.hasText(dto.getAssetCode())) {
      code = dto.getAssetCode().trim().toLowerCase();
      if (findByKey(projectId, "manual:" + code) != null) {
        throw new AssetException(AssetErrorCode.DUPLICATE_ASSET_KEY, "manual:" + code);
      }
    } else {
      code = generateCode(projectId);
    }
    String assetType = parseAssetType(dto.getAssetType());

    AssetItemPO po = new AssetItemPO();
    po.setProjectId(projectId);
    po.setAssetKey("manual:" + code);
    po.setSourceType(AssetSourceType.MANUAL.name());
    po.setSourceId(code);
    po.setAssetType(assetType);
    po.setName(dto.getName().trim());
    po.setDescription(dto.getDescription());
    po.setLayerCode(upperOrNull(dto.getLayerCode()));
    po.setDomainCode(upperOrNull(dto.getDomainCode()));
    po.setDirectoryId(dto.getDirectoryId());
    po.setOwner(StringUtils.hasText(dto.getOwner()) ? dto.getOwner().trim() : operator);
    po.setStatus(AssetStatus.PENDING.name());
    po.setAccessUri(dto.getAccessUri());
    po.setViewCount30d(0);
    po.setCreatedBy(operator);
    po.setUpdatedBy(operator);
    po.setDeleted(false);
    try {
      itemMapper.insert(po);
    } catch (DuplicateKeyException e) {
      throw new AssetException(AssetErrorCode.DUPLICATE_ASSET_KEY, po.getAssetKey());
    }

    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        "ASSET_MANUAL_CREATE", "Register manual asset", "ASSET",
        String.valueOf(po.getId()), po.getAssetKey(), "APPLICATION",
        Map.of("assetType", assetType)));
    AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_CREATED,
        "手工登记资产 " + po.getName(),
        Map.of("id", String.valueOf(po.getId()), "assetKey", po.getAssetKey()), null);
    return toView(po);
  }

  /** 编辑快照字段(名称/描述/访问入口)。编辑权在资产中心(D1)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public AssetView updateSnapshot(Long id, String name, String description, String accessUri,
      String operator) {
    AssetItemPO po = requireItem(id);
    if (StringUtils.hasText(name)) {
      po.setName(name.trim());
    }
    if (description != null) {
      po.setDescription(description.trim());
    }
    if (accessUri != null) {
      po.setAccessUri(accessUri.trim());
    }
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    itemMapper.updateById(po);

    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        "ASSET_UPDATE", "Update asset snapshot", "ASSET",
        String.valueOf(id), po.getAssetKey(), "APPLICATION", Map.of()));
    AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
        "编辑资产 " + po.getName(), Map.of("id", id), null);
    return toView(po);
  }

  /** 变更统一负责人(D4:负责人唯一事实源在资产中心)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public AssetView changeOwner(Long id, String owner, String operator) {
    AssetItemPO po = requireItem(id);
    po.setOwner(owner.trim());
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    itemMapper.updateById(po);

    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        "ASSET_OWNER", "Change asset owner", "ASSET",
        String.valueOf(id), po.getAssetKey(), "APPLICATION",
        Map.of("owner", po.getOwner())));
    AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_UPDATED,
        "资产 " + po.getName() + " 负责人 -> " + po.getOwner(), Map.of("id", id), null);
    return toView(po);
  }

  /**
   * 软删(48003):OFFLINE/SOURCE_GONE 可删;手工登记未上架(PENDING/OFFLINE/IGNORED)可撤销登记。
   */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id, String operator) {
    AssetItemPO po = requireItem(id);
    if (!isDeletable(po)) {
      throw new AssetException(AssetErrorCode.ILLEGAL_STATE_OPERATION,
          "仅已下架/源已消失(或未上架的手工登记)资产可删除,当前状态 " + po.getStatus());
    }
    po.setDeleted(true);
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    itemMapper.updateById(po);

    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        "ASSET_DELETE", "Delete asset", "ASSET",
        String.valueOf(id), po.getAssetKey(), "APPLICATION", Map.of()));
    AuditTransactions.completeOnCommit(audit, AuditEventType.RESOURCE_DELETED,
        "删除资产 " + po.getName(), Map.of("id", id), null);
  }

  // ---------- internal ----------

  static boolean isDeletable(AssetItemPO po) {
    if (DELETABLE_STATUS.contains(po.getStatus())) {
      return true;
    }
    return AssetSourceType.MANUAL.name().equals(po.getSourceType())
        && MANUAL_DELETABLE_STATUS.contains(po.getStatus());
  }

  AssetItemPO requireItem(Long id) {
    AssetItemPO po = itemMapper.selectOne(new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, currentProject.requireProjectId())
        .eq(AssetItemPO::getId, id)
        .eq(AssetItemPO::getDeleted, false));
    if (po == null) {
      throw new AssetException(AssetErrorCode.ASSET_NOT_FOUND, "id=" + id);
    }
    return po;
  }

  private String generateCode(Long projectId) {
    String base = "man_" + System.currentTimeMillis();
    String code = base;
    int suffix = 1;
    while (findByKey(projectId, "manual:" + code) != null) {
      code = base + "_" + suffix++;
    }
    return code;
  }

  private AssetItemPO findByKey(Long projectId, String assetKey) {
    return itemMapper.selectOne(new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, projectId)
        .eq(AssetItemPO::getAssetKey, assetKey)
        .eq(AssetItemPO::getDeleted, false)
        .last("LIMIT 1"));
  }

  static String parseAssetType(String assetType) {
    if (!StringUtils.hasText(assetType)) {
      return AssetType.DOC.name();
    }
    try {
      return AssetType.valueOf(assetType.trim().toUpperCase()).name();
    } catch (IllegalArgumentException e) {
      throw new AssetException(AssetErrorCode.INVALID_ARGUMENT,
          "资产类型仅支持 TABLE/METRIC/DATASET/DASHBOARD/CHART/TASK/DOC");
    }
  }

  private static String upperOrNull(String value) {
    return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
  }

  static AssetView toView(AssetItemPO po) {
    return new AssetView(po.getId(), po.getAssetKey(), po.getSourceType(), po.getSourceId(),
        po.getAssetType(), po.getName(), po.getDescription(), po.getLayerCode(),
        po.getDomainCode(), po.getDirectoryId(), po.getOwner(), po.getStatus(),
        po.getSecurityLevelCode(), po.getHealthScore(), po.getHealthGrade(),
        po.getViewCount30d(), po.getAccessUri(), po.getSourceUpdatedAt(),
        po.getFirstListedAt(), po.getLastListedAt(), po.getLastOfflineAt(),
        po.getLastOfflineReason(), po.getReconciledAt(), po.getCreateTime(),
        po.getUpdateTime());
  }
}
