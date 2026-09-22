package io.yak.ops.business.workflow.runtime;

import io.yak.ops.business.workflow.observability.WorkflowEventStream;

import io.yak.framework.workflow.engine.api.DefaultWorkflowEngine;
import io.yak.framework.workflow.engine.api.WorkflowRecoveryCoordinator;
import io.yak.framework.workflow.engine.definition.EdgeDefinition;
import io.yak.framework.workflow.engine.definition.NodeDefinition;
import io.yak.framework.workflow.engine.definition.NodeFailurePolicy;
import io.yak.framework.workflow.engine.definition.NodeInputMapping;
import io.yak.framework.workflow.engine.definition.NodeTimeoutPolicy;
import io.yak.framework.workflow.engine.definition.RetryPolicy;
import io.yak.framework.workflow.engine.definition.TriggerRule;
import io.yak.framework.workflow.engine.definition.WorkflowDefinition;
import io.yak.framework.workflow.engine.definition.WorkflowFailureStrategy;
import io.yak.framework.workflow.engine.definition.WorkflowTimeoutPolicy;
import io.yak.framework.workflow.engine.event.WorkflowEventListener;
import io.yak.framework.workflow.engine.execution.NodeAttempt;
import io.yak.framework.workflow.engine.execution.NodeExecution;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import io.yak.framework.workflow.engine.spi.ExecutionLock;
import io.yak.framework.workflow.engine.spi.ExecutionRepository;
import io.yak.framework.workflow.engine.spi.IdGenerator;
import io.yak.framework.workflow.engine.spi.NodeCancellation;
import io.yak.framework.workflow.engine.spi.NodeControlResult;
import io.yak.framework.workflow.engine.spi.NodeDispatch;
import io.yak.framework.workflow.engine.spi.NodeExecutor;
import io.yak.framework.workflow.engine.spi.NodePauseRequest;
import io.yak.framework.workflow.engine.spi.NodeRecovery;
import io.yak.framework.workflow.engine.spi.NodeResumeRequest;
import io.yak.framework.workflow.engine.spi.WorkflowDefinitionRepository;
import io.yak.framework.workflow.engine.state.NodeAttemptStatus;
import io.yak.framework.workflow.engine.support.InMemoryExecutionRepository;
import io.yak.framework.workflow.engine.support.InMemoryWorkflowDefinitionRepository;
import io.yak.framework.workflow.engine.support.LocalExecutionLock;
import io.yak.framework.workflow.engine.support.UuidIdGenerator;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.domain.WorkflowEdgeSpec;
import io.yak.ops.business.workflow.domain.WorkflowNodeSpec;
import io.yak.ops.business.workflow.domain.WorkflowRunSpec;
import io.yak.ops.business.workflow.repository.InMemoryWorkflowRuntimeRepository;
import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository;
import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository.NodeMetadataRecord;
import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository.RuntimeMetadataRecord;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO.AttemptVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO.NodeInstanceVO;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import io.yak.ops.core.project.ProjectContextScope;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Yak Framework 工作流引擎的可持久化运行时适配层。 */
@Service
public class WorkflowRuntime {
  private static final Logger log = LoggerFactory.getLogger(WorkflowRuntime.class);
  private static final long TASK_POLL_INTERVAL_MILLIS = 500L;

  /** 每次 task start/status/cancel 都是短生命周期 I/O；不会为任务全生命周期占用固定平台线程。 */
  private final ExecutorService ioExecutor;
  private final ScheduledExecutorService runtimeScheduler;
  private final DefaultWorkflowEngine engine;
  private final WorkflowRecoveryCoordinator recoveryCoordinator;
  private final WorkflowEventStream eventStreamService;
  private final TaskRegistry taskRegistry;
  private final TaskExecutionGateway taskExecutionGateway;
  private final WorkflowRuntimeRepository runtimePersistence;
  private final CurrentProject currentProject;
  private final ProjectContextScope projectContextScope;
  private final boolean projectRequired;
  private final long taskPollIntervalMillis;
  private final ConcurrentMap<String, ConcurrentLinkedQueue<NodeDispatch>> pendingDispatches =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Object> publishLocks = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, NodeTaskControl> taskControls = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ConcurrentMap<String, NodeDispatch>> latestDispatches =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ProjectContext> executionProjects = new ConcurrentHashMap<>();
  private final Set<String> activeExecutions = ConcurrentHashMap.newKeySet();
  private final Map<String, WorkflowExecutionMetadata> metadata = new ConcurrentHashMap<>();

  @Autowired
  public WorkflowRuntime(
      WorkflowEventStream eventStreamService,
      TaskRegistry taskRegistry,
      TaskExecutionGateway taskExecutionGateway,
      ObjectProvider<WorkflowDefinitionRepository> definitionRepository,
      ObjectProvider<ExecutionRepository> executionRepository,
      ObjectProvider<WorkflowRuntimeRepository> runtimePersistence,
      CurrentProject currentProject,
      ProjectContextScope projectContextScope,
      @Value("${yak.database.enabled:true}") boolean databaseEnabled) {
    this(
        eventStreamService,
        taskRegistry,
        taskExecutionGateway,
        TASK_POLL_INTERVAL_MILLIS,
        resolvePersistence(
            definitionRepository,
            databaseEnabled,
            InMemoryWorkflowDefinitionRepository::new,
            "WorkflowDefinitionRepository"),
        resolvePersistence(
            executionRepository,
            databaseEnabled,
            InMemoryExecutionRepository::new,
            "ExecutionRepository"),
        resolvePersistence(
            runtimePersistence,
            databaseEnabled,
            InMemoryWorkflowRuntimeRepository::new,
            "WorkflowRuntimeRepository"),
        currentProject,
        projectContextScope,
        true);
  }

