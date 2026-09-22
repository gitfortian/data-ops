/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.core.metadata.IPage
 *  com.baomidou.mybatisplus.extension.plugins.pagination.Page
 *  org.springframework.beans.BeanUtils
 */
package io.yak.framework.security.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;

public final class CopyBeanUtil {
    private CopyBeanUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static <T> T copy(Object source, Class<T> target) {
        if (source == null || target == null) {
            return null;
        }
        try {
            T newInstance = target.getDeclaredConstructor(new Class[0]).newInstance(new Object[0]);
            BeanUtils.copyProperties((Object)source, newInstance);
            return newInstance;
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Bean copy failed: " + source.getClass().getName() + " -> " + target.getName() + ". Target class must provide a no-args constructor.", e);
        }
    }

    public static <T, K> List<K> copyList(List<T> source, Class<K> target) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return source.stream().filter(Objects::nonNull).map(element -> CopyBeanUtil.copy(element, target)).collect(Collectors.toList());
    }

    public static <T, K> List<K> copyList(List<T> source, Class<K> target, Consumer<K> consumer) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return source.stream().map(element -> CopyBeanUtil.copy(element, target)).peek(consumer).collect(Collectors.toList());
    }

    public static <T, K> IPage<K> copyPage(IPage<T> source, Class<K> target) {
        if (source == null || target == null) {
            return null;
        }
        Page targetPage = new Page();
        BeanUtils.copyProperties(source, (Object)targetPage);
        targetPage.setTotal(source.getTotal());
        targetPage.setRecords(CopyBeanUtil.copyList(source.getRecords(), target));
        return targetPage;
    }

    public static <T, K> IPage<K> copyPageExcludeList(IPage<T> source) {
        if (source == null) {
            return null;
        }
        return (IPage)CopyBeanUtil.copy(source, Page.class);
    }
}

