package io.yak.ops.business.mdm.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 订阅方编码解析测试(R6):解析不出平台用户必须返回空(交由上层标注「不可达」),
 * 安全服务缺席或反查报错也不能把主数据业务流程带下去。
 */
class MdmUserDirectoryTest {

  @SuppressWarnings("unchecked")
  private final ObjectProvider<UserService> userServices = mock(ObjectProvider.class);

  private UserService userService;
  private MdmUserDirectory directory;

  @BeforeEach
  void setUp() {
    userService = mock(UserService.class);
    when(userServices.getIfAvailable()).thenReturn(userService);
    directory = new MdmUserDirectory(userServices);
  }

  private static UserBriefVO user(Long id) {
    UserBriefVO brief = new UserBriefVO();
    brief.setId(id);
    return brief;
  }

  @Test
  void resolvesTrimmedCodeToPlatformUserId() {
    when(userService.getUserBriefByUsername("alice")).thenReturn(user(11L));

    assertEquals(Optional.of(11L), directory.userIdOf("  alice  "));
  }

  @Test
  void blankCodeSkipsLookup() {
    assertTrue(directory.userIdOf(null).isEmpty());
    assertTrue(directory.userIdOf("   ").isEmpty());
    verify(userService, never()).getUserBriefByUsername(any());
  }

  @Test
  void absentSecurityServiceAndUnknownUserBothMeanUnreachable() {
    when(userServices.getIfAvailable()).thenReturn(null);
    assertTrue(directory.userIdOf("alice").isEmpty());

    when(userServices.getIfAvailable()).thenReturn(userService);
    when(userService.getUserBriefByUsername("ghost")).thenReturn(null);
    assertTrue(directory.userIdOf("ghost").isEmpty());

    when(userService.getUserBriefByUsername("deleted")).thenReturn(user(-1L));
    assertTrue(directory.userIdOf("deleted").isEmpty());
  }

  @Test
  void lookupFailureDegradesToUnreachable() {
    when(userService.getUserBriefByUsername("alice"))
        .thenThrow(new RuntimeException("security down"));

    assertEquals(Optional.empty(), directory.userIdOf("alice"));
  }
}
