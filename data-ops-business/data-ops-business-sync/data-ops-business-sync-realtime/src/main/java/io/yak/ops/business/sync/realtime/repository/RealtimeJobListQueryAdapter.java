package io.yak.ops.business.sync.realtime.repository;

import io.yak.ops.business.sync.realtime.dao.RealtimeJobDao;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobListRow;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.repository.support.RealtimeJsonCodec;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class RealtimeJobListQueryAdapter implements RealtimeJobListQuery {

  private final RealtimeJobDao dao;
  private final RealtimeJsonCodec json;

  public RealtimeJobListQueryAdapter(RealtimeJobDao dao, RealtimeJsonCodec json) {
    this.dao = dao;
    this.json = json;
  }

  @Override
  public RealtimeJobPage page(
      int pageNo,
      int pageSize,
      String keyword,
      Long id,
      String releaseState,
      String stateGroup) {
    int normalizedSize = Math.max(1, Math.min(pageSize, 100));
    int normalizedPage = Math.max(1, pageNo);
    String pattern = StringUtils.hasText(keyword) ? "%" + keyword.trim() + "%" : null;
    String normalizedReleaseState = normalizeReleaseState(releaseState);
    String normalizedStateGroup = normalizeStateGroup(stateGroup);
    long total = dao.countPage(pattern, id, normalizedReleaseState, normalizedStateGroup);
    List<RealtimeJobView> records =
        dao.page(
                pattern,
                id,
                normalizedReleaseState,
                normalizedStateGroup,
                normalizedSize,
                (normalizedPage - 1) * normalizedSize)
            .stream()
            .map(this::mapView)
            .toList();
    return new RealtimeJobPage(records, total, normalizedPage, normalizedSize);
  }

  private String normalizeReleaseState(String releaseState) {
    if (!StringUtils.hasText(releaseState)) return null;
    String normalized = releaseState.trim().toUpperCase(Locale.ROOT);
    if (!"DRAFT".equals(normalized) && !"PUBLISHED".equals(normalized)) {
      throw new IllegalArgumentException("不支持的实时任务发布状态：" + releaseState);
    }
    return normalized;
  }

  private String normalizeStateGroup(String stateGroup) {
    if (!StringUtils.hasText(stateGroup)) return null;
    String normalized = stateGroup.trim().toUpperCase(Locale.ROOT);
    if (!List.of("RUNNING", "STOPPED", "ABNORMAL").contains(normalized)) {
      throw new IllegalArgumentException("不支持的实时任务状态筛选：" + stateGroup);
    }
    return normalized;
  }

  private RealtimeJobView mapView(RealtimeJobListRow row) {
    RealtimeJobView.Deployment deployment = null;
    if (row.getDeploymentId() != null) {
      deployment =
          new RealtimeJobView.Deployment(
              row.getDeploymentId(),
              value(row.getDeploymentDefinitionVersion()),
              row.getDeploymentSpecSummary(),
              row.getDeploymentConfigDigest(),
              row.getDeploymentIdempotencyKey(),
              row.getDeploymentGatewayJobId(),
              row.getDeploymentRuntimeRevision(),
              json.readEnvironmentSnapshot(row.getDeploymentRuntimeEnvironmentSnapshotJson()),
              row.getDeploymentStatus(),
              Boolean.TRUE.equals(row.getDeploymentResultUncertain()),
              row.getDeploymentErrorMessage(),
              row.getDeploymentCreateTime(),
              row.getDeploymentUpdateTime());
    }
    return new RealtimeJobView(
        row.getId(),
        row.getJobName(),
        row.getDescription(),
        json.readSpec(row.getSpecJson()),
        row.getRuntimeEnvironmentId(),
        row.getReleaseState(),
        row.getDesiredState(),
        row.getObservedState(),
        value(row.getDefinitionVersion()),
        row.getPublishedVersion(),
        row.getConfigDigest(),
        row.getLastError(),
        row.getCreateTime(),
        row.getUpdateTime(),
        Boolean.TRUE.equals(row.getPublishedUpdateAvailable()),
        deployment);
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
