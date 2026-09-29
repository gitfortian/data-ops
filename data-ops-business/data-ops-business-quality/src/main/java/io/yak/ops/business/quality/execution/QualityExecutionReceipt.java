package io.yak.ops.business.quality.execution;

import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;

/** Immediate receipt returned after a quality execution is accepted. */
public record QualityExecutionReceipt(
    String executionNo,
    ExecutionStatus executionStatus,
    CheckResult checkResult) {}
