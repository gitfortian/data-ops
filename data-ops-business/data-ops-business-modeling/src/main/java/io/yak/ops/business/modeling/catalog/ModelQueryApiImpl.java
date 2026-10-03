package io.yak.ops.business.modeling.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.modeling.api.ModelQueryApi;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.dao.model.ModelingModelPO;
import io.yak.ops.core.project.CurrentProject;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 模型只读 SPI 实现:批量解析展示名与发布版本(供 metric 等跨模块消费方)。 */
@Component
@RequiredArgsConstructor
public class ModelQueryApiImpl implements ModelQueryApi {

  private final ModelingModelMapper modelMapper;
  private final CurrentProject currentProject;

  @Override
  public Map<Long, ModelBrief> resolve(Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    List<Long> distinctIds = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    if (distinctIds.isEmpty()) {
      return Map.of();
    }
    Long projectId = currentProject.requireProjectId();
    Map<Long, ModelBrief> result = new LinkedHashMap<>();
    modelMapper.selectList(new LambdaQueryWrapper<ModelingModelPO>()
            .eq(ModelingModelPO::getProjectId, projectId)
            .eq(ModelingModelPO::getDeleted, Boolean.FALSE)
            .in(ModelingModelPO::getId, distinctIds))
        .forEach(po -> result.put(po.getId(), new ModelBrief(
            po.getId(), po.getModelCode(), po.getModelName(),
            po.getLayerCode(), po.getLatestVersionNo())));
    return Map.copyOf(result);
  }
}
