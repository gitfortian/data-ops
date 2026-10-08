package io.yak.ops.platform.security.contract;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityPermissionCodeTest {

    @Test
    void canonicalOwnerPreservesAllHistoricalPermissionCodes() throws Exception {
        Map<String, String> expected = Map.ofEntries(
        Map.entry("GROUP_CODE", "security"),
        Map.entry("GROUP_NAME", "系统管理"),
        Map.entry("ROOT", "security:root"),
        Map.entry("User.READ", "security:user:read"),
        Map.entry("User.CREATE", "security:user:create"),
        Map.entry("User.UPDATE", "security:user:update"),
        Map.entry("User.RESET_PASSWORD", "security:user:reset-password"),
        Map.entry("User.DELETE", "security:user:delete"),
        Map.entry("Role.READ", "security:role:read"),
        Map.entry("Role.CREATE", "security:role:create"),
        Map.entry("Role.UPDATE", "security:role:update"),
        Map.entry("Role.ASSIGN", "security:role:assign"),
        Map.entry("Role.DELETE", "security:role:delete"),
        Map.entry("Permission.MENU_CODE", "system-permissions"),
        Map.entry("Permission.READ", "security:permission:read"),
        Map.entry("Permission.IMPORT", "security:permission:import"),
        Map.entry("Permission.DELETE", "security:permission:delete"),
        Map.entry("Department.MENU_CODE", "system-departments"),
        Map.entry("Department.READ", "security:department:read"),
        Map.entry("Department.CREATE", "security:department:create"),
        Map.entry("Department.EDIT", "security:department:edit"),
        Map.entry("Department.DELETE", "security:department:delete"),
        Map.entry("Department.IMPORT", "security:department:import"),
        Map.entry("Project.MENU_CODE", "system-security-projects"),
        Map.entry("Project.READ", "security:project:read"),
        Map.entry("ResourcePermission.READ", "security:resource-permission:read"),
        Map.entry("Config.READ", "security:config:read"),
        Map.entry("OperationLog.READ", "security:operation-log:read")
        );
        Map<String, String> actual = fields(SecurityPermissionCode.class);
        assertEquals(28, actual.size());
        assertEquals(expected, actual);
    }

    private static Map<String, String> fields(Class<?> type) throws IllegalAccessException {
        Map<String, String> values = new TreeMap<>();
        for (Field field : type.getFields()) {
            if (field.getType() == String.class) {
                values.put(field.getName(), (String) field.get(null));
            }
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            for (Field field : nested.getFields()) {
                if (field.getType() == String.class) {
                    values.put(nested.getSimpleName() + "." + field.getName(), (String) field.get(null));
                }
            }
        }
        return values;
    }
}