  /** Focused persistence wiring constructor used by tests and database-disabled development. */
  public WorkflowRuntime(
      WorkflowEventStream eventStreamService,
      TaskRegistry taskRegistry,
      TaskExecutionGateway taskExecutionGateway,
      ObjectProvider<WorkflowDefinitionRepository> definitionRepository,
      ObjectProvider<ExecutionRepository> executionRepository,
      ObjectProvider<WorkflowRuntimeRepository> runtimePersistence,
      boolean databaseEnabled) {
    this(
        eventStreamService,
        taskRegistry,
        taskExecutionGateway,
        TASK_POLL_INTERVAL_MILLIS,
        resolvePersistence(
            definitionRepository,
            databaseEnabled,
            InMemoryWorkflowDefinitionRepository::new,
            "WorkflowDefinitionRepository"),
        resolvePersistence(
            executionRepository,
            databaseEnabled,
            InMemoryExecutionRepository::new,
            "ExecutionRepository"),
        resolvePersistence(
            runtimePersistence,
            databaseEnabled,
            InMemoryWorkflowRuntimeRepository::new,
            "WorkflowRuntimeRepository"),
        null,
        null,
        false);
  }

  /** Focused tests and explicit database-disabled development keep the lightweight runtime. */
  public WorkflowRuntime(
      WorkflowEventStream eventStreamService,
      TaskRegistry taskRegistry,
      TaskExecutionGateway taskExecutionGateway,
      long taskPollIntervalMillis) {
    this(
        eventStreamService,
        taskRegistry,
        taskExecutionGateway,
        taskPollIntervalMillis,
        new InMemoryWorkflowDefinitionRepository(),
        new InMemoryExecutionRepository(),
        new InMemoryWorkflowRuntimeRepository(),
        null,
        null,
        false);
  }

  /** Project-aware focused runtime used by Project Space regression tests. */
  public WorkflowRuntime(
      WorkflowEventStream eventStreamService,
      TaskRegistry taskRegistry,
      TaskExecutionGateway taskExecutionGateway,
      long taskPollIntervalMillis,
      CurrentProject currentProject,
      ProjectContextScope projectContextScope) {
    this(
        eventStreamService,
        taskRegistry,
        taskExecutionGateway,
        taskPollIntervalMillis,
        new InMemoryWorkflowDefinitionRepository(),
        new InMemoryExecutionRepository(),
        new InMemoryWorkflowRuntimeRepository(),
        currentProject,
        projectContextScope,
        true);
  }

  private static <T> T resolvePersistence(
      ObjectProvider<T> provider,
      boolean databaseEnabled,
      Supplier<T> fallback,
      String componentName) {
    T resolved = provider.getIfAvailable();
    if (resolved != null) {
      return resolved;
    }
    if (!databaseEnabled) {
      return fallback.get();
    }
    throw new IllegalStateException(
        "Workflow durable persistence bean is missing while yak.database.enabled=true: "
            + componentName);
  }

  public WorkflowRuntime(
      WorkflowEventStream eventStreamService,
      TaskRegistry taskRegistry,
      TaskExecutionGateway taskExecutionGateway,
      long taskPollIntervalMillis,
      WorkflowDefinitionRepository definitionRepository,
      ExecutionRepository executionRepository,
      WorkflowRuntimeRepository runtimePersistence) {
    this(
        eventStreamService,
        taskRegistry,
        taskExecutionGateway,
        taskPollIntervalMillis,
        definitionRepository,
        executionRepository,
        runtimePersistence,
        null,
        null,
        false);
  }

