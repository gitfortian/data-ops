package io.yak.ops.business.sync.offline.definition;

import io.yak.ops.common.enums.PublishState;
import io.yak.framework.common.PageData;
import io.yak.ops.business.job.task.TaskDefinition;
import io.yak.ops.business.job.task.TaskProvider;
import io.yak.ops.business.job.task.TaskRegistration;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.sync.offline.domain.OfflineDefinitionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.core.project.CurrentProject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Exposes workflow-eligible Offline Sync definitions through the generic Job task contract. */
@Component
public class OfflineSyncTaskProvider implements TaskProvider {

  private static final int PAGE_SIZE = 200;
  

  private final ObjectProvider<OfflineJobDefinitionService> definitionServiceProvider;
  private final CurrentProject currentProject;

  @org.springframework.beans.factory.annotation.Autowired
  public OfflineSyncTaskProvider(
      ObjectProvider<OfflineJobDefinitionService> definitionServiceProvider,
      CurrentProject currentProject) {
    this.definitionServiceProvider = definitionServiceProvider;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused tests; unscoped discovery intentionally returns empty. */
  public OfflineSyncTaskProvider(
      ObjectProvider<OfflineJobDefinitionService> definitionServiceProvider) {
    this(
        definitionServiceProvider,
        Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public List<TaskDefinition> list() {
    return registrations().stream().map(TaskRegistration::definition).toList();
  }

  @Override
  public TaskVersionSnapshot snapshot(String taskId) {
    currentProject.requireProjectId();
    OfflineJobDefinition current = service().require(parseTaskId(taskId));
    if (!isWorkflowEligible(current)) {
      throw new IllegalArgumentException("离线同步任务当前不可用于工作流：" + taskId);
    }
    return registration(current).snapshot();
  }

  @Override
  public List<TaskRegistration> registrations() {
    // Job Registry is GLOBAL infrastructure. It must not turn an empty ProjectContext into a
    // cross-Project Offline task enumeration; Project-aware callers refresh the registry in scope.
    if (!currentProject.isPresent()) return List.of();

    OfflineJobDefinitionService service = definitionServiceProvider.getIfAvailable();
    if (service == null) return List.of();

    List<TaskRegistration> registrations = new ArrayList<>();
    int pageNo = 1;
    while (true) {
      PageData<OfflineJobDefinition> page = service.pageDomain(query(pageNo));
      for (OfflineJobDefinition definition : page.records()) {
        if (!isWorkflowEligible(definition)) continue;
        try {
          registrations.add(registration(service.require(definition.getId())));
        } catch (RuntimeException ignored) {
          // Draft/deleted definitions or definitions without executable JobSpec are not discoverable.
        }
      }
      if (pageNo >= page.pages()) break;
      pageNo++;
    }
    return List.copyOf(registrations);
  }

  private TaskRegistration registration(OfflineJobDefinition definition) {
    if (!isWorkflowEligible(definition)) {
      throw new IllegalArgumentException("离线同步任务当前不可用于工作流：" + definition.getId());
    }
    definition.requireProjectId();
    // W1-2 契约 C4：工作流快照同样取自已发布 revision，未发布任务不可被发现。
    io.yak.ops.business.sync.offline.domain.OfflineJobRevision revision =
        service().publishedRevision(definition);
    if (revision == null) {
      throw new IllegalStateException("离线同步任务尚未发布：" + definition.getId());
    }
    String id = String.valueOf(definition.getId());
    String name = definition.getJobName();
    TaskDefinition descriptor = new TaskDefinition(id, name, "SYNC");
    TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
        id,
        name,
        "SYNC",
        Math.max(1, revision.getVersionNo() == null ? 1 : revision.getVersionNo()),
        revision.getConfigDigest(),
        revision.getDefinitionJson(),
        revision.getJobSpecJson());
    return new TaskRegistration(descriptor, snapshot);
  }

  private OfflineDefinitionQuery query(int pageNo) {
    return new OfflineDefinitionQuery(
        pageNo,
        PAGE_SIZE,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private OfflineJobDefinitionService service() {
    OfflineJobDefinitionService service = definitionServiceProvider.getIfAvailable();
    if (service == null) throw new IllegalStateException("离线同步能力未启用");
    return service;
  }

  private Long parseTaskId(String taskId) {
    if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId 不能为空");
    try {
      long value = Long.parseLong(taskId.trim());
      if (value <= 0L) throw new NumberFormatException("not positive");
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("SYNC taskId 不合法：" + taskId, exception);
    }
  }

  static boolean isWorkflowEligible(OfflineJobDefinition definition) {
    return definition != null
        && definition.getId() != null
        && definition.getJobName() != null
        && PublishState.PUBLISHED.matches(definition.getReleaseState())
        && !Boolean.TRUE.equals(definition.getScheduleEnabled());
  }
}
