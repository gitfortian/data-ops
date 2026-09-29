package io.yak.ops.business.workflow.definition;

import io.yak.ops.common.enums.PublishState;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.definition.WorkflowTaskBindingResolver.BindingView;
import io.yak.ops.business.workflow.domain.WorkflowEdgeSpec;
import io.yak.ops.business.workflow.domain.WorkflowNodeSpec;
import io.yak.ops.business.workflow.domain.WorkflowRunSpec;
import io.yak.ops.business.workflow.execution.WorkflowLaunchDefinitionGateway;
import io.yak.ops.business.workflow.domain.WorkflowVersion;
import io.yak.ops.business.workflow.repository.NoopWorkflowDefinitionRepository;
import io.yak.ops.business.workflow.repository.WorkflowDefinitionRepository;
import io.yak.ops.business.workflow.repository.WorkflowDefinitionRepository.DefinitionRecord;
import io.yak.ops.business.workflow.repository.WorkflowDefinitionRepository.VersionRecord;
import io.yak.ops.business.workflow.runtime.WorkflowRuntime;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionCreateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO.EdgeDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO.NodeDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO.EdgeVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO.NodeVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowVersionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowVersionVO.TaskBindingVO;
import io.yak.ops.core.project.CurrentProject;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 工作流草稿、不可变发布版本和运行入口。数据库是生产环境事实来源。 */
@Service
public class WorkflowDefinitionManager implements WorkflowLaunchDefinitionGateway {
  private static final Set<String> ACTIVE = Set.of(
      "CREATED", "WAITING", "READY", "SUBMITTED", "RUNNING", "PAUSING", "PAUSED", "RESUMING");

  private final WorkflowRuntime runtimeService;
  private final WorkflowTaskBindingResolver taskBindingResolver;
  private final WorkflowDefinitionRepository persistence;
  private final WorkflowExecutionDao executionDao;

  /**
   * Process-local aggregate cache only. It is never an authorization source: durable deployments
   * prove current-Project visibility through WorkflowDefinitionRepository before reading this map.
   */
  private final ConcurrentMap<String, WorkflowDefinitionAggregate> definitions = new ConcurrentHashMap<>();

  @Autowired
  public WorkflowDefinitionManager(
      WorkflowRuntime runtimeService,
      TaskRegistry taskRegistry,
      ObjectProvider<TaskCatalogService> taskCatalogService,
      ObjectMapper objectMapper,
      ObjectProvider<WorkflowDefinitionRepository> persistence,
      ObjectProvider<WorkflowExecutionDao> executionDao,
      CurrentProject currentProject,
      @Value("${yak.database.enabled:true}") boolean databaseEnabled) {
    this(
        runtimeService,
        new WorkflowTaskBindingResolver(
            taskRegistry,
            taskCatalogService.getIfAvailable(),
            objectMapper,
            currentProject),
        resolvePersistence(persistence, databaseEnabled),
        resolveExecutionDao(executionDao, databaseEnabled));
  }

  /** Focused tests and explicit database-disabled development retain the lightweight catalog. */
  public WorkflowDefinitionManager(
      WorkflowRuntime runtimeService,
      TaskRegistry taskRegistry) {
    this(
        runtimeService,
        new WorkflowTaskBindingResolver(taskRegistry, null, new ObjectMapper()),
        NoopWorkflowDefinitionRepository.INSTANCE,
        null);
  }

  public WorkflowDefinitionManager(
      WorkflowRuntime runtimeService,
      TaskRegistry taskRegistry,
      WorkflowDefinitionRepository persistence) {
    this(
        runtimeService,
        new WorkflowTaskBindingResolver(taskRegistry, null, new ObjectMapper()),
        persistence,
        null);
  }

  public WorkflowDefinitionManager(
      WorkflowRuntime runtimeService,
      TaskRegistry taskRegistry,
      WorkflowDefinitionRepository persistence,
      WorkflowExecutionDao executionDao) {
    this(
        runtimeService,
        new WorkflowTaskBindingResolver(taskRegistry, null, new ObjectMapper()),
        persistence,
        executionDao);
  }

  public WorkflowDefinitionManager(
      WorkflowRuntime runtimeService,
      TaskRegistry taskRegistry,
      TaskCatalogService taskCatalogService,
      WorkflowDefinitionRepository persistence) {
    this(
        runtimeService,
        new WorkflowTaskBindingResolver(taskRegistry, taskCatalogService, new ObjectMapper()),
        persistence,
        null);
  }

