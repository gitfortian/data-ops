package io.yak.ops.business.dashboard.controller.v1.converter;

import io.yak.ops.business.dashboard.controller.v1.vo.DashboardOverviewView;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardOverview;
import org.springframework.stereotype.Component;

/** Dashboard overview Domain -> HTTP view conversion. */
@Component
public class DashboardOverviewViewConverter {

  public DashboardOverviewView convert(DashboardOverview source) {
    return new DashboardOverviewView(
        source.dashboardCount(),
        source.publishedDashboardCount(),
        source.recentDashboards().stream().map(this::item).toList());
  }

  private DashboardOverviewView.Item item(DashboardAsset source) {
    return new DashboardOverviewView.Item(
        source.id(),
        source.name(),
        source.description(),
        source.currentVersionId(),
        source.currentVersionNo(),
        source.publishedVersionId(),
        source.publishedVersionNo(),
        source.publishedTime(),
        source.createTime(),
        source.updateTime());
  }
}
