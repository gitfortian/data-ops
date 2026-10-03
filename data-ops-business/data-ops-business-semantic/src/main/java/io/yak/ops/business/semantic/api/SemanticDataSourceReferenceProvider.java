package io.yak.ops.business.semantic.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.datasource.api.DataSourceReferenceProvider;
import io.yak.ops.business.semantic.dao.mapper.SemanticLayerMapper;
import io.yak.ops.business.semantic.dao.mapper.SemanticProcessSourceMapper;
import io.yak.ops.business.semantic.dao.model.SemanticLayerPO;
import io.yak.ops.business.semantic.dao.model.SemanticProcessSourcePO;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 数据源删除守卫(Ticket 01):统计绑定该源的业务过程源表与分层配置。 */
@Component
@RequiredArgsConstructor
public class SemanticDataSourceReferenceProvider implements DataSourceReferenceProvider {

  private final SemanticProcessSourceMapper processSourceMapper;
  private final SemanticLayerMapper layerMapper;
  private final CurrentProject currentProject;

  @Override
  public String moduleName() {
    return "语义中心";
  }

  @Override
  public long countReferences(Long dataSourceId) {
    if (dataSourceId == null) return 0L;
    long projectId = currentProject.requireProjectId();
    Long processSources =
        processSourceMapper.selectCount(
            new QueryWrapper<SemanticProcessSourcePO>()
                .eq("project_id", projectId)
                .eq("datasource_id", dataSourceId));
    Long layers =
        layerMapper.selectCount(
            new QueryWrapper<SemanticLayerPO>()
                .eq("project_id", projectId)
                .eq("datasource_id", dataSourceId));
    return (processSources == null ? 0L : processSources) + (layers == null ? 0L : layers);
  }
}
