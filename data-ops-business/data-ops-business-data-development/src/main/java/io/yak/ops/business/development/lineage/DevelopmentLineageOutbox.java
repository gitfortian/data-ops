package io.yak.ops.business.development.lineage;

import io.yak.ops.business.development.repository.DevelopmentLineageOutboxRepository;
import io.yak.ops.business.development.repository.DevelopmentLineageOutboxRepository.OutboxRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Durable SQL-lineage work committed atomically with the published revision. */
@Component
public class DevelopmentLineageOutbox {

  private final DevelopmentLineageOutboxRepository repository;

  public DevelopmentLineageOutbox(DevelopmentLineageOutboxRepository repository) {
    this.repository = repository;
  }

  public void enqueue(long nodeId, long revisionId) {
    repository.enqueue(UUID.randomUUID().toString(), nodeId, revisionId);
  }

  /** Cross-project dispatcher query; each returned task restores its project before processing. */
  public List<Task> due(int limit) {
    return repository.due(limit).stream().map(DevelopmentLineageOutbox::toTask).toList();
  }

  public boolean claim(Task task) {
    return repository.claim(toRecord(task));
  }

  public void complete(Task task) {
    repository.complete(toRecord(task));
  }

  public void fail(Task task, Throwable failure) {
    long delay = Math.min(3600, 1L << Math.min(12, task.attempts()));
    String message = failure.getMessage() == null
        ? failure.getClass().getSimpleName()
        : failure.getMessage();
    repository.fail(
        toRecord(task),
        message.substring(0, Math.min(2000, message.length())),
        delay);
  }

  private static Task toTask(OutboxRecord record) {
    return new Task(
        record.taskId(),
        record.projectId(),
        record.nodeId(),
        record.revisionId(),
        record.attempts());
  }

  private static OutboxRecord toRecord(Task task) {
    return new OutboxRecord(
        task.taskId(),
        task.projectId(),
        task.nodeId(),
        task.revisionId(),
        task.attempts());
  }

  public record Task(String taskId, Long projectId, long nodeId, long revisionId, int attempts) {
    public Task {
      if (projectId == null || projectId <= 0L) {
        throw new IllegalArgumentException("lineage outbox projectId must be positive");
      }
    }
  }
}
