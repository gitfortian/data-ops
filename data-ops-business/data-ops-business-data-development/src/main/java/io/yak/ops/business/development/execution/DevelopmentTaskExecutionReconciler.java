package io.yak.ops.business.development.execution;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps durable execution history aligned with the shared in-process Task Runtime. */
@Component
public class DevelopmentTaskExecutionReconciler {

  private static final int RECONCILE_BATCH_SIZE = 200;

  private final DevelopmentTaskExecutionControlService controlService;

  public DevelopmentTaskExecutionReconciler(DevelopmentTaskExecutionControlService controlService) {
    this.controlService = controlService;
  }

  @Scheduled(fixedDelayString = "${yak.data-development.execution.reconcile-delay-ms:1000}")
  public void reconcile() {
    controlService.reconcileActiveExecutions(RECONCILE_BATCH_SIZE);
  }
}
