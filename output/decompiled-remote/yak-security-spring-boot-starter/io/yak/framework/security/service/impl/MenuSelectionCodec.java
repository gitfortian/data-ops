/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public final class MenuSelectionCodec {
    public static final long MENU_GROUP_NODE_ID = -1L;

    private MenuSelectionCodec() {
    }

    public static long encodeMenuId(Long menuId) {
        if (menuId == null || menuId <= 0L) {
            throw new IllegalArgumentException("\u83dc\u5355 ID \u5fc5\u987b\u4e3a\u6b63\u6570");
        }
        return -(menuId + 1L);
    }

    public static boolean isEncodedMenuId(Long value) {
        return value != null && value < -1L;
    }

    public static long decodeMenuId(Long encodedId) {
        if (!MenuSelectionCodec.isEncodedMenuId(encodedId)) {
            throw new IllegalArgumentException("\u4e0d\u662f\u6709\u6548\u7684\u83dc\u5355\u9009\u62e9 ID");
        }
        return -encodedId.longValue() - 1L;
    }

    public static List<Long> extractPermissionIds(Collection<Long> values) {
        LinkedHashSet<Long> result = new LinkedHashSet<Long>();
        if (values != null) {
            for (Long value : values) {
                if (value == null || value <= 0L) continue;
                result.add(value);
            }
        }
        return new ArrayList<Long>(result);
    }

    public static List<Long> extractMenuIds(Collection<Long> values) {
        LinkedHashSet<Long> result = new LinkedHashSet<Long>();
        if (values != null) {
            for (Long value : values) {
                if (!MenuSelectionCodec.isEncodedMenuId(value)) continue;
                result.add(MenuSelectionCodec.decodeMenuId(value));
            }
        }
        return new ArrayList<Long>(result);
    }
}