  /** Project-aware focused-test constructor for Task Catalog binding tests. */
  public WorkflowDefinitionManager(
      WorkflowRuntime runtimeService,
      TaskRegistry taskRegistry,
      TaskCatalogService taskCatalogService,
      WorkflowDefinitionRepository persistence,
      CurrentProject currentProject) {
    this(
        runtimeService,
        new WorkflowTaskBindingResolver(
            taskRegistry, taskCatalogService, new ObjectMapper(), currentProject),
        persistence,
        null);
  }

  private WorkflowDefinitionManager(
      WorkflowRuntime runtimeService,
      WorkflowTaskBindingResolver taskBindingResolver,
      WorkflowDefinitionRepository persistence,
      WorkflowExecutionDao executionDao) {
    this.runtimeService = runtimeService;
    this.taskBindingResolver = taskBindingResolver;
    this.persistence = persistence;
    this.executionDao = executionDao;
    // Do not restore a global catalog during bean construction. Durable catalog reads require the
    // trusted CurrentProject and are loaded lazily by list()/require() after ProjectScope binds it.
  }

  private static WorkflowDefinitionRepository resolvePersistence(
      ObjectProvider<WorkflowDefinitionRepository> provider,
      boolean databaseEnabled) {
    WorkflowDefinitionRepository resolved = provider.getIfAvailable();
    if (resolved != null) return resolved;
    if (!databaseEnabled) return NoopWorkflowDefinitionRepository.INSTANCE;
    throw new IllegalStateException(
        "Workflow durable persistence bean is missing while yak.database.enabled=true: "
            + "WorkflowDefinitionRepository");
  }

  private static WorkflowExecutionDao resolveExecutionDao(
      ObjectProvider<WorkflowExecutionDao> provider,
      boolean databaseEnabled) {
    WorkflowExecutionDao resolved = provider.getIfAvailable();
    if (resolved != null) return resolved;
    if (!databaseEnabled) return null;
    throw new IllegalStateException(
        "Workflow durable execution DAO is missing while yak.database.enabled=true: WorkflowExecutionDao");
  }

  public List<WorkflowDefinitionVO> list(String keyword, String status) {
    String k = normalize(keyword);
    String s = normalizeUpper(status);
    List<WorkflowDefinitionAggregate> visible = visibleDefinitions();
    return visible.stream()
        .filter(x -> k == null
            || x.name.toLowerCase(Locale.ROOT).contains(k)
            || text(x.description).toLowerCase(Locale.ROOT).contains(k))
        .filter(x -> s == null || PublishState.of(s) == PublishState.of(x.status))
        .sorted(Comparator.comparing((WorkflowDefinitionAggregate x) -> x.updateTime).reversed())
        .map(this::toView)
        .toList();
  }

  public WorkflowDefinitionVO create(WorkflowDefinitionCreateDTO request) {
    if (request == null) throw new IllegalArgumentException("工作流创建参数不能为空");
    WorkflowDefinitionAggregate state = new WorkflowDefinitionAggregate();
    Instant now = Instant.now();
    state.id = "workflow-" + UUID.randomUUID();
    state.name = required(request.name(), "工作流名称不能为空");
    state.description = trim(request.description());
    state.status = PublishState.DRAFT.name();
    state.nodes = List.of();
    state.edges = List.of();
    state.input = Map.of();
    state.editorMeta = Map.of();
    state.failureStrategy = "CONTINUE_INDEPENDENT_BRANCHES";
    state.draftRevision = 1L;
    state.versions = new ArrayList<>();
    state.createTime = now;
    state.updateTime = now;
    persistence.saveDefinition(toRecord(state));
    definitions.put(state.id, state);
    return toView(state);
  }

  public WorkflowDefinitionVO get(String id) {
    return toView(require(id));
  }