  public WorkflowRuntime(
      WorkflowEventStream eventStreamService,
      TaskRegistry taskRegistry,
      TaskExecutionGateway taskExecutionGateway,
      long taskPollIntervalMillis,
      WorkflowDefinitionRepository definitionRepository,
      ExecutionRepository executionRepository,
      WorkflowRuntimeRepository runtimePersistence,
      CurrentProject currentProject,
      ProjectContextScope projectContextScope,
      boolean projectRequired) {
    if (projectRequired && (currentProject == null || projectContextScope == null)) {
      throw new IllegalArgumentException(
          "Project-aware WorkflowRuntime requires CurrentProject and ProjectContextScope");
    }
    this.eventStreamService = eventStreamService;
    this.taskRegistry = taskRegistry;
    this.taskExecutionGateway = taskExecutionGateway;
    this.runtimePersistence = runtimePersistence;
    this.currentProject = currentProject;
    this.projectContextScope = projectContextScope;
    this.projectRequired = projectRequired;
    this.taskPollIntervalMillis = Math.max(1L, taskPollIntervalMillis);
    this.ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
    AtomicInteger schedulerIndex = new AtomicInteger();
    this.runtimeScheduler = Executors.newScheduledThreadPool(2, runnable -> {
      Thread thread = new Thread(runnable);
      thread.setName("yak-workflow-runtime-" + schedulerIndex.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    });

    RuntimeNodeExecutor nodeExecutor = new RuntimeNodeExecutor();
    ExecutionLock executionLock = new LocalExecutionLock();
    IdGenerator idGenerator = new UuidIdGenerator();
    Clock clock = Clock.systemUTC();
    this.engine = new DefaultWorkflowEngine(
        definitionRepository,
        executionRepository,
        nodeExecutor,
        executionLock,
        idGenerator,
        clock,
        WorkflowEventListener.noop());
    this.recoveryCoordinator = new WorkflowRecoveryCoordinator(
        definitionRepository,
        executionRepository,
        nodeExecutor,
        executionLock,
        idGenerator,
        clock);
    this.runtimeScheduler.scheduleAtFixedRate(
        this::scanTimeouts, 250L, 250L, TimeUnit.MILLISECONDS);
  }

  /** 直接运行 API：DTO 只存在于接口边界，进入 Runtime 后立即转换为领域规格。 */
  public WorkflowInstanceVO run(WorkflowRunDTO request) {
    requireCurrentProject();
    return run(toRunSpec(request));
  }

  /** 直接运行领域入口：执行开始前固定一次当前任务快照。 */
  public WorkflowInstanceVO run(WorkflowRunSpec request) {
    requireCurrentProject();
    Map<String, TaskVersionSnapshot> snapshots = new LinkedHashMap<>();
    for (WorkflowNodeSpec node : request.nodes()) {
      snapshots.put(node.id(), taskRegistry.snapshot(node.taskId()));
    }
    return run(request, snapshots, null, null, false);
  }

  /** WorkflowVersion/草稿测试运行入口，使用调用方已经固定的任务版本快照。 */
  public WorkflowInstanceVO run(
      WorkflowRunSpec request,
      Map<String, TaskVersionSnapshot> taskVersionsByNode,
      String workflowVersionId,
      Integer workflowVersionNo,
      boolean testRun) {
    requireCurrentProject();
    String definitionId = workflowVersionId == null || workflowVersionId.isBlank()
        ? "workflow-runtime-" + UUID.randomUUID()
        : workflowVersionId;
    Map<String, TaskVersionSnapshot> tasks =
        validateTaskSnapshots(request.nodes(), taskVersionsByNode);
    List<NodeDefinition> nodes = request.nodes().stream()
        .map(node -> toNodeDefinition(node, tasks.get(node.id())))
        .toList();
    List<EdgeDefinition> edges = request.edges().stream().map(this::toEdgeDefinition).toList();
    WorkflowDefinition definition = new WorkflowDefinition(
        definitionId,
        request.name(),
        WorkflowFailureStrategy.valueOf(request.failureStrategy()),
        request.workflowTimeoutSeconds() > 0
            ? WorkflowTimeoutPolicy.of(Duration.ofSeconds(request.workflowTimeoutSeconds()))
            : WorkflowTimeoutPolicy.none(),
        nodes,
        edges);
    WorkflowExecutionMetadata runMetadata = new WorkflowExecutionMetadata(
        request.name(),
        request.edges().size(),
        request.workflowTimeoutSeconds(),
        request.failureStrategy(),
        workflowVersionId,
        workflowVersionNo,
        testRun,
        nodeMetadata(request.nodes(), tasks));

    engine.registerDefinition(definition);
    runtimePersistence.prepareMetadata(definitionId, toPersistence(runMetadata));

    WorkflowExecution execution = engine.start(definitionId, request.input());
    registerExecution(execution, runMetadata);
    WorkflowInstanceVO started = toView(execution, runMetadata);
    log.info(
        "[workflow] prepared execution={}, engineDefinition={}, workflowVersion={}, versionNo={}, testRun={}, nodes={}, edges={}",
        execution.id(),
        definitionId,
        workflowVersionId,
        workflowVersionNo,
        testRun,
        request.nodes().size(),
        request.edges().size());
    return started;
  }

  public WorkflowInstanceVO activate(String executionId) {
    activateExecution(executionId);
    return getInstance(executionId);
  }

  public WorkflowInstanceVO pause(String executionId) {
    requireMetadata(executionId);
    return publishAndView(engine.pause(executionId, "Paused from Yak Ops"));
  }

  public WorkflowInstanceVO resume(String executionId) {
    requireMetadata(executionId);
    WorkflowExecution execution = engine.resume(executionId);
    activeExecutions.add(executionId);
    WorkflowInstanceVO snapshot = publishAndView(execution);
    drainDispatches(executionId);
    return snapshot;
  }

  public WorkflowInstanceVO cancel(String executionId) {
    requireMetadata(executionId);
    return publishAndView(engine.cancel(executionId, "Canceled from Yak Ops"));
  }

  public WorkflowInstanceVO continueAfterFailure(String executionId, String nodeId) {
    requireMetadata(executionId);
    WorkflowExecution execution = engine.continueAfterFailure(executionId, nodeId);
    WorkflowInstanceVO snapshot = publishAndView(execution);
    reactivateExecution(executionId);
    return snapshot;
  }

  public WorkflowInstanceVO retryFailedNode(String executionId, String nodeId) {
    requireMetadata(executionId);
    WorkflowInstanceVO snapshot = publishAndView(engine.retryFailedNode(executionId, nodeId));
    reactivateExecution(executionId);
    return snapshot;
  }

  public WorkflowInstanceVO retryFailedNodes(String executionId) {
    requireExecution(executionId);
    WorkflowInstanceVO snapshot = publishAndView(engine.retryFailedNodes(executionId));
    reactivateExecution(executionId);
    return snapshot;
  }

  public WorkflowInstanceVO restart(String executionId) {
    WorkflowExecutionMetadata source = requireMetadata(executionId);
    WorkflowExecution execution = engine.restart(executionId);
    registerExecution(execution, source);
    return toView(execution, source);
  }

  public WorkflowInstanceVO rerunFromNode(String executionId, String nodeId) {
    WorkflowExecutionMetadata source = requireMetadata(executionId);
    WorkflowExecution execution = engine.rerunFromNode(executionId, nodeId);
    registerExecution(execution, source);
    return toView(execution, source);
  }

  public List<WorkflowInstanceVO> listInstances() {
    requireCurrentProject();
    List<WorkflowInstanceVO> result = new ArrayList<>();
    for (String id : runtimePersistence.listExecutionIds()) {
      WorkflowExecutionMetadata runMetadata = findMetadata(id);
      if (runMetadata != null) {
        engine.findExecution(id)
            .map(execution -> toView(execution, runMetadata))
            .ifPresent(result::add);
      }
    }
    return result;
  }

  public WorkflowInstanceVO getInstance(String executionId) {
    WorkflowExecutionMetadata runMetadata = requireMetadata(executionId);
    return toView(requireExecutionAfterAuthorization(executionId), runMetadata);
  }

  public SseEmitter subscribe(String executionId) {
    WorkflowInstanceVO snapshot = getInstance(executionId);
    SseEmitter emitter = eventStreamService.subscribe(executionId, snapshot);
    activateExecution(executionId);
    publishCurrent(executionId);
    return emitter;
  }

  /** Called once after application startup to rebuild only non-terminal runtime state. */
  public int recoverPersistedExecutions() {
    requireCurrentProject();
    int recovered = 0;
    for (String executionId : runtimePersistence.findRecoverableExecutionIds()) {
      try {
        WorkflowExecutionMetadata runMetadata = requireMetadata(executionId);
        WorkflowExecution before = requireExecutionAfterAuthorization(executionId);
        if (before.status().isTerminal()) continue;

        metadata.put(executionId, runMetadata);
        WorkflowExecution execution = recoveryCoordinator.recover(executionId);
        if (!execution.status().isTerminal()) {
          activeExecutions.add(executionId);
        }
        publishCurrent(executionId);
        recovered++;
        log.info(
            "[workflow] recovered execution={}, status={}, definition={}",
            executionId,
            execution.status(),
            execution.definitionId());
      } catch (RuntimeException exception) {
        log.error(
            "[workflow] recovery failed execution={}, message={}",
            executionId,
            exception.getMessage(),
            exception);
      }
    }
    return recovered;
  }

  void activateExecution(String executionId) {
    getInstance(executionId);
    if (activeExecutions.add(executionId)) {
      log.info("[workflow] activated execution={}", executionId);
    }
    drainDispatches(executionId);
  }

  private void reactivateExecution(String executionId) {
    if (activeExecutions.add(executionId)) {
      log.info("[workflow] reactivated execution={}", executionId);
    }
    drainDispatches(executionId);
  }

  private void registerExecution(WorkflowExecution execution, WorkflowExecutionMetadata runMetadata) {
    ProjectContext project = requireCurrentProject();
    runtimePersistence.saveMetadata(execution.id(), toPersistence(runMetadata));
    bindExecutionProject(execution.id(), project);
    metadata.put(execution.id(), runMetadata);
  }

  private ProjectContext requireCurrentProject() {
    if (!projectRequired) return null;
    if (currentProject == null) {
      throw new ProjectContextException(ProjectContextError.PROJECT_REQUIRED);
    }
    return currentProject.require();
  }

  private void bindExecutionProject(String executionId, ProjectContext project) {
    if (!projectRequired || project == null) return;
    ProjectContext existing = executionProjects.putIfAbsent(executionId, project);
    if (existing != null && !existing.projectId().equals(project.projectId())) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
  }

  private ProjectContext requireBoundExecutionProject(String executionId) {
    if (!projectRequired) return null;
    ProjectContext project = executionProjects.get(executionId);
    if (project == null) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    return project;
  }

  private void verifyBoundExecutionProject(String executionId, ProjectContext caller) {
    if (!projectRequired) return;
    ProjectContext bound = executionProjects.get(executionId);
    if (bound != null && !bound.projectId().equals(caller.projectId())) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
  }

  private Runnable scopeExecutionWork(String executionId, Runnable action) {
    if (!projectRequired) return action;
    ProjectContext project = requireBoundExecutionProject(executionId);
    return () -> projectContextScope.run(project, action);
  }

  private void executeExecutionWork(String executionId, Runnable action) {
    Runnable scoped = scopeExecutionWork(executionId, action);
    ioExecutor.execute(scoped);
  }

  private WorkflowExecutionMetadata requireMetadata(String id) {
    WorkflowExecutionMetadata runMetadata = findMetadata(id);
    if (runMetadata == null) {
      if (projectRequired) {
        throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
      }
      throw new IllegalArgumentException("Workflow execution metadata not found: " + id);
    }
    return runMetadata;
  }

  private WorkflowExecutionMetadata findMetadata(String id) {
    ProjectContext caller = requireCurrentProject();
    verifyBoundExecutionProject(id, caller);
    WorkflowExecutionMetadata cached = metadata.get(id);
    if (cached != null) return cached;
    return runtimePersistence.findMetadata(id)
        .map(this::fromPersistence)
        .map(value -> {
          bindExecutionProject(id, caller);
          metadata.put(id, value);
          return value;
        })
        .orElse(null);
  }

  private WorkflowExecution requireExecution(String id) {
    requireMetadata(id);
    return requireExecutionAfterAuthorization(id);
  }

  private WorkflowExecution requireExecutionAfterAuthorization(String id) {
    return engine.findExecution(id)
        .orElseThrow(() -> new IllegalArgumentException("Workflow execution not found: " + id));
  }

  private WorkflowRunSpec toRunSpec(WorkflowRunDTO request) {
    List<WorkflowNodeSpec> nodes = request.nodes().stream()
        .map(node -> new WorkflowNodeSpec(
            node.id(),
            node.taskId(),
            0D,
            0D,
            node.maxAttempts(),
            node.retryDelaySeconds(),
            node.dispatchTimeoutSeconds(),
            node.executionTimeoutSeconds(),
            node.inputMapping(),
            node.triggerRule(),
            node.failurePolicy()))
        .toList();
    List<WorkflowEdgeSpec> edges = request.edges().stream()
        .map(edge -> new WorkflowEdgeSpec(edge.source(), edge.target()))
        .toList();
    return new WorkflowRunSpec(
        request.name(),
        nodes,
        edges,
        request.input(),
        request.workflowTimeoutSeconds(),
        request.failureStrategy());
  }

  private Map<String, TaskVersionSnapshot> validateTaskSnapshots(
      List<WorkflowNodeSpec> nodes,
      Map<String, TaskVersionSnapshot> supplied) {
    Map<String, TaskVersionSnapshot> result = new LinkedHashMap<>();
    for (WorkflowNodeSpec node : nodes) {
      TaskVersionSnapshot task = supplied == null ? null : supplied.get(node.id());
      if (task == null) {
        throw new IllegalArgumentException("工作流节点缺少任务版本快照：" + node.id());
      }
      if (!node.taskId().equals(task.taskId())) {
        throw new IllegalArgumentException("工作流节点任务快照不匹配：" + node.id());
      }
      if (!taskExecutionGateway.supports(task.type())) {
        throw new IllegalArgumentException(
            "工作流没有可用的任务执行器：type=" + task.type() + "，task=" + task.taskId());
      }
      result.put(node.id(), task);
    }
    return Map.copyOf(result);
  }

  private NodeDefinition toNodeDefinition(WorkflowNodeSpec node, TaskVersionSnapshot task) {
    RetryPolicy retry = node.maxAttempts() > 1
        ? RetryPolicy.fixed(node.maxAttempts(), Duration.ofSeconds(node.retryDelaySeconds()))
        : RetryPolicy.none();
    NodeTimeoutPolicy timeout = NodeTimeoutPolicy.of(
        Duration.ofSeconds(node.dispatchTimeoutSeconds()),
        Duration.ofSeconds(node.executionTimeoutSeconds()));
    return new NodeDefinition(
        node.id(),
        task.name(),
        TriggerRule.valueOf(node.triggerRule()),
        retry,
        NodeFailurePolicy.valueOf(node.failurePolicy()),
        timeout,
        NodeInputMapping.of(node.inputMapping()),
        Map.of("taskId", task.taskId(), "taskVersion", task.version(), "taskType", task.type()));
  }

  private EdgeDefinition toEdgeDefinition(WorkflowEdgeSpec edge) {
    return new EdgeDefinition(edge.source(), edge.target());
  }

  private Map<String, NodeMetadata> nodeMetadata(
      List<WorkflowNodeSpec> nodes,
      Map<String, TaskVersionSnapshot> tasks) {
    Map<String, NodeMetadata> result = new LinkedHashMap<>();
    for (WorkflowNodeSpec node : nodes) {
      TaskVersionSnapshot task = tasks.get(node.id());
      result.put(
          node.id(),
          new NodeMetadata(
              task,
              node.triggerRule(),
              node.failurePolicy(),
              node.maxAttempts(),
              node.retryDelaySeconds(),
              node.dispatchTimeoutSeconds(),
              node.executionTimeoutSeconds(),
              node.inputMapping()));
    }
    return Map.copyOf(result);
  }

  private RuntimeMetadataRecord toPersistence(WorkflowExecutionMetadata value) {
    Map<String, NodeMetadataRecord> nodes = new LinkedHashMap<>();
    value.nodes().forEach((nodeId, node) -> nodes.put(
        nodeId,
        new NodeMetadataRecord(
            node.task(),
            node.triggerRule(),
            node.failurePolicy(),
            node.maxAttempts(),
            node.retryDelaySeconds(),
            node.dispatchTimeoutSeconds(),
            node.executionTimeoutSeconds(),
            node.inputMapping())));
    return new RuntimeMetadataRecord(
        value.name(),
        value.edgeCount(),
        value.workflowTimeoutSeconds(),
        value.failureStrategy(),
        value.workflowVersionId(),
        value.workflowVersionNo(),
        value.testRun(),
        nodes);
  }

  private WorkflowExecutionMetadata fromPersistence(RuntimeMetadataRecord value) {
    Map<String, NodeMetadata> nodes = new LinkedHashMap<>();
    value.nodes().forEach((nodeId, node) -> nodes.put(
        nodeId,
        new NodeMetadata(
            node.task(),
            node.triggerRule(),
            node.failurePolicy(),
            node.maxAttempts(),
            node.retryDelaySeconds(),
            node.dispatchTimeoutSeconds(),
            node.executionTimeoutSeconds(),
            node.inputMapping())));
    return new WorkflowExecutionMetadata(
        value.name(),
        value.edgeCount(),
        value.workflowTimeoutSeconds(),
        value.failureStrategy(),
        value.workflowVersionId(),
        value.workflowVersionNo(),
        value.testRun(),
        Map.copyOf(nodes));
  }

  private void enqueueDispatch(NodeDispatch dispatch) {
    latestDispatches
        .computeIfAbsent(dispatch.workflowExecutionId(), ignored -> new ConcurrentHashMap<>())
        .put(dispatch.nodeId(), dispatch);
    pendingDispatches
        .computeIfAbsent(dispatch.workflowExecutionId(), ignored -> new ConcurrentLinkedQueue<>())
        .offer(dispatch);
    if (activeExecutions.contains(dispatch.workflowExecutionId())) {
      drainDispatches(dispatch.workflowExecutionId());
    }
  }

  private void drainDispatches(String executionId) {
    ConcurrentLinkedQueue<NodeDispatch> queue = pendingDispatches.get(executionId);
    if (queue == null) return;
    NodeDispatch dispatch;
    while ((dispatch = queue.poll()) != null) {
      scheduleDispatch(dispatch);
    }
  }

  private void scheduleDispatch(NodeDispatch dispatch) {
    Instant availableAt = dispatch.availableAt();
    long delayMillis = availableAt == null
        ? 0L
        : Math.max(0L, Duration.between(Instant.now(), availableAt).toMillis());
    Runnable scopedStart = scopeExecutionWork(
        dispatch.workflowExecutionId(), () -> startNode(dispatch));
    Runnable start = () -> ioExecutor.execute(scopedStart);
    if (delayMillis <= 0L) {
      start.run();
      return;
    }
    runtimeScheduler.schedule(start, delayMillis, TimeUnit.MILLISECONDS);
    log.debug(
        "[workflow] delayed dispatch scheduled execution={}, node={}, attempt={}, delayMillis={}",
        dispatch.workflowExecutionId(),
        dispatch.nodeId(),
        dispatch.attemptId(),
        delayMillis);
  }

  private void startNode(NodeDispatch dispatch) {
    NodeTaskControl control = taskControls.get(dispatch.attemptId());
    if (control == null) {
      log.debug(
          "[workflow] stale queued dispatch skipped execution={}, node={}, attempt={}",
          dispatch.workflowExecutionId(),
          dispatch.nodeId(),
          dispatch.attemptId());
      return;
    }
    if (control.canceled()) {
      cleanupAttempt(dispatch, control);
      return;
    }
    try {
      NodeMetadata node = requireMetadata(dispatch.workflowExecutionId()).nodes().get(dispatch.nodeId());
      if (node == null) {
        throw new IllegalStateException("工作流节点运行元数据不存在：" + dispatch.nodeId());
      }
      TaskExecution taskExecution = taskExecutionGateway.start(
          node.task(), dispatch.attemptId(), dispatch.nodeInput());
      runtimePersistence.bindExternalExecution(dispatch.attemptId(), taskExecution.executionId());
      control.bindTaskExecution(node.task().type(), taskExecution.executionId());
      if (control.canceled()) {
        cancelRemote(node.task().type(), taskExecution.executionId());
        cleanupAttempt(dispatch, control);
        return;
      }
      engine.acknowledgeNodeStarted(
          dispatch.workflowExecutionId(), dispatch.nodeId(), dispatch.attemptId());
      publishCurrent(dispatch.workflowExecutionId());
      log.info(
          "[workflow] task started workflowExecution={}, node={}, attempt={}, task={}, type={}, taskVersion={}, taskExecution={}",
          dispatch.workflowExecutionId(),
          dispatch.nodeId(),
          dispatch.attemptId(),
          node.task().taskId(),
          node.task().type(),
          node.task().version(),
          taskExecution.executionId());
      handleTaskSnapshot(dispatch, control, node, taskExecution);
    } catch (RuntimeException exception) {
      failNode(dispatch, control, exception);
    }
  }

  private void reconcileRecoveredAttempt(NodeRecovery recovery, NodeTaskControl control) {
    NodeDispatch dispatch = recovery.dispatch();
    if (control.canceled() || taskControls.get(dispatch.attemptId()) != control) return;
    try {
      NodeMetadata node = requireMetadata(dispatch.workflowExecutionId()).nodes().get(dispatch.nodeId());
      if (node == null) {
        throw new IllegalStateException("工作流节点恢复元数据不存在：" + dispatch.nodeId());
      }
      String taskExecutionId = control.taskExecutionId();
      if (taskExecutionId == null) {
        enqueueDispatch(dispatch);
        return;
      }
      TaskExecution taskExecution =
          taskExecutionGateway.status(node.task().type(), taskExecutionId);
      if (recovery.attemptStatus() == NodeAttemptStatus.SUBMITTED && !taskExecution.terminal()) {
        engine.acknowledgeNodeStarted(
            dispatch.workflowExecutionId(), dispatch.nodeId(), dispatch.attemptId());
        publishCurrent(dispatch.workflowExecutionId());
      }
      handleTaskSnapshot(dispatch, control, node, taskExecution);
    } catch (RuntimeException exception) {
      failNode(dispatch, control, exception);
    }
  }

  private void pollNode(NodeDispatch dispatch, NodeTaskControl control, NodeMetadata node) {
    if (control.canceled() || taskControls.get(dispatch.attemptId()) != control) return;
    try {
      String taskExecutionId = control.taskExecutionId();
      if (taskExecutionId == null) {
        throw new IllegalStateException("任务执行 ID 不存在");
      }
      handleTaskSnapshot(
          dispatch,
          control,
          node,
          taskExecutionGateway.status(node.task().type(), taskExecutionId));
    } catch (RuntimeException exception) {
      failNode(dispatch, control, exception);
    }
  }

  private void handleTaskSnapshot(
      NodeDispatch dispatch,
      NodeTaskControl control,
      NodeMetadata node,
      TaskExecution taskExecution) {
    if (control.canceled()) return;
    if (!taskExecution.terminal()) {
      Runnable scopedPoll = scopeExecutionWork(
          dispatch.workflowExecutionId(), () -> pollNode(dispatch, control, node));
      runtimeScheduler.schedule(
          () -> ioExecutor.execute(scopedPoll),
          taskPollIntervalMillis,
          TimeUnit.MILLISECONDS);
      return;
    }
    try {
      if (taskExecution.successful()) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("taskId", node.task().taskId());
        output.put("taskName", node.task().name());
        output.put("taskType", node.task().type());
        output.put("taskVersion", node.task().version());
        output.put("taskExecutionId", taskExecution.executionId());
        output.put("taskExecutionStatus", taskExecution.status());
        if ("SYNC".equalsIgnoreCase(node.task().type())) {
          output.put("syncExecutionId", taskExecution.executionId());
          output.put("syncStatus", taskExecution.status());
        }
        output.put("receivedInput", dispatch.nodeInput());
        output.put("taskOutput", taskExecution.output());
        engine.completeNode(
            dispatch.workflowExecutionId(), dispatch.nodeId(), dispatch.attemptId(), output);
      } else {
        String message = taskExecution.errorMessage();
        if (message == null || message.isBlank()) {
          message = "任务执行失败，类型=" + node.task().type() + "，状态=" + taskExecution.status();
        }
        engine.failNode(
            dispatch.workflowExecutionId(), dispatch.nodeId(), dispatch.attemptId(), message);
      }
      publishCurrent(dispatch.workflowExecutionId());
    } finally {
      cleanupAttempt(dispatch, control);
    }
  }

