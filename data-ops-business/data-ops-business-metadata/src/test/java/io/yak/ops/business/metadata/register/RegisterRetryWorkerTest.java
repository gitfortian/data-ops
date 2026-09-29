package io.yak.ops.business.metadata.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metadata.api.RegisterCommand;
import io.yak.ops.business.metadata.register.RegisterRetryStore.Task;
import io.yak.ops.common.enums.metadata.MetadataEnums.RetryOperation;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 重放器（ticket 130，抄 {@code DevelopmentLineageWorker} 骨架）。两条主线：
 * 上下文先于重放（T10），以及"抢不到就什么都不是"。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegisterRetryWorkerTest {

  private static final long PROJECT = 7L;

  private final RegisterRetryStore retryStore = mock(RegisterRetryStore.class);
  private final MetadataRegistrationService registrationService =
      mock(MetadataRegistrationService.class);
  private final ProjectContextScope projectScope = mock(ProjectContextScope.class);
  private final RegisterRetryWorker worker =
      new RegisterRetryWorker(retryStore, registrationService, projectScope);

  @BeforeEach
  void runInline() {
    doAnswer(
            invocation -> {
              invocation.getArgument(1, Runnable.class).run();
              return null;
            })
        .when(projectScope)
        .run(any(), any());
    when(retryStore.claim(any())).thenReturn(true);
  }

  @Test
  void eachTaskRestoresItsOwnProjectBeforeTouchingAnything() {
    AtomicReference<ProjectContext> seen = new AtomicReference<>();
    doAnswer(
            invocation -> {
              seen.set(invocation.getArgument(0, ProjectContext.class));
              invocation.<Runnable>getArgument(1).run();
              return null;
            })
        .when(projectScope)
        .run(any(), any());
    when(retryStore.due(20)).thenReturn(List.of(task(RetryOperation.UNREGISTER)));

    worker.poll();

    // outbox 跨项目捞任务，上下文必须逐条跟着走——否则重放写进错误项目的目录（plan §9 T10）。
    assertThat(seen.get().projectId()).isEqualTo(PROJECT);
  }

  @Test
  void aLostClaimSkipsQuietlyBecauseSomebodyElseOwnsTheRowNow() {
    when(retryStore.claim(any())).thenReturn(false);
    when(retryStore.due(20)).thenReturn(List.of(task(RetryOperation.UNREGISTER)));

    worker.poll();

    verify(registrationService, never()).unregister(any(), any(), any(), any());
    verify(retryStore, never()).complete(any());
    // 不是失败：别人的认领让这条 due 变陈旧，本 worker 连退避都不该写。
    verify(retryStore, never()).fail(any(), any());
  }

  @Test
  void registerReplayDeserializesTheQueuedCommandInsteadOfReQueryingTheSource() {
    Task task = task(RetryOperation.REGISTER);
    RegisterCommand command = new RegisterCommand();
    command.setAssetKey("modeling:model:9");
    when(retryStore.due(20)).thenReturn(List.of(task));
    when(retryStore.deserialize(task.payload())).thenReturn(command);

    worker.poll();

    verify(registrationService).register(PROJECT, command);
    verify(retryStore).complete(task);
  }

  @Test
  void unregisterReplaysStraightFromTheRowColumns() {
    Task task = task(RetryOperation.UNREGISTER);
    when(retryStore.due(20)).thenReturn(List.of(task));

    worker.poll();

    verify(registrationService).unregister(PROJECT, "dataModel", "42", "modeling:model:9");
    verify(retryStore).complete(task);
  }

  @Test
  void aReplayFailureGoesThroughTheBackoffNotIntoTheVoid() {
    Task task = task(RetryOperation.UNREGISTER);
    RuntimeException boom = new RuntimeException("共表暂时不可写");
    doThrow(boom).when(registrationService).unregister(any(), any(), any(), any());
    when(retryStore.due(20)).thenReturn(List.of(task));

    worker.poll();

    ArgumentCaptor<Task> failed = ArgumentCaptor.forClass(Task.class);
    verify(retryStore).fail(failed.capture(), eq(boom));
    assertThat(failed.getValue().taskId()).isEqualTo("t-1");
    verify(retryStore, never()).complete(any());
  }

  private static Task task(RetryOperation operation) {
    return new Task(
        "t-1",
        PROJECT,
        "dataModel",
        "42",
        "modeling:model:9",
        operation,
        2,
        operation == RetryOperation.REGISTER ? "{\"attributes\":{}}" : null);
  }
}
