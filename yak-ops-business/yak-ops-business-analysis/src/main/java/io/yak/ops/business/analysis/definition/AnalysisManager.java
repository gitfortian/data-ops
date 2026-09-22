package io.yak.ops.business.analysis.definition;

import io.yak.ops.business.analysis.AnalysisDeletionGuard;
import io.yak.ops.business.analysis.domain.AnalysisAsset;
import io.yak.ops.business.analysis.domain.AnalysisDefinition;
import io.yak.ops.business.analysis.repository.AnalysisRepository;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Owns the mutation lifecycle of the current reusable Analysis definition. */
@Component
public class AnalysisManager {

  private final AnalysisRepository repository;
  private final AnalysisDefinitionNormalizer normalizer;
  private final AnalysisReader reader;
  private final CurrentProject currentProject;
  private final ApplicationEventPublisher events;
  private final List<AnalysisDeletionGuard> deletionGuards;

  public AnalysisManager(
      AnalysisRepository repository,
      AnalysisDefinitionNormalizer normalizer,
      AnalysisReader reader,
      CurrentProject currentProject,
      ApplicationEventPublisher events,
      List<AnalysisDeletionGuard> deletionGuards) {
    this.repository = repository;
    this.normalizer = normalizer;
    this.reader = reader;
    this.currentProject = currentProject;
    this.events = events;
    this.deletionGuards = deletionGuards == null ? List.of() : List.copyOf(deletionGuards);
  }

  @Transactional("yakBusinessTransactionManager")
  public AnalysisAsset create(AnalysisSaveCommand command) {
    long projectId = currentProject.requireProjectId();
    AnalysisDefinition definition = normalizer.normalize(command);
    long analysisId = repository.insert(definition);
    events.publishEvent(AnalysisChangedEvent.refreshed(projectId, analysisId));
    return reader.require(analysisId);
  }

  @Transactional("yakBusinessTransactionManager")
  public AnalysisAsset update(long analysisId, AnalysisSaveCommand command) {
    long projectId = currentProject.requireProjectId();
    AnalysisReader.requireAnalysisId(analysisId);
    reader.require(analysisId);
    AnalysisDefinition definition = normalizer.normalize(command);
    repository.update(analysisId, definition);
    events.publishEvent(AnalysisChangedEvent.refreshed(projectId, analysisId));
    return reader.require(analysisId);
  }

  @Transactional("yakBusinessTransactionManager")
  public void delete(long analysisId) {
    long projectId = currentProject.requireProjectId();
    AnalysisReader.requireAnalysisId(analysisId);
    reader.require(analysisId);
    deletionGuards.forEach(guard -> guard.requireDeletable(analysisId));
    repository.delete(analysisId);
    events.publishEvent(AnalysisChangedEvent.deleted(projectId, analysisId));
  }
}
