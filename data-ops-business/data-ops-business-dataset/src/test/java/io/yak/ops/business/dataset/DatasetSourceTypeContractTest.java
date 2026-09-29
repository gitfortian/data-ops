package io.yak.ops.business.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatasetSourceTypeContractTest {

  @Test
  void persistedSourceTypeValuesStayStable() {
    assertThat(DatasetSourceType.values())
        .containsExactlyInAnyOrder(
            DatasetSourceType.QUERY_REVISION,
            DatasetSourceType.SQL_QUERY,
            DatasetSourceType.TABLE,
            DatasetSourceType.VIEW);
  }
}
