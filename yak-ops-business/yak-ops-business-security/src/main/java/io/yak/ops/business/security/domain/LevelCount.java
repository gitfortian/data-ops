package io.yak.ops.business.security.domain;

/** 安全总览:某等级的定级对象计数。 */
public record LevelCount(String levelCode, String levelName, Integer rank, long count) {}
