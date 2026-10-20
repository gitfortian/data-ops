package io.yak.ops.business.modeling.lineage;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.domain.LineageGraph;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.common.util.metadata.PhysicalTableAssetKey;
import io.yak.ops.core.project.CurrentProject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Combines registered lineage with the model owner's saved ODS source binding, without writes. */
@Component
public class ModelingLineageGraphReader {

  // Negative IDs belong only to this graph response; persistent lineage IDs are positive.
  private static final long SOURCE_PROJECTION_ID = Long.MIN_VALUE;
  private final ModelRepository models;
  private final LineageQueryService lineage;
  private final CurrentProject projects;

  public ModelingLineageGraphReader(
      ModelRepository models, LineageQueryService lineage, CurrentProject projects) {
    this.models = models;
    this.lineage = lineage;
    this.projects = projects;
  }

  @Transactional(value = "yakBusinessTransactionManager", readOnly = true)
  public LineageGraph graph(Long modelId, LineageDirection direction, int depth) {
    long projectId = projects.requireProjectId();
    if (depth < 1 || depth > LineageQueryService.MAX_GRAPH_DEPTH) {
      throw new IllegalArgumentException("血缘深度超出支持范围");
    }
    Model model = models.findById(modelId)
        .orElseThrow(() -> new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(modelId)));
    LineageDirection actualDirection = direction == null ? LineageDirection.BOTH : direction;
    LineageAsset registered = lineage.findAssetByKey(
        ModelingLineageRegistrationService.modelAssetKey(modelId)).orElse(null);
    LineageAsset root = registered == null ? modelProjection(model, projectId) : registered;
    LineageGraph graph = registered == null
        ? new LineageGraph(root, actualDirection, depth, List.of(root), List.of())
        : lineage.graph(root.id(), actualDirection, depth);
    if (actualDirection == LineageDirection.DOWNSTREAM || !hasOdsSource(model)) return graph;

    String sourceKey = PhysicalTableAssetKey.of(String.valueOf(model.sourceDatasourceId()),
        normalize(model.sourceDatabase()), normalize(model.sourceSchema()), normalize(model.sourceTable()));
    LineageAsset source = graph.nodes().stream()
        .filter(asset -> sourceKey.equals(asset.assetKey())).findFirst()
        .orElseGet(() -> lineage.findAssetByKey(sourceKey)
            .orElseGet(() -> sourceProjection(model, projectId, sourceKey)));
    if (graph.relations().stream().anyMatch(relation ->
        relation.sourceAssetId() == source.id() && relation.targetAssetId() == root.id())) {
      return graph;
    }

    Map<Long, LineageAsset> nodes = new LinkedHashMap<>();
    Map<Long, LineageRelation> relations = new LinkedHashMap<>();
    graph.nodes().forEach(asset -> nodes.put(asset.id(), asset));
    graph.relations().forEach(relation -> relations.put(relation.id(), relation));
    nodes.putIfAbsent(source.id(), source);
    // The bound source is one hop away. Reuse its registered upstream graph within the remaining depth.
    if (source.id() > 0 && depth > 1) {
      LineageGraph upstream = lineage.graph(source.id(), LineageDirection.UPSTREAM, depth - 1);
      upstream.nodes().forEach(asset -> nodes.putIfAbsent(asset.id(), asset));
      upstream.relations().forEach(relation -> relations.putIfAbsent(relation.id(), relation));
    }
    ObjectNode evidence = JsonNodeFactory.instance.objectNode();
    evidence.put("modelSourceBinding", true);
    evidence.put("projectionOnly", true);
    relations.put(SOURCE_PROJECTION_ID, new LineageRelation(
        SOURCE_PROJECTION_ID, projectId, source.id(), root.id(), LineageRelationType.READS_FROM,
        "MODELING", modelId + ":source-binding", null, null, null, null, evidence, null, null));
    return new LineageGraph(root, actualDirection, depth,
        List.copyOf(nodes.values()), List.copyOf(relations.values()));
  }

  private static boolean hasOdsSource(Model model) {
    return "ODS".equalsIgnoreCase(model.layerCode()) && model.sourceDatasourceId() != null
        && model.sourceDatasourceId() > 0 && model.sourceDatabase() != null
        && !model.sourceDatabase().isBlank() && model.sourceTable() != null && !model.sourceTable().isBlank();
  }

  private static LineageAsset modelProjection(Model model, long projectId) {
    ObjectNode properties = JsonNodeFactory.instance.objectNode();
    properties.put("projectionOnly", true);
    properties.put("modelCode", model.code());
    return new LineageAsset(-model.id(), projectId,
        ModelingLineageRegistrationService.modelAssetKey(model.id()), LineageAssetType.TABLE,
        model.name(), "MODELING", String.valueOf(model.id()), null, null, null, null,
        model.code(), null, properties, null, null);
  }

  private static LineageAsset sourceProjection(Model model, long projectId, String assetKey) {
    ObjectNode properties = JsonNodeFactory.instance.objectNode();
    properties.put("projectionOnly", true);
    return new LineageAsset(SOURCE_PROJECTION_ID, projectId, assetKey, LineageAssetType.TABLE,
        model.sourceTable(), "MODELING", model.id() + ":source-binding", null,
        String.valueOf(model.sourceDatasourceId()), model.sourceDatabase(), model.sourceSchema(),
        model.sourceTable(), null, properties, null, null);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
