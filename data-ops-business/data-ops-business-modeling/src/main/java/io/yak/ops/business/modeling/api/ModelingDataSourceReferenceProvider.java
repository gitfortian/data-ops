package io.yak.ops.business.modeling.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.datasource.api.DataSourceReferenceProvider;
import io.yak.ops.business.modeling.dao.mapper.ModelingColumnMappingMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.business.modeling.dao.model.ModelingModelPO;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 数据源删除守卫(Ticket 01):统计绑定该源的存活模型与字段映射。 */
@Component
@RequiredArgsConstructor
public class ModelingDataSourceReferenceProvider implements DataSourceReferenceProvider {

  private final ModelingModelMapper modelMapper;
  private final ModelingColumnMappingMapper columnMappingMapper;
  private final CurrentProject currentProject;

  @Override
  public String moduleName() {
    return "数据建模";
  }

  @Override
  public long countReferences(Long dataSourceId) {
    if (dataSourceId == null) return 0L;
    long projectId = currentProject.requireProjectId();
    Long models =
        modelMapper.selectCount(
            new QueryWrapper<ModelingModelPO>()
                .eq("project_id", projectId)
                .eq("source_datasource_id", dataSourceId)
                .eq("deleted", false));
    Long mappings =
        columnMappingMapper.selectCount(
            new QueryWrapper<ModelingColumnMappingPO>()
                .eq("project_id", projectId)
                .eq("source_datasource_id", dataSourceId));
    return (models == null ? 0L : models) + (mappings == null ? 0L : mappings);
  }
}
