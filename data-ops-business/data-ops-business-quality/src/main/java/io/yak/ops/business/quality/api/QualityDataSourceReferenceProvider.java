package io.yak.ops.business.quality.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.datasource.api.DataSourceReferenceProvider;
import io.yak.ops.business.quality.dao.mapper.QualityMonitorMapper;
import io.yak.ops.business.quality.dao.mapper.QualityTableAssetMapper;
import io.yak.ops.common.bean.po.quality.QualityMonitorPO;
import io.yak.ops.common.bean.po.quality.QualityTableAssetPO;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 数据源删除守卫(Ticket 01):统计引用该源的表资产与监控规则(排除软删)。 */
@Component
@RequiredArgsConstructor
public class QualityDataSourceReferenceProvider implements DataSourceReferenceProvider {

  private final QualityTableAssetMapper tableAssetMapper;
  private final QualityMonitorMapper monitorMapper;
  private final CurrentProject currentProject;

  @Override
  public String moduleName() {
    return "数据质量";
  }

  @Override
  public long countReferences(Long dataSourceId) {
    if (dataSourceId == null) return 0L;
    long projectId = currentProject.requireProjectId();
    Long assets =
        tableAssetMapper.selectCount(
            new QueryWrapper<QualityTableAssetPO>()
                .eq("project_id", projectId)
                .eq("data_source_id", dataSourceId)
                .eq("deleted", false));
    Long monitors =
        monitorMapper.selectCount(
            new QueryWrapper<QualityMonitorPO>()
                .eq("project_id", projectId)
                .eq("data_source_id", dataSourceId)
                .eq("deleted", false));
    return (assets == null ? 0L : assets) + (monitors == null ? 0L : monitors);
  }
}
