package io.yak.ops.business.sync.offline.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.datasource.api.DataSourceReferenceProvider;
import io.yak.ops.business.sync.offline.dao.mapper.OfflineJobDefinitionMapper;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 数据源删除守卫(Ticket 01):统计以该源为源表/目标表的离线同步作业定义。 */
@Component
@RequiredArgsConstructor
public class OfflineSyncDataSourceReferenceProvider implements DataSourceReferenceProvider {

  private final OfflineJobDefinitionMapper jobDefinitionMapper;
  private final CurrentProject currentProject;

  @Override
  public String moduleName() {
    return "离线同步";
  }

  @Override
  public long countReferences(Long dataSourceId) {
    if (dataSourceId == null) return 0L;
    Long count =
        jobDefinitionMapper.selectCount(
            new QueryWrapper<OfflineJobDefinitionPO>()
                .eq("project_id", currentProject.requireProjectId())
                .and(
                    wrapper ->
                        wrapper
                            .eq("source_datasource_id", dataSourceId)
                            .or()
                            .eq("sink_datasource_id", dataSourceId)));
    return count == null ? 0L : count;
  }
}
