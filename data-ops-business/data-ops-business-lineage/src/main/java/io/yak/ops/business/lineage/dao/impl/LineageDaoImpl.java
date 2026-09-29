package io.yak.ops.business.lineage.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.lineage.config.ConditionalOnLineagePersistence;
import io.yak.ops.business.lineage.dao.LineageDao;
import io.yak.ops.business.lineage.dao.mapper.LineageAssetMapper;
import io.yak.ops.business.lineage.dao.mapper.LineageQueryMapper;
import io.yak.ops.business.lineage.dao.mapper.LineageRelationMapper;
import io.yak.ops.business.lineage.dao.mapper.LineageWriteMapper;
import io.yak.ops.business.lineage.dao.model.LineageAssetPO;
import io.yak.ops.business.lineage.dao.model.LineageRelationPO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis-Plus DAO with XML reserved for complex/atomic SQL. */
@Repository
@DependsOn("yakLineageFlyway")
@ConditionalOnLineagePersistence
public class LineageDaoImpl implements LineageDao {

  private final LineageAssetMapper assetMapper;
  private final LineageRelationMapper relationMapper;
  private final LineageWriteMapper writeMapper;
  private final LineageQueryMapper queryMapper;
  private final CurrentProject currentProject;

  @Autowired
  public LineageDaoImpl(
      LineageAssetMapper assetMapper,
      LineageRelationMapper relationMapper,
      LineageWriteMapper writeMapper,
      LineageQueryMapper queryMapper,
      CurrentProject currentProject) {
    this.assetMapper = assetMapper;
    this.relationMapper = relationMapper;
    this.writeMapper = writeMapper;
    this.queryMapper = queryMapper;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused DAO tests. */
  public LineageDaoImpl(
      LineageAssetMapper assetMapper,
      LineageRelationMapper relationMapper,
      LineageWriteMapper writeMapper,
      LineageQueryMapper queryMapper) {
    this(assetMapper, relationMapper, writeMapper, queryMapper, Optional::<ProjectContext>empty);
  }

  @Override
  public int upsertAsset(LineageAssetPO asset) {
    claimLegacyAsset(asset);
    return writeMapper.upsertAsset(asset);
  }

  @Override
  public int upsertRelation(LineageRelationPO relation) {
    return writeMapper.upsertRelation(relation);
  }

  @Override
  public List<LineageAssetPO> upsertAssets(List<LineageAssetPO> assets, int batchSize) {
    if (assets == null || assets.isEmpty()) return List.of();
    Long batchProjectId = commonProjectId(assets);
    Map<String, LineageAssetPO> result = new LinkedHashMap<>();
    for (int start = 0; start < assets.size(); start += batchSize) {
      List<LineageAssetPO> batch = assets.subList(start, Math.min(start + batchSize, assets.size()));
      batch.forEach(this::claimLegacyAsset);
      writeMapper.upsertAssets(batch);
      List<String> keys = batch.stream().map(LineageAssetPO::getAssetKey).toList();
      assetMapper.selectList(
          Wrappers.<LineageAssetPO>lambdaQuery()
              .in(LineageAssetPO::getAssetKey, keys)
              .eq(batchProjectId != null, LineageAssetPO::getProjectId, batchProjectId))
          .forEach(row -> result.put(row.getAssetKey(), row));
    }
    return new ArrayList<>(result.values());
  }

  @Override
  public void upsertRelations(List<LineageRelationPO> relations, int batchSize) {
    if (relations == null || relations.isEmpty()) return;
    for (int start = 0; start < relations.size(); start += batchSize) {
      List<LineageRelationPO> batch =
          relations.subList(start, Math.min(start + batchSize, relations.size()));
      writeMapper.upsertRelations(batch);
    }
  }

  @Override
  public int deleteRelationsByEvidence(String sourceType, String sourceId) {
    Long projectId = currentProjectId();
    return relationMapper.delete(
        Wrappers.<LineageRelationPO>lambdaQuery()
            .eq(projectId != null, LineageRelationPO::getProjectId, projectId)
            .eq(LineageRelationPO::getSourceType, sourceType)
            .eq(LineageRelationPO::getSourceId, sourceId));
  }

  @Override
  public Set<Long> selectAssetIdsByEvidence(String sourceType, String sourceId) {
    List<Long> ids = queryMapper.selectAssetIdsByEvidence(sourceType, sourceId, currentProjectId());
    return ids == null || ids.isEmpty() ? Set.of() : Set.copyOf(ids);
  }

  @Override
  public int deleteUnreferencedOwnedAssets(Set<Long> assetIds, String ownerType, String ownerId) {
    if (assetIds == null || assetIds.isEmpty()) return 0;
    return writeMapper.deleteUnreferencedOwnedAssets(
        assetIds, ownerType, ownerId, currentProjectId());
  }

  @Override
  public LineageAssetPO selectAssetForUpdate(String assetKey) {
    return writeMapper.selectAssetForUpdate(assetKey, currentProjectId());
  }

  @Override
  public LineageAssetPO selectAsset(long assetId) {
    Long projectId = currentProjectId();
    return assetMapper.selectOne(
        Wrappers.<LineageAssetPO>lambdaQuery()
            .eq(LineageAssetPO::getId, assetId)
            .eq(projectId != null, LineageAssetPO::getProjectId, projectId)
            .last("LIMIT 1"));
  }

  @Override
  public LineageAssetPO selectAssetByKey(String assetKey) {
    return selectAssetByKey(assetKey, null);
  }

  @Override
  public LineageAssetPO selectAssetByKey(String assetKey, Long projectId) {
    Long effectiveProjectId = projectId == null ? currentProjectId() : projectId;
    return assetMapper.selectOne(
        Wrappers.<LineageAssetPO>lambdaQuery()
            .eq(LineageAssetPO::getAssetKey, assetKey)
            .eq(effectiveProjectId != null, LineageAssetPO::getProjectId, effectiveProjectId)
            .last("LIMIT 1"));
  }

  @Override
  public List<LineageAssetPO> selectAssets(AssetSearch query) {
    AssetSearch condition = query == null ? new AssetSearch(null, null, 30) : query;
    Long projectId = currentProjectId();
    return assetMapper.selectList(
        Wrappers.<LineageAssetPO>lambdaQuery()
            .eq(projectId != null, LineageAssetPO::getProjectId, projectId)
            .and(
                StringUtils.hasText(condition.keyword()),
                nested ->
                    nested
                        .like(LineageAssetPO::getName, condition.keyword())
                        .or()
                        .like(LineageAssetPO::getAssetKey, condition.keyword())
                        .or()
                        .like(LineageAssetPO::getTableName, condition.keyword())
                        .or()
                        .like(LineageAssetPO::getColumnName, condition.keyword()))
            .eq(
                StringUtils.hasText(condition.assetType()),
                LineageAssetPO::getAssetType,
                condition.assetType())
            .orderByDesc(LineageAssetPO::getUpdateTime)
            .orderByDesc(LineageAssetPO::getId)
            .last("LIMIT " + Math.max(1, condition.limit())));
  }

  @Override
  public long countAssets(String assetType) {
    Long projectId = currentProjectId();
    return assetMapper.selectCount(
        Wrappers.<LineageAssetPO>lambdaQuery()
            .eq(projectId != null, LineageAssetPO::getProjectId, projectId)
            .eq(StringUtils.hasText(assetType), LineageAssetPO::getAssetType, assetType));
  }

  @Override
  public long countAssetsUpdatedBetween(Timestamp start, Timestamp end) {
    Long projectId = currentProjectId();
    return assetMapper.selectCount(
        Wrappers.<LineageAssetPO>lambdaQuery()
            .eq(projectId != null, LineageAssetPO::getProjectId, projectId)
            .ge(LineageAssetPO::getUpdateTime, start)
            .lt(LineageAssetPO::getUpdateTime, end));
  }

  @Override
  public long countRelations() {
    Long projectId = currentProjectId();
    return relationMapper.selectCount(
        Wrappers.<LineageRelationPO>lambdaQuery()
            .eq(projectId != null, LineageRelationPO::getProjectId, projectId));
  }

  @Override
  public List<LineageRelationPO> selectRecentRelations(int limit) {
    Long projectId = currentProjectId();
    return relationMapper.selectList(
        Wrappers.<LineageRelationPO>lambdaQuery()
            .eq(projectId != null, LineageRelationPO::getProjectId, projectId)
            .orderByDesc(LineageRelationPO::getUpdateTime)
            .orderByDesc(LineageRelationPO::getObservedAt)
            .orderByDesc(LineageRelationPO::getId)
            .last("LIMIT " + Math.max(1, limit)));
  }

  @Override
  public List<LineageAssetPO> selectAssetsByIds(Set<Long> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) return List.of();
    Long projectId = currentProjectId();
    return assetMapper.selectList(
        Wrappers.<LineageAssetPO>lambdaQuery()
            .in(LineageAssetPO::getId, assetIds)
            .eq(projectId != null, LineageAssetPO::getProjectId, projectId)
            .orderByAsc(LineageAssetPO::getId));
  }

