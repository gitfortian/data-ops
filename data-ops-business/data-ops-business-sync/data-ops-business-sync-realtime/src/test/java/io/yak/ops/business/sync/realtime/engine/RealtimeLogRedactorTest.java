package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RealtimeLogRedactorTest {

  @Test
  void masksCommonSecretShapes() {
    String value =
        new RealtimeLogRedactor()
            .redact("password=top-secret token:abc jdbc:mysql://root:db-secret@mysql/shop");
    assertThat(value)
        .doesNotContain("top-secret", "abc", "db-secret")
        .contains("password=******", "token:******", "root:******@mysql");
  }
}
