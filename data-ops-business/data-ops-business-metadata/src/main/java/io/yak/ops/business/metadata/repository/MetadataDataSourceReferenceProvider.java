package io.yak.ops.business.metadata.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.datasource.api.DataSourceReferenceProvider;
import io.yak.ops.business.metadata.dao.mapper.MdCollectJobMapper;
import io.yak.ops.business.metadata.dao.model.MdCollectJobPO;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 数据源删除守卫(Ticket 01):统计挂在该源上的存活采集作业。 */
@Component
@RequiredArgsConstructor
public class MetadataDataSourceReferenceProvider implements DataSourceReferenceProvider {

  private final MdCollectJobMapper collectJobMapper;
  private final CurrentProject currentProject;

  @Override
  public String moduleName() {
    return "元数据采集";
  }

  @Override
  public long countReferences(Long dataSourceId) {
    if (dataSourceId == null) return 0L;
    Long count =
        collectJobMapper.selectCount(
            new QueryWrapper<MdCollectJobPO>()
                .eq("project_id", currentProject.requireProjectId())
                .eq("data_source_id", dataSourceId)
                .eq("deleted", false));
    return count == null ? 0L : count;
  }
}
