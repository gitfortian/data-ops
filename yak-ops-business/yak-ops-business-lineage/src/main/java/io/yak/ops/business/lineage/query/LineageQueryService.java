package io.yak.ops.business.lineage.query;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.domain.LineageRelation;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable read facade; specialized readers own query implementation details. */
@Service
public class LineageQueryService {

  public static final int MAX_GRAPH_DEPTH = LineageGraphReader.MAX_GRAPH_DEPTH;
  public static final int MAX_ASSET_SEARCH_LIMIT =
      LineageAssetReader.MAX_ASSET_SEARCH_LIMIT;
  public static final int MAX_OVERVIEW_RELATION_LIMIT =
      LineageOverviewReader.MAX_RELATION_LIMIT;

  private final LineageAssetReader assetReader;
  private final LineageGraphReader graphReader;
  private final LineageOverviewReader overviewReader;

  /**
   * Compatibility constructor for direct unit construction that does not use overview queries.
   * Spring uses the role-complete constructor below.
   */
  public LineageQueryService(
      LineageAssetReader assetReader, LineageGraphReader graphReader) {
    this(assetReader, graphReader, null);
  }

  @Autowired
  public LineageQueryService(
      LineageAssetReader assetReader,
      LineageGraphReader graphReader,
      LineageOverviewReader overviewReader) {
    this.assetReader = assetReader;
    this.graphReader = graphReader;
    this.overviewReader = overviewReader;
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public LineageAsset getAsset(long assetId) {
    return assetReader.getAsset(assetId);
  }

  /** Missing-by-key is a normal fallback branch for derived metadata registration. */
  @Transactional(
      value = "yakBusinessTransactionManager",
      readOnly = true,
      noRollbackFor = IllegalArgumentException.class)
  public LineageAsset getAssetByKey(String assetKey) {
    return assetReader.getAssetByKey(assetKey);
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public List<LineageAsset> searchAssets(
      String keyword, LineageAssetType assetType, int limit) {
    return assetReader.searchAssets(keyword, assetType, limit);
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public Overview overview(Instant updatedFrom, Instant updatedTo, int relationLimit) {
    if (overviewReader == null) {
      throw new IllegalStateException("LineageOverviewReader 未装配");
    }
    return overviewReader.overview(updatedFrom, updatedTo, relationLimit);
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public LineageGraph upstream(long assetId, int depth) {
    return graphReader.graph(assetId, LineageDirection.UPSTREAM, depth);
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public LineageGraph downstream(long assetId, int depth) {
    return graphReader.graph(assetId, LineageDirection.DOWNSTREAM, depth);
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public LineageGraph graph(long assetId, LineageDirection direction, int depth) {
    return graphReader.graph(assetId, direction, depth);
  }

  public record Overview(
      long assetCount,
      long relationCount,
      long updatedAssetCount,
      long tableAssetCount,
      long columnAssetCount,
      long datasetAssetCount,
      List<LineageAsset> nodes,
      List<LineageRelation> relations) {
  }
}