  @Override
  public LineageRelationPO selectRelationByIdentity(LineageRelationPO identity) {
    Long projectId = identity.getProjectId() == null ? currentProjectId() : identity.getProjectId();
    return relationMapper.selectOne(
        Wrappers.<LineageRelationPO>lambdaQuery()
            .eq(projectId != null, LineageRelationPO::getProjectId, projectId)
            .eq(LineageRelationPO::getSourceAssetId, identity.getSourceAssetId())
            .eq(LineageRelationPO::getTargetAssetId, identity.getTargetAssetId())
            .eq(LineageRelationPO::getRelationType, identity.getRelationType())
            .eq(LineageRelationPO::getSourceType, identity.getSourceType())
            .eq(LineageRelationPO::getSourceId, identity.getSourceId())
            .eq(LineageRelationPO::getVersion, identity.getVersion()));
  }

  @Override
  public List<LineageRelationPO> selectOutgoingRelations(Set<Long> sourceAssetIds) {
    if (sourceAssetIds == null || sourceAssetIds.isEmpty()) return List.of();
    Long projectId = currentProjectId();
    return relationMapper.selectList(
        Wrappers.<LineageRelationPO>lambdaQuery()
            .in(LineageRelationPO::getSourceAssetId, sourceAssetIds)
            .eq(projectId != null, LineageRelationPO::getProjectId, projectId)
            .orderByAsc(LineageRelationPO::getId));
  }

  @Override
  public List<LineageRelationPO> selectIncomingRelations(Set<Long> targetAssetIds) {
    if (targetAssetIds == null || targetAssetIds.isEmpty()) return List.of();
    Long projectId = currentProjectId();
    return relationMapper.selectList(
        Wrappers.<LineageRelationPO>lambdaQuery()
            .in(LineageRelationPO::getTargetAssetId, targetAssetIds)
            .eq(projectId != null, LineageRelationPO::getProjectId, projectId)
            .orderByAsc(LineageRelationPO::getId));
  }

  private void claimLegacyAsset(LineageAssetPO asset) {
    if (asset != null && asset.getProjectId() != null) {
      writeMapper.claimLegacyAssetProject(asset.getAssetKey(), asset.getProjectId());
    }
  }

  private Long currentProjectId() {
    return currentProject.current().map(ProjectContext::projectId).orElse(null);
  }

  private Long commonProjectId(List<LineageAssetPO> assets) {
    Long projectId = assets.get(0).getProjectId();
    for (LineageAssetPO asset : assets) {
      if (!Objects.equals(projectId, asset.getProjectId())) {
        throw new IllegalArgumentException("批量血缘资产必须属于同一 Project");
      }
    }
    return projectId;
  }
}
