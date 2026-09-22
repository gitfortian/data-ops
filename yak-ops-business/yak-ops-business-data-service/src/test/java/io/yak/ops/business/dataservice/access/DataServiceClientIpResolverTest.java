package io.yak.ops.business.dataservice.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class DataServiceClientIpResolverTest {

  @Test
  void forwardingHeadersAreIgnoredWhenDirectPeerIsNotTrusted() {
    DataServiceClientIpResolver resolver = new DataServiceClientIpResolver("");
    HttpServletRequest request = request(
        "10.0.0.10", "203.0.113.7", "198.51.100.8");

    assertThat(resolver.resolve(request)).isEqualTo("10.0.0.10");
  }

  @Test
  void trustedProxyChainResolvesFirstUntrustedHopFromTheRight() {
    DataServiceClientIpResolver resolver = new DataServiceClientIpResolver("10.0.0.0/8");
    HttpServletRequest request = request(
        "10.0.0.10", "198.51.100.7, 10.0.0.9", null);

    assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
  }

  @Test
  void prependedSpoofedForwardedAddressCannotOverrideExternalClient() {
    DataServiceClientIpResolver resolver = new DataServiceClientIpResolver("10.0.0.0/8");
    HttpServletRequest request = request(
        "10.0.0.10", "1.1.1.1, 198.51.100.7, 10.0.0.9", null);

    assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
  }

  @Test
  void malformedForwardedChainFallsBackToDirectTrustedPeer() {
    DataServiceClientIpResolver resolver = new DataServiceClientIpResolver("10.0.0.0/8");
    HttpServletRequest request = request(
        "10.0.0.10", "not-an-ip", "198.51.100.8");

    assertThat(resolver.resolve(request)).isEqualTo("10.0.0.10");
  }

  @Test
  void realIpIsUsedOnlyBehindTrustedPeerWhenForwardedForIsAbsent() {
    DataServiceClientIpResolver resolver = new DataServiceClientIpResolver("10.0.0.0/8");
    HttpServletRequest request = request("10.0.0.10", null, "198.51.100.8");

    assertThat(resolver.resolve(request)).isEqualTo("198.51.100.8");
  }

  private HttpServletRequest request(String remote, String forwardedFor, String realIp) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn(remote);
    when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
    when(request.getHeader("X-Real-IP")).thenReturn(realIp);
    return request;
  }
}
