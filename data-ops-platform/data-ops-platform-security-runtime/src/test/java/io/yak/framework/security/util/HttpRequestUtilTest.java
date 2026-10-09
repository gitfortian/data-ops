package io.yak.framework.security.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class HttpRequestUtilTest {

  @Test
  void clientIdentityHeadersAreNeverTrusted() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-SSO-USER", "attacker");
    request.addHeader("X-SSO-USER-ID", "999");

    assertNull(HttpRequestUtil.getOperator(request));
    assertNull(HttpRequestUtil.getOperatorId(request));
    assertNull(request.getSession(false));
  }
  @Test
  void projectIdHeaderRetainsMissingInvalidAndExplicitBehaviors() {
    MockHttpServletRequest missing = new MockHttpServletRequest();
    assertThat(HttpRequestUtil.getProjectId(missing)).isNull();
    assertThat(HttpRequestUtil.getProjectId(missing, 12)).isEqualTo(12L);
    MockHttpServletRequest valid = new MockHttpServletRequest();
    valid.addHeader(HttpRequestUtil.PROJECT_ID, "1024");
    assertThat(HttpRequestUtil.getProjectId(valid)).isEqualTo(1024L);
    MockHttpServletRequest invalid = new MockHttpServletRequest();
    invalid.addHeader(HttpRequestUtil.PROJECT_ID, "not-a-number");
    assertThat(HttpRequestUtil.getProjectId(invalid)).isNull();
  }

  @Test
  void requestHeaderLookupRemainsBoundToCurrentServletRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-TEST-HEADER", "present");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      assertThat(HttpRequestUtil.getHeaderValue("X-TEST-HEADER")).isEqualTo("present");
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
  }
}
