package io.yak.ops.business.agent.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.agent.domain.*;
import io.yak.ops.business.modeling.api.ModelSuggestionQueryApi;
import io.yak.ops.business.semantic.api.StandardSuggestionQueryApi;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class StandardMatchGatewayTest {
  private final ModelSuggestionQueryApi models = mock(ModelSuggestionQueryApi.class);
  private final StandardSuggestionQueryApi standards = mock(StandardSuggestionQueryApi.class);
  private final StandardMatchGateway gateway = new StandardMatchGateway(provider(models), provider(standards));
  private final StandardMatchTarget target = new StandardMatchTarget(7, "user_id", "BIGINT", "用户编号", "");
  private final StandardMatchProposal valid = new StandardMatchProposal(List.of(new StandardMatchProposal.Choice(9, 2, "业务编号")), List.of());

  @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T api) {
    ObjectProvider<T> value = mock(ObjectProvider.class); when(value.getIfAvailable()).thenReturn(api); return value;
  }
  @BeforeEach void source() {
    when(models.require(7)).thenReturn(new ModelSuggestionQueryApi.Context(7, "用户", "MYSQL", "a"));
    var candidate = new StandardSuggestionQueryApi.TypeCandidate(9, 2, "user_id", "用户编号", "BIGINT", "");
    when(standards.types("")).thenReturn(new StandardSuggestionQueryApi.Pool(List.of(candidate), false));
    when(standards.requireType(9, 2)).thenReturn(candidate);
  }
  @Test void sourceLabelsAreAuthoritativeAndAdoptionRechecksVersions() {
    var result = gateway.validate(target, gateway.prepare(target), valid, 1, "hash");
    assertEquals("用户编号", result.candidates().getFirst().name());
    assertEquals(result, gateway.revalidate(result));
    when(standards.requireType(9, 2)).thenThrow(new IllegalArgumentException("version changed"));
    assertThrows(IllegalArgumentException.class, () -> gateway.revalidate(result));
  }
  @Test void inventedIdsDuplicateIdsAndExcessCandidatesCannotBePublished() {
    var original = gateway.prepare(target);
    for (var choices : List.of(List.of(new StandardMatchProposal.Choice(99, 1, "伪造")),
        List.of(valid.candidates().getFirst(), valid.candidates().getFirst()),
        java.util.Collections.nCopies(4, valid.candidates().getFirst()))) {
      assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original,
          new StandardMatchProposal(choices, List.of()), 1, "hash"));
    }
    verify(standards, never()).requireType(99, 1);
  }
  @Test void changedModelAndUnreadableSourceAreFailuresRatherThanEmptyMatches() {
    var original = gateway.prepare(target);
    when(models.require(7)).thenReturn(new ModelSuggestionQueryApi.Context(7, "用户", "MYSQL", "b"));
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, valid, 1, "hash"));
    when(standards.types("")).thenThrow(new IllegalStateException("source down"));
    assertThrows(IllegalStateException.class, () -> gateway.prepare(target));
  }
  @Test void noMatchStillRequiresLiveSourceAuthorization() {
    var original = gateway.prepare(target);
    var empty = new StandardMatchProposal(List.of(), List.of("请补充业务含义"));
    assertTrue(gateway.validate(target, original, empty, 1, "hash").candidates().isEmpty());
    when(standards.types("")).thenThrow(new SecurityException("revoked"));
    assertThrows(SecurityException.class, () -> gateway.validate(target, original, empty, 1, "hash"));
  }
  @Test void descriptionOnlyDraftStillRechecksAuthorizationAndRejectsOversizeText() {
    var original = gateway.prepare(target);
    var description = new StandardMatchProposal(List.of(), List.of(), "用户唯一编号");
    var result = gateway.validate(target, original, description, 1, "hash");
    assertEquals("用户唯一编号", gateway.revalidate(result).fieldDescription());
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original,
        new StandardMatchProposal(List.of(), List.of(), "x".repeat(513)), 1, "hash"));
    when(models.require(7)).thenThrow(new SecurityException("revoked"));
    assertThrows(SecurityException.class, () -> gateway.revalidate(result));
  }

}
