package io.yak.ops.business.metric.publication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CurrentUserMetricPublicationReadAccessTest {

  @Test
  void deniesMetricPublicationReadWhenNoAuthenticatedPermissionContextExists() {
    assertThat(new CurrentUserMetricPublicationReadAccess().canRead(12L)).isFalse();
  }

  @Test
  void deniesMetricPublicationReadWhenProjectIsMissing() {
    assertThat(new CurrentUserMetricPublicationReadAccess().canRead(null)).isFalse();
  }
}
