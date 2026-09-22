package io.yak.ops.business.dataset.lineage;

import io.yak.ops.core.project.CurrentProject;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes Dataset lineage refresh requests without leaking Spring events into business roles. */
@Component
public class DatasetLineageRefreshPublisher {

  private final ApplicationEventPublisher eventPublisher;
  private final CurrentProject currentProject;

  public DatasetLineageRefreshPublisher(
      ApplicationEventPublisher eventPublisher, CurrentProject currentProject) {
    this.eventPublisher = eventPublisher;
    this.currentProject = currentProject;
  }

  public void request(long datasetId) {
    eventPublisher.publishEvent(
        new DatasetLineageRefreshRequested(currentProject.requireProjectId(), datasetId));
  }
}
