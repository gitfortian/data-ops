package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.yak.framework.security.common.entity.Permission;
import io.yak.framework.security.common.po.PermissionPO;
import io.yak.framework.security.dao.PermissionDao;
import io.yak.framework.security.dao.mapper.PermissionMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Preserve MyBatis state transitions while moving only Permission/DAO ABI ownership. */
class PermissionPersistencePortIntegrationTest {

  @Test
  void oldDaoImplementationStillImplementsRelocatedInterface() {
    assertTrue(PermissionDao.class.isAssignableFrom(PermissionDaoImpl.class));
  }

  @Test
  void onlyUnlistedActiveDeclaredPermissionsAreDisabledNeverDeleted() {
    PermissionMapper mapper = mock(PermissionMapper.class);
    PermissionPO oldDeclared = stored(1L, "security:old", true, true, "legacy-menu");
    PermissionPO manual = stored(2L, "security:manual", false, true, "manual-menu");
    PermissionPO alreadyDisabled = stored(3L, "security:disabled", true, false, null);
    when(mapper.selectList(any(Wrapper.class))).thenReturn(
        List.of(oldDeclared, manual, alreadyDisabled));

    new PermissionDaoImpl(mapper).synchronizeDeclared(List.of());

    assertFalse(oldDeclared.getActive());
    assertTrue(manual.getActive());
    assertFalse(alreadyDisabled.getActive());
    verify(mapper).updateById(oldDeclared);
    verify(mapper, never()).updateById(manual);
    verify(mapper, never()).updateById(alreadyDisabled);
    verify(mapper, never()).deleteById(any());
  }

  @Test
  void existingMenuBindingIsPreservedAndPreviouslyDisabledPermissionReactivates() {
    PermissionMapper mapper = mock(PermissionMapper.class);
    PermissionPO existing = stored(14L, "security:project:read",
        true, false, "system-security-projects");
    when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
    Permission desired = new Permission();
    desired.setPermissionCode("security:project:read");
    desired.setPermissionName("Read Project");
    desired.setLeaf(true);
    desired.setLevel(2);
    desired.setMenuCode(null);
    desired.setActive(true);
    desired.setDeclared(true);

    new PermissionDaoImpl(mapper).synchronizeDeclared(List.of(desired));

    assertTrue(existing.getActive());
    assertTrue(existing.getDeclared());
    assertEquals("system-security-projects", existing.getMenuCode());
    verify(mapper).updateById(existing);
    verify(mapper, never()).deleteById(any());
  }

  @Test
  void groupsInsertBeforeLeafAndLeafReferencesGeneratedParentId() {
    PermissionMapper mapper = mock(PermissionMapper.class);
    when(mapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
    AtomicLong ids = new AtomicLong(100);
    when(mapper.insert(any(PermissionPO.class))).thenAnswer(invocation -> {
      PermissionPO row = invocation.getArgument(0);
      row.setId(ids.incrementAndGet());
      return 1;
    });

    Permission group = desired("security", "Security", false, 1, null);
    Permission leaf = desired("security:project:read", "Read Project",
        true, 2, "security");
    new PermissionDaoImpl(mapper).synchronizeDeclared(List.of(leaf, group));

    ArgumentCaptor<PermissionPO> inserted = ArgumentCaptor.forClass(PermissionPO.class);
    verify(mapper, times(2)).insert(inserted.capture());
    assertEquals(List.of("security", "security:project:read"),
        inserted.getAllValues().stream().map(PermissionPO::getPermissionCode).toList());
    assertEquals(101L, inserted.getAllValues().get(1).getParentId());
    verify(mapper, never()).deleteById(any());
  }

  private static Permission desired(String code, String name, boolean leaf,
      int level, String parent) {
    Permission p = new Permission();
    p.setPermissionCode(code);
    p.setPermissionName(name);
    p.setLeaf(leaf);
    p.setLevel(level);
    p.setParentCode(parent);
    p.setActive(true);
    p.setDeclared(true);
    return p;
  }

  private static PermissionPO stored(Long id, String code, boolean declared,
      boolean active, String menu) {
    PermissionPO p = new PermissionPO();
    p.setId(id);
    p.setPermissionCode(code);
    p.setPermissionName(code);
    p.setDeclared(declared);
    p.setActive(active);
    p.setMenuCode(menu);
    return p;
  }
}
