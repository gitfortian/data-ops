package io.yak.ops.business.modeling.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelColumnMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.semantic.api.LayerStdBindingReader;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardReferenceReader;
import io.yak.ops.common.bean.po.modeling.ModelingModelColumnPO;
import io.yak.ops.common.bean.po.modeling.ModelingModelPO;
import io.yak.ops.core.project.CurrentProject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * semantic 反向只读 SPI 实现(M2-5 定标观察期):按 layer_code 聚合项目内
 * 未删除模型的字段数与 std_field_id 落标数,供「数仓分层」列表展示各层绑定率。
 * std_field_id 为字段↔标准字段权威落点(与 M2-1 状态条同口径)。
 */
@Component
@RequiredArgsConstructor
public class ModelingLayerStdBindingReader implements LayerStdBindingReader, StandardReferenceReader {

  private final ModelingModelMapper modelMapper;
  private final ModelingModelColumnMapper columnMapper;
  private final CurrentProject currentProject;

  @Override
  public Map<String, StdBindingStats> bindingStatsByLayer() {
    long projectId = currentProject.requireProjectId();
    List<ModelingModelPO> models =
        modelMapper.selectList(
            new LambdaQueryWrapper<ModelingModelPO>()
                .select(ModelingModelPO::getId, ModelingModelPO::getLayerCode)
                .eq(ModelingModelPO::getProjectId, projectId)
                .eq(ModelingModelPO::getDeleted, false)
                .isNotNull(ModelingModelPO::getLayerCode)
                .ne(ModelingModelPO::getLayerCode, ""));
    if (models.isEmpty()) {
      return Map.of();
    }
    Map<Long, String> layerOfModel = new HashMap<>();
    models.forEach(model -> layerOfModel.put(model.getId(), model.getLayerCode()));
    Map<String, long[]> totals = new HashMap<>();
    for (Map<String, Object> row :
        columnMapper.selectMaps(
            new QueryWrapper<ModelingModelColumnPO>()
                .select(
                    "model_id AS model_id",
                    "COUNT(*) AS column_total",
                    "SUM(CASE WHEN std_field_id IS NULL THEN 0 ELSE 1 END) AS std_bound_columns")
                .eq("project_id", projectId)
                .groupBy("model_id"))) {
      Object modelId = row.get("model_id");
      String layer = modelId == null ? null : layerOfModel.get(((Number) modelId).longValue());
      if (layer == null) {
        continue;
      }
      long total = numberOrZero(row.get("column_total"));
      long bound = numberOrZero(row.get("std_bound_columns"));
      long[] acc = totals.computeIfAbsent(layer, k -> new long[2]);
      acc[0] += total;
      acc[1] += bound;
    }
    Map<String, StdBindingStats> stats = new HashMap<>();
    totals.forEach(
        (layer, acc) -> stats.put(layer, new StdBindingStats(acc[0], acc[1])));
    return Map.copyOf(stats);
  }

  @Override
  public long countReferences(StandardKind kind, Long standardId, String codeSetCode) {
    LambdaQueryWrapper<ModelingModelColumnPO> query =
        new LambdaQueryWrapper<ModelingModelColumnPO>()
            .eq(ModelingModelColumnPO::getProjectId, currentProject.requireProjectId());
    switch (kind) {
      case NAMING -> query.eq(ModelingModelColumnPO::getStdNamingId, standardId);
      case TYPE -> query.eq(ModelingModelColumnPO::getStdTypeId, standardId);
      case CODE -> query.eq(ModelingModelColumnPO::getStdCodeSetCode, codeSetCode);
      case UNIT -> query.eq(ModelingModelColumnPO::getStdUnitId, standardId);
      case CALIBER -> query.eq(ModelingModelColumnPO::getStdCaliberId, standardId);
      case SECURITY -> query.eq(ModelingModelColumnPO::getStdSecurityId, standardId);
    }
    return columnMapper.selectCount(query);
  }

  private static long numberOrZero(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }
}
