package io.yak.ops.business.asset.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 预检令牌:签名绑定项目+资产集合+5 分钟窗(与 lifecycle 确认 token 同一纪律)。 */
class PrecheckTokenServiceTest {

  private final PrecheckTokenService service = new PrecheckTokenService();

  @Test
  void acceptsRoundTripRegardlessOfIdOrder() {
    String token = service.issue(1L, List.of(30L, 10L, 20L));
    assertDoesNotThrow(() -> service.validate(1L, List.of(10L, 20L, 30L), token));
  }

  @Test
  void rejectsDifferentAssetSet() {
    String token = service.issue(1L, List.of(10L, 20L));
    AssetException ex =
        assertThrows(AssetException.class, () -> service.validate(1L, List.of(10L, 99L), token));
    assertEquals(AssetErrorCode.PRECHECK_TOKEN_INVALID, ex.getErrorCode());
  }

  @Test
  void rejectsOtherProject() {
    String token = service.issue(1L, List.of(10L));
    assertThrows(AssetException.class, () -> service.validate(2L, List.of(10L), token));
  }

  @Test
  void rejectsTamperedToken() {
    String token = service.issue(1L, List.of(10L));
    String tampered = token.substring(0, token.length() - 2) + "xx";
    assertThrows(AssetException.class, () -> service.validate(1L, List.of(10L), tampered));
  }

  @Test
  void rejectsBlankAndGarbageTokens() {
    assertThrows(AssetException.class, () -> service.validate(1L, List.of(10L), null));
    assertThrows(AssetException.class, () -> service.validate(1L, List.of(10L), " "));
    AssetException ex = assertThrows(AssetException.class,
        () -> service.validate(1L, List.of(10L), "not-a-token!!"));
    assertEquals(AssetErrorCode.PRECHECK_TOKEN_INVALID, ex.getErrorCode());
  }

  @Test
  void tokenFromAnotherInstanceIsRejected() {
    String token = new PrecheckTokenService().issue(1L, List.of(10L));
    assertThrows(AssetException.class, () -> service.validate(1L, List.of(10L), token));
    assertTrue(token.length() > 10);
    assertNotEquals(token, service.issue(1L, List.of(10L)));
  }
}
