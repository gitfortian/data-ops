package io.yak.ops.business.job.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TaskRuntimeDomainTruthTest {

  @Test
  void registrationRequiresDescriptorAndSnapshotToRepresentSameTask() {
    TaskDefinition definition = new TaskDefinition("task-1", "SQL", "SQL");
    TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
        "task-1", "SQL", "SQL", 7L, "digest", "{}", "{}");

    TaskRegistration registration = new TaskRegistration(definition, snapshot);

    assertEquals("task-1", registration.definition().id());
    assertEquals(7L, registration.snapshot().version());
  }

  @Test
  void registrationRejectsSnapshotDrift() {
    TaskDefinition definition = new TaskDefinition("task-1", "SQL", "SQL");
    TaskVersionSnapshot wrongTask = new TaskVersionSnapshot(
        "task-2", "SQL", "SQL", 7L, "digest", "{}", "{}");
    TaskVersionSnapshot wrongType = new TaskVersionSnapshot(
        "task-1", "SYNC", "SYNC", 7L, "digest", "{}", "{}");

    assertThrows(IllegalArgumentException.class, () -> new TaskRegistration(definition, wrongTask));
    assertThrows(IllegalArgumentException.class, () -> new TaskRegistration(definition, wrongType));
  }
}
