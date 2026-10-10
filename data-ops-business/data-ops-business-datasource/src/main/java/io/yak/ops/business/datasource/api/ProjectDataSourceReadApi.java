package io.yak.ops.business.datasource.api;

/**
 * Source-owner read capability for other domains. Resolves through the existing
 * project-scoped datasource repository; never exports connection parameters or SQL.
 * Permission checks remain the caller's responsibility and must run per request.
 */
public interface ProjectDataSourceReadApi {
  /** Fails if the datasource is absent from the current project. */
  void requireReadableSource(long dataSourceId);
}
