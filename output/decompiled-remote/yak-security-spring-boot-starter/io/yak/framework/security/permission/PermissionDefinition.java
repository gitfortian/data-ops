/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.util.Assert
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.permission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public final class PermissionDefinition {
    private final String code;
    private final String name;
    private final List<Item> permissions;

    private PermissionDefinition(String code, String name, List<Item> permissions) {
        Assert.hasText((String)code, (String)"Permission group code must not be blank");
        Assert.hasText((String)name, (String)"Permission group name must not be blank");
        this.code = code.trim();
        this.name = name.trim();
        this.permissions = Collections.unmodifiableList(new ArrayList<Item>(permissions));
    }

    public static PermissionDefinition of(String code, String name, String ... permissionCodes) {
        ArrayList<Item> items = new ArrayList<Item>();
        if (permissionCodes != null) {
            for (String permissionCode : permissionCodes) {
                items.add(Item.of(permissionCode, permissionCode));
            }
        }
        return new PermissionDefinition(code, name, items);
    }

    public static PermissionDefinition of(String code, String name, Item permission, Item ... additionalPermissions) {
        Assert.notNull((Object)permission, (String)"Permission must not be null");
        ArrayList<Item> items = new ArrayList<Item>();
        items.add(permission);
        if (additionalPermissions != null) {
            Collections.addAll(items, additionalPermissions);
        }
        return new PermissionDefinition(code, name, items);
    }

    public String getCode() {
        return this.code;
    }

    public String getName() {
        return this.name;
    }

    public List<Item> getPermissions() {
        return this.permissions;
    }

    static PermissionDefinition fromItems(String code, String name, List<Item> permissions) {
        return new PermissionDefinition(code, name, permissions);
    }

    public static final class Item {
        private final String code;
        private final String name;
        private final String description;
        private final String menuCode;

        private Item(String code, String name, String description, String menuCode) {
            Assert.hasText((String)code, (String)"Permission code must not be blank");
            Assert.hasText((String)name, (String)"Permission name must not be blank");
            this.code = code.trim();
            this.name = name.trim();
            this.description = StringUtils.hasText((String)description) ? description.trim() : null;
            this.menuCode = StringUtils.hasText((String)menuCode) ? menuCode.trim() : null;
        }

        public static Item of(String code, String name) {
            return new Item(code, name, null, null);
        }

        public static Item of(String code, String name, String description) {
            return new Item(code, name, description, null);
        }

        public static Item ofMenu(String code, String name, String description, String menuCode) {
            return new Item(code, name, description, menuCode);
        }

        public String getCode() {
            return this.code;
        }

        public String getName() {
            return this.name;
        }

        public String getDescription() {
            return this.description;
        }

        public String getMenuCode() {
            return this.menuCode;
        }
    }
}

