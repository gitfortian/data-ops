package io.yak.ops.business.metric.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metric.dao.mapper.MetricValidationEvidenceMapper;
import io.yak.ops.business.metric.domain.MetricValidationEvidence;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.ProviderState;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.ValidationIssue;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.ValidationResult;
import io.yak.ops.common.bean.po.metric.MetricValidationEvidencePO;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Project-scoped adapter for append-only validation evidence. */
@Repository
public class MetricValidationEvidenceRepositoryAdapter
    implements MetricValidationEvidenceRepository {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final MetricValidationEvidenceMapper mapper;
  private final CurrentProject currentProject;

  public MetricValidationEvidenceRepositoryAdapter(
      MetricValidationEvidenceMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public MetricValidationEvidence append(MetricValidationEvidence evidence) {
    MetricValidationEvidencePO po = new MetricValidationEvidencePO();
    po.setProjectId(currentProject.requireProjectId());
    po.setMetricId(evidence.metricId());
    po.setMetricVersionId(evidence.metricVersionId());
    po.setMetricVersion(evidence.metricVersion());
    po.setResult(evidence.result().name());
    po.setProviderState(evidence.providerState().name());
    po.setIssuesJson(writeIssues(evidence.issues()));
    po.setProvider(evidence.provider());
    po.setSnapshotDigest(evidence.snapshotDigest());
    po.setCheckedBy(evidence.checkedBy());
    po.setCheckedAt(evidence.checkedAt());
    mapper.insert(po);
    return new MetricValidationEvidence(
        po.getId(), evidence.metricId(), evidence.metricVersionId(), evidence.metricVersion(),
        evidence.result(), evidence.providerState(), evidence.issues(), evidence.provider(),
        evidence.snapshotDigest(), evidence.checkedBy(), evidence.checkedAt());
  }

  @Override
  public List<MetricValidationEvidence> listByVersion(Long metricId, int metricVersion) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(new LambdaQueryWrapper<MetricValidationEvidencePO>()
        .eq(MetricValidationEvidencePO::getProjectId, projectId)
        .eq(MetricValidationEvidencePO::getMetricId, metricId)
        .eq(MetricValidationEvidencePO::getMetricVersion, metricVersion)
        .orderByDesc(MetricValidationEvidencePO::getCheckedAt)
        .orderByDesc(MetricValidationEvidencePO::getId))
        .stream().map(MetricValidationEvidenceRepositoryAdapter::toDomain).toList();
  }

  @Override
  public MetricValidationEvidence findLatestReady(Long metricId, int metricVersion) {
    Long projectId = currentProject.requireProjectId();
    MetricValidationEvidencePO po = mapper.selectOne(new LambdaQueryWrapper<MetricValidationEvidencePO>()
        .eq(MetricValidationEvidencePO::getProjectId, projectId)
        .eq(MetricValidationEvidencePO::getMetricId, metricId)
        .eq(MetricValidationEvidencePO::getMetricVersion, metricVersion)
        .eq(MetricValidationEvidencePO::getResult, ValidationResult.PASSED.name())
        .eq(MetricValidationEvidencePO::getProviderState, ProviderState.READY.name())
        .orderByDesc(MetricValidationEvidencePO::getCheckedAt)
        .orderByDesc(MetricValidationEvidencePO::getId)
        .last("LIMIT 1"));
    return po == null ? null : toDomain(po);
  }

  @Override
  public boolean hasEvidence(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectCount(new LambdaQueryWrapper<MetricValidationEvidencePO>()
        .eq(MetricValidationEvidencePO::getProjectId, projectId)
        .eq(MetricValidationEvidencePO::getMetricId, metricId)) > 0;
  }

  private static MetricValidationEvidence toDomain(MetricValidationEvidencePO po) {
    return new MetricValidationEvidence(
        po.getId(),
        po.getMetricId(),
        po.getMetricVersionId(),
        po.getMetricVersion(),
        validationResult(po.getResult()),
        providerState(po.getProviderState()),
        readIssues(po.getIssuesJson()),
        po.getProvider(),
        po.getSnapshotDigest(),
        po.getCheckedBy(),
        po.getCheckedAt());
  }

  private static ValidationResult validationResult(String value) {
    if (value == null || value.isBlank()) return ValidationResult.NOT_APPLICABLE;
    return switch (value == null ? "" : value) {
      case "READY" -> ValidationResult.PASSED;
      case "BLOCKED" -> ValidationResult.FAILED;
      default -> ValidationResult.valueOf(value);
    };
  }

  private static ProviderState providerState(String value) {
    return value == null ? ProviderState.READY : ProviderState.valueOf(value);
  }

  private static String writeIssues(List<ValidationIssue> issues) {
    try {
      return OBJECT_MAPPER.writeValueAsString(issues == null ? List.of() : issues);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize metric validation issues", exception);
    }
  }

  private static List<ValidationIssue> readIssues(String issuesJson) {
    if (issuesJson == null || issuesJson.isBlank()) return List.of();
    try {
      return OBJECT_MAPPER.readerForListOf(ValidationIssue.class).readValue(issuesJson);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored metric validation issues are invalid", exception);
    }
  }
}
