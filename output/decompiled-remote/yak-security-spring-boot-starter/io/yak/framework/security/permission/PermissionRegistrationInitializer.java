/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.aop.support.AopUtils
 *  org.springframework.beans.factory.ListableBeanFactory
 *  org.springframework.beans.factory.SmartInitializingSingleton
 *  org.springframework.core.MethodIntrospector
 *  org.springframework.core.annotation.AnnotatedElementUtils
 *  org.springframework.util.ClassUtils
 *  org.springframework.util.StringUtils
 */
package io.yak.framework.security.permission;

import io.yak.framework.security.permission.PermissionDefinition;
import io.yak.framework.security.permission.PermissionDefinitionProvider;
import io.yak.framework.security.permission.PermissionRegistrationService;
import io.yak.framework.security.permission.YakPermission;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public class PermissionRegistrationInitializer
implements SmartInitializingSingleton {
    private final ListableBeanFactory beanFactory;
    private final PermissionRegistrationService registrationService;

    public PermissionRegistrationInitializer(ListableBeanFactory beanFactory, PermissionRegistrationService registrationService) {
        this.beanFactory = beanFactory;
        this.registrationService = registrationService;
    }

    public void afterSingletonsInstantiated() {
        LinkedHashMap<String, GroupBuilder> groups = new LinkedHashMap<String, GroupBuilder>();
        this.beanFactory.getBeansOfType(PermissionDefinitionProvider.class).values().forEach(provider -> provider.getPermissionDefinitions().forEach(definition -> PermissionRegistrationInitializer.merge(groups, definition)));
        for (String beanName : this.beanFactory.getBeanDefinitionNames()) {
            Class type = this.beanFactory.getType(beanName, false);
            if (type == null) continue;
            Class targetType = ClassUtils.getUserClass((Class)type);
            PermissionRegistrationInitializer.addAnnotation(groups, (YakPermission)AnnotatedElementUtils.findMergedAnnotation((AnnotatedElement)targetType, YakPermission.class));
            Map methods = MethodIntrospector.selectMethods((Class)targetType, method -> (YakPermission)AnnotatedElementUtils.findMergedAnnotation((AnnotatedElement)AopUtils.getMostSpecificMethod((Method)method, (Class)targetType), YakPermission.class));
            methods.values().forEach(annotation -> PermissionRegistrationInitializer.addAnnotation(groups, annotation));
        }
        ArrayList<PermissionDefinition> definitions = new ArrayList<PermissionDefinition>();
        groups.values().forEach(group -> definitions.add(group.build()));
        this.registrationService.synchronize(definitions);
    }

    private static void addAnnotation(Map<String, GroupBuilder> groups, YakPermission annotation) {
        if (annotation == null) {
            return;
        }
        String groupCode = StringUtils.hasText((String)annotation.groupCode()) ? annotation.groupCode() : PermissionRegistrationInitializer.inferGroupCode(annotation.code());
        PermissionRegistrationInitializer.merge(groups, PermissionDefinition.of(groupCode, annotation.group(), PermissionDefinition.Item.ofMenu(annotation.code(), annotation.name(), annotation.description(), annotation.menuCode()), new PermissionDefinition.Item[0]));
    }

    private static String inferGroupCode(String code) {
        int separator = code.indexOf(58);
        if (separator <= 0) {
            throw new IllegalStateException("Permission '" + code + "' must contain ':' or declare groupCode");
        }
        return code.substring(0, separator);
    }

    private static void merge(Map<String, GroupBuilder> groups, PermissionDefinition definition) {
        groups.computeIfAbsent(definition.getCode(), code -> new GroupBuilder((String)code, definition.getName())).add(definition);
    }

    private static final class GroupBuilder {
        private final String code;
        private final String name;
        private final Map<String, PermissionDefinition.Item> items = new LinkedHashMap<String, PermissionDefinition.Item>();

        private GroupBuilder(String code, String name) {
            this.code = code;
            this.name = name;
        }

        private void add(PermissionDefinition definition) {
            if (!this.name.equals(definition.getName())) {
                throw new IllegalStateException("Conflicting permission group declaration: " + this.code);
            }
            for (PermissionDefinition.Item item : definition.getPermissions()) {
                PermissionDefinition.Item old = this.items.putIfAbsent(item.getCode(), item);
                if (old == null || old.getName().equals(item.getName()) && Objects.equals(old.getMenuCode(), item.getMenuCode())) continue;
                throw new IllegalStateException("Conflicting permission declaration: " + item.getCode());
            }
        }

        private PermissionDefinition build() {
            return PermissionDefinition.fromItems(this.code, this.name, new ArrayList<PermissionDefinition.Item>(this.items.values()));
        }
    }
}