  private void failNode(NodeDispatch dispatch, NodeTaskControl control, RuntimeException exception) {
    if (control.canceled()) {
      cleanupAttempt(dispatch, control);
      return;
    }
    String message = exception.getMessage() == null
        ? exception.getClass().getSimpleName()
        : exception.getMessage();
    log.error(
        "[workflow] node failed execution={}, node={}, attempt={}, message={}",
        dispatch.workflowExecutionId(),
        dispatch.nodeId(),
        dispatch.attemptId(),
        message,
        exception);
    try {
      engine.failNode(
          dispatch.workflowExecutionId(), dispatch.nodeId(), dispatch.attemptId(), message);
      publishCurrent(dispatch.workflowExecutionId());
    } catch (RuntimeException callback) {
      log.warn(
          "[workflow] failure callback skipped execution={}, node={}, attempt={}, message={}",
          dispatch.workflowExecutionId(),
          dispatch.nodeId(),
          dispatch.attemptId(),
          callback.getMessage());
    } finally {
      cleanupAttempt(dispatch, control);
    }
  }

  private void cleanupAttempt(NodeDispatch dispatch, NodeTaskControl control) {
    taskControls.remove(dispatch.attemptId(), control);
    drainDispatches(dispatch.workflowExecutionId());
  }

  private void cancelRemote(String taskType, String taskExecutionId) {
    try {
      taskExecutionGateway.cancel(taskType, taskExecutionId);
    } catch (RuntimeException exception) {
      log.warn(
          "[workflow] task cancel ignored type={}, taskExecution={}, message={}",
          taskType,
          taskExecutionId,
          exception.getMessage());
    }
  }