  public WorkflowDefinitionVO update(String id, WorkflowDefinitionUpdateDTO request) {
    if (request == null) throw new IllegalArgumentException("工作流配置不能为空");
    WorkflowDefinitionAggregate state = require(id);
    synchronized (state) {
      String nextName = required(request.name(), "工作流名称不能为空");
      String nextDescription = trim(request.description());
      List<WorkflowNodeSpec> nextNodes = request.nodes().stream().map(this::toSpec).toList();
      List<WorkflowEdgeSpec> nextEdges = request.edges().stream().map(this::toSpec).toList();
      Map<String, Object> nextInput = Map.copyOf(new LinkedHashMap<>(request.input()));
      Map<String, Object> nextEditorMeta = Map.copyOf(new LinkedHashMap<>(request.editorMeta()));
      long nextTimeout = request.workflowTimeoutSeconds();
      String nextFailureStrategy = request.failureStrategy();
      boolean changed = !Objects.equals(state.name, nextName)
          || !Objects.equals(state.description, nextDescription)
          || !Objects.equals(state.nodes, nextNodes)
          || !Objects.equals(state.edges, nextEdges)
          || !Objects.equals(state.input, nextInput)
          || !Objects.equals(state.editorMeta, nextEditorMeta)
          || state.workflowTimeoutSeconds != nextTimeout
          || !Objects.equals(state.failureStrategy, nextFailureStrategy);

      if (!changed) return toView(state);

      WorkflowDefinitionSnapshot previous = WorkflowDefinitionSnapshot.capture(state);
      state.name = nextName;
      state.description = nextDescription;
      state.nodes = nextNodes;
      state.edges = nextEdges;
      state.input = nextInput;
      state.editorMeta = nextEditorMeta;
      state.workflowTimeoutSeconds = nextTimeout;
      state.failureStrategy = nextFailureStrategy;
      state.draftRevision++;
      state.updateTime = Instant.now();
      try {
        persistence.saveDefinition(toRecord(state));
      } catch (RuntimeException exception) {
        previous.restore(state);
        throw exception;
      }
      return toView(state);
    }
  }

  /** Explicitly advances one draft node to the TaskAsset current revision. */
  public WorkflowDefinitionVO upgradeTaskRevision(String id, String nodeId) {
    WorkflowDefinitionAggregate state = require(id);
    synchronized (state) {
      int index = -1;
      WorkflowNodeSpec current = null;
      for (int i = 0; i < state.nodes.size(); i++) {
        WorkflowNodeSpec candidate = state.nodes.get(i);
        if (candidate.id().equals(nodeId)) {
          index = i;
          current = candidate;
          break;
        }
      }
      if (current == null) throw new IllegalArgumentException("工作流节点不存在：" + nodeId);

      WorkflowNodeSpec upgraded = taskBindingResolver.upgradeToLatest(current);
      if (upgraded.equals(current)) return toView(state);

      WorkflowDefinitionSnapshot previous = WorkflowDefinitionSnapshot.capture(state);
      List<WorkflowNodeSpec> nextNodes = new ArrayList<>(state.nodes);
      nextNodes.set(index, upgraded);
      state.nodes = List.copyOf(nextNodes);
      state.draftRevision++;
      state.updateTime = Instant.now();
      try {
        persistence.saveDefinition(toRecord(state));
      } catch (RuntimeException exception) {
        previous.restore(state);
        throw exception;
      }
      return toView(state);
    }
  }

  public WorkflowDefinitionVO online(String id) {
    WorkflowDefinitionAggregate state = require(id);
    synchronized (state) {
      WorkflowDefinitionSnapshot previous = WorkflowDefinitionSnapshot.capture(state);
      WorkflowVersion published = null;
      try {
        if (state.activeVersion == null || draftChanged(state)) {
          PreparedDraft draft = prepare(state);
          published = new WorkflowVersion(
              "workflow-version-" + UUID.randomUUID(),
              state.id,
              state.latestVersionNo + 1,
              state.draftRevision,
              draft.spec(),
              state.editorMeta,
              draft.tasks(),
              Instant.now());
          state.latestVersionNo = published.versionNo();
          state.versions.add(published);
          state.activeVersion = published;
        }
        state.status = PublishState.PUBLISHED.name();
        state.updateTime = Instant.now();
        if (published == null) persistence.saveDefinition(toRecord(state));
        else persistence.publish(toRecord(state), toRecord(published));
      } catch (RuntimeException exception) {
        if (published != null) state.versions.remove(published);
        previous.restore(state);
        throw exception;
      }
      return toView(state);
    }
  }

