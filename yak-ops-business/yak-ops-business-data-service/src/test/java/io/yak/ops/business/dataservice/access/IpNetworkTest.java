package io.yak.ops.business.dataservice.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IpNetworkTest {

  @Test
  void normalizesIpv4HostBitsAndMatchesCidr() {
    assertThat(IpNetwork.normalizeNetwork("10.20.30.40/24"))
        .isEqualTo("10.20.30.0/24");
    assertThat(IpNetwork.contains("10.20.0.0/16", "10.20.99.8")).isTrue();
    assertThat(IpNetwork.contains("10.20.0.0/16", "10.21.0.1")).isFalse();
  }

  @Test
  void exactAddressActsAsHostNetwork() {
    assertThat(IpNetwork.normalizeNetwork("203.0.113.7")).isEqualTo("203.0.113.7");
    assertThat(IpNetwork.contains("203.0.113.7", "203.0.113.7")).isTrue();
    assertThat(IpNetwork.contains("203.0.113.7", "203.0.113.8")).isFalse();
  }

  @Test
  void supportsIpv6Networks() {
    assertThat(IpNetwork.contains("2001:db8::/32", "2001:db8:1234::1")).isTrue();
    assertThat(IpNetwork.contains("2001:db8::/32", "2001:db9::1")).isFalse();
  }

  @Test
  void rejectsHostnamesAndInvalidPrefix() {
    assertThatThrownBy(() -> IpNetwork.normalizeNetwork("example.com"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> IpNetwork.normalizeNetwork("10.0.0.1/33"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
