package io.yak.ops.business.dashboard.repository;

import io.yak.ops.business.dashboard.dao.mapper.DashboardOverviewMapper;
import io.yak.ops.business.dashboard.dao.model.DashboardOverviewSummaryPO;
import io.yak.ops.business.dashboard.dao.model.DashboardPO;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.CurrentProject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for bounded Dashboard overview projections. */
@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DashboardOverviewRepositoryAdapter implements DashboardOverviewRepository {

  private final DashboardOverviewMapper mapper;
  private final CurrentProject currentProject;

  @Override
  public Summary summarize() {
    DashboardOverviewSummaryPO value = mapper.selectSummary(projectId());
    if (value == null) return new Summary(0L, 0L);
    return new Summary(number(value.getDashboardCount()), number(value.getPublishedDashboardCount()));
  }

  @Override
  public List<DashboardAsset> listRecent(int limit) {
    return mapper.selectRecent(projectId(), normalizeLimit(limit)).stream()
        .map(this::toDomain)
        .toList();
  }

  private DashboardAsset toDomain(DashboardPO value) {
    return new DashboardAsset(
        value.getId(),
        value.getName(),
        value.getDescription(),
        value.getCurrentVersionId(),
        number(value.getCurrentVersionNo()),
        value.getPublishedVersionId(),
        number(value.getPublishedVersionNo()),
        instant(value.getPublishedTime()),
        instant(value.getCreateTime()),
        instant(value.getUpdateTime()));
  }

  private int normalizeLimit(int limit) {
    return Math.max(1, Math.min(20, limit));
  }

  private long number(Long value) {
    return value == null ? 0L : Math.max(0L, value);
  }

  private int number(Integer value) {
    return value == null ? 0 : Math.max(0, value);
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private long projectId() {
    return currentProject.requireProjectId();
  }
}
