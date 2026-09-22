package io.yak.ops.business.dataservice.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.access.AccessContext;
import io.yak.ops.business.dataservice.domain.access.ConsumerAccessScope;
import io.yak.ops.business.dataservice.domain.access.DataServiceApiKey;
import io.yak.ops.business.dataservice.domain.access.DataServiceConsumer;
import io.yak.ops.business.dataservice.repository.DataServiceApiKeyRepository;
import io.yak.ops.business.dataservice.repository.DataServiceConsumerRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataServiceAuthorizerTest {

  @Test
  void ipPolicyRejectsBeforeConsumerOrApiKeyLookup() {
    DataServiceApiKeyRepository repository = mock(DataServiceApiKeyRepository.class);
    ApiKeySecretGenerator secrets = mock(ApiKeySecretGenerator.class);
    DataServiceRateLimiter rateLimiter = mock(DataServiceRateLimiter.class);
    DataServiceIpAccessAuthorizer ipAccessAuthorizer = mock(DataServiceIpAccessAuthorizer.class);
    DataServiceConsumerRepository consumerRepository = mock(DataServiceConsumerRepository.class);
    DataServiceConsumerIpAccessAuthorizer consumerIp = mock(DataServiceConsumerIpAccessAuthorizer.class);
    DataServiceDefinition definition = mock(DataServiceDefinition.class);
    when(definition.id()).thenReturn(7L);
    DataServiceForbiddenException forbidden =
        new DataServiceForbiddenException("blocked by network policy");
    org.mockito.Mockito.doThrow(forbidden)
        .when(ipAccessAuthorizer)
        .authorize(7L, "203.0.113.9");

    DataServiceAuthorizer authorizer = new DataServiceAuthorizer(
        repository, secrets, rateLimiter, ipAccessAuthorizer, consumerRepository, consumerIp);

    assertThatThrownBy(
        () -> authorizer.authorize(definition, "yak-key", "203.0.113.9"))
        .isSameAs(forbidden);

    verify(ipAccessAuthorizer).authorize(7L, "203.0.113.9");
    verifyNoInteractions(repository, secrets, rateLimiter, consumerRepository, consumerIp);
  }

  @Test
  void consumerKeyCanAccessMultipleGrantedApisAndAuditUsesCallerName() {
    DataServiceApiKeyRepository repository = mock(DataServiceApiKeyRepository.class);
    ApiKeySecretGenerator secrets = mock(ApiKeySecretGenerator.class);
    DataServiceRateLimiter rateLimiter = mock(DataServiceRateLimiter.class);
    DataServiceIpAccessAuthorizer ipAccessAuthorizer = mock(DataServiceIpAccessAuthorizer.class);
    DataServiceConsumerRepository consumerRepository = mock(DataServiceConsumerRepository.class);
    DataServiceConsumerIpAccessAuthorizer consumerIp = mock(DataServiceConsumerIpAccessAuthorizer.class);
    DataServiceDefinition definition = mock(DataServiceDefinition.class);
    when(definition.id()).thenReturn(8L);
    when(definition.projectId()).thenReturn(99L);
    when(consumerRepository.hasConfiguredAccess(99L, 8L)).thenReturn(true);
    when(secrets.hash("yak-key")).thenReturn("hash");

    LocalDateTime now = LocalDateTime.now();
    DataServiceApiKey key = new DataServiceApiKey(
        5L, null, 3L, "生产 Key", "yak_abcd", "hash", true, 60,
        null, null, now, now);
    DataServiceConsumer consumer = new DataServiceConsumer(
        3L, 99L, "BI 系统", null, ConsumerAccessScope.ALL, true, 60, now, now);
    when(repository.findByHash("hash")).thenReturn(Optional.of(key));
    when(consumerRepository.findByIdForProject(3L, 99L)).thenReturn(Optional.of(consumer));
    when(consumerRepository.hasAccess(3L, 99L, 8L)).thenReturn(true);
    when(repository.save(key)).thenReturn(key);

    DataServiceAuthorizer authorizer = new DataServiceAuthorizer(
        repository, secrets, rateLimiter, ipAccessAuthorizer, consumerRepository, consumerIp);
    AccessContext access = authorizer.authorize(definition, "yak-key", "10.0.0.8");

    assertThat(access.apiKeyId()).isEqualTo(5L);
    assertThat(access.apiKeyName()).isEqualTo("BI 系统");
    verify(consumerIp).authorize(3L, "10.0.0.8");
    verify(rateLimiter).acquire(key);
  }
}
