package io.yak.ops.business.resource.domain;

import io.yak.ops.common.enums.resource.ResourceNodeType;

/** 资源领域分页查询条件。 */
public record ResourceQuery(
    int pageNo,
    int pageSize,
    Long parentId,
    String keyword,
    ResourceNodeType nodeType) {}
