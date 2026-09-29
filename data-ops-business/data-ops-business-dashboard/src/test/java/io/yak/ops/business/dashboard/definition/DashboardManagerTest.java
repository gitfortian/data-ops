package io.yak.ops.business.dashboard.definition;

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
import io.yak.ops.business.dashboard.repository.DashboardRepository;
import io.yak.ops.business.dashboard.version.DashboardVersionAppender;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DashboardManagerTest {

  @Test
  void createAppendsV1AndPublishesProjectScopedChangeFact() {
    DashboardRepository repository = mock(DashboardRepository.class);
    DashboardCompositionNormalizer composition = mock(DashboardCompositionNormalizer.class);
    DashboardVersionAppender versions = mock(DashboardVersionAppender.class);
    DashboardReader reader = mock(DashboardReader.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    DashboardManager manager = new DashboardManager(
        repository, composition, versions, reader, currentProject(23L), events);
    DashboardDraft draft = new DashboardDraft("D", null, null, null, List.of(), List.of(), List.of());
    DashboardDetail detail = new DashboardDetail(
        mock(DashboardAsset.class), null, null, List.of(), List.of(), List.of(), List.of());

    when(composition.normalize(draft)).thenReturn(draft);
    when(repository.insertDashboard("D", null)).thenReturn(7L);
    when(reader.get(7L)).thenReturn(detail);

    DashboardDetail created = manager.create(draft);

    assertThat(created).isSameAs(detail);
    verify(versions).append(7L, 1, draft);
    verify(events).publishEvent(DashboardChangedEvent.refreshed(23L, 7L));
  }

  private CurrentProject currentProject(long projectId) {
    return () -> Optional.of(new ProjectContext(projectId, "P" + projectId));
  }
}