  /** 下线只阻止新的正式运行，已经启动的版本实例继续完成。 */
  public WorkflowDefinitionVO offline(String id) {
    WorkflowDefinitionAggregate state = require(id);
    synchronized (state) {
      if (state.activeVersion == null) throw new IllegalStateException("工作流还没有已发布版本");
      String previousStatus = state.status;
      Instant previousUpdateTime = state.updateTime;
      state.status = PublishState.OFFLINE.name();
      state.updateTime = Instant.now();
      try {
        persistence.saveDefinition(toRecord(state));
      } catch (RuntimeException exception) {
        state.status = previousStatus;
        state.updateTime = previousUpdateTime;
        throw exception;
      }
      return toView(state);
    }
  }

  public WorkflowDefinitionVO run(String id) {
    return runPublished(id, false);
  }

  /** 调度并行策略专用：并发准入由 Trigger Ledger 协调器负责。 */
  public WorkflowDefinitionVO runConcurrent(String id) {
    return runPublished(id, true);
  }

  @Override
  public WorkflowDefinitionVO runPublishedDefinition(String workflowId) {
    return run(workflowId);
  }

  @Override
  public WorkflowDefinitionVO runConcurrentPublishedDefinition(String workflowId) {
    return runConcurrent(workflowId);
  }

  @Override
  public WorkflowDefinitionVO currentDefinition(String workflowId) {
    return get(workflowId);
  }

  private WorkflowDefinitionVO runPublished(String id, boolean allowConcurrent) {
    WorkflowDefinitionAggregate state = require(id);
    synchronized (state) {
      if (!PublishState.PUBLISHED.matches(state.status) || state.activeVersion == null) {
        throw new IllegalStateException("工作流没有启用的发布版本，不能正式执行");
      }
      if (!allowConcurrent) ensureIdle(state);
      WorkflowVersion version = state.activeVersion;
      return activate(
          state,
          runtimeService.run(
              version.runSpec(),
              version.taskVersionsByNode(),
              version.id(),
              version.versionNo(),
              false));
    }
  }

  /** 直接执行当前草稿；不要求发布，也不改变激活版本。 */
  public WorkflowDefinitionVO testRun(String id) {
    WorkflowDefinitionAggregate state = require(id);
    synchronized (state) {
      ensureIdle(state);
      PreparedDraft draft = prepare(state);
      return activate(state, runtimeService.run(draft.spec(), draft.tasks(), null, null, true));
    }
  }

  @Override
  public WorkflowDefinitionVO testRunDraft(String workflowId) {
    return testRun(workflowId);
  }

  public List<WorkflowVersionVO> versions(String id) {
    WorkflowDefinitionAggregate state = require(id);
    synchronized (state) {
      String activeId = state.activeVersion == null ? null : state.activeVersion.id();
      return state.versions.stream()
          .sorted(Comparator.comparingInt(WorkflowVersion::versionNo).reversed())
          .map(version -> versionView(version, version.id().equals(activeId)))
          .toList();
    }
  }

  /** 单版本详情：diff/查看数据源（对齐多版本契约 C3 的 GET versions/{no}）。 */
  public WorkflowVersionDetailView versionDetail(String id, int versionNo) {
    WorkflowDefinitionAggregate state = require(id);
    synchronized (state) {
      WorkflowVersion target = findVersion(state, versionNo);
      boolean active = state.activeVersion != null && state.activeVersion.id().equals(target.id());
      return new WorkflowVersionDetailView(
          versionView(target, active), target.runSpec(), target.editorMeta());
    }
  }

  /**
   * 回滚=追加式 activate：以 v{n} 快照内容追加新版本并置为启用+已发布，不抹历史
   * （语义对齐数字大屏 DigitalScreenPublisher）。
   */
  public WorkflowDefinitionVO rollback(String id, int versionNo) {
    WorkflowDefinitionAggregate state = require(id);
    synchronized (state) {
      WorkflowVersion target = findVersion(state, versionNo);
      WorkflowDefinitionSnapshot previous = WorkflowDefinitionSnapshot.capture(state);
      WorkflowVersion appended = null;
      try {
        appended = new WorkflowVersion(
            "workflow-version-" + UUID.randomUUID(),
            state.id,
            state.latestVersionNo + 1,
            target.draftRevision(),
            target.runSpec(),
            target.editorMeta(),
            target.taskVersionsByNode(),
            Instant.now());
        state.latestVersionNo = appended.versionNo();
        state.versions.add(appended);
        state.activeVersion = appended;
        state.status = PublishState.PUBLISHED.name();
        state.updateTime = Instant.now();
        persistence.publish(toRecord(state), toRecord(appended));
      } catch (RuntimeException exception) {
        if (appended != null) state.versions.remove(appended);
        previous.restore(state);
        throw exception;
      }
      return toView(state);
    }
  }

