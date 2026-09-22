package io.yak.ops.common.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublishStateTest {

  @Test
  void parsesOwnNamesRoundTrip() {
    for (PublishState state : PublishState.values()) {
      assertThat(PublishState.of(state.name())).isEqualTo(state);
    }
  }

  @Test
  void mapsLegacyAliases() {
    assertThat(PublishState.of("ONLINE")).isEqualTo(PublishState.PUBLISHED);
    assertThat(PublishState.of("active")).isEqualTo(PublishState.PUBLISHED);
    assertThat(PublishState.of("OFF")).isEqualTo(PublishState.OFFLINE);
  }

  @Test
  void fallsBackToDraftForBlankOrUnknown() {
    assertThat(PublishState.of(null)).isEqualTo(PublishState.DRAFT);
    assertThat(PublishState.of("  ")).isEqualTo(PublishState.DRAFT);
    assertThat(PublishState.of("WHATEVER")).isEqualTo(PublishState.DRAFT);
  }

  @Test
  void matchesComparesLeniently() {
    assertThat(PublishState.PUBLISHED.matches("ONLINE")).isTrue();
    assertThat(PublishState.DRAFT.matches("PUBLISHED")).isFalse();
  }
}
