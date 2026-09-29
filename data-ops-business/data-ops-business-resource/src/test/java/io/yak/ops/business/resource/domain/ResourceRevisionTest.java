package io.yak.ops.business.resource.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResourceRevisionTest {

  @Test
  void contentChangeAdvancesCurrentRevisionWithoutCreatingHistoricalState() {
    ResourceNode resource = new ResourceNode();
    resource.setVersion(4);
    resource.setFileSize(10L);
    resource.setChecksum("old");
    resource.setContentType("text/plain");

    ResourceRevision.current(resource)
        .next(20L, "new", "text/plain")
        .applyTo(resource);

    assertThat(resource.getVersion()).isEqualTo(5);
    assertThat(resource.getFileSize()).isEqualTo(20L);
    assertThat(resource.getChecksum()).isEqualTo("new");
  }
}
