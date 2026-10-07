package io.yak.ops.business.agent.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.yak.ops.business.agent.domain.*;
import io.yak.ops.business.modeling.api.MappingSuggestionQueryApi;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ModelMappingGatewayTest {
  private final MappingSuggestionQueryApi api = mock(MappingSuggestionQueryApi.class);
  private final ModelMappingTarget target = new ModelMappingTarget(7, "user_id", 9, "db", "users", "买家编号", "");
  private final ModelMappingGateway gateway = gateway();
  private ModelMappingGateway gateway() {
    @SuppressWarnings("unchecked") var provider = (ObjectProvider<MappingSuggestionQueryApi>) mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(api); return new ModelMappingGateway(provider);
  }
  private void source(String definition, String sourceDefinition) {
    when(api.require(7, "user_id", 9, "db", "users", "")).thenReturn(new MappingSuggestionQueryApi.Context("用户", "MYSQL",
        "user_id", "BIGINT", "用户编号", definition, sourceDefinition,
        List.of(new MappingSuggestionQueryApi.SourceColumn("buyer_id", "BIGINT", "买家编号", false)), false));
  }
  private ModelMappingProposal proposal(String column) {
    return new ModelMappingProposal(List.of(new ModelMappingProposal.Choice(column, "对应买家编号；请核对粒度")), List.of());
  }
  @Test void labelsAndTypesComeFromCurrentSourceNotTheModel() {
    source("a", "b"); var value = gateway.validate(target, gateway.prepare(target), proposal("buyer_id"), 2, "hash");
    assertEquals("BIGINT", value.targetType()); assertEquals("BIGINT", value.candidates().getFirst().type());
    assertEquals("b", value.sourceDefinition());
  }
  @Test void unknownDuplicateAndOversizeCandidatesAreRejected() {
    source("a", "b"); var original = gateway.prepare(target);
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, proposal("operator_id"), 1, "h"));
    var c = new ModelMappingProposal.Choice("buyer_id", "reason");
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, new ModelMappingProposal(List.of(c, c), List.of()), 1, "h"));
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, new ModelMappingProposal(List.of(), List.of("x".repeat(513))), 1, "h"));
  }
  @Test void modelOrSourceDriftRejectsEvenAnEmptyAnswer() {
    source("a", "b"); var original = gateway.prepare(target); source("a", "changed");
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, new ModelMappingProposal(List.of(), List.of()), 1, "h"));
    source("changed", "b");
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, proposal("buyer_id"), 1, "h"));
  }
  @Test void adoptionChecksBothSavedMappingAndPhysicalSourceFingerprint() {
    source("a", "b"); var value = gateway.validate(target, gateway.prepare(target), proposal("buyer_id"), 2, "h");
    source("a", "changed"); assertThrows(IllegalArgumentException.class, () -> gateway.revalidate(value));
    source("a", "b"); assertEquals("buyer_id", gateway.revalidate(value).candidates().getFirst().sourceColumn());
  }
}
