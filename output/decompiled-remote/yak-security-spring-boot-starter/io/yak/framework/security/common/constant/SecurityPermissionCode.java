/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.common.constant;

public final class SecurityPermissionCode {
    public static final String GROUP_CODE = "security";
    public static final String GROUP_NAME = "\u7cfb\u7edf\u7ba1\u7406";
    public static final String ROOT = "security:root";

    private SecurityPermissionCode() {
    }

    public static final class OperationLog {
        public static final String READ = "security:operation-log:read";

        private OperationLog() {
        }
    }

    public static final class Config {
        public static final String READ = "security:config:read";

        private Config() {
        }
    }

    public static final class ResourcePermission {
        public static final String READ = "security:resource-permission:read";

        private ResourcePermission() {
        }
    }

    public static final class Project {
        public static final String MENU_CODE = "system-security-projects";
        public static final String READ = "security:project:read";

        private Project() {
        }
    }

    public static final class Department {
        public static final String MENU_CODE = "system-departments";
        public static final String READ = "security:department:read";
        public static final String CREATE = "security:department:create";
        public static final String EDIT = "security:department:edit";
        public static final String DELETE = "security:department:delete";
        public static final String IMPORT = "security:department:import";

        private Department() {
        }
    }

    public static final class Permission {
        public static final String MENU_CODE = "system-permissions";
        public static final String READ = "security:permission:read";
        public static final String IMPORT = "security:permission:import";
        public static final String DELETE = "security:permission:delete";

        private Permission() {
        }
    }

    public static final class Role {
        public static final String READ = "security:role:read";
        public static final String CREATE = "security:role:create";
        public static final String UPDATE = "security:role:update";
        public static final String ASSIGN = "security:role:assign";
        public static final String DELETE = "security:role:delete";

        private Role() {
        }
    }

    public static final class User {
        public static final String READ = "security:user:read";
        public static final String CREATE = "security:user:create";
        public static final String UPDATE = "security:user:update";
        public static final String RESET_PASSWORD = "security:user:reset-password";
        public static final String DELETE = "security:user:delete";

        private User() {
        }
    }
}

