package io.yak.ops.business.agent.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.agent.domain.SessionMeta;
import io.yak.ops.business.agent.repository.SessionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 会话归属校验行为测试：先校验后读写，越权直接拒绝（DOMAIN 硬规则 4 / 安全能力清单）。 */
class AgentSessionOwnerValidatorTest {

  private SessionRepository sessionRepository;
  private AgentSessionOwnerValidator ownerValidator;

  @BeforeEach
  void setUp() {
    sessionRepository = mock(SessionRepository.class);
    ownerValidator = new AgentSessionOwnerValidator(sessionRepository);
  }

  @Test
  void firstVisitBindsOwnership() {
    when(sessionRepository.findBySessionId("s1")).thenReturn(Optional.empty());

    ownerValidator.ensureOwner("s1", 42L);

    verify(sessionRepository).insert(new SessionMeta("s1", 42L, 0L, null, null, null));
  }

  @Test
  void firstVisitUsesQuestionHeadAsInitialTitle() {
    when(sessionRepository.findBySessionId("s1")).thenReturn(Optional.empty());

    ownerValidator.ensureOwner("s1", 42L, 1L, "上个月各区域销售额是多少？");

    verify(sessionRepository)
        .insert(new SessionMeta("s1", 42L, 1L, "上个月各区域销售额是多少？", null, null));
  }

  @Test
  void initialTitleIsTruncatedAndWhitespaceFolded() {
    when(sessionRepository.findBySessionId("long")).thenReturn(Optional.empty());

    String longQuestion = "  分析\n\n最近   一年的销售趋势，包括区域、产品线与渠道明细，并给出建议。";
    ownerValidator.ensureOwner("long", 42L, 1L, longQuestion);

    org.mockito.ArgumentCaptor<SessionMeta> captor =
        org.mockito.ArgumentCaptor.forClass(SessionMeta.class);
    verify(sessionRepository).insert(captor.capture());
    String title = captor.getValue().title();
    assertEquals(31, title.length());
    assertTrue(title.endsWith("…"));
    assertFalse(title.contains("\n"));
  }

  @Test
  void sameOwnerPassesWithoutRebinding() {
    when(sessionRepository.findBySessionId("s1"))
        .thenReturn(Optional.of(new SessionMeta("s1", 42L, 1L, "t", null, null)));

    ownerValidator.ensureOwner("s1", 42L);

    verify(sessionRepository, never()).insert(any());
  }

  @Test
  void crossUserAccessIsRejected() {
    when(sessionRepository.findBySessionId("s1"))
        .thenReturn(Optional.of(new SessionMeta("s1", 42L, 1L, "t", null, null)));

    assertThrows(IllegalArgumentException.class, () -> ownerValidator.ensureOwner("s1", 99L));
    verify(sessionRepository, never()).insert(any());
  }
}
