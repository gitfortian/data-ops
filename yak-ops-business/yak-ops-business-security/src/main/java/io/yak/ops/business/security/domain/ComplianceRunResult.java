package io.yak.ops.business.security.domain;

/** 一次合规体检的汇总。 */
public record ComplianceRunResult(String batchId, int checked, int passed, int failed) {}
