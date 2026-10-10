package io.yak.ops.business.metadata.api;

/**
 * Metadata's required source-existence port: the Boot composition layer must implement
 * this with the original project-scoped Datasource owner. No connection parameters leak.
 */
@FunctionalInterface
public interface PhysicalSourceAccessPort {
  void requireCurrentProjectSource(long dataSourceId);
}
