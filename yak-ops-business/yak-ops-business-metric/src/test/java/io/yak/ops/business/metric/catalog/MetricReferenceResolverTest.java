package io.yak.ops.business.metric.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metric.catalog.MetricReferenceResolver.ProviderState;
import io.yak.ops.business.metric.catalog.MetricReferenceResolver.Reference;
import io.yak.ops.business.metric.catalog.MetricReferenceResolver.ReferenceResolution;
import io.yak.ops.business.metric.repository.MetricRepository;
import io.yak.ops.business.modeling.api.ModelQueryApi;
import io.yak.ops.business.modeling.api.ModelQueryApi.ModelBrief;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MetricReferenceResolverTest {

  private StandardQueryApi standardQueryApi;
  private ModelQueryApi modelQueryApi;
  private ObjectProvider<StandardQueryApi> standardProvider;
  private ObjectProvider<ModelQueryApi> modelProvider;
  private MetricReferenceResolver resolver;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    standardQueryApi = mock(StandardQueryApi.class);
    modelQueryApi = mock(ModelQueryApi.class);
    standardProvider = mock(ObjectProvider.class);
    modelProvider = mock(ObjectProvider.class);

    when(standardProvider.getIfAvailable()).thenReturn(standardQueryApi);
    when(modelProvider.getIfAvailable()).thenReturn(modelQueryApi);

    resolver = new MetricReferenceResolver(
        mock(MetricRepository.class),
        mock(ObjectProvider.class),
        standardProvider,
        modelProvider);
  }

  @Test
  void standardResolutionDistinguishesAvailableEmptyAndUnavailable() {
    Standard standard = new Standard(
        7L, null, "STD_7", "标准7", null, 4, 0, false,
        null, null, null, null, null);
    when(standardQueryApi.get(7L)).thenReturn(standard);
    when(standardQueryApi.get(8L)).thenReturn(null);
    when(standardQueryApi.get(9L)).thenThrow(new IllegalStateException("semantic down"));

    ReferenceResolution available = resolver.standardReferenceResolution(7L);
    ReferenceResolution empty = resolver.standardReferenceResolution(8L);
    ReferenceResolution unavailable = resolver.standardReferenceResolution(9L);

    assertThat(available.state()).isEqualTo(ProviderState.AVAILABLE);
    assertThat(available.reference()).isEqualTo(new Reference("STD_7", 4));
    assertThat(empty.state()).isEqualTo(ProviderState.EMPTY);
    assertThat(empty.reference()).isEqualTo(Reference.EMPTY);
    assertThat(unavailable.state()).isEqualTo(ProviderState.UNAVAILABLE);
    assertThat(unavailable.reference()).isEqualTo(Reference.EMPTY);
  }

  @Test
  void missingStandardProviderIsUnavailableNotEmpty() {
    when(standardProvider.getIfAvailable()).thenReturn(null);

    ReferenceResolution result = resolver.standardReferenceResolution(7L);

    assertThat(result.state()).isEqualTo(ProviderState.UNAVAILABLE);
    assertThat(result.reference()).isEqualTo(Reference.EMPTY);
  }

  @Test
  void modelResolutionDistinguishesAvailableEmptyAndUnavailable() {
    when(modelQueryApi.resolve(List.of(11L)))
        .thenReturn(Map.of(11L, new ModelBrief(11L, "MODEL_11", "模型11", "DWD", 6)));
    when(modelQueryApi.resolve(List.of(12L))).thenReturn(Map.of());
    when(modelQueryApi.resolve(List.of(13L))).thenReturn(null);

    ReferenceResolution available = resolver.modelReferenceResolution(11L);
    ReferenceResolution empty = resolver.modelReferenceResolution(12L);
    ReferenceResolution unavailable = resolver.modelReferenceResolution(13L);

    assertThat(available.state()).isEqualTo(ProviderState.AVAILABLE);
    assertThat(available.reference()).isEqualTo(new Reference("MODEL_11", 6));
    assertThat(empty.state()).isEqualTo(ProviderState.EMPTY);
    assertThat(empty.reference()).isEqualTo(Reference.EMPTY);
    assertThat(unavailable.state()).isEqualTo(ProviderState.UNAVAILABLE);
    assertThat(unavailable.reference()).isEqualTo(Reference.EMPTY);
  }

  @Test
  void legacyReferenceMethodsKeepEmptyFallbackCompatibility() {
    when(standardQueryApi.get(21L)).thenReturn(null);
    when(modelQueryApi.resolve(List.of(22L))).thenThrow(new IllegalStateException("modeling down"));

    assertThat(resolver.standardReference(21L)).isEqualTo(Reference.EMPTY);
    assertThat(resolver.modelReference(22L)).isEqualTo(Reference.EMPTY);
  }
}
