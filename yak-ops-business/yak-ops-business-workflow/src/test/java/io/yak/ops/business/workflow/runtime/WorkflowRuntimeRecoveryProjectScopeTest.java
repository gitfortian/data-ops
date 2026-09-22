package io.yak.ops.business.workflow.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository;
import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository.ProjectExecutionRef;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowRuntimeRecoveryProjectScopeTest {

  @Mock private WorkflowRuntime runtime;
  @Mock private WorkflowRuntimeRepository repository;

  @Test
  void restoresPersistedProjectBeforeRecoveringEachProjectGroup() {
    when(repository.findRecoverableExecutionsForDispatch()).thenReturn(List.of(
        new ProjectExecutionRef(7L, "execution-7"),
        new ProjectExecutionRef(9L, "execution-9")));
    when(runtime.recoverPersistedExecutions()).thenReturn(1);
    RecordingProjectContextScope projectScope = new RecordingProjectContextScope();
    WorkflowRuntimeRecovery recovery = new WorkflowRuntimeRecovery(runtime, repository, projectScope);

    recovery.recover();

    assertThat(projectScope.projectIds()).containsExactly(7L, 9L);
    verify(runtime).activate("execution-7");
    verify(runtime).activate("execution-9");
    verify(runtime, times(2)).recoverPersistedExecutions();
  }

  private static final class RecordingProjectContextScope implements ProjectContextScope {
    private final List<Long> projectIds = new ArrayList<>();

    @Override
    public <T> T call(ProjectContext context, Supplier<T> action) {
      projectIds.add(context.projectId());
      return action.get();
    }

    List<Long> projectIds() {
      return List.copyOf(projectIds);
    }
  }
}
