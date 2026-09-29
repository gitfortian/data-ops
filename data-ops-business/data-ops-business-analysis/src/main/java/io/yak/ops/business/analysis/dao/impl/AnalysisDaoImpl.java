package io.yak.ops.business.analysis.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.analysis.dao.AnalysisDao;
import io.yak.ops.business.analysis.dao.mapper.AnalysisMapper;
import io.yak.ops.business.analysis.dao.model.AnalysisPO;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.CurrentProject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@DependsOn("yakAnalysisFlyway")
@ConditionalOnDataSourceEnabled
public class AnalysisDaoImpl implements AnalysisDao {

  private final AnalysisMapper mapper;
  private final CurrentProject currentProject;

  public AnalysisDaoImpl(AnalysisMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public long insert(AnalysisPO value) {
    Timestamp now = Timestamp.from(Instant.now());
    value.setProjectId(projectId());
    value.setCreateTime(now);
    value.setUpdateTime(now);
    if (mapper.insert(value) != 1 || value.getId() == null) {
      throw new IllegalStateException("创建 Analysis 后未返回主键");
    }
    return value.getId();
  }

  @Override
  public void update(long analysisId, AnalysisPO value) {
    int updated = mapper.update(
        null,
        Wrappers.<AnalysisPO>lambdaUpdate()
            .eq(AnalysisPO::getProjectId, projectId())
            .eq(AnalysisPO::getId, analysisId)
            .set(AnalysisPO::getName, value.getName())
            .set(AnalysisPO::getDescription, value.getDescription())
            .set(AnalysisPO::getDatasetId, value.getDatasetId())
            .set(AnalysisPO::getChartType, value.getChartType())
            .set(AnalysisPO::getQuerySpecJson, value.getQuerySpecJson())
            .set(AnalysisPO::getVisualConfigJson, value.getVisualConfigJson())
            .set(AnalysisPO::getUpdateTime, Timestamp.from(Instant.now())));
    if (updated != 1) throw notFound(analysisId);
  }

  @Override
  public Optional<AnalysisPO> findById(long analysisId) {
    return Optional.ofNullable(
        mapper.selectOne(
            Wrappers.<AnalysisPO>lambdaQuery()
                .eq(AnalysisPO::getProjectId, projectId())
                .eq(AnalysisPO::getId, analysisId)));
  }

  @Override
  public List<AnalysisPO> list() {
    return mapper.selectList(
        Wrappers.<AnalysisPO>lambdaQuery()
            .eq(AnalysisPO::getProjectId, projectId())
            .orderByDesc(AnalysisPO::getUpdateTime)
            .orderByDesc(AnalysisPO::getId));
  }

  @Override
  public void delete(long analysisId) {
    int deleted = mapper.delete(
        Wrappers.<AnalysisPO>lambdaQuery()
            .eq(AnalysisPO::getProjectId, projectId())
            .eq(AnalysisPO::getId, analysisId));
    if (deleted != 1) throw notFound(analysisId);
  }

  private long projectId() {
    return currentProject.requireProjectId();
  }

  private IllegalArgumentException notFound(long analysisId) {
    return new IllegalArgumentException("Analysis 不存在：" + analysisId);
  }
}
