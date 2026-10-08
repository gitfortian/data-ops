package io.yak.ops.business.metric.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.metric.api.MetricDraftQueryApi.*;
import io.yak.ops.business.metric.domain.*;
import io.yak.ops.business.metric.repository.MetricRepository;
import io.yak.ops.business.modeling.api.ModelSuggestionQueryApi;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MetricDraftQueryAdapterTest {
  private final MetricRepository metrics = mock(MetricRepository.class);
  private final ModelSuggestionQueryApi models = mock(ModelSuggestionQueryApi.class);
  private final ActionAuthorization authorization = mock(ActionAuthorization.class);
  @SuppressWarnings("unchecked") private final ObjectProvider<ModelSuggestionQueryApi> provider = mock(ObjectProvider.class);
  private final MetricDraftQueryAdapter adapter = new MetricDraftQueryAdapter(metrics, provider, authorization);
  private final Input atomic = new Input(null, null, "ATOMIC", 9L, List.of(), "每日金额合计");
  private final Draft sum = new Draft("交易额", "每日金额合计", "DAY", "SUM", "amount", List.of(), List.of());
  @BeforeEach void setup() {
    when(provider.getIfAvailable()).thenReturn(models);
    when(models.fields(9)).thenReturn(new ModelSuggestionQueryApi.Fields(9, "a".repeat(64),
        List.of(new ModelSuggestionQueryApi.Field("amount", "DECIMAL", "金额"))));
  }
  private Metric upstream(long id, int version, MetricType type) {
    var metric = mock(Metric.class);
    when(metric.version()).thenReturn(version); when(metric.metricType()).thenReturn(type);
    when(metric.status()).thenReturn(MetricStatus.ENABLED); when(metric.modelId()).thenReturn(9L);
    when(metric.metricCode()).thenReturn("amount_" + id); when(metric.metricName()).thenReturn("金额");
    when(metric.measureExpr()).thenReturn("SUM(amount)"); when(metrics.findById(id)).thenReturn(Optional.of(metric));
    return metric;
  }
  @Test void deniedReadNeverConsultsSources() {
    doThrow(new SecurityException("forbidden")).when(authorization).requirePermission(MetricPermissionCode.READ);
    assertThrows(SecurityException.class, () -> adapter.prepare(atomic)); verifyNoInteractions(metrics, models, provider);
  }
  @Test void atomicUsesOnlySelectedFieldsAndRechecksStructureOnAdoption() {
    String digest = adapter.prepare(atomic).definition(); assertEquals(sum, adapter.validate(atomic, digest, sum));
    assertThrows(IllegalArgumentException.class, () -> adapter.validate(atomic, digest,
        new Draft("交易额", "说明", "DAY", "SUM", "invented", List.of(), List.of())));
    when(models.fields(9)).thenReturn(new ModelSuggestionQueryApi.Fields(9, "b".repeat(64),
        List.of(new ModelSuggestionQueryApi.Field("amount", "DECIMAL", "金额"))));
    assertThrows(IllegalArgumentException.class, () -> adapter.validate(atomic, digest, sum));
    verifyNoInteractions(metrics);
  }
  @Test void derivedUsesOneEnabledAtomicAndOriginalQualifierCompiler() {
    upstream(8, 2, MetricType.ATOMIC);
    var input = new Input(null, null, "DERIVED", null, List.of(8L), "每日大额交易");
    var draft = new Draft("大额交易", "金额超过100", "DAY", null, null, List.of(new Qualifier("amount", ">", "100")), List.of());
    assertEquals(draft, adapter.validate(input, adapter.prepare(input).definition(), draft));
    when(metrics.findById(8L).orElseThrow().status()).thenReturn(MetricStatus.DISABLED);
    assertThrows(IllegalArgumentException.class, () -> adapter.prepare(input));
  }
  @Test void compositeRejectsInventedRefsMalformedExpressionsAndDependencyDrift() {
    var first = upstream(8, 2, MetricType.ATOMIC); upstream(10, 1, MetricType.DERIVED);
    var input = new Input(null, null, "COMPOSITE", null, List.of(8L, 10L), "每日金额比例");
    var draft = new Draft("比例", "已选金额之比", "DAY", null, null, List.of(),
        List.of(new Token("REF", 8L), new Token("DIV", null), new Token("REF", 10L)));
    String digest = adapter.prepare(input).definition(); assertEquals(draft, adapter.validate(input, digest, draft));
    for (var tokens : List.of(List.of(new Token("REF", 99L)), List.of(new Token("ADD", null)),
        List.of(new Token("LPAREN", null), new Token("REF", 8L)))) {
      assertThrows(IllegalArgumentException.class, () -> adapter.validate(input, digest,
          new Draft("比例", "说明", "DAY", null, null, List.of(), tokens)));
    }
    when(first.version()).thenReturn(3);
    assertThrows(IllegalArgumentException.class, () -> adapter.validate(input, digest, draft));
    verifyNoInteractions(models);
  }
  @Test void editedMetricRequiresExactVersionAndCannotReferenceItself() {
    upstream(7, 3, MetricType.COMPOSITE);
    var input = new Input(7L, 2, "COMPOSITE", null, List.of(8L), "需求");
    assertThrows(IllegalArgumentException.class, () -> adapter.prepare(input));
    assertThrows(IllegalArgumentException.class, () -> adapter.prepare(new Input(7L, 3, "COMPOSITE", null, List.of(7L), "需求")));
  }
  @Test void numericMetricCodeCannotSilentlyBecomeAConstantInTheOriginalFormulaEditor() {
    var metric = upstream(8, 2, MetricType.ATOMIC); when(metric.metricCode()).thenReturn("123");
    assertThrows(IllegalArgumentException.class, () -> adapter.prepare(new Input(null, null, "COMPOSITE", null, List.of(8L), "每日金额")));
  }

}