  /** 单版本详情视图：摘要 + 运行规格 + 画布元数据（前端 diff 数据源）。 */
  public record WorkflowVersionDetailView(
      WorkflowVersionVO summary,
      io.yak.ops.business.workflow.domain.WorkflowRunSpec runSpec,
      Map<String, Object> editorMeta) {}

  private WorkflowVersion findVersion(WorkflowDefinitionAggregate state, int versionNo) {
    return state.versions.stream()
        .filter(version -> version.versionNo() == versionNo)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "工作流版本不存在：" + state.id + " v" + versionNo));
  }

  public WorkflowDefinitionVO pause(String id) {
    WorkflowDefinitionAggregate state = require(id);
    synchronized (state) {
      WorkflowInstanceVO execution = runtimeService.pause(requireLatestExecution(state));
      state.latestExecutionStatus = execution.status();
      state.updateTime = Instant.now();
      persistence.saveDefinition(toRecord(state));
      return toView(state);
    }
  }

  public WorkflowDefinitionVO resume(String id) {
    WorkflowDefinitionAggregate state = require(id);
    synchronized (state) {
      WorkflowInstanceVO execution = runtimeService.resume(requireLatestExecution(state));
      state.latestExecutionStatus = execution.status();
      state.updateTime = Instant.now();
      persistence.saveDefinition(toRecord(state));
      return toView(state);
    }
  }

  public void delete(String id) {
    WorkflowDefinitionAggregate state = require(id);
    synchronized (state) {
      if (hasActiveExecution(state)) {
        throw new IllegalStateException("工作流正在运行，不能删除");
      }
      if (PublishState.PUBLISHED.matches(state.status)) {
        throw new IllegalStateException("已启用工作流请先下线后再删除");
      }
      persistence.deleteDefinition(state.id);
      definitions.remove(state.id, state);
    }
  }

  private WorkflowDefinitionVO activate(WorkflowDefinitionAggregate state, WorkflowInstanceVO prepared) {
    String previousExecutionId = state.latestExecutionId;
    String previousExecutionStatus = state.latestExecutionStatus;
    Instant previousUpdateTime = state.updateTime;
    state.latestExecutionId = prepared.id();
    state.latestExecutionStatus = prepared.status();
    state.updateTime = Instant.now();
    try {
      persistence.saveDefinition(toRecord(state));
    } catch (RuntimeException exception) {
      state.latestExecutionId = previousExecutionId;
      state.latestExecutionStatus = previousExecutionStatus;
      state.updateTime = previousUpdateTime;
      throw exception;
    }

    WorkflowInstanceVO execution = runtimeService.activate(prepared.id());
    if (!Objects.equals(state.latestExecutionStatus, execution.status())) {
      state.latestExecutionStatus = execution.status();
      state.updateTime = Instant.now();
      persistence.saveDefinition(toRecord(state));
    }
    return toView(state);
  }

  private void ensureIdle(WorkflowDefinitionAggregate state) {
    if (hasActiveExecution(state)) {
      throw new IllegalStateException("工作流已有运行中的执行实例");
    }
  }

  /**
   * 生产环境以 durable WorkflowExecution 为并发事实源；latestExecution 只保留最近实例展示语义。
   * database-disabled 的 focused test/development 才回退到历史 latest 状态判断。
   */
  private boolean hasActiveExecution(WorkflowDefinitionAggregate state) {
    if (executionDao != null) {
      return executionDao.countActiveExecutions(state.id) > 0L;
    }
    refresh(state);
    return isActive(state.latestExecutionStatus);
  }

  private PreparedDraft prepare(WorkflowDefinitionAggregate state) {
    validateGraph(state);
    WorkflowStartGraphCompiler.RuntimeGraph graph = WorkflowStartGraphCompiler.compile(
        state.nodes,
        state.edges,
        state.editorMeta,
        state.input);
    Map<String, TaskVersionSnapshot> tasks = new LinkedHashMap<>();
    for (WorkflowNodeSpec node : graph.nodes()) {
      tasks.put(node.id(), taskBindingResolver.snapshot(node));
    }
    return new PreparedDraft(toRunSpec(state, graph), Map.copyOf(tasks));
  }

  private void validateGraph(WorkflowDefinitionAggregate state) {
    if (state.nodes.isEmpty()) throw new IllegalStateException("请先配置至少一个任务节点");
    Set<String> ids = new HashSet<>();
    for (WorkflowNodeSpec node : state.nodes) {
      if (!ids.add(node.id())) {
        throw new IllegalStateException("工作流存在重复节点 ID：" + node.id());
      }
    }
    Map<String, Integer> in = new HashMap<>();
    Map<String, List<String>> next = new HashMap<>();
    ids.forEach(nodeId -> {
      in.put(nodeId, 0);
      next.put(nodeId, new ArrayList<>());
    });
    for (WorkflowEdgeSpec edge : state.edges) {
      if (!ids.contains(edge.source()) || !ids.contains(edge.target())) {
        throw new IllegalStateException(
            "连线引用了不存在的节点：" + edge.source() + " -> " + edge.target());
      }
      if (edge.source().equals(edge.target())) {
        throw new IllegalStateException("工作流节点不能连接自身：" + edge.source());
      }
      next.get(edge.source()).add(edge.target());
      in.compute(edge.target(), (key, value) -> value == null ? 1 : value + 1);
    }
    ArrayDeque<String> queue = new ArrayDeque<>();
    in.forEach((nodeId, degree) -> {
      if (degree == 0) queue.add(nodeId);
    });
    int visited = 0;
    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      visited++;
      for (String successor : next.get(current)) {
        if (in.compute(successor, (key, value) -> value == null ? 0 : value - 1) == 0) {
          queue.addLast(successor);
        }
      }
    }
    if (visited != ids.size()) {
      throw new IllegalStateException("工作流存在循环依赖，不能发布或测试");
    }
  }

  private WorkflowRunSpec toRunSpec(
      WorkflowDefinitionAggregate state,
      WorkflowStartGraphCompiler.RuntimeGraph graph) {
    return new WorkflowRunSpec(
        state.name,
        graph.nodes(),
        graph.edges(),
        state.input,
        state.workflowTimeoutSeconds,
        state.failureStrategy);
  }

  private WorkflowVersionVO versionView(WorkflowVersion version, boolean active) {
    // 绑定列表按 nodeId 定序：Map 迭代序不稳定会让版本 diff/测试抖动。
    List<TaskBindingVO> bindings = version.taskVersionsByNode().entrySet().stream()
        .sorted(java.util.Map.Entry.comparingByKey())
        .map(entry -> new TaskBindingVO(
            entry.getKey(),
            entry.getValue().taskId(),
            entry.getValue().name(),
            entry.getValue().version()))
        .toList();
    return new WorkflowVersionVO(
        version.id(),
        version.versionNo(),
        active,
        version.runSpec().nodes().size(),
        version.runSpec().edges().size(),
        bindings,
        version.publishedAt());
  }

  private WorkflowDefinitionVO toView(WorkflowDefinitionAggregate state) {
    synchronized (state) {
      refresh(state);
      WorkflowVersion active = state.activeVersion;
      return new WorkflowDefinitionVO(
          state.id,
          state.name,
          state.description,
          state.status,
          state.nodes.size(),
          state.edges.size(),
          state.nodes.stream().map(this::toView).toList(),
          state.edges.stream().map(this::toView).toList(),
          state.input,
          state.editorMeta,
          state.workflowTimeoutSeconds,
          state.failureStrategy,
          active == null ? null : active.id(),
          active == null ? null : active.versionNo(),
          state.latestVersionNo,
          draftChanged(state),
          state.latestExecutionId,
          state.latestExecutionStatus,
          state.createTime,
          state.updateTime);
    }
  }

  private WorkflowNodeSpec toSpec(NodeDTO node) {
    return new WorkflowNodeSpec(
        node.id(),
        node.taskId(),
        node.taskAssetId(),
        node.taskRevisionId(),
        node.taskRevisionNo(),
        node.positionX(),
        node.positionY(),
        node.maxAttempts(),
        node.retryDelaySeconds(),
        node.dispatchTimeoutSeconds(),
        node.executionTimeoutSeconds(),
        node.inputMapping(),
        node.triggerRule(),
        node.failurePolicy());
  }

  private WorkflowEdgeSpec toSpec(EdgeDTO edge) {
    return new WorkflowEdgeSpec(edge.source(), edge.target());
  }

  private NodeVO toView(WorkflowNodeSpec node) {
    BindingView binding = taskBindingResolver.describe(node);
    return new NodeVO(
        node.id(),
        node.taskId(),
        binding == null ? null : binding.taskAssetId(),
        binding == null ? null : binding.taskRevisionId(),
        binding == null ? null : binding.taskRevisionNo(),
        binding == null ? null : binding.taskAssetName(),
        binding == null ? null : binding.taskType(),
        binding == null ? null : binding.taskAssetStatus(),
        binding == null ? null : binding.latestTaskRevisionId(),
        binding == null ? null : binding.latestTaskRevisionNo(),
        binding != null && binding.updateAvailable(),
        node.positionX(),
        node.positionY(),
        node.maxAttempts(),
        node.retryDelaySeconds(),
        node.dispatchTimeoutSeconds(),
        node.executionTimeoutSeconds(),
        node.inputMapping(),
        node.triggerRule(),
        node.failurePolicy());
  }

  private EdgeVO toView(WorkflowEdgeSpec edge) {
    return new EdgeVO(edge.source(), edge.target());
  }

  private boolean draftChanged(WorkflowDefinitionAggregate state) {
    return state.activeVersion == null
        || state.activeVersion.draftRevision() != state.draftRevision;
  }

  private void refresh(WorkflowDefinitionAggregate state) {
    if (!StringUtils.hasText(state.latestExecutionId)) return;
    String current = runtimeService.getInstance(state.latestExecutionId).status();
    if (!Objects.equals(state.latestExecutionStatus, current)) {
      state.latestExecutionStatus = current;
      state.updateTime = Instant.now();
      persistence.saveDefinition(toRecord(state));
    }
  }

  private WorkflowDefinitionAggregate require(String id) {
    if (!StringUtils.hasText(id)) throw new IllegalArgumentException("工作流 ID 不能为空");
    String normalizedId = id.trim();
    if (!persistence.authoritative()) {
      WorkflowDefinitionAggregate state = definitions.get(normalizedId);
      if (state == null) throw new IllegalArgumentException("工作流定义不存在：" + id);
      return state;
    }

    DefinitionRecord record = persistence.findDefinition(normalizedId)
        .orElseThrow(() -> new IllegalArgumentException("工作流定义不存在：" + id));
    return definitions.computeIfAbsent(normalizedId, ignored -> hydrate(record));
  }

  private List<WorkflowDefinitionAggregate> visibleDefinitions() {
    if (!persistence.authoritative()) return List.copyOf(definitions.values());
    return persistence.loadDefinitions().stream()
        .map(record -> definitions.computeIfAbsent(record.id(), ignored -> hydrate(record)))
        .toList();
  }

  private WorkflowDefinitionAggregate hydrate(DefinitionRecord record) {
    WorkflowDefinitionAggregate state = fromRecord(record);
    List<WorkflowVersion> versions = persistence.loadVersions(record.id()).stream()
        .map(this::fromRecord)
        .toList();
    state.versions = new ArrayList<>(versions);
    if (StringUtils.hasText(record.activeVersionId())) {
      state.activeVersion = versions.stream()
          .filter(version -> record.activeVersionId().equals(version.id()))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "工作流激活版本不存在：" + record.id() + " -> " + record.activeVersionId()));
    }
    return state;
  }

  private String requireLatestExecution(WorkflowDefinitionAggregate state) {
    refresh(state);
    if (!StringUtils.hasText(state.latestExecutionId)) {
      throw new IllegalStateException("工作流还没有可控制的执行实例");
    }
    return state.latestExecutionId;
  }

  private boolean isActive(String status) {
    return status != null && ACTIVE.contains(status.toUpperCase(Locale.ROOT));
  }

  private DefinitionRecord toRecord(WorkflowDefinitionAggregate state) {
    return new DefinitionRecord(
        state.id,
        state.name,
        state.description,
        state.status,
        state.failureStrategy,
        state.nodes,
        state.edges,
        state.input,
        state.editorMeta,
        state.workflowTimeoutSeconds,
        state.draftRevision,
        state.latestVersionNo,
        state.activeVersion == null ? null : state.activeVersion.id(),
        state.latestExecutionId,
        state.latestExecutionStatus,
        state.createTime,
        state.updateTime);
  }

  private VersionRecord toRecord(WorkflowVersion version) {
    return new VersionRecord(
        version.id(),
        version.workflowId(),
        version.versionNo(),
        version.draftRevision(),
        version.runSpec(),
        version.editorMeta(),
        version.taskVersionsByNode(),
        version.publishedAt());
  }

  private WorkflowDefinitionAggregate fromRecord(DefinitionRecord record) {
    WorkflowDefinitionAggregate state = new WorkflowDefinitionAggregate();
    state.id = record.id();
    state.name = record.name();
    state.description = record.description();
    state.status = record.status();
    state.failureStrategy = record.failureStrategy();
    state.latestExecutionId = record.latestExecutionId();
    state.latestExecutionStatus = record.latestExecutionStatus();
    state.nodes = List.copyOf(record.nodes());
    state.edges = List.copyOf(record.edges());
    state.input = Map.copyOf(record.input());
    state.editorMeta = Map.copyOf(record.editorMeta());
    state.workflowTimeoutSeconds = record.workflowTimeoutSeconds();
    state.draftRevision = record.draftRevision();
    state.latestVersionNo = record.latestVersionNo();
    state.createTime = record.createTime();
    state.updateTime = record.updateTime();
    return state;
  }

  private WorkflowVersion fromRecord(VersionRecord record) {
    return new WorkflowVersion(
        record.id(),
        record.workflowId(),
        record.versionNo(),
        record.draftRevision(),
        record.runSpec(),
        record.editorMeta(),
        record.taskVersionsByNode(),
        record.publishedAt());
  }

  private String required(String value, String message) {
    if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message);
    return value.trim();
  }

  private String trim(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private String text(String value) {
    return value == null ? "" : value;
  }

  private String normalize(String value) {
    return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
  }

  private String normalizeUpper(String value) {
    return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
  }

  private record PreparedDraft(
      WorkflowRunSpec spec,
      Map<String, TaskVersionSnapshot> tasks) {
  }

  private static final class WorkflowDefinitionAggregate {
    String id;
    String name;
    String description;
    String status;
    String failureStrategy;
    String latestExecutionId;
    String latestExecutionStatus;
    List<WorkflowNodeSpec> nodes;
    List<WorkflowEdgeSpec> edges;
    Map<String, Object> input;
    Map<String, Object> editorMeta;
    long workflowTimeoutSeconds;
    long draftRevision;
    int latestVersionNo;
    List<WorkflowVersion> versions;
    WorkflowVersion activeVersion;
    Instant createTime;
    Instant updateTime;
  }

  private record WorkflowDefinitionSnapshot(
      String name,
      String description,
      String status,
      String failureStrategy,
      String latestExecutionId,
      String latestExecutionStatus,
      List<WorkflowNodeSpec> nodes,
      List<WorkflowEdgeSpec> edges,
      Map<String, Object> input,
      Map<String, Object> editorMeta,
      long workflowTimeoutSeconds,
      long draftRevision,
      int latestVersionNo,
      WorkflowVersion activeVersion,
      Instant updateTime) {

    static WorkflowDefinitionSnapshot capture(WorkflowDefinitionAggregate state) {
      return new WorkflowDefinitionSnapshot(
          state.name,
          state.description,
          state.status,
          state.failureStrategy,
          state.latestExecutionId,
          state.latestExecutionStatus,
          state.nodes,
          state.edges,
          state.input,
          state.editorMeta,
          state.workflowTimeoutSeconds,
          state.draftRevision,
          state.latestVersionNo,
          state.activeVersion,
          state.updateTime);
    }

    void restore(WorkflowDefinitionAggregate state) {
      state.name = name;
      state.description = description;
      state.status = status;
      state.failureStrategy = failureStrategy;
      state.latestExecutionId = latestExecutionId;
      state.latestExecutionStatus = latestExecutionStatus;
      state.nodes = nodes;
      state.edges = edges;
      state.input = input;
      state.editorMeta = editorMeta;
      state.workflowTimeoutSeconds = workflowTimeoutSeconds;
      state.draftRevision = draftRevision;
      state.latestVersionNo = latestVersionNo;
      state.activeVersion = activeVersion;
      state.updateTime = updateTime;
    }
  }
}
