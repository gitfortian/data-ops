package io.yak.ops.business.dashboard.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.dashboard.DashboardService;
import io.yak.ops.business.dashboard.composition.DashboardCompositionNormalizer;
import io.yak.ops.business.dashboard.composition.DashboardFilterPolicy;
import io.yak.ops.business.dashboard.composition.DashboardInteractionPolicy;
import io.yak.ops.business.dashboard.composition.DashboardJsonPolicy;
import io.yak.ops.business.dashboard.composition.DashboardLayoutPolicy;
import io.yak.ops.business.dashboard.composition.DashboardWidgetPolicy;
import io.yak.ops.business.dashboard.definition.DashboardManager;
import io.yak.ops.business.dashboard.gateway.analysis.AnalysisDashboardAdapter;
import io.yak.ops.business.dashboard.gateway.dataset.DatasetDashboardAdapter;
import io.yak.ops.business.dashboard.gateway.lineage.LineageDashboardAdapter;
import io.yak.ops.business.dashboard.lineage.DashboardInlineLineageExtractor;
import io.yak.ops.business.dashboard.lineage.DashboardLineageRefreshListener;
import io.yak.ops.business.dashboard.lineage.DashboardLineageSynchronizer;
import io.yak.ops.business.dashboard.publication.DashboardEffectiveSnapshotReader;
import io.yak.ops.business.dashboard.publication.DashboardPublisher;
import io.yak.ops.business.dashboard.read.DashboardOverviewReader;
import io.yak.ops.business.dashboard.read.DashboardReader;
import io.yak.ops.business.dashboard.repository.DashboardOverviewRepositoryAdapter;
import io.yak.ops.business.dashboard.repository.DashboardReferenceRepositoryAdapter;
import io.yak.ops.business.dashboard.repository.DashboardRepositoryAdapter;
import io.yak.ops.business.dashboard.repository.DashboardVersionRepositoryAdapter;
import io.yak.ops.business.dashboard.repository.codec.DashboardJsonCodec;
import io.yak.ops.business.dashboard.version.DashboardVersionAppender;
import io.yak.ops.business.dashboard.version.DashboardVersionManager;
import io.yak.ops.business.dashboard.version.DashboardVersionReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

class DashboardRoleConventionTest {

  @Test
  void onlyStableFacadeUsesServiceStereotypeAmongDashboardApplicationRoles() {
    assertThat(DashboardService.class.isAnnotationPresent(Service.class)).isTrue();

    for (Class<?> type : internalRoles()) {
      assertThat(type.isAnnotationPresent(Service.class)).as(type.getName()).isFalse();
    }
  }

  @Test
  void internalApplicationRolesUseComponentStereotype() {
    for (Class<?> type : internalRoles()) {
      assertThat(type.isAnnotationPresent(Component.class)).as(type.getName()).isTrue();
    }
  }

  @Test
  void persistenceAdaptersUseRepositoryStereotype() {
    for (Class<?> type : List.of(
        DashboardRepositoryAdapter.class,
        DashboardVersionRepositoryAdapter.class,
        DashboardReferenceRepositoryAdapter.class,
        DashboardOverviewRepositoryAdapter.class)) {
      assertThat(type.isAnnotationPresent(Repository.class)).as(type.getName()).isTrue();
      assertThat(type.isAnnotationPresent(Service.class)).as(type.getName()).isFalse();
    }
  }

  private List<Class<?>> internalRoles() {
    return List.of(
        DashboardManager.class,
        DashboardReader.class,
        DashboardOverviewReader.class,
        DashboardVersionAppender.class,
        DashboardVersionManager.class,
        DashboardVersionReader.class,
        DashboardPublisher.class,
        DashboardEffectiveSnapshotReader.class,
        DashboardCompositionNormalizer.class,
        DashboardWidgetPolicy.class,
        DashboardLayoutPolicy.class,
        DashboardFilterPolicy.class,
        DashboardInteractionPolicy.class,
        DashboardJsonPolicy.class,
        AnalysisDashboardAdapter.class,
        DatasetDashboardAdapter.class,
        LineageDashboardAdapter.class,
        DashboardLineageRefreshListener.class,
        DashboardLineageSynchronizer.class,
        DashboardInlineLineageExtractor.class,
        DashboardJsonCodec.class);
  }
}
