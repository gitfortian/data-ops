package io.yak.ops.business.resource.domain;

/** 可在线查看或编辑的资源内容。 */
public record ResourceContent(
    Long resourceId,
    String fullPath,
    String content,
    int skipLineNum,
    int lineCount,
    boolean hasMore) {}
