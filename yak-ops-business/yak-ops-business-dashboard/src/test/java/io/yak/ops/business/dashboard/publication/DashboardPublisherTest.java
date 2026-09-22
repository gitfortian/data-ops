package io.yak.ops.business.dashboard.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dashboard.change.DashboardChangedEvent;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardDetail;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import io.yak.ops.business.dashboard.read.DashboardReader;
import io.yak.ops.business.dashboard.repository.DashboardRepository;
import io.yak.ops.business.dashboard.repository.DashboardVersionRepository;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DashboardPublisherTest {

  @Test
  void publishMovesPointerAndPublishesProjectScopedChangeFact() {
    DashboardReader reader = mock(DashboardReader.class);
    DashboardRepository dashboards = mock(DashboardRepository.class);
    DashboardVersionRepository versions = mock(DashboardVersionRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    DashboardPublisher publisher = new DashboardPublisher(
        reader, dashboards, versions, currentProject(23L), events);
    DashboardAsset dashboard = new DashboardAsset(
        7L, "D", null, 12L, 3, 10L, 2, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH);
    DashboardVersion version = new DashboardVersion(12L, 7L, 3, "D", null, null, Instant.EPOCH);
    DashboardVersionSnapshot snapshot =
        new DashboardVersionSnapshot(version, null, List.of(), List.of(), List.of());
    DashboardDetail detail = new DashboardDetail(
        dashboard, version, null, List.of(version), List.of(), List.of(), List.of());

    when(reader.require(7L)).thenReturn(dashboard);
    when(versions.findVersionSnapshot(12L)).thenReturn(Optional.of(snapshot));
    when(reader.get(7L)).thenReturn(detail);

    DashboardDetail published = publisher.publish(7L);

    assertThat(published).isSameAs(detail);
    verify(dashboards).updatePublishedVersion(7L, 12L, 3);
    verify(events).publishEvent(DashboardChangedEvent.refreshed(23L, 7L));
  }

  private CurrentProject currentProject(long projectId) {
    return () -> Optional.of(new ProjectContext(projectId, "P" + projectId));
  }
}
