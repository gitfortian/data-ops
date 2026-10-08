package io.yak.framework.security.common.constant;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A8.2b: keep existing external SecurityPermissionCode FQCN and nested classes compatible. */
class SecurityPermissionLegacyFacadeTest {

    @Test
    void oldAndCanonicalPermissionClassesExposeIdenticalStaticStringFields() throws Exception {
        Map<String, String> legacy = fields(SecurityPermissionCode.class);
        Map<String, String> canonical =
                fields(io.yak.ops.platform.security.contract.SecurityPermissionCode.class);
        assertEquals(28, canonical.size());
        assertEquals(canonical, legacy);
        assertEquals("security:project:read", legacy.get("Project.READ"));
        assertEquals("security:user:reset-password", legacy.get("User.RESET_PASSWORD"));
        assertEquals("system-permissions", legacy.get("Permission.MENU_CODE"));
    }

    @Test
    void legacyNestedBinaryNamesRemainResolvable() throws Exception {
        assertEquals("io.yak.framework.security.common.constant.SecurityPermissionCode",
                Class.forName("io.yak.framework.security.common.constant.SecurityPermissionCode")
                        .getName());
        assertEquals("io.yak.framework.security.common.constant.SecurityPermissionCode$Project",
                Class.forName("io.yak.framework.security.common.constant.SecurityPermissionCode$Project")
                        .getName());
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
                    values.put(nested.getSimpleName() + "." + field.getName(),
                            (String) field.get(null));
                }
            }
        }
        return values;
    }
}
