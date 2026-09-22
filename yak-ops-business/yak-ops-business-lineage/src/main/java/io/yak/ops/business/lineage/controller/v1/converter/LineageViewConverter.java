package io.yak.ops.business.lineage.controller.v1.converter;

import io.yak.ops.business.lineage.controller.v1.vo.LineageViews.AssetView;
import io.yak.ops.business.lineage.controller.v1.vo.LineageViews.GraphView;
import io.yak.ops.business.lineage.controller.v1.vo.LineageViews.RelationView;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.domain.LineageRelation;
import java.util.List;
import org.springframework.stereotype.Component;

/** Pure Domain to HTTP view converter. */
@Component
public class LineageViewConverter {

  public AssetView asset(LineageAsset value) {
    if (value == null) return null;
    return new AssetView(String.valueOf(value.id()), value.assetKey(), value.assetType(), value.name(),
        value.sourceType(), value.sourceId(),
        value.parentAssetId() == null ? null : String.valueOf(value.parentAssetId()),
        value.dataSourceId(), value.databaseName(), value.schemaName(), value.tableName(),
        value.columnName(), value.properties(), value.createTime(), value.updateTime());
  }

  public RelationView relation(LineageRelation value) {
    if (value == null) return null;
    return new RelationView(String.valueOf(value.id()), String.valueOf(value.sourceAssetId()),
        String.valueOf(value.targetAssetId()), value.relationType(), value.sourceType(), value.sourceId(),
        value.expression(), value.confidence(), value.version(), value.observedAt(), value.properties(),
        value.createTime(), value.updateTime());
  }

  public GraphView graph(LineageGraph value) {
    List<AssetView> nodes = value.nodes().stream().map(this::asset).toList();
    List<RelationView> relations = value.relations().stream().map(this::relation).toList();
    return new GraphView(asset(value.root()), value.direction(), value.depth(), nodes, relations);
  }
}
