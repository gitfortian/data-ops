package io.yak.ops.business.resource.domain;

import io.yak.ops.common.enums.resource.ResourceStorageType;

/** 已安装存储插件的业务投影。 */
public record ResourceStoragePlugin(
    ResourceStorageType type,
    String name,
    boolean active) {}
