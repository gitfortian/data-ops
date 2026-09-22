package io.yak.ops.business.dashboard.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dashboard.change.DashboardChangedEvent;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import io.yak.ops.business.dashboard.publication.DashboardEffectiveSnapshotReader;
import io.yak.ops.business.dashboard.publication.DashboardEffectiveSnapshotReader.EffectiveSnapshot;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DashboardLineageRefreshListenerTest {

  @Test
  void refreshRestoresProjectAndUsesEffectiveSnapshot() {
    DashboardEffectiveSnapshotReader snapshots = mock(DashboardEffectiveSnapshotReader.class);
    DashboardLineageSynchronizer lineage = mock(DashboardLineageSynchronizer.class);
    RecordingProjectContextScope scope = new RecordingProjectContextScope();
    DashboardLineageRefreshListener listener =
        new DashboardLineageRefreshListener(snapshots, lineage, scope);
    DashboardAsset dashboard = dashboard();
    DashboardVersion version = new DashboardVersion(12L, 7L, 3, "D", null, null, Instant.EPOCH);
    DashboardVersionSnapshot snapshot =
        new DashboardVersionSnapshot(version, null, List.of(), List.of(), List.of());
    when(snapshots.read(7L)).thenReturn(new EffectiveSnapshot(dashboard, snapshot, true));

    listener.refresh(DashboardChangedEvent.refreshed(23L, 7L));

    assertThat(scope.context.projectId()).isEqualTo(23L);
    verify(lineage).syncVersion(dashboard, version, List.of(), true);
  }

  @Test
  void projectionFailureDoesNotEscapeCommittedBusinessMutation() {
    DashboardEffectiveSnapshotReader snapshots = mock(DashboardEffectiveSnapshotReader.class);
    DashboardLineageSynchronizer lineage = mock(DashboardLineageSynchronizer.class);
    DashboardLineageRefreshListener listener = new DashboardLineageRefreshListener(
        snapshots, lineage, new RecordingProjectContextScope());
    doThrow(new IllegalStateException("lineage unavailable")).when(snapshots).read(7L);

    assertThatCode(() -> listener.refresh(DashboardChangedEvent.refreshed(23L, 7L)))
        .doesNotThrowAnyException();
  }

  @Test
  void deleteOnlyClearsDashboardEvidenceInsideOwningProject() {
    DashboardEffectiveSnapshotReader snapshots = mock(DashboardEffectiveSnapshotReader.class);
    DashboardLineageSynchronizer lineage = mock(DashboardLineageSynchronizer.class);
    RecordingProjectContextScope scope = new RecordingProjectContextScope();
    DashboardLineageRefreshListener listener =
        new DashboardLineageRefreshListener(snapshots, lineage, scope);

    listener.refresh(DashboardChangedEvent.deleted(23L, 7L));

    assertThat(scope.context.projectId()).isEqualTo(23L);
    verify(lineage).clear(7L);
  }

  private DashboardAsset dashboard() {
    return new DashboardAsset(
        7L, "D", null, 12L, 3, 12L, 3, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH);
  }

  private static final class RecordingProjectContextScope implements ProjectContextScope {
    private ProjectContext context;

    @Override
    public <T> T call(ProjectContext context, Supplier<T> action) {
      this.context = context;
      return action.get();
    }
  }
}