  private void scanTimeouts() {
    for (String executionId : List.copyOf(activeExecutions)) {
      try {
        scopeExecutionWork(executionId, () -> scanExecutionTimeout(executionId)).run();
      } catch (RuntimeException exception) {
        log.debug(
            "[workflow] timeout scan skipped execution={}, message={}",
            executionId,
            exception.getMessage());
      }
    }
  }

  private void scanExecutionTimeout(String executionId) {
    WorkflowExecution before = requireExecution(executionId);
    String signature = runtimeSignature(before);
    WorkflowExecution after = engine.checkTimeouts(executionId);
    if (!signature.equals(runtimeSignature(after))) {
      publishCurrent(executionId);
    }
  }

  private String runtimeSignature(WorkflowExecution execution) {
    StringBuilder signature = new StringBuilder(execution.status().name());
    execution.nodes().values().forEach(node -> {
      signature.append('|').append(node.nodeId()).append(':').append(node.status().name());
      if (!node.attempts().isEmpty()) {
        NodeAttempt attempt = node.attempts().get(node.attempts().size() - 1);
        signature.append(':').append(attempt.id()).append(':').append(attempt.status().name());
      }
    });
    return signature.toString();
  }

  private WorkflowInstanceVO publishAndView(WorkflowExecution execution) {
    WorkflowExecutionMetadata runMetadata = requireMetadata(execution.id());
    WorkflowInstanceVO snapshot = toView(execution, runMetadata);
    eventStreamService.publish(snapshot);
    if (isTerminal(snapshot.status())) {
      cleanupTerminalRuntime(execution.id());
    }
    return snapshot;
  }

