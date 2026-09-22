/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class MathUtil {
    private MathUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static long getRandomNumber(int len) {
        if (len <= 0 || len > 18) {
            return 0L;
        }
        if (len == 1) {
            return ThreadLocalRandom.current().nextLong(1L, 10L);
        }
        long minValue = (long)Math.pow(10.0, len - 1);
        long maxValue = (long)Math.pow(10.0, len);
        return ThreadLocalRandom.current().nextLong(minValue, maxValue);
    }

    public static Set<Long> getIntersection(List<Long> list1, List<Long> list2) {
        if (list1 == null || list1.isEmpty() || list2 == null || list2.isEmpty()) {
            return Collections.emptySet();
        }
        HashSet<Long> result = new HashSet<Long>();
        HashSet<Long> targetSet = new HashSet<Long>(list2);
        for (Long number : list1) {
            if (!targetSet.contains(number)) continue;
            result.add(number);
        }
        return result;
    }
}

