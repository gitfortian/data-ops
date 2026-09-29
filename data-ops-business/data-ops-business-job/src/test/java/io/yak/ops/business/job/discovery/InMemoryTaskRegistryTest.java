package io.yak.ops.business.job.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.job.task.TaskDefinition;
import io.yak.ops.business.job.task.TaskProvider;
import io.yak.ops.business.job.task.TaskRegistration;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class InMemoryTaskRegistryTest {

  @Test
  void aggregatesOnlyTaskProviderRegistrations() {
    TaskProvider provider = mock(TaskProvider.class);
    when(provider.registrations()).thenReturn(List.of(registration("task-1", "SQL", 3L)));
    InMemoryTaskRegistry registry = new InMemoryTaskRegistry(providers(provider));

    assertThat(registry.list()).containsExactly(new TaskDefinition("task-1", "Task task-1", "SQL"));
    assertThat(registry.snapshot("task-1").version()).isEqualTo(3L);
  }

  @Test
  void rejectsDuplicateTaskIdsAcrossProviders() {
    TaskProvider first = mock(TaskProvider.class);
    TaskProvider second = mock(TaskProvider.class);
    when(first.registrations()).thenReturn(List.of(registration("same", "SQL", 1L)));
    when(second.registrations()).thenReturn(List.of(registration("same", "SYNC", 2L)));
    InMemoryTaskRegistry registry = new InMemoryTaskRegistry(providers(first, second));

    assertThatThrownBy(registry::list)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("重复的工作流任务 ID");
  }

  private TaskRegistration registration(String id, String type, long version) {
    TaskDefinition definition = new TaskDefinition(id, "Task " + id, type);
    TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
        id, definition.name(), type, version, "digest", "{}", "{}");
    return new TaskRegistration(definition, snapshot);
  }

  @SafeVarargs
  @SuppressWarnings("unchecked")
  private final ObjectProvider<TaskProvider> providers(TaskProvider... values) {
    ObjectProvider<TaskProvider> provider = mock(ObjectProvider.class);
    when(provider.orderedStream()).thenAnswer(ignored -> java.util.stream.Stream.of(values));
    return provider;
  }
}
