package io.yak.ops.business.metric.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metric.repository.MetricRepository;
import io.yak.ops.business.modeling.api.ModelQueryApi;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MetricReferenceResolverTest {

  @Test
  void confirmedMissingStandardIsRemovedButMissingProviderIsUnavailable() {
    MetricRepository repository = mock(MetricRepository.class);
    ObjectProvider<ProcessApi> processProvider = mock(ObjectProvider.class);
    ObjectProvider<StandardQueryApi> standardProvider = mock(ObjectProvider.class);
    ObjectProvider<ModelQueryApi> modelProvider = mock(ObjectProvider.class);
    StandardQueryApi standardApi = mock(StandardQueryApi.class);
    when(standardProvider.getIfAvailable()).thenReturn(standardApi);
    when(standardApi.get(10L)).thenReturn(null);

    MetricReferenceResolver resolver = new MetricReferenceResolver(
        repository, processProvider, standardProvider, modelProvider);

    assertThat(resolver.standardReferenceResolution(10L).status())
        .isEqualTo(MetricReferenceResolver.ResolutionStatus.REMOVED);

    when(standardProvider.getIfAvailable()).thenReturn(null);
    assertThat(resolver.standardReferenceResolution(10L).status())
        .isEqualTo(MetricReferenceResolver.ResolutionStatus.UNAVAILABLE);
  }

  @Test
  void modelProviderFailureIsUnavailableWhileConfirmedMissingModelIsRemoved() {
    MetricRepository repository = mock(MetricRepository.class);
    ObjectProvider<ProcessApi> processProvider = mock(ObjectProvider.class);
    ObjectProvider<StandardQueryApi> standardProvider = mock(ObjectProvider.class);
    ObjectProvider<ModelQueryApi> modelProvider = mock(ObjectProvider.class);
    ModelQueryApi modelApi = mock(ModelQueryApi.class);
    when(modelProvider.getIfAvailable()).thenReturn(modelApi);
    when(modelApi.resolve(any())).thenReturn(Map.of());

    MetricReferenceResolver resolver = new MetricReferenceResolver(
        repository, processProvider, standardProvider, modelProvider);

    assertThat(resolver.modelReferenceResolution(20L).status())
        .isEqualTo(MetricReferenceResolver.ResolutionStatus.REMOVED);

    when(modelApi.resolve(any())).thenThrow(new IllegalStateException("provider down"));
    assertThat(resolver.modelReferenceResolution(20L).status())
        .isEqualTo(MetricReferenceResolver.ResolutionStatus.UNAVAILABLE);
  }
}
