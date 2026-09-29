package io.yak.ops.business.dataset.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetMapper;
import io.yak.ops.business.dataset.dao.mapper.DatasetVersionMapper;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.business.dataset.dao.model.DatasetVersionPO;
import io.yak.ops.business.datasource.api.DataSourceReferenceProvider;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 数据源删除守卫(Ticket 01):统计草稿/版本引用该源的数据集。
 * dataset 表以字符串存数据源 ID(VARCHAR 列);版本表无 project 列,按所属数据集归项目。
 */
@Component
@RequiredArgsConstructor
public class DatasetDataSourceReferenceProvider implements DataSourceReferenceProvider {

  private final DatasetMapper datasetMapper;
  private final DatasetVersionMapper datasetVersionMapper;
  private final CurrentProject currentProject;

  @Override
  public String moduleName() {
    return "数据集";
  }

  @Override
  public long countReferences(Long dataSourceId) {
    if (dataSourceId == null) return 0L;
    long projectId = currentProject.requireProjectId();
    String idText = String.valueOf(dataSourceId);
    Long datasets =
        datasetMapper.selectCount(
            new QueryWrapper<DatasetPO>()
                .eq("project_id", projectId)
                .eq("draft_data_source_id", idText));
    Long versions =
        datasetVersionMapper.selectCount(
            new QueryWrapper<DatasetVersionPO>()
                .eq("data_source_id", idText)
                .inSql(
                    "dataset_id",
                    "SELECT id FROM yak_dataset WHERE project_id = " + projectId));
    return (datasets == null ? 0L : datasets) + (versions == null ? 0L : versions);
  }
}
