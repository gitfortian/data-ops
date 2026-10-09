package io.yak.framework.security.permission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Security Platform's deterministic, persistence-free permission declaration plan.
 *
 * <p>Keeps the historical group-first ordering, first-wins duplicate behavior,
 * and conflict rule (code + name + leaf) used by PermissionRegistrationService.
 * Project/role grants and database mutation belong to the caller's DAO.
 */
public final class PermissionDeclarationPlan {

  private final List<Entry> entries;

  private PermissionDeclarationPlan(List<Entry> entries) {
    this.entries = List.copyOf(entries);
  }

  public List<Entry> entries() {
    return entries;
  }

  public static PermissionDeclarationPlan from(Collection<PermissionDefinition> definitions) {
    Map<String, Entry> desired = new LinkedHashMap<>();
    for (PermissionDefinition group : definitions) {
      putUnique(desired,
          new Entry(group.getCode(), group.getName(), null, null, null, false, 1));
      for (PermissionDefinition.Item item : group.getPermissions()) {
        putUnique(desired,
            new Entry(item.getCode(), item.getName(), item.getDescription(),
                item.getMenuCode(), group.getCode(), true, 2));
      }
    }
    return new PermissionDeclarationPlan(new ArrayList<>(desired.values()));
  }

  private static void putUnique(Map<String, Entry> desired, Entry value) {
    Entry previous = desired.putIfAbsent(value.code(), value);
    if (previous != null
        && (!previous.name().equals(value.name()) || previous.leaf() != value.leaf())) {
      throw new IllegalStateException("Conflicting permission declaration: " + value.code());
    }
  }

  /** Unpersisted normalized permission; no Security DAO or database entity dependency. */
  public record Entry(String code, String name, String description, String menuCode,
      String parentCode, boolean leaf, int level) {}
}
