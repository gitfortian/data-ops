package io.yak.ops.business.dashboard.repository;

import io.yak.ops.business.dashboard.dao.DashboardDao;
import io.yak.ops.business.dashboard.dao.model.DashboardPO;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for Dashboard identity and version pointers. */
@Repository
@ConditionalOnDataSourceEnabled
public class DashboardRepositoryAdapter implements DashboardRepository {

  private final DashboardDao dao;

  public DashboardRepositoryAdapter(DashboardDao dao) {
    this.dao = dao;
  }

  @Override
  public long insertDashboard(String name, String description) {
    DashboardPO dashboard = new DashboardPO();
    dashboard.setName(name);
    dashboard.setDescription(description);
    requireInserted(dao.insertDashboard(dashboard), "创建 Dashboard 失败");
    if (dashboard.getId() == null) {
      throw new IllegalStateException("创建 Dashboard 后未返回主键");
    }
    return dashboard.getId();
  }

  @Override
  public void updateCurrentVersion(
      long dashboardId,
      long versionId,
      int versionNo,
      String name,
      String description) {
    if (dao.updateCurrentVersion(dashboardId, versionId, versionNo, name, description) != 1) {
      throw new IllegalArgumentException("Dashboard 不存在：" + dashboardId);
    }
  }

  @Override
  public void updatePublishedVersion(long dashboardId, long versionId, int versionNo) {
    if (dao.updatePublishedVersion(dashboardId, versionId, versionNo) != 1) {
      throw new IllegalArgumentException("Dashboard 不存在：" + dashboardId);
    }
  }

  @Override
  public Optional<DashboardAsset> findDashboard(long dashboardId) {
    return Optional.ofNullable(dao.selectDashboard(dashboardId)).map(this::toDomain);
  }

  @Override
  public List<DashboardAsset> listDashboards() {
    return dao.selectDashboards().stream().map(this::toDomain).toList();
  }

  @Override
  public void deleteDashboard(long dashboardId) {
    if (dao.deleteDashboard(dashboardId) != 1) {
      throw new IllegalArgumentException("Dashboard 不存在：" + dashboardId);
    }
  }

  private DashboardAsset toDomain(DashboardPO row) {
    return new DashboardAsset(
        row.getId(),
        row.getName(),
        row.getDescription(),
        row.getCurrentVersionId(),
        value(row.getCurrentVersionNo()),
        row.getPublishedVersionId(),
        value(row.getPublishedVersionNo()),
        instant(row.getPublishedTime()),
        instant(row.getCreateTime()),
        instant(row.getUpdateTime()));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private void requireInserted(int count, String message) {
    if (count != 1) throw new IllegalStateException(message);
  }
}
