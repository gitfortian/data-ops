package io.yak.ops.business.metric.publication;

/** Current-user authorization seam for internal Metric publication evidence reads. */
@FunctionalInterface
public interface MetricPublicationReadAccess {
  boolean canRead(Long projectId);
}
