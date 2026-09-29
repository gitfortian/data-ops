package io.yak.ops.business.mdm.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.datasource.api.DataSourceReferenceProvider;
import io.yak.ops.business.mdm.dao.mapper.MdmCollectLinkMapper;
import io.yak.ops.business.mdm.dao.mapper.MdmSourceMapper;
import io.yak.ops.common.bean.po.mdm.MdmCollectLinkPO;
import io.yak.ops.common.bean.po.mdm.MdmSourcePO;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 数据源删除守卫(Ticket 01):统计引用该源的 MDM 来源与采集链路。 */
@Component
@RequiredArgsConstructor
public class MdmDataSourceReferenceProvider implements DataSourceReferenceProvider {

  private final MdmSourceMapper sourceMapper;
  private final MdmCollectLinkMapper collectLinkMapper;
  private final CurrentProject currentProject;

  @Override
  public String moduleName() {
    return "主数据";
  }

  @Override
  public long countReferences(Long dataSourceId) {
    if (dataSourceId == null) return 0L;
    long projectId = currentProject.requireProjectId();
    Long sources =
        sourceMapper.selectCount(
            new QueryWrapper<MdmSourcePO>()
                .eq("project_id", projectId)
                .eq("datasource_id", dataSourceId));
    Long links =
        collectLinkMapper.selectCount(
            new QueryWrapper<MdmCollectLinkPO>()
                .eq("project_id", projectId)
                .eq("datasource_id", dataSourceId));
    return (sources == null ? 0L : sources) + (links == null ? 0L : links);
  }
}
