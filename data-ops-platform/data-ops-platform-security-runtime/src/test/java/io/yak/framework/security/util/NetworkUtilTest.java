package io.yak.framework.security.util;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class NetworkUtilTest {
  @Test
  void shouldRespectProxyChainAndFallbackToRemoteAddress() {
    MockHttpServletRequest proxy = new MockHttpServletRequest();
    proxy.setRemoteAddr("203.0.113.20");
    proxy.addHeader("X-Forwarded-For", "unknown, 198.51.100.3, 203.0.113.20");
    assertThat(NetworkUtil.getRealIpAddress(proxy)).isEqualTo("198.51.100.3");
    MockHttpServletRequest direct = new MockHttpServletRequest();
    direct.setRemoteAddr("203.0.113.21");
    assertThat(NetworkUtil.getRealIpAddress(direct)).isEqualTo("203.0.113.21");
  }

  @Test
  void shouldKeepRequestContextAndBackgroundFallback() {
    RequestContextHolder.resetRequestAttributes();
    assertThat(NetworkUtil.getRealIpAddressOrDefault("192.0.2.42")).isEqualTo("192.0.2.42");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("198.51.100.71");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      assertThat(NetworkUtil.getRealIpAddress()).isEqualTo("198.51.100.71");
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
  }
}
