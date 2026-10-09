import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.framework.security.common.dto.PageParamDTO;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.permission.PermissionDefinition;
import io.yak.framework.security.permission.YakPermission;

/**
 * Compile ONLY against the original main branch's real data-security JAR.
 * Execute unchanged .class bytes against the A8.2 repackaged Boot classpath.
 * Runtime must not contain the old Starter JAR.
 */
@YakPermission(code = "a8:legacy:read", name = "Legacy reader",
    group = "Legacy", groupCode = "a8-legacy")
public final class SecurityLegacyConsumer implements AuthenticationManager {
  private Long lastLogin;
  private boolean signedIn;

  @Override
  public void login(Long userId) {
    lastLogin = userId;
    signedIn = true;
  }

  @Override
  public void logout() {
    signedIn = false;
  }

  @Override
  public boolean isLogin() {
    return signedIn;
  }

  @Override
  public Long getLoginUserId() {
    return lastLogin;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    PageParamDTO page = new PageParamDTO();
    require(page.getPage() == 1 && page.getSize() == 10, "Page defaults changed");
    page.setPage(3);
    page.setSize(25);
    require(page.getPage() == 3 && page.getSize() == 25, "Lombok accessor ABI changed");

    User user = new User();
    user.setId(42L);
    user.setUserName("legacy");
    require(Long.valueOf(42L).equals(user.getId())
        && "legacy".equals(user.getUserName()), "User/base entity ABI changed");

    PermissionDefinition permission = PermissionDefinition.of(
        "data", "Data", new String[] {"data:read"});
    require("data".equals(permission.getCode())
        && permission.getPermissions().size() == 1
        && "data:read".equals(permission.getPermissions().get(0).getCode()),
        "PermissionDefinition linkage changed");

    YakPermission annotation = SecurityLegacyConsumer.class
        .getAnnotation(YakPermission.class);
    require(annotation != null
        && "a8:legacy:read".equals(annotation.code())
        && "a8-legacy".equals(annotation.groupCode()),
        "Old runtime annotation ABI changed");

    SecurityLegacyConsumer authenticator = new SecurityLegacyConsumer();
    authenticator.login(42L, "legacy");
    require(authenticator.isLogin()
        && Long.valueOf(42L).equals(authenticator.getLoginUserId()),
        "AuthenticationManager default method dispatch changed");
    authenticator.logout();
    require(!authenticator.isLogin(), "AuthenticationManager logout ABI changed");

    System.out.println("Legacy Security consumer linked and executed unchanged");
  }
}