  private void publishCurrent(String executionId) {
    Object lock = publishLocks.computeIfAbsent(executionId, ignored -> new Object());
    synchronized (lock) {
      WorkflowExecutionMetadata runMetadata = findMetadata(executionId);
      if (runMetadata == null) return;
      engine.findExecution(executionId)
          .map(execution -> toView(execution, runMetadata))
          .ifPresent(snapshot -> {
            eventStreamService.publish(snapshot);
            if (isTerminal(snapshot.status())) {
              cleanupTerminalRuntime(executionId);
              publishLocks.remove(executionId, lock);
            }
          });
    }
  }

  private void cleanupTerminalRuntime(String executionId) {
    activeExecutions.remove(executionId);
    pendingDispatches.remove(executionId);
    latestDispatches.remove(executionId);
    metadata.remove(executionId);
    executionProjects.remove(executionId);
    taskControls.entrySet()
        .removeIf(entry -> executionId.equals(entry.getValue().workflowExecutionId()));
  }

  private boolean isTerminal(String status) {
    return "SUCCESS".equals(status)
        || "SUCCESS_WITH_WARNINGS".equals(status)
        || "FAILED".equals(status)
        || "WARNING".equals(status)
        || "CANCELED".equals(status)
        || "TIMED_OUT".equals(status);
  }

