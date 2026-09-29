package io.yak.ops.business.dataservice.access;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.domain.access.DataServiceIpAccessRule;
import io.yak.ops.business.dataservice.domain.access.IpAccessMode;
import io.yak.ops.business.dataservice.domain.access.IpAccessRuleType;
import io.yak.ops.business.dataservice.repository.DataServiceIpAccessRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataServiceIpAccessAuthorizerTest {

  private final DataServiceIpAccessRepository repository = mock(DataServiceIpAccessRepository.class);
  private final DataServiceIpAccessAuthorizer authorizer = new DataServiceIpAccessAuthorizer(repository);

  @Test
  void noneModeDoesNotRequireClientIp() {
    when(repository.findMode(7L)).thenReturn(IpAccessMode.NONE);

    assertThatCode(() -> authorizer.authorize(7L, null)).doesNotThrowAnyException();
  }

  @Test
  void allowlistOnlyAdmitsMatchingActiveNetwork() {
    when(repository.findMode(7L)).thenReturn(IpAccessMode.ALLOWLIST);
    when(repository.findRules(7L)).thenReturn(List.of(rule(
        IpAccessRuleType.ALLOWLIST, "10.20.0.0/16", true, null)));

    assertThatCode(() -> authorizer.authorize(7L, "10.20.8.9")).doesNotThrowAnyException();
    assertThatThrownBy(() -> authorizer.authorize(7L, "10.21.8.9"))
        .isInstanceOf(DataServiceForbiddenException.class)
        .hasMessageContaining("白名单");
  }

  @Test
  void activeAllowlistFailsClosedWhenClientIpCannotBeResolved() {
    when(repository.findMode(7L)).thenReturn(IpAccessMode.ALLOWLIST);
    when(repository.findRules(7L)).thenReturn(List.of());

    assertThatThrownBy(() -> authorizer.authorize(7L, null))
        .isInstanceOf(DataServiceForbiddenException.class)
        .hasMessageContaining("无法确认");
  }

  @Test
  void denylistRejectsMatchButIgnoresExpiredRule() {
    when(repository.findMode(7L)).thenReturn(IpAccessMode.DENYLIST);
    when(repository.findRules(7L)).thenReturn(List.of(
        rule(IpAccessRuleType.DENYLIST, "203.0.113.0/24", true, null),
        rule(
            IpAccessRuleType.DENYLIST,
            "198.51.100.0/24",
            true,
            LocalDateTime.of(2000, 1, 1, 0, 0))));

    assertThatThrownBy(() -> authorizer.authorize(7L, "203.0.113.9"))
        .isInstanceOf(DataServiceForbiddenException.class);
    assertThatCode(() -> authorizer.authorize(7L, "198.51.100.9"))
        .doesNotThrowAnyException();
  }

  private DataServiceIpAccessRule rule(
      IpAccessRuleType type,
      String network,
      boolean enabled,
      LocalDateTime expiresAt) {
    return new DataServiceIpAccessRule(
        1L,
        7L,
        type,
        network,
        null,
        enabled,
        expiresAt,
        LocalDateTime.of(2026, 1, 1, 0, 0),
        LocalDateTime.of(2026, 1, 1, 0, 0));
  }
}
