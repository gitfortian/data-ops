package io.yak.ops.business.lineage.repository;

import io.yak.ops.business.lineage.dao.LineageDao;
import io.yak.ops.business.lineage.dao.model.LineageAssetPO;
import io.yak.ops.business.lineage.dao.model.LineageRelationPO;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetDraft;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.domain.LineageRelationDraft;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.repository.support.LineageJsonCodec;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Persistence adapter translating lineage domain models to database rows. */
@Repository
@RequiredArgsConstructor
public class LineageRepositoryAdapter
    implements LineageRepository, LineageOverviewRepository {

  private final LineageDao lineageDao;
  private final LineageJsonCodec jsonCodec;

  @Override
  public LineageAsset upsertAsset(LineageAssetDraft draft) {
    LineageAssetPO row = toAssetPO(draft);
    lineageDao.upsertAsset(row);
    return Optional.ofNullable(lineageDao.selectAssetByKey(draft.assetKey(), draft.projectId()))
        .map(this::toAsset)
        .orElseThrow(() -> new IllegalStateException("保存血缘资产后无法读取资产"));
  }

  @Override
  public LineageRelation upsertRelation(LineageRelationDraft draft) {
    LineageRelationPO row = toRelationPO(draft);
    lineageDao.upsertRelation(row);
    return Optional.ofNullable(lineageDao.selectRelationByIdentity(row))
        .map(this::toRelation)
        .orElseThrow(() -> new IllegalStateException("保存血缘关系后无法读取关系"));
  }

  @Override
  public Map<String, LineageAsset> upsertAssets(List<LineageAssetDraft> drafts, int batchSize) {
    if (drafts == null || drafts.isEmpty()) return Map.of();
    List<LineageAssetPO> rows = drafts.stream().map(this::toAssetPO).toList();
    Map<String, LineageAsset> result = new LinkedHashMap<>();
    lineageDao.upsertAssets(rows, batchSize)
        .forEach(row -> result.put(row.getAssetKey(), toAsset(row)));
    if (result.size() != drafts.size()) {
      throw new IllegalStateException("批量保存血缘资产后无法读取全部资产");
    }
    return Map.copyOf(result);
  }

  @Override
  public void upsertRelations(List<LineageRelationDraft> drafts, int batchSize) {
    if (drafts == null || drafts.isEmpty()) return;
    lineageDao.upsertRelations(drafts.stream().map(this::toRelationPO).toList(), batchSize);
  }

  @Override
  public int deleteRelationsByEvidence(String sourceType, String sourceId) {
    return lineageDao.deleteRelationsByEvidence(sourceType, sourceId);
  }

  @Override
  public Set<Long> findAssetIdsByEvidence(String sourceType, String sourceId) {
    return lineageDao.selectAssetIdsByEvidence(sourceType, sourceId);
  }

  @Override
  public int deleteUnreferencedOwnedAssets(Set<Long> assetIds, String ownerType, String ownerId) {
    return lineageDao.deleteUnreferencedOwnedAssets(assetIds, ownerType, ownerId);
  }

  @Override
  public Optional<LineageAsset> lockAssetByKey(String assetKey) {
    return Optional.ofNullable(lineageDao.selectAssetForUpdate(assetKey)).map(this::toAsset);
  }

  @Override
  public Optional<LineageAsset> findAsset(long assetId) {
    return Optional.ofNullable(lineageDao.selectAsset(assetId)).map(this::toAsset);
  }

  @Override
  public Optional<LineageAsset> findAssetByKey(String assetKey) {
    return Optional.ofNullable(lineageDao.selectAssetByKey(assetKey)).map(this::toAsset);
  }

  @Override
  public List<LineageAsset> searchAssets(String keyword, LineageAssetType assetType, int limit) {
    String type = assetType == null ? null : assetType.name();
    return lineageDao.selectAssets(new LineageDao.AssetSearch(keyword, type, limit)).stream()
        .map(this::toAsset)
        .toList();
  }

  @Override
  public long countAssets(LineageAssetType assetType) {
    return lineageDao.countAssets(assetType == null ? null : assetType.name());
  }

  @Override
  public long countAssetsUpdatedBetween(Instant start, Instant end) {
    return lineageDao.countAssetsUpdatedBetween(Timestamp.from(start), Timestamp.from(end));
  }

  @Override
  public long countRelations() {
    return lineageDao.countRelations();
  }

  @Override
  public List<LineageRelation> findRecentRelations(int limit) {
    return lineageDao.selectRecentRelations(limit).stream().map(this::toRelation).toList();
  }

  @Override
  public List<LineageAsset> findAssetsByIds(Set<Long> assetIds) {
    return lineageDao.selectAssetsByIds(assetIds).stream().map(this::toAsset).toList();
  }

  @Override
  public List<LineageRelation> findOutgoingRelations(Set<Long> sourceAssetIds) {
    return lineageDao.selectOutgoingRelations(sourceAssetIds).stream().map(this::toRelation).toList();
  }

  @Override
  public List<LineageRelation> findIncomingRelations(Set<Long> targetAssetIds) {
    return lineageDao.selectIncomingRelations(targetAssetIds).stream().map(this::toRelation).toList();
  }

  private LineageAssetPO toAssetPO(LineageAssetDraft draft) {
    LineageAssetPO row = new LineageAssetPO();
    row.setProjectId(draft.projectId());
    row.setAssetKey(draft.assetKey());
    row.setAssetType(draft.assetType().name());
    row.setName(draft.name());
    row.setSourceType(draft.sourceType());
    row.setSourceId(draft.sourceId());
    row.setParentAssetId(draft.parentAssetId());
    row.setDataSourceId(draft.dataSourceId());
    row.setDatabaseName(draft.databaseName());
    row.setSchemaName(draft.schemaName());
    row.setTableName(draft.tableName());
    row.setColumnName(draft.columnName());
    row.setProperties(jsonCodec.write(draft.properties()));
    return row;
  }

  private LineageRelationPO toRelationPO(LineageRelationDraft draft) {
    LineageRelationPO row = new LineageRelationPO();
    row.setProjectId(draft.projectId());
    row.setSourceAssetId(draft.sourceAssetId());
    row.setTargetAssetId(draft.targetAssetId());
    row.setRelationType(draft.relationType().name());
    row.setSourceType(draft.sourceType());
    row.setSourceId(draft.sourceId());
    row.setExpression(draft.expression());
    row.setConfidence(draft.confidence());
    row.setVersion(draft.version());
    row.setObservedAt(Timestamp.from(draft.observedAt()));
    row.setProperties(jsonCodec.write(draft.properties()));
    return row;
  }

  private LineageAsset toAsset(LineageAssetPO row) {
    return new LineageAsset(
        row.getId(),
        row.getProjectId(),
        row.getAssetKey(),
        LineageAssetType.valueOf(row.getAssetType()),
        row.getName(),
        row.getSourceType(),
        row.getSourceId(),
        row.getParentAssetId(),
        row.getDataSourceId(),
        row.getDatabaseName(),
        row.getSchemaName(),
        row.getTableName(),
        row.getColumnName(),
        jsonCodec.read(row.getProperties()),
        instant(row.getCreateTime()),
        instant(row.getUpdateTime()));
  }

  private LineageRelation toRelation(LineageRelationPO row) {
    return new LineageRelation(
        row.getId(),
        row.getProjectId(),
        row.getSourceAssetId(),
        row.getTargetAssetId(),
        LineageRelationType.valueOf(row.getRelationType()),
        row.getSourceType(),
        row.getSourceId(),
        row.getExpression(),
        row.getConfidence(),
        row.getVersion(),
        instant(row.getObservedAt()),
        jsonCodec.read(row.getProperties()),
        instant(row.getCreateTime()),
        instant(row.getUpdateTime()));
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
