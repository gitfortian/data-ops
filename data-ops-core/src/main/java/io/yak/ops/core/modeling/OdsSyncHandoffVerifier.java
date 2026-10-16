package io.yak.ops.core.modeling;

/**
 * Cross-domain verification port: Offline Sync may ask Modeling to confirm
 * source, current published ODS and a fresh target observation.
 * Does not grant DDL, task creation, or execution privileges.
 */
public interface OdsSyncHandoffVerifier {
  void verify(long projectId, long modelId, int versionNo, long observationId,
              long sourceDatasourceId, String sourceTable,
              long sinkDatasourceId, String sinkTable);
}
