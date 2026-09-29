package io.yak.ops.business.lineage.domain;

import java.util.List;

/** Materialized graph slice around a selected root asset. */
public record LineageGraph(
    LineageAsset root,
    LineageDirection direction,
    int depth,
    List<LineageAsset> nodes,
    List<LineageRelation> relations) {
}
