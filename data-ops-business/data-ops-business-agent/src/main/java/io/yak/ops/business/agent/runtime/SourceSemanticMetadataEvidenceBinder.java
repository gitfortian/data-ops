package io.yak.ops.business.agent.runtime;

import io.yak.ops.business.metadata.api.PhysicalScopeEvidenceQueryApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * F-039 trusted boundary between Metadata owner's bounded, project-scoped catalog facts
 * and Agent's exact immutable scope. Not a DataSource permission grant.
 *
 * Source permission MUST be checked by an independently authenticated DataSource owner
 * before calling this method; evidence-only project visibility never authorizes execution.
 */
public final class SourceSemanticMetadataEvidenceBinder {
  private final PhysicalScopeEvidenceQueryApi metadata;
  private final DatasourceReadAuthorization authorization;

  /**
   * Application-provided current-user DataSource READ authorization check, with trusted
   * principal and current project resolution. Must reject withdrawn access or unknown IDs.
   */
  @FunctionalInterface
  public interface DatasourceReadAuthorization {
    void requireReadAccess(long currentProjectId, String datasourceId);
  }

  public SourceSemanticMetadataEvidenceBinder(PhysicalScopeEvidenceQueryApi metadata,
      DatasourceReadAuthorization authorization) {
    this.metadata = Objects.requireNonNull(metadata);
    this.authorization = Objects.requireNonNull(authorization);
  }

  /** No page/row/credential access. Exactly selected columns must exist in Metadata evidence. */
  public SourceSemanticScope bind(long trustedProjectId, List<String> tableKeys) {
    if (trustedProjectId <= 0) throw new IllegalArgumentException("[F039_PROJECT_REQUIRED]");
    if (tableKeys == null || tableKeys.isEmpty() || tableKeys.size() > 20) {
      throw new IllegalArgumentException("[F039_TABLE_SELECTION_BOUNDS]");
    }
    // Project-scoped metadata lookup; do NOT accept an arbitrary DTO from client/model.
    var evidence = metadata.readSelectedTables(tableKeys);
    if (evidence == null || evidence.projectId() != trustedProjectId) {
      throw new IllegalStateException("[F039_METADATA_PROJECT_MISMATCH]");
    }
    authorization.requireReadAccess(trustedProjectId, evidence.dataSourceId());
    List<SourceSemanticScope.Table> tables = new ArrayList<>();
    for (var table : evidence.tables()) {
      var colIds = table.columns().stream().map(
          c -> SourceSemanticScope.required(c.name(), "columnName") + "@"
              + SourceSemanticScope.required(c.contentHash(), "columnHash")).toList();
      // This hash originates from the Metadata owner (not a model, UI or guessed DDL).
      tables.add(new SourceSemanticScope.Table(table.assetKey(), table.contentHash(), colIds));
    }
    var scope = new SourceSemanticScope(trustedProjectId, evidence.dataSourceId(),
        evidence.database(), evidence.schema(), evidence.collectJobId(), tables);
    if (scope.tables().size() != tableKeys.size()
        || !scope.tables().stream().map(SourceSemanticScope.Table::assetKey).toList()
            .equals(tableKeys.stream().sorted().toList())) {
      throw new IllegalStateException("[F039_INCOMPLETE_METADATA_SELECTION]");
    }
    return scope;
  }

  /** Fresh authorization and evidence equality for task resume/admission, fail-closed on drift. */
  public SourceSemanticScope recheck(SourceSemanticScope frozen) {
    Objects.requireNonNull(frozen);
    var current = bind(frozen.projectId(),
        frozen.tables().stream().map(SourceSemanticScope.Table::assetKey).toList());
    if (!current.equals(frozen) || !current.fingerprint().equals(frozen.fingerprint())) {
      throw new IllegalStateException("[F039_METADATA_OR_AUTHORIZATION_DRIFT]");
    }
    return current;
  }
}
