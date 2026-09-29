package io.yak.ops.business.lineage.query;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.repository.LineageRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Builds bounded lineage graphs from repository read primitives. */
@Component
public class LineageGraphReader {

  static final int MAX_GRAPH_DEPTH = 10;

  private final LineageRepository repository;
  private final LineageAssetReader assetReader;

  public LineageGraphReader(
      LineageRepository repository, LineageAssetReader assetReader) {
    this.repository = repository;
    this.assetReader = assetReader;
  }

  public LineageGraph graph(long assetId, LineageDirection direction, int depth) {
    LineageAsset root = assetReader.getAsset(assetId);
    LineageDirection actualDirection =
        direction == null ? LineageDirection.BOTH : direction;
    if (depth < 1 || depth > MAX_GRAPH_DEPTH) {
      throw new IllegalArgumentException(
          "depth 必须在 1 到 " + MAX_GRAPH_DEPTH + " 之间");
    }

    Map<Long, LineageAsset> nodes = new LinkedHashMap<>();
    Map<Long, LineageRelation> relations = new LinkedHashMap<>();
    nodes.put(root.id(), root);

    Set<Long> visited = new LinkedHashSet<>();
    visited.add(root.id());
    Set<Long> frontier = new LinkedHashSet<>();
    frontier.add(root.id());

    for (int level = 0; level < depth && !frontier.isEmpty(); level++) {
      List<LineageRelation> discoveredRelations = new ArrayList<>();
      if (actualDirection != LineageDirection.UPSTREAM) {
        discoveredRelations.addAll(repository.findOutgoingRelations(frontier));
      }
      if (actualDirection != LineageDirection.DOWNSTREAM) {
        discoveredRelations.addAll(repository.findIncomingRelations(frontier));
      }

      Set<Long> endpointIds = new LinkedHashSet<>();
      Set<Long> nextFrontier = new LinkedHashSet<>();
      for (LineageRelation relation : discoveredRelations) {
        relations.putIfAbsent(relation.id(), relation);
        endpointIds.add(relation.sourceAssetId());
        endpointIds.add(relation.targetAssetId());
        if (!visited.contains(relation.sourceAssetId())) {
          nextFrontier.add(relation.sourceAssetId());
        }
        if (!visited.contains(relation.targetAssetId())) {
          nextFrontier.add(relation.targetAssetId());
        }
      }

      repository.findAssetsByIds(endpointIds)
          .forEach(asset -> nodes.putIfAbsent(asset.id(), asset));
      nextFrontier.removeAll(visited);
      visited.addAll(nextFrontier);
      frontier = nextFrontier;
    }

    return new LineageGraph(
        root,
        actualDirection,
        depth,
        List.copyOf(nodes.values()),
        List.copyOf(relations.values()));
  }
}
