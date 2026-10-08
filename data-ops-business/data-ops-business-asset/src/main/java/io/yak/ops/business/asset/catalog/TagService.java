package io.yak.ops.business.asset.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.dao.mapper.AssetTagMapper;
import io.yak.ops.business.asset.dao.mapper.AssetTagRelMapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.asset.health.HealthRecomputeService;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import io.yak.ops.business.asset.dao.model.AssetTagPO;
import io.yak.ops.business.asset.dao.model.AssetTagRelPO;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 业务标签字典 + 资产打标/去标(ticket 93)。 */
@Service
@RequiredArgsConstructor
public class TagService {

  private final CurrentProject currentProject;
  private final AssetTagMapper tagMapper;
  private final AssetTagRelMapper tagRelMapper;
  private final AssetItemMapper itemMapper;
  private final BusinessAuditService auditService;
  private final HealthRecomputeService healthRecompute;

  public record TagView(
      Long id, String tagCode, String tagName, String color, String description,
      long usageCount, LocalDateTime createTime) {}

  public List<TagView> list(String keyword) {
    Long projectId = currentProject.requireProjectId();
    List<AssetTagPO> tags = tagMapper.selectList(new LambdaQueryWrapper<AssetTagPO>()
        .eq(AssetTagPO::getProjectId, projectId)
        .eq(AssetTagPO::getDeleted, false)
        .and(StringUtils.hasText(keyword), w -> w
            .like(AssetTagPO::getTagName, keyword)
            .or().like(AssetTagPO::getTagCode, keyword))
        .orderByAsc(AssetTagPO::getId));
    return tags.stream().map(po -> toView(po, usageCount(projectId, po.getId()))).toList();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public TagView create(String tagCode, String tagName, String color, String description,
      String operator) {
    Long projectId = currentProject.requireProjectId();
    String code = StringUtils.hasText(tagCode)
        ? tagCode.trim().toLowerCase() : "tag_" + System.currentTimeMillis();
    if (codeExists(projectId, code)) {
      throw new AssetException(AssetErrorCode.DUPLICATE_TAG_CODE, code);
    }
    AssetTagPO po = new AssetTagPO();
    po.setProjectId(projectId);
    po.setTagCode(code);
    po.setTagName(tagName.trim());
    po.setColor(color);
    po.setDescription(description);
    po.setCreatedBy(operator);
    po.setUpdatedBy(operator);
    po.setDeleted(false);
    try {
      tagMapper.insert(po);
    } catch (DuplicateKeyException e) {
      throw new AssetException(AssetErrorCode.DUPLICATE_TAG_CODE, code);
    }
    audit("ASSET_TAG_CREATE", "Create asset tag", po.getId(), code, operator,
        AuditEventType.RESOURCE_CREATED, "新建标签 " + po.getTagName());
    return toView(po, 0);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public TagView update(Long id, String tagCode, String tagName, String color,
      String description, String operator) {
    Long projectId = currentProject.requireProjectId();
    AssetTagPO po = requireTag(projectId, id);
    if (StringUtils.hasText(tagCode) && !tagCode.trim().equalsIgnoreCase(po.getTagCode())) {
      String code = tagCode.trim().toLowerCase();
      if (codeExists(projectId, code)) {
        throw new AssetException(AssetErrorCode.DUPLICATE_TAG_CODE, code);
      }
      po.setTagCode(code);
    }
    if (StringUtils.hasText(tagName)) {
      po.setTagName(tagName.trim());
    }
    if (color != null) {
      po.setColor(color);
    }
    if (description != null) {
      po.setDescription(description);
    }
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    tagMapper.updateById(po);
    return toView(po, usageCount(projectId, id));
  }

  /** 删标签:关系物理删,字典软删。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id, String operator) {
    Long projectId = currentProject.requireProjectId();
    AssetTagPO po = requireTag(projectId, id);
    tagRelMapper.delete(new LambdaQueryWrapper<AssetTagRelPO>()
        .eq(AssetTagRelPO::getProjectId, projectId)
        .eq(AssetTagRelPO::getTagId, id));
    po.setDeleted(true);
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    tagMapper.updateById(po);
    audit("ASSET_TAG_DELETE", "Delete asset tag", id, po.getTagCode(), operator,
        AuditEventType.RESOURCE_DELETED, "删除标签 " + po.getTagName());
  }

  /** 批量打标(已存在的跳过,幂等)。返回新增关系数。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int attach(Long assetId, List<Long> tagIds, String operator) {
    Long projectId = currentProject.requireProjectId();
    requireAsset(projectId, assetId);
    int added = 0;
    for (Long tagId : tagIds) {
      requireTag(projectId, tagId);
      boolean exists = tagRelMapper.selectCount(relationScope(projectId, assetId)
          .eq(AssetTagRelPO::getTagId, tagId)) > 0;
      if (exists) {
        continue;
      }
      AssetTagRelPO rel = new AssetTagRelPO();
      rel.setProjectId(projectId);
      rel.setAssetId(assetId);
      rel.setTagId(tagId);
      rel.setCreatedBy(operator);
      rel.setCreateTime(LocalDateTime.now());
      try {
        tagRelMapper.insert(rel);
        added++;
      } catch (DuplicateKeyException e) {
        // 并发重复打标:幂等忽略
      }
    }
    if (added > 0) {
      audit("ASSET_TAG_ATTACH", "Tag asset", assetId, String.valueOf(assetId), operator,
          AuditEventType.RESOURCE_UPDATED, "打标 " + added + " 个");
      healthRecompute.recomputeItems(projectId, List.of(assetId));
    }
    return added;
  }

  /** 去标(幂等,不存在即 0)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int detach(Long assetId, Long tagId, String operator) {
    Long projectId = currentProject.requireProjectId();
    requireAsset(projectId, assetId);
    int deleted = tagRelMapper.delete(relationScope(projectId, assetId)
        .eq(AssetTagRelPO::getTagId, tagId));
    if (deleted > 0) {
      audit("ASSET_TAG_DETACH", "Untag asset", assetId, String.valueOf(assetId), operator,
          AuditEventType.RESOURCE_UPDATED, "去标 " + tagId);
      healthRecompute.recomputeItems(projectId, List.of(assetId));
    }
    return deleted;
  }

  /** 资产的标签清单(详情"业务标签"分区复用)。 */
  public List<TagView> tagsOfAsset(Long assetId) {
    Long projectId = currentProject.requireProjectId();
    List<AssetTagRelPO> rels = tagRelMapper.selectList(relationScope(projectId, assetId));
    if (rels.isEmpty()) {
      return List.of();
    }
    List<Long> tagIds = rels.stream().map(AssetTagRelPO::getTagId).toList();
    List<AssetTagPO> tags = tagMapper.selectList(new LambdaQueryWrapper<AssetTagPO>()
        .eq(AssetTagPO::getProjectId, projectId)
        .in(AssetTagPO::getId, tagIds)
        .eq(AssetTagPO::getDeleted, false));
    return tags.stream().map(po -> toView(po, 0)).toList();
  }

  // ---------- internal ----------

  /** An asset-tag relation is always queried within the owning project and asset. */
  private static LambdaQueryWrapper<AssetTagRelPO> relationScope(Long projectId, Long assetId) {
    return new LambdaQueryWrapper<AssetTagRelPO>()
        .eq(AssetTagRelPO::getProjectId, projectId)
        .eq(AssetTagRelPO::getAssetId, assetId);
  }

  AssetTagPO requireTag(Long projectId, Long id) {
    AssetTagPO po = tagMapper.selectOne(new LambdaQueryWrapper<AssetTagPO>()
        .eq(AssetTagPO::getProjectId, projectId)
        .eq(AssetTagPO::getId, id)
        .eq(AssetTagPO::getDeleted, false));
    if (po == null) {
      throw new AssetException(AssetErrorCode.ASSET_NOT_FOUND, "标签 id=" + id);
    }
    return po;
  }

  private void requireAsset(Long projectId, Long assetId) {
    Long count = itemMapper.selectCount(new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, projectId)
        .eq(AssetItemPO::getId, assetId)
        .eq(AssetItemPO::getDeleted, false));
    if (count == null || count == 0) {
      throw new AssetException(AssetErrorCode.ASSET_NOT_FOUND, "id=" + assetId);
    }
  }

  private boolean codeExists(Long projectId, String code) {
    return tagMapper.selectCount(new LambdaQueryWrapper<AssetTagPO>()
        .eq(AssetTagPO::getProjectId, projectId)
        .eq(AssetTagPO::getTagCode, code)
        .eq(AssetTagPO::getDeleted, false)) > 0;
  }

  private long usageCount(Long projectId, Long tagId) {
    Long count = tagRelMapper.selectCount(new LambdaQueryWrapper<AssetTagRelPO>()
        .eq(AssetTagRelPO::getProjectId, projectId)
        .eq(AssetTagRelPO::getTagId, tagId));
    return count == null ? 0 : count;
  }

  private void audit(String code, String action, Long id, String name, String operator,
      AuditEventType type, String message) {
    AuditOperationHandle handle = auditService.start(new AuditOperationRequest(
        code, action, "ASSET_TAG",
        id == null ? null : String.valueOf(id), name, "APPLICATION", Map.of()));
    AuditTransactions.completeOnCommit(handle, type, message,
        Map.of("target", String.valueOf(id)), null);
  }

  private static TagView toView(AssetTagPO po, long usage) {
    return new TagView(po.getId(), po.getTagCode(), po.getTagName(), po.getColor(),
        po.getDescription(), usage, po.getCreateTime());
  }
}