  private WorkflowInstanceVO toView(WorkflowExecution execution, WorkflowExecutionMetadata runMetadata) {
    List<NodeInstanceVO> nodes = execution.nodes().values().stream()
        .map(node -> toNodeView(execution.id(), node, runMetadata.nodes().get(node.nodeId())))
        .toList();
    return new WorkflowInstanceVO(
        execution.id(),
        execution.definitionId(),
        execution.sourceExecutionId(),
        runMetadata.name(),
        execution.status().name(),
        runMetadata.failureStrategy(),
        execution.createdAt(),
        execution.runStartedAt(),
        execution.endedAt(),
        runMetadata.workflowTimeoutSeconds(),
        execution.input(),
        nodes.size(),
        runMetadata.edgeCount(),
        nodes,
        runMetadata.workflowVersionId(),
        runMetadata.workflowVersionNo(),
        runMetadata.testRun());
  }

  private NodeInstanceVO toNodeView(
      String executionId,
      NodeExecution node,
      NodeMetadata nodeMetadata) {
    TaskVersionSnapshot task = nodeMetadata == null ? null : nodeMetadata.task();
    List<AttemptVO> attempts = node.attempts().stream().map(this::toAttemptView).toList();
    NodeAttempt attempt = node.attempts().isEmpty()
        ? null
        : node.attempts().get(node.attempts().size() - 1);
    ConcurrentMap<String, NodeDispatch> dispatches = latestDispatches.get(executionId);
    NodeDispatch dispatch = dispatches == null ? null : dispatches.get(node.nodeId());
    return new NodeInstanceVO(
        node.nodeId(),
        task == null ? null : task.taskId(),
        task == null ? node.nodeId() : task.name(),
        task == null ? "UNKNOWN" : task.type(),
        node.status().name(),
        nodeMetadata == null ? TriggerRule.ALL_SUCCESS.name() : nodeMetadata.triggerRule(),
        nodeMetadata == null ? NodeFailurePolicy.FAIL_WORKFLOW.name() : nodeMetadata.failurePolicy(),
        node.errorMessage(),
        attempt == null || attempt.failureReason() == null
            ? null
            : attempt.failureReason().name(),
        node.downstreamContinuationAllowed(),
        attempts.size(),
        attempt == null ? null : attempt.id(),
        attempt == null ? null : attempt.attemptNumber(),
        nodeMetadata == null ? 1 : nodeMetadata.maxAttempts(),
        nodeMetadata == null ? 0L : nodeMetadata.retryDelaySeconds(),
        nodeMetadata == null ? 0L : nodeMetadata.dispatchTimeoutSeconds(),
        nodeMetadata == null ? 0L : nodeMetadata.executionTimeoutSeconds(),
        nodeMetadata == null ? Map.of() : nodeMetadata.inputMapping(),
        dispatch == null ? Map.of() : dispatch.nodeInput(),
        dispatch == null ? Map.of() : dispatch.predecessorOutputs(),
        node.output(),
        attempts);
  }

