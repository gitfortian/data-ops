package io.yak.ops.business.modeling.metadata;

import io.yak.ops.business.metadata.api.EntityProjection;
import io.yak.ops.business.metadata.api.EntityProvider;
import io.yak.ops.business.modeling.config.ConditionalOnModelingPersistence;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.lineage.ModelingLineageRegistrationService;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.modeling.dao.model.ModelingModelPO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code dataModel} 的源域实时供给（metadata ticket 118，plan §3.2b）。
 *
 * <p>bean 名必须等于 {@code type_def.provider_bean} 里种的 {@code modelEntityProvider}——详情聚合
 * 按那一列寻址，不是按代码里的类型常量。
 *
 * <p><b>列清单只进 {@code extra}</b>：目录的 {@code md_attributes} 存的是登记时那份投影，
 * 把源域当前的表结构塞进去就有第二真相了（plan §1.3、§10 测试 10）。本类因此也不写 {@code attributes}。
 *
 * <p>{@code sourceHash} 留空：投影指纹的口径归工单 131 的登记挂钩，实时读侧另算一把就是第二个派生点，
 * 两通道会互相把对方的行判成 CHANGED。详情页要的是"现在长什么样"，不是"和目录差多少"。
 */
@Component("modelEntityProvider")
@ConditionalOnModelingPersistence
@RequiredArgsConstructor
public class ModelEntityProvider implements EntityProvider {

  private final ModelingModelMapper modelMapper;
  private final io.yak.ops.business.modeling.version.ModelPublishedStructureReader structureReader;

  @Override
  public String typeName() {
    return "dataModel";
  }

  @Override
  public Optional<EntityProjection> refresh(String sourceId) {
    Long modelId = parseIdOrNull(sourceId);
    if (modelId == null) {
      return Optional.empty();
    }
    ModelingModelPO po = modelMapper.selectById(modelId);
    if (po == null || Boolean.TRUE.equals(po.getDeleted())) {
      return Optional.empty();
    }
    StructureView structure = structureReader.publishedStructure(modelId);
    EntityProjection projection = new EntityProjection();
    projection.setSourceId(String.valueOf(po.getId()));
    projection.setAssetKey(ModelingLineageRegistrationService.modelAssetKey(po.getId()));
    projection.setName(po.getModelCode());
    projection.setDisplayName(po.getModelName());
    projection.setSummary(po.getDescription());
    projection.setOwnerUser(po.getUpdatedBy() == null ? po.getCreatedBy() : po.getUpdatedBy());
    projection.setDomainIds(po.getDomainId() == null ? null : String.valueOf(po.getDomainId()));
    projection.setLayerCode(po.getLayerCode());
    projection.setSourceUpdatedAt(po.getUpdateTime());
    projection.getExtra().put("status", po.getStatus());
    projection.getExtra().put("dialect", po.getDialect());
    projection.getExtra().put("latestVersionNo", po.getLatestVersionNo());
    projection.getExtra().put("tableName", structure.tableName());
    projection.getExtra().put("tableComment", structure.tableComment());
    projection.getExtra().put("primaryKey", structure.primaryKey());
    projection.getExtra().put("partition", partition(structure.partition()));
    projection.getExtra().put("columns", columns(structure.columns()));
    return Optional.of(projection);
  }

  /**
   * 列清单：只挑展示与"该不该沉淀为标准字段"要用的那些键。
   *
   * <p>{@code stdFieldId} 是语义回流的另一半——详情页把"该列注释"与"关联标准字段"并排放，
   * 人工点"沉淀为标准字段"走 semantic 既有接口，本类不做任何自动抽取。
   */
  private static List<Map<String, Object>> columns(List<StructureView.ColumnView> views) {
    List<Map<String, Object>> columns = new ArrayList<>();
    for (StructureView.ColumnView column : views == null ? List.<StructureView.ColumnView>of() : views) {
      Map<String, Object> view = new LinkedHashMap<>();
      put(view, "columnName", column.columnName());
      put(view, "dataType", dataType(column));
      put(view, "nullable", column.nullable());
      put(view, "defaultValue", column.defaultValue());
      put(view, "comment", column.comment());
      put(view, "businessDescription", column.businessDescription());
      put(view, "fieldRole", column.fieldRole());
      put(view, "aggregateFunc", column.aggregateFunc());
      put(view, "transformExpr", column.transformExpr());
      put(view, "stdFieldId", column.stdFieldId());
      columns.add(view);
    }
    return columns;
  }

  private static String dataType(StructureView.ColumnView column) {
    if (column.dataType() == null || column.length() == null) {
      return column.dataType();
    }
    return column.scale() == null
        ? column.dataType() + "(" + column.length() + ")"
        : column.dataType() + "(" + column.length() + "," + column.scale() + ")";
  }

  private static Map<String, Object> partition(StructureView.PartitionView partition) {
    if (partition == null) {
      return Map.of();
    }
    Map<String, Object> view = new LinkedHashMap<>();
    put(view, "type", partition.type());
    put(view, "columns", partition.columns());
    put(view, "expression", partition.expression());
    return view;
  }

  private static void put(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  private static Long parseIdOrNull(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
