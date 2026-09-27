package io.yak.ops.business.metric.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.metric.dao.mapper.MetricValidationEvidenceMapper;
import io.yak.ops.common.bean.po.metric.MetricValidationEvidencePO;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Project-scoped append-only validation evidence persistence. */
@Repository
public class MetricValidationEvidenceRepositoryAdapter
    implements MetricValidationEvidenceRepository {

  private final MetricValidationEvidenceMapper mapper;
  private final CurrentProject currentProject;

  public MetricValidationEvidenceRepositoryAdapter(
      MetricValidationEvidenceMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public MetricValidationEvidencePO append(MetricValidationEvidencePO evidence) {
    evidence.setProjectId(currentProject.requireProjectId());
    mapper.insert(evidence);
    return evidence;
  }

  @Override
  public List<MetricValidationEvidencePO> listByVersion(Long metricId, int metricVersion) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(new LambdaQueryWrapper<MetricValidationEvidencePO>()
        .eq(MetricValidationEvidencePO::getProjectId, projectId)
        .eq(MetricValidationEvidencePO::getMetricId, metricId)
        .eq(MetricValidationEvidencePO::getMetricVersion, metricVersion)
        .orderByDesc(MetricValidationEvidencePO::getCheckedAt)
        .orderByDesc(MetricValidationEvidencePO::getId));
  }

  @Override
  public MetricValidationEvidencePO findLatestReady(Long metricId, int metricVersion) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectOne(new LambdaQueryWrapper<MetricValidationEvidencePO>()
        .eq(MetricValidationEvidencePO::getProjectId, projectId)
        .eq(MetricValidationEvidencePO::getMetricId, metricId)
        .eq(MetricValidationEvidencePO::getMetricVersion, metricVersion)
        .eq(MetricValidationEvidencePO::getResult, "READY")
        .orderByDesc(MetricValidationEvidencePO::getCheckedAt)
        .orderByDesc(MetricValidationEvidencePO::getId)
        .last("LIMIT 1"));
  }
}
