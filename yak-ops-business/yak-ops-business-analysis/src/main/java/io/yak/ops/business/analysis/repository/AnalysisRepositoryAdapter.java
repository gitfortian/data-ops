package io.yak.ops.business.analysis.repository;

import io.yak.ops.business.analysis.dao.AnalysisDao;
import io.yak.ops.business.analysis.dao.model.AnalysisPO;
import io.yak.ops.business.analysis.domain.AnalysisAsset;
import io.yak.ops.business.analysis.domain.AnalysisDefinition;
import io.yak.ops.business.analysis.repository.codec.AnalysisJsonCodec;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@DependsOn("yakAnalysisFlyway")
@ConditionalOnDataSourceEnabled
public class AnalysisRepositoryAdapter implements AnalysisRepository {

  private final AnalysisDao dao;
  private final AnalysisJsonCodec jsonCodec;

  public AnalysisRepositoryAdapter(AnalysisDao dao, AnalysisJsonCodec jsonCodec) {
    this.dao = dao;
    this.jsonCodec = jsonCodec;
  }

  @Override
  public long insert(AnalysisDefinition definition) {
    return dao.insert(toPO(definition));
  }

  @Override
  public void update(long analysisId, AnalysisDefinition definition) {
    dao.update(analysisId, toPO(definition));
  }

  @Override
  public Optional<AnalysisAsset> findById(long analysisId) {
    return dao.findById(analysisId).map(this::toDomain);
  }

  @Override
  public List<AnalysisAsset> list() {
    return dao.list().stream().map(this::toDomain).toList();
  }

  @Override
  public void delete(long analysisId) {
    dao.delete(analysisId);
  }

  private AnalysisPO toPO(AnalysisDefinition definition) {
    AnalysisPO value = new AnalysisPO();
    value.setName(definition.name());
    value.setDescription(definition.description());
    value.setDatasetId(definition.datasetId());
    value.setChartType(definition.chartType().name());
    value.setQuerySpecJson(jsonCodec.writeQuerySpec(definition.querySpec()));
    value.setVisualConfigJson(jsonCodec.writeVisualConfig(definition.visualConfig()));
    return value;
  }

  private AnalysisAsset toDomain(AnalysisPO value) {
    long id = value.getId();
    return new AnalysisAsset(
        id,
        value.getName(),
        value.getDescription(),
        value.getDatasetId(),
        AnalysisChartType.valueOf(value.getChartType()),
        jsonCodec.readQuerySpec(value.getQuerySpecJson(), id),
        jsonCodec.readVisualConfig(value.getVisualConfigJson(), id),
        value.getCreateTime() == null ? null : value.getCreateTime().toInstant(),
        value.getUpdateTime() == null ? null : value.getUpdateTime().toInstant());
  }
}
