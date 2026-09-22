package io.yak.ops.business.dashboard.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dashboard.change.DashboardChangedEvent;
import io.yak.ops.business.dashboard.composition.DashboardCompositionNormalizer;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardDetail;
import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.read.DashboardReader;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DashboardVersionManagerTest {

  @Test
  void saveCreatesVersionAndPublishesProjectScopedChangeFact() {
    DashboardReader dashboards = mock(DashboardReader.class);
    DashboardVersionReader versionReader = mock(DashboardVersionReader.class);
    DashboardCompositionNormalizer composition = mock(DashboardCompositionNormalizer.class);
    DashboardVersionAppender appender = mock(DashboardVersionAppender.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    DashboardVersionManager manager = new DashboardVersionManager(
        dashboards, versionReader, composition, appender, currentProject(23L), events);
    DashboardDraft draft = new DashboardDraft("V", null, null, null, List.of(), List.of(), List.of());
    DashboardDetail detail = new DashboardDetail(
        mock(DashboardAsset.class), null, null, List.of(), List.of(), List.of(), List.of());

    when(dashboards.require(3L)).thenReturn(mock(DashboardAsset.class));
    when(composition.normalize(draft)).thenReturn(draft);
    when(dashboards.get(3L)).thenReturn(detail);

    DashboardDetail saved = manager.saveVersion(3L, draft);

    assertThat(saved).isSameAs(detail);
    verify(appender).appendNext(3L, draft);
    verify(events).publishEvent(DashboardChangedEvent.refreshed(23L, 3L));
  }

  private CurrentProject currentProject(long projectId) {
    return () -> Optional.of(new ProjectContext(projectId, "P" + projectId));
  }
}
