package io.yak.ops.business.lifecycle.preview;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.lifecycle.exception.LifecycleException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 确认令牌单测:签发/校验回路、防篡改、防换模型、5 分钟窗口与跨实例失效。 */
class ConfirmTokenServiceTest {

  private final ConfirmTokenService service = new ConfirmTokenService();

  @Test
  void issueThenValidateRoundtrip() {
    String token = service.issue(1L, List.of(10L, 20L, 30L));
    assertThatCode(() -> service.validate(1L, List.of(10L, 20L, 30L), token))
        .doesNotThrowAnyException();
  }

  @Test
  void modelOrderDoesNotMatter() {
    String token = service.issue(1L, List.of(30L, 10L, 20L));
    assertThatCode(() -> service.validate(1L, List.of(10L, 20L, 30L), token))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsTamperedToken() {
    String token = service.issue(1L, List.of(10L));
    String tampered = token.substring(0, token.length() - 2) + "xx";
    assertThatThrownBy(() -> service.validate(1L, List.of(10L), tampered))
        .isInstanceOf(LifecycleException.class);
  }

  @Test
  void rejectsDifferentModelSet() {
    String token = service.issue(1L, List.of(10L, 20L));
    assertThatThrownBy(() -> service.validate(1L, List.of(10L, 99L), token))
        .isInstanceOf(LifecycleException.class);
  }

  @Test
  void rejectsDifferentProject() {
    String token = service.issue(1L, List.of(10L));
    assertThatThrownBy(() -> service.validate(2L, List.of(10L), token))
        .isInstanceOf(LifecycleException.class);
  }

  @Test
  void rejectsBlankAndGarbageTokens() {
    assertThatThrownBy(() -> service.validate(1L, List.of(10L), null))
        .isInstanceOf(LifecycleException.class);
    assertThatThrownBy(() -> service.validate(1L, List.of(10L), "   "))
        .isInstanceOf(LifecycleException.class);
    assertThatThrownBy(() -> service.validate(1L, List.of(10L), "not-a-token!!"))
        .isInstanceOf(LifecycleException.class);
  }

  @Test
  void tokenFromAnotherInstanceIsRejected() {
    // 密钥随实例生成:重启后旧令牌应失效
    String token = new ConfirmTokenService().issue(1L, List.of(10L));
    assertThatThrownBy(() -> service.validate(1L, List.of(10L), token))
        .isInstanceOf(LifecycleException.class);
  }
}
