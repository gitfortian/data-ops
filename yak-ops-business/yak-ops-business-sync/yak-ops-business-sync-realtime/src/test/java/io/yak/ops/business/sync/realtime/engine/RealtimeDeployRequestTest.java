package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RealtimeDeployRequestTest {

  @Test
  void namedBindingsRemainRedactedAndAreClearedWithRequest() {
    RealtimeDeployRequest.CredentialBinding source =
        new RealtimeDeployRequest.CredentialBinding("reader", "source-secret");
    RealtimeDeployRequest.CredentialBinding sink =
        new RealtimeDeployRequest.CredentialBinding("writer", "sink-secret");
    RealtimeDeployRequest.CredentialBindings credentials =
        new RealtimeDeployRequest.CredentialBindings(source, sink);
    RealtimeDeployRequest request =
        new RealtimeDeployRequest(
            "password: ${SECRET:source.password}", "key", credentials);

    assertThat(request.source()).isSameAs(source);
    assertThat(request.sink()).isSameAs(sink);
    assertThat(request.toString()).doesNotContain("source-secret", "sink-secret");
    assertThat(source.toString()).doesNotContain("source-secret");

    request.close();

    assertThat(source.password()).containsOnly('\0');
    assertThat(sink.password()).containsOnly('\0');
  }
}
