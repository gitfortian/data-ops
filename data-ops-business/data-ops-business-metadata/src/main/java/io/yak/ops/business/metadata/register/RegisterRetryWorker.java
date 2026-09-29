package io.yak.ops.business.metadata.register;

import io.yak.ops.business.metadata.register.RegisterRetryStore.Task;
import io.yak.ops.common.enums.metadata.MetadataEnums.RetryOperation;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * outbox 重放器（ticket 130，plan §3.2c）。逐字照抄 {@code DevelopmentLineageWorker} 的骨架：
 * {@code due → projectScope.run → claim → 干活 → complete/fail}，
 * 只加了 DEAD 终态（在 {@link RegisterRetryStore#fail} 里判）。
 *
 * <p><b>第一件事是恢复项目上下文，不是重放</b>：调度线程没有 HTTP 头，而落库链路深处
 * （变更流水、共表行）都按项目窄化——不恢复就是把"重放"变成"在最深处抛异常"（plan §9 T10；
 * 同 {@code MetadataCollectScheduleHandler}）。
 *
 * <p>不经平台调度器（{@code yak_schedule_job}）：这条队列每分钟都要动，
 * 而调度器自己的可用性不该成为目录追平的前置条件。
 */
@Slf4j
@Component
public class RegisterRetryWorker {

  private final RegisterRetryStore retryStore;
  private final MetadataRegistrationService registrationService;
  private final ProjectContextScope projectScope;

  public RegisterRetryWorker(
      RegisterRetryStore retryStore,
      MetadataRegistrationService registrationService,
      ProjectContextScope projectScope) {
    this.retryStore = retryStore;
    this.registrationService = registrationService;
    this.projectScope = projectScope;
  }

  @Scheduled(fixedDelayString = "${yak.metadata.register-retry.poll-delay-ms:1000}")
  public void poll() {
    for (Task task : retryStore.due(RegisterRetryStore.POLL_BATCH)) {
      process(task);
    }
  }

  void process(Task task) {
    projectScope.run(new ProjectContext(task.projectId(), null), () -> processInProject(task));
  }

  private void processInProject(Task task) {
    if (!retryStore.claim(task)) {
      // 别的实例已认领（或状态在 due 与 claim 之间变了）：跳过不是失败，没有重试可言。
      return;
    }
    try {
      replay(task);
      retryStore.complete(task);
    } catch (Throwable failure) {
      retryStore.fail(task, failure);
      log.warn(
          "登记重放失败 task={} op={} key={} attempts={}",
          task.taskId(),
          task.operation(),
          task.assetKey(),
          task.attempts() + 1,
          failure);
    }
  }

  private void replay(Task task) {
    if (task.operation() == RetryOperation.REGISTER) {
      registrationService.register(task.projectId(), retryStore.deserialize(task.payload()));
    } else {
      registrationService.unregister(task.projectId(), task.typeName(), task.sourceId(), task.assetKey());
    }
  }
}
