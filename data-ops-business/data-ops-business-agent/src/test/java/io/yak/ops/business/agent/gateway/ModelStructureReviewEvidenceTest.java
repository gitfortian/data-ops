package io.yak.ops.business.agent.gateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.agent.domain.GovernanceEvidenceLedger;
import io.yak.ops.business.agent.domain.ModelStructureReviewTarget;
import io.yak.ops.business.modeling.api.ModelStructureReviewQueryApi;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ModelStructureReviewEvidenceTest {
  @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T value) {
    ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(value); return provider;
  }
  @Test void unavailableDeniedAndWrongTargetNeverBecomeEmptySuccessOrLeakDiagnostics() {
    var api = mock(ModelStructureReviewQueryApi.class);
    var target = new ModelStructureReviewTarget("7", 3, "a".repeat(64));
    var gateway = new GovernanceEvidenceGateway(null, null, null, null, provider(api));
    when(api.read(7, 3, target.definition())).thenThrow(new IllegalStateException("SECRET_SQL"))
        .thenThrow(new SecurityException("SECRET_TOKEN"))
        .thenReturn(new ModelStructureReviewQueryApi.Context("42", "8", 3, "11", target.definition(), 1, 1, List.of(), List.of(), List.of()));
    String output = gateway.modelStructureReview(target, 42, new GovernanceEvidenceLedger());
    assertTrue(output.contains("UNAVAILABLE")); assertFalse(output.contains("SECRET_SQL"));
    output = gateway.modelStructureReview(target, 42, new GovernanceEvidenceLedger());
    assertTrue(output.contains("PERMISSION_DENIED")); assertFalse(output.contains("SECRET_TOKEN"));
    assertTrue(gateway.modelStructureReview(target, 42, new GovernanceEvidenceLedger()).contains("UNAVAILABLE"));
  }
}
