package io.yak.ops.business.modeling.asset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.asset.api.AssetStatusModelFacts;
import io.yak.ops.business.modeling.config.ConditionalOnModelingPersistence;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelColumnMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.semantic.api.LayerConfigApi;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.business.modeling.dao.model.ModelingModelColumnPO;
import io.yak.ops.business.modeling.dao.model.ModelingModelPO;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * M2-1 状态条事实供给(asset ticket 序列):模型物理落点与字段落标计数。
 * 物理库/数据源取分层配置(与 TTL 下发同口径),表名空则 modelCode 兜底
 * (与血缘登记 tableNameOf 同口径);std_field_id 为字段↔标准字段权威落点。
 */
@Component
@ConditionalOnModelingPersistence
@RequiredArgsConstructor
public class AssetStatusModelFactsAdapter implements AssetStatusModelFacts {

  private final ModelingModelMapper modelMapper;
  private final ModelingModelColumnMapper columnMapper;
  private final LayerConfigApi layerConfigApi;

  @Override
  public Optional<ModelFacts> modelFacts(String sourceId) {
    Long id = parseIdOrNull(sourceId);
    if (id == null) {
      return Optional.empty();
    }
    ModelingModelPO model = modelMapper.selectById(id);
    if (model == null || Boolean.TRUE.equals(model.getDeleted())) {
      return Optional.empty();
    }
    long total = columnMapper.selectCount(new LambdaQueryWrapper<ModelingModelColumnPO>()
        .eq(ModelingModelColumnPO::getModelId, id));
    long bound = columnMapper.selectCount(new LambdaQueryWrapper<ModelingModelColumnPO>()
        .eq(ModelingModelColumnPO::getModelId, id)
        .isNotNull(ModelingModelColumnPO::getStdFieldId));
    WarehouseLayer layer = StringUtils.hasText(model.getLayerCode())
        ? layerConfigApi.resolveByCode(model.getLayerCode())
        : null;
    return Optional.of(new ModelFacts(
        model.getLayerCode(),
        layer == null ? null : layer.stdMandatory(),
        layer == null ? null : layer.datasourceId(),
        layer == null ? null : layer.databaseName(),
        null,
        StringUtils.hasText(model.getTableName()) ? model.getTableName() : model.getModelCode(),
        total,
        bound,
        model.getStatus(),
        StringUtils.hasText(model.getPartitionType())));
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
