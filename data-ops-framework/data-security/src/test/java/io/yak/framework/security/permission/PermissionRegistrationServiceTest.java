package io.yak.framework.security.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

import io.yak.framework.security.common.entity.Permission;
import io.yak.framework.security.dao.PermissionDao;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PermissionRegistrationServiceTest {

  @Test
  void convertsGroupsAndLeavesForSynchronization() {
    PermissionDao dao = Mockito.mock(PermissionDao.class);
    PermissionRegistrationService service = new PermissionRegistrationService(dao);

    service.synchronize(List.of(PermissionDefinition.of("job", "作业管理",
        PermissionDefinition.Item.of("job:create", "创建作业"))));

    ArgumentCaptor<List<Permission>> captor = ArgumentCaptor.forClass(List.class);
    verify(dao).synchronizeDeclared(captor.capture());
    assertThat(captor.getValue()).extracting(Permission::getPermissionCode)
        .containsExactly("job", "job:create");
    Permission leaf = captor.getValue().get(1);
    assertThat(leaf.getParentCode()).isEqualTo("job");
    assertThat(leaf.getPermissionName()).isEqualTo("创建作业");
    assertThat(leaf.getActive()).isTrue();
    assertThat(leaf.getDeclared()).isTrue();
  }

  @Test
  void rejectsConflictingCodes() {
    PermissionDao dao = Mockito.mock(PermissionDao.class);
    PermissionRegistrationService service = new PermissionRegistrationService(dao);

    assertThatThrownBy(() -> service.synchronize(List.of(
        PermissionDefinition.of("job", "作业管理", "job:create"),
        PermissionDefinition.of("job:create", "冲突分组", "job:create:child"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("job:create");
    org.mockito.Mockito.verify(dao, org.mockito.Mockito.never()).synchronizeDeclared(anyList());
  }

  @Test
  void preservesFirstWinsAndGroupBeforeLeafInDaoPayload() {
    PermissionDao dao = Mockito.mock(PermissionDao.class);
    PermissionRegistrationService service = new PermissionRegistrationService(dao);
    service.synchronize(List.of(
        PermissionDefinition.of("security", "Security",
            PermissionDefinition.Item.ofMenu(
                "security:project:read", "Read Project", "First description", "first-menu")),
        PermissionDefinition.of("security", "Security",
            PermissionDefinition.Item.ofMenu(
                "security:project:read", "Read Project", "Second description", "second-menu"))));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Permission>> captor = ArgumentCaptor.forClass(List.class);
    verify(dao).synchronizeDeclared(captor.capture());
    List<Permission> rows = captor.getValue();
    assertThat(rows).extracting(Permission::getPermissionCode)
        .containsExactly("security", "security:project:read");
    assertThat(rows.get(0).getLeaf()).isFalse();
    assertThat(rows.get(0).getLevel()).isEqualTo(1);
    assertThat(rows.get(0).getParentCode()).isNull();
    assertThat(rows.get(0).getActive()).isTrue();
    assertThat(rows.get(0).getDeclared()).isTrue();
    Permission leaf = rows.get(1);
    assertThat(leaf.getParentCode()).isEqualTo("security");
    assertThat(leaf.getDescription()).isEqualTo("First description");
    assertThat(leaf.getMenuCode()).isEqualTo("first-menu");
    assertThat(leaf.getLevel()).isEqualTo(2);
    assertThat(leaf.getActive()).isTrue();
    assertThat(leaf.getDeclared()).isTrue();
  }

  @Test
  void preservesSecurityTransactionManagerAnnotation() throws Exception {
    org.springframework.transaction.annotation.Transactional tx =
        PermissionRegistrationService.class
            .getMethod("synchronize", java.util.Collection.class)
            .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
    assertThat(tx).isNotNull();
    assertThat(tx.transactionManager()).isEqualTo("yakSecurityTransactionManager");
  }

  @Test
  void passesEmptyDeclarationSetToDaoForExistingDeactivationSemantics() {
    PermissionDao dao = Mockito.mock(PermissionDao.class);
    new PermissionRegistrationService(dao).synchronize(List.of());
    verify(dao).synchronizeDeclared(List.of());
  }

}
