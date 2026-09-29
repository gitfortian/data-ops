package io.yak.ops.business.dataservice.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.datasource.api.DataSourceReferenceProvider;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiPO;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 数据源删除守卫(Ticket 01):统计以该源为查询目标的 API 定义。 */
@Component
@RequiredArgsConstructor
public class DataServiceDataSourceReferenceProvider implements DataSourceReferenceProvider {

  private final DataServiceApiMapper apiMapper;
  private final CurrentProject currentProject;

  @Override
  public String moduleName() {
    return "数据服务";
  }

  @Override
  public long countReferences(Long dataSourceId) {
    if (dataSourceId == null) return 0L;
    Long count =
        apiMapper.selectCount(
            new QueryWrapper<DataServiceApiPO>()
                .eq("project_id", currentProject.requireProjectId())
                .eq("data_source_id", dataSourceId));
    return count == null ? 0L : count;
  }
}
