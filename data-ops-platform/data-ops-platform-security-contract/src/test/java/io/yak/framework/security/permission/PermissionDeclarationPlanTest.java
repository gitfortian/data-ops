package io.yak.framework.security.permission;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Regression evidence for the transferred pure permission planning algorithm. */
class PermissionDeclarationPlanTest {

  @Test
  void emitsGroupsBeforeLeavesAndPreservesMenuAndParentData() {
    PermissionDefinition first = PermissionDefinition.of(" job ", " Job ",
        PermissionDefinition.Item.ofMenu(" job:read ", " Read ", " Browse ", " jobs-menu "),
        PermissionDefinition.Item.of("job:run", "Run"));
    PermissionDefinition second = PermissionDefinition.of("security", "Security",
        PermissionDefinition.Item.of("security:project:read", "Read Project"));
    List<PermissionDeclarationPlan.Entry> rows =
        PermissionDeclarationPlan.from(List.of(first, second)).entries();

    assertEquals(List.of("job", "job:read", "job:run", "security",
            "security:project:read"),
        rows.stream().map(PermissionDeclarationPlan.Entry::code).toList());
    var group = rows.get(0);
    assertEquals("Job", group.name());
    assertFalse(group.leaf());
    assertEquals(1, group.level());
    assertNull(group.parentCode());
    assertNull(group.menuCode());
    var action = rows.get(1);
    assertEquals("Read", action.name());
    assertEquals("Browse", action.description());
    assertEquals("jobs-menu", action.menuCode());
    assertEquals("job", action.parentCode());
    assertTrue(action.leaf());
    assertEquals(2, action.level());
    assertThrows(UnsupportedOperationException.class, () -> rows.clear());
  }

  @Test
  void retainsHistoricalFirstWinsForCompatibleDuplicateCodes() {
    PermissionDefinition first = PermissionDefinition.of("job", "Job",
        PermissionDefinition.Item.ofMenu("job:read", "Read", "first description", "first-menu"));
    PermissionDefinition second = PermissionDefinition.of("job", "Job",
        PermissionDefinition.Item.ofMenu("job:read", "Read", "second description", "second-menu"));
    var rows = PermissionDeclarationPlan.from(List.of(first, second)).entries();
    assertEquals(2, rows.size());
    assertEquals("first description", rows.get(1).description());
    assertEquals("first-menu", rows.get(1).menuCode());
  }

  @Test
  void rejectsDifferentNamesOrGroupLeafConflictsBeforePersistence() {
    var first = PermissionDefinition.of("job", "Job", "job:read");
    var conflictName = PermissionDefinition.of("job", "Another", "job:write");
    var conflictLeaf = PermissionDefinition.of("job:read", "Nested", "job:read:child");
    var nameError = assertThrows(IllegalStateException.class,
        () -> PermissionDeclarationPlan.from(List.of(first, conflictName)));
    assertEquals("Conflicting permission declaration: job", nameError.getMessage());
    var leafError = assertThrows(IllegalStateException.class,
        () -> PermissionDeclarationPlan.from(List.of(first, conflictLeaf)));
    assertEquals("Conflicting permission declaration: job:read", leafError.getMessage());
  }

  @Test
  void emptyDefinitionsCreateAnEmptyPlanWithoutSpecialDatabaseBehavior() {
    assertTrue(PermissionDeclarationPlan.from(List.of()).entries().isEmpty());
  }
}