  private AttemptVO toAttemptView(NodeAttempt attempt) {
    return new AttemptVO(
        attempt.id(),
        attempt.attemptNumber(),
        attempt.status().name(),
        attempt.failureReason() == null ? null : attempt.failureReason().name(),
        attempt.errorMessage(),
        attempt.availableAt(),
        attempt.startedAt(),
        attempt.pausedAt(),
        attempt.pausedDuration().toMillis(),
        attempt.endedAt());
  }

  @PreDestroy
  void shutdown() {
    runtimeScheduler.shutdownNow();
    ioExecutor.shutdownNow();
  }

  private record WorkflowExecutionMetadata(
      String name,
      int edgeCount,
      long workflowTimeoutSeconds,
      String failureStrategy,
      String workflowVersionId,
      Integer workflowVersionNo,
      boolean testRun,
      Map<String, NodeMetadata> nodes) {
  }

  private record NodeMetadata(
      TaskVersionSnapshot task,
      String triggerRule,
      String failurePolicy,
      int maxAttempts,
      long retryDelaySeconds,
      long dispatchTimeoutSeconds,
      long executionTimeoutSeconds,
      Map<String, String> inputMapping) {
  }

  private final class RuntimeNodeExecutor implements NodeExecutor {
    @Override
    public void submit(NodeDispatch dispatch) {
      bindExecutionProject(dispatch.workflowExecutionId(), requireCurrentProject());
      taskControls.computeIfAbsent(
          dispatch.attemptId(),
          ignored -> new NodeTaskControl(dispatch.workflowExecutionId()));
      enqueueDispatch(dispatch);
    }

    @Override
    public void recover(NodeRecovery recovery) {
      NodeDispatch dispatch = recovery.dispatch();
      bindExecutionProject(dispatch.workflowExecutionId(), requireCurrentProject());
      latestDispatches
          .computeIfAbsent(dispatch.workflowExecutionId(), ignored -> new ConcurrentHashMap<>())
          .put(dispatch.nodeId(), dispatch);
      NodeTaskControl control = taskControls.computeIfAbsent(
          dispatch.attemptId(),
          ignored -> new NodeTaskControl(dispatch.workflowExecutionId()));
      NodeMetadata node = requireMetadata(dispatch.workflowExecutionId()).nodes().get(dispatch.nodeId());
      if (node == null) {
        throw new IllegalStateException("工作流节点恢复元数据不存在：" + dispatch.nodeId());
      }
      runtimePersistence.findExternalExecution(dispatch.attemptId())
          .ifPresent(id -> control.bindTaskExecution(node.task().type(), id));

      if (recovery.attemptStatus() == NodeAttemptStatus.PAUSED) {
        return;
      }
      if (control.taskExecutionId() == null) {
        enqueueDispatch(dispatch);
        return;
      }
      executeExecutionWork(
          dispatch.workflowExecutionId(), () -> reconcileRecoveredAttempt(recovery, control));
    }

    @Override
    public void cancel(NodeCancellation cancellation) {
      NodeTaskControl control = taskControls.get(cancellation.attemptId());
      if (control == null) return;
      control.cancel();
      String taskExecutionId = control.taskExecutionId();
      String taskType = control.taskType();
      if (taskExecutionId != null && taskType != null) {
        executeExecutionWork(
            control.workflowExecutionId(), () -> cancelRemote(taskType, taskExecutionId));
      }
    }

    @Override
    public NodeControlResult pause(NodePauseRequest request) {
      log.debug(
          "[workflow] task pause unsupported by runtime adapter; pausing scheduling only execution={}, node={}, attempt={}",
          request.workflowExecutionId(),
          request.nodeId(),
          request.attemptId());
      return NodeControlResult.UNSUPPORTED;
    }

    @Override
    public void resume(NodeResumeRequest request) {
      // UNSUPPORTED attempts are never physically resumed; engine resume only releases deferred scheduling.
    }
  }

  private static final class NodeTaskControl {
    private final String workflowExecutionId;
    private volatile String taskType;
    private volatile String taskExecutionId;
    private volatile boolean canceled;

    NodeTaskControl(String workflowExecutionId) {
      this.workflowExecutionId = workflowExecutionId;
    }

    String workflowExecutionId() {
      return workflowExecutionId;
    }

    String taskType() {
      return taskType;
    }

    String taskExecutionId() {
      return taskExecutionId;
    }

    void bindTaskExecution(String type, String id) {
      this.taskType = type;
      this.taskExecutionId = id;
    }

    void cancel() {
      this.canceled = true;
    }

    boolean canceled() {
      return canceled;
    }
  }
}
