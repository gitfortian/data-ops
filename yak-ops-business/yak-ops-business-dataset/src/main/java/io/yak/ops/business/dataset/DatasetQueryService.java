package io.yak.ops.business.dataset;

import io.yak.ops.business.dataset.definition.DatasetReader;
import io.yak.ops.business.dataset.observability.DatasetQueryPerformanceReader;
import io.yak.ops.business.dataset.query.DatasetQueryCoordinator;
import io.yak.ops.core.project.CurrentProject;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Stable application entry point used by Dashboard/Chart consumers. */
@Service
public class DatasetQueryService {

  private final DatasetQueryCoordinator coordinator;
  private final DatasetQueryPerformanceReader performanceReader;
  private final DatasetReader datasetReader;
  private final CurrentProject currentProject;

  @Autowired
  public DatasetQueryService(
      DatasetQueryCoordinator coordinator,
      DatasetQueryPerformanceReader performanceReader,
      DatasetReader datasetReader,
      CurrentProject currentProject) {
    this.coordinator = coordinator;
    this.performanceReader = performanceReader;
    this.datasetReader = datasetReader;
    this.currentProject = currentProject;
  }

  public DatasetQueryService(
      DatasetQueryCoordinator coordinator,
      DatasetQueryPerformanceReader performanceReader) {
    this(coordinator, performanceReader, null,
        Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  public DatasetQueryResult query(long datasetId, DatasetQueryRequest request) {
    return coordinator.query(datasetId, request);
  }

  public List<DatasetQueryPerformance> recentPerformance(Set<Long> datasetIds, int limit) {
    return recentPerformance(datasetIds, Set.of(), Set.of(), null, limit);
  }

  public List<DatasetQueryPerformance> recentPerformance(
      Set<Long> datasetIds, Set<String> queryIds, int limit) {
    return recentPerformance(datasetIds, queryIds, Set.of(), null, limit);
  }

  public List<DatasetQueryPerformance> recentPerformance(
      Set<Long> datasetIds,
      Set<String> queryIds,
      Set<DatasetQueryStatus> statuses,
      Long minTotalMillis,
      int limit) {
    ScopedDatasetIds scope = scopedDatasetIds(datasetIds);
    if (scope.projectScoped() && scope.datasetIds().isEmpty()) return List.of();
    return performanceReader.recent(
        scope.datasetIds(), queryIds, statuses, minTotalMillis, limit);
  }

  private ScopedDatasetIds scopedDatasetIds(Set<Long> requested) {
    if (!currentProject.isPresent() || datasetReader == null) {
      return new ScopedDatasetIds(requested == null ? Set.of() : requested, false);
    }
    Set<Long> allowed = new HashSet<>();
    for (Dataset dataset : datasetReader.list()) allowed.add(dataset.id());
    if (requested == null || requested.isEmpty()) {
      return new ScopedDatasetIds(Set.copyOf(allowed), true);
    }
    allowed.retainAll(requested);
    return new ScopedDatasetIds(Set.copyOf(allowed), true);
  }

  private record ScopedDatasetIds(Set<Long> datasetIds, boolean projectScoped) {}
}
