package io.yak.ops.business.workflow.repository;

import java.util.List;

/** Fallback used when the shared Yak business database is explicitly disabled. */
public final class NoopWorkflowDefinitionRepository implements WorkflowDefinitionRepository {

  public static final NoopWorkflowDefinitionRepository INSTANCE =
      new NoopWorkflowDefinitionRepository();

  private NoopWorkflowDefinitionRepository() {
  }

  @Override
  public List<DefinitionRecord> loadDefinitions() {
    return List.of();
  }

  @Override
  public List<VersionRecord> loadVersions(String workflowId) {
    return List.of();
  }

  @Override
  public void saveDefinition(DefinitionRecord definition) {
    // In-memory WorkflowDefinitionManager remains the fallback fact source.
  }

  @Override
  public void publish(DefinitionRecord definition, VersionRecord version) {
    // In-memory WorkflowDefinitionManager remains the fallback fact source.
  }

  @Override
  public void deleteDefinition(String workflowId) {
    // No durable state to remove.
  }

  @Override
  public boolean authoritative() {
    return false;
  }
}
