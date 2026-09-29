package io.yak.ops.business.dataservice.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.domain.access.DataServiceApiKey;
import io.yak.ops.business.dataservice.domain.access.DataServiceIpAccessRule;
import io.yak.ops.business.dataservice.domain.access.IpAccessMode;
import io.yak.ops.business.dataservice.domain.access.IpAccessRuleType;
import io.yak.ops.business.dataservice.repository.DataServiceApiKeyRepository;
import io.yak.ops.business.dataservice.repository.DataServiceIpAccessRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataServiceAccessOverviewReaderTest {

  @Test
  void projectsAccessStateWithoutRequiringGeneralReadPermission() {
    DataServiceReader dataServiceReader = mock(DataServiceReader.class);
    DataServiceParameterNameReader parameterNameReader = mock(DataServiceParameterNameReader.class);
    DataServiceApiKeyRepository keyRepository = mock(DataServiceApiKeyRepository.class);
    DataServiceIpAccessRepository ipRepository = mock(DataServiceIpAccessRepository.class);
    DataServiceAccessOverviewReader reader = new DataServiceAccessOverviewReader(
        dataServiceReader, parameterNameReader, keyRepository, ipRepository);

    DataServiceDefinition definition = DataServiceDefinition.restore(
        7L,
        3L,
        4L,
        new DataServiceSettings("Orders", "/orders", 100, 30, true, null, false),
        new PublishedRuntimeSnapshot(9L, "select id from orders where id = :id"),
        new SourceReference("TEST", "orders", 11L, 1),
        RuntimePolicy.defaults(false),
        AuthMode.API_KEY,
        LocalDateTime.of(2026, 9, 1, 9, 0),
        LocalDateTime.of(2026, 9, 2, 9, 0));

    when(dataServiceReader.list()).thenReturn(List.of(definition));
    when(parameterNameReader.parameterNames("select id from orders where id = :id"))
        .thenReturn(List.of("id"));
    when(keyRepository.findByApiId(7L)).thenReturn(List.of(
        new DataServiceApiKey(1L, 7L, "active", "yak_a", "hash-a", true, 60,
            LocalDateTime.of(2099, 1, 1, 0, 0), null, null, null),
        new DataServiceApiKey(2L, 7L, "expired", "yak_b", "hash-b", true, 60,
            LocalDateTime.of(2020, 1, 1, 0, 0), null, null, null)));
    when(ipRepository.findMode(7L)).thenReturn(IpAccessMode.DENYLIST);
    when(ipRepository.findRules(7L)).thenReturn(List.of(
        new DataServiceIpAccessRule(1L, 7L, IpAccessRuleType.ALLOWLIST, "10.0.0.0/8",
            null, true, null, null, null),
        new DataServiceIpAccessRule(2L, 7L, IpAccessRuleType.ALLOWLIST, "192.168.0.0/16",
            null, false, null, null, null),
        new DataServiceIpAccessRule(3L, 7L, IpAccessRuleType.DENYLIST, "203.0.113.9/32",
            null, true, null, null, null)));

    DataServiceAccessOverviewItem item = reader.list().get(0);

    assertThat(item.apiId()).isEqualTo(7L);
    assertThat(item.runtimePath()).isEqualTo("/api/v1/data-service/runtime/orders");
    assertThat(item.parameterNames()).containsExactly("id");
    assertThat(item.authMode()).isEqualTo(AuthMode.API_KEY);
    assertThat(item.ipAccessMode()).isEqualTo(IpAccessMode.DENYLIST);
    assertThat(item.apiKeyCount()).isEqualTo(2);
    assertThat(item.activeApiKeyCount()).isEqualTo(1);
    assertThat(item.allowlistRuleCount()).isEqualTo(2);
    assertThat(item.activeAllowlistRuleCount()).isEqualTo(1);
    assertThat(item.denylistRuleCount()).isEqualTo(1);
    assertThat(item.activeDenylistRuleCount()).isEqualTo(1);
  }
}
