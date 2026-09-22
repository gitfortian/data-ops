package io.yak.ops.business.approval.domain;

import java.util.List;

/** 流程定义中的一级审批配置:级别从 1 开始连续编号,同级任一人可决(ANY)。 */
public record FlowStepConfig(int level, List<String> approvers) {

  public FlowStepConfig {
    approvers = approvers == null ? List.of() : List.copyOf(approvers);
  }
}
