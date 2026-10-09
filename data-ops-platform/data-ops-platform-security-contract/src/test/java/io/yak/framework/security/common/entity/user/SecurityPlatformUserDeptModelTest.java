package io.yak.framework.security.common.entity.user;

import io.yak.framework.security.common.entity.BaseEntity;
import io.yak.framework.security.common.entity.dept.Dept;
import io.yak.framework.security.common.entity.dept.DeptBrief;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Preserve legacy User/Dept FQCNs and public bean ABI after ownership transfer. */
class SecurityPlatformUserDeptModelTest {
  @Test
  void loginUserModelRetainsBaseEntityIdentityAndNeverLogsPassword() {
    User user = new User();
    user.setId(101L);
    user.setUserName("alice");
    user.setPw("plain-password-example");
    user.setSalt("test-salt-example");
    user.setDeptId(77L);
    assertEquals(BaseEntity.class, User.class.getSuperclass());
    assertEquals(Long.valueOf(101), user.getId());
    assertEquals("alice", user.getUserName());
    assertEquals(Long.valueOf(77), user.getDeptId());
    assertEquals(Integer.valueOf(1), user.getStatus());
    assertFalse(user.toString().contains("plain-password-example"));
    assertFalse(user.toString().contains("test-salt-example"));
    assertEquals("io.yak.framework.security.common.entity.user.User", User.class.getName());
  }

  @Test
  void userBriefAndDeptModelsPreserveStablePublicFields() {
    UserBrief user = new UserBrief();
    user.setId(1L);
    user.setUserName("alice");
    user.setRealName("Alice");
    user.setDeptId(7L);
    assertEquals(Long.valueOf(7), user.getDeptId());

    Dept dept = new Dept();
    dept.setId(7L);
    dept.setDeptName("Engineering");
    dept.setParentId(1L);
    dept.setLeaf(true);
    dept.setLevel(2);
    dept.setDescription("Product team");
    assertEquals("Engineering", dept.getDeptName());
    assertEquals(Boolean.TRUE, dept.getLeaf());

    DeptBrief brief = new DeptBrief();
    brief.setId(7L);
    brief.setDeptName("Engineering");
    brief.setParentId(1L);
    brief.setLeaf(true);
    brief.setLevel(2);
    assertEquals(Long.valueOf(1), brief.getParentId());
    assertEquals("io.yak.framework.security.common.entity.dept.DeptBrief", DeptBrief.class.getName());
  }
}
