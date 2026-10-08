package io.yak.ops.core.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ThreadLocalProjectContextTest {

  private static final class TestScope extends ThreadLocalProjectContext {
    void bind(ProjectContext context) {
      bindTrusted(context);
    }

    void clear() {
      clearTrusted();
    }
  }

  @Test
  void nestedScopesRestoreTheOriginalProject() {
    TestScope scope = new TestScope();
    scope.bind(new ProjectContext(1L, "original"));

    assertEquals(3L, scope.call(new ProjectContext(2L, "outer"), () -> {
      assertEquals(2L, scope.requireProjectId());
      return scope.call(new ProjectContext(3L, "inner"), scope::requireProjectId);
    }));

    assertEquals(1L, scope.requireProjectId());
    scope.clear();
    assertTrue(scope.current().isEmpty());
  }

  @Test
  void aThrowingBackgroundActionAlwaysRestoresThePreviousProject() {
    TestScope scope = new TestScope();
    scope.bind(new ProjectContext(4L, "request"));

    assertThrows(IllegalStateException.class,
        () -> scope.call(new ProjectContext(5L, "background"), () -> {
          assertEquals(5L, scope.requireProjectId());
          throw new IllegalStateException("deliberate failure");
        }));

    assertEquals(4L, scope.requireProjectId());
  }

  @Test
  void backgroundWorkWithoutAParentBindingLeavesNoProjectBehind() {
    TestScope scope = new TestScope();
    assertFalse(scope.isPresent());
    assertEquals(9L, scope.call(new ProjectContext(9L, null), scope::requireProjectId));
    assertFalse(scope.isPresent());
  }

  @Test
  void invalidScopeArgumentsCannotChangeTheExistingContext() {
    TestScope scope = new TestScope();
    scope.bind(new ProjectContext(11L, null));

    assertThrows(IllegalArgumentException.class,
        () -> scope.call(null, scope::requireProjectId));
    assertThrows(IllegalArgumentException.class,
        () -> scope.call(new ProjectContext(12L, null), null));

    assertEquals(11L, scope.requireProjectId());
  }

  @Test
  void parentThreadBindingIsNotInheritedByAnotherThread() throws InterruptedException {
    TestScope scope = new TestScope();
    scope.bind(new ProjectContext(21L, "request"));
    AtomicBoolean otherThreadWasEmpty = new AtomicBoolean();
    AtomicBoolean workerSawItsProject = new AtomicBoolean();
    AtomicBoolean workerCleanedItsProject = new AtomicBoolean();
    Thread worker = new Thread(() -> {
      otherThreadWasEmpty.set(scope.current().isEmpty());
      scope.run(new ProjectContext(22L, "worker"), () ->
          workerSawItsProject.set(Long.valueOf(22L).equals(scope.requireProjectId())));
      workerCleanedItsProject.set(scope.current().isEmpty());
    });
    worker.start();
    worker.join(3000);

    assertFalse(worker.isAlive(), "worker must finish");
    assertTrue(otherThreadWasEmpty.get());
    assertTrue(workerSawItsProject.get());
    assertTrue(workerCleanedItsProject.get());
    assertEquals(21L, scope.requireProjectId());
  }
}
