package io.yak.ops.business.metric.repository;

import io.yak.ops.common.bean.po.metric.MetricActivePublicationPO;
import io.yak.ops.common.bean.po.metric.MetricPublicationEventPO;
import java.util.List;

/** Publication truth boundary: append-only lifecycle ledger plus one current active pointer. */
public interface MetricPublicationRepository {

  /** Locks the owning Metric row for a publish transaction and returns its current editable version. */
  Integer lockCurrentMetricVersion(Long metricId);

  MetricPublicationEventPO appendEvent(MetricPublicationEventPO event);

  MetricPublicationEventPO findEvent(Long eventId);

  List<MetricPublicationEventPO> listEvents(Long metricId);

  boolean hasEvents(Long metricId);

  MetricActivePublicationPO findActive(Long metricId);

  List<MetricActivePublicationPO> listActive();

  List<MetricActivePublicationPO> listActiveByMetricIds(List<Long> metricIds);

  /** Locks the active pointer so withdrawal cannot race with a replacement publish. */
  MetricActivePublicationPO findActiveForUpdate(Long metricId);

  void replaceActive(MetricActivePublicationPO pointer);

  boolean clearActive(Long metricId, Long expectedPublicationEventId);
}
