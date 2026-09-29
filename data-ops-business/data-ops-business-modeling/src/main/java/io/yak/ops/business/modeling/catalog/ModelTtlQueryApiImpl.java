package io.yak.ops.business.modeling.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.modeling.api.ModelTtlQueryApi;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.common.bean.po.modeling.ModelingModelPO;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 模型 TTL 只读 SPI 实现:lifecycle 模块的下发目标解析(表名按 model_code 兜底)。 */
@Component
@RequiredArgsConstructor
public class ModelTtlQueryApiImpl implements ModelTtlQueryApi {

  private final ModelingModelMapper modelMapper;
  private final CurrentProject currentProject;

  @Override
  public TtlModelSource resolve(Long modelId) {
    ModelingModelPO po =
        modelMapper.selectOne(
            new LambdaQueryWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getId, modelId)
                .eq(ModelingModelPO::getProjectId, currentProject.requireProjectId())
                .eq(ModelingModelPO::getDeleted, Boolean.FALSE));
    if (po == null) {
      throw new ModelingException(ModelingErrorCode.NOT_FOUND, String.valueOf(modelId));
    }
    return toSource(po);
  }

  @Override
  public List<TtlModelSource> listAll() {
    return modelMapper
        .selectList(
            new LambdaQueryWrapper<ModelingModelPO>()
                .eq(ModelingModelPO::getProjectId, currentProject.requireProjectId())
                .eq(ModelingModelPO::getDeleted, Boolean.FALSE)
                .orderByAsc(ModelingModelPO::getId))
        .stream()
        .map(this::toSource)
        .toList();
  }

  private TtlModelSource toSource(ModelingModelPO po) {
    String tableName =
        po.getTableName() == null || po.getTableName().isBlank() ? po.getModelCode() : po.getTableName();
    return new TtlModelSource(
        po.getId(),
        po.getModelCode(),
        po.getModelName(),
        po.getLayerCode(),
        po.getDialect(),
        tableName,
        po.getPartitionType(),
        po.getPartitionColumns());
  }
}
