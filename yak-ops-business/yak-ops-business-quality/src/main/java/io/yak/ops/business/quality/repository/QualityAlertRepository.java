package io.yak.ops.business.quality.repository;

import io.yak.ops.business.quality.domain.QualityDomain.AlertEventSpec;

/** Persistence port for quality alert events. */
public interface QualityAlertRepository {
  void insertAlertEvent(AlertEventSpec alert);
}
