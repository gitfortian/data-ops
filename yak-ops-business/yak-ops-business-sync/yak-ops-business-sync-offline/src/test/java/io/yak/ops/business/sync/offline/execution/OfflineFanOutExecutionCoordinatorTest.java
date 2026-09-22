package io.yak.ops.business.sync.offline.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobResponse;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpNodeResponse;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpRequestException;
import io.yak.ops.business.sync.offline.engine.plan.OfflineExecutionPlan;
import io.yak.ops.business.sync.offline.engine.plan.OfflineExecutionPlanCodec;
import io.yak.ops.business.sync.offline.engine.plan.OfflineExecutionStrategy;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class OfflineFanOutExecutionCoordinatorTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final OfflineBatchExecutionRepository batchRepository =
      mock(OfflineBatchExecutionRepository.class);
  private final OfflineJobDefinitionService definitionService =
      mock(OfflineJobDefinitionService.class);
  private final OfflineExecutionStateManager stateManager =
      mock(OfflineExecutionStateManager.class);
  private final LinkUpClient linkUpClient = mock(LinkUpClient.class);
  private final OfflineExecutionPlanCodec codec = new OfflineExecutionPlanCodec(mapper);

  private OfflineFanOutExecutionCoordinator coordinator;

  @BeforeEach
  void setUp() {
    coordinator =
        new OfflineFanOutExecutionCoordinator(
            batchRepository,
            definitionService,
            stateManager,
            linkUpClient,
            codec,
            mapper);
    when(definitionService.resolveExecutionJobSpec(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void submitsEveryUnitAndAggregatesPartialFailureWithoutParentRetry() throws Exception {
    OfflineExecutionPlan plan = plan();
    OfflineJobExecution execution = execution();
    LinkUpNodeResponse node = new LinkUpNodeResponse();
    node.setInstanceId("worker-1");
    when(linkUpClient.node()).thenReturn(node);
    when(linkUpClient.submit(
            eq("yak-offline-test-f0001"),
            eq("idem:fanout:0001"),
            eq(1),
            any(JsonNode.class)))
        .thenReturn(response("job-1", "SUCCEEDED", 10L));
    when(linkUpClient.submit(
            eq("yak-offline-test-f0002"),
            eq("idem:fanout:0002"),
            eq(1),
            any(JsonNode.class)))
        .thenReturn(response("job-2", "FAILED", 0L));

    coordinator.submit(execution, plan);

    ArgumentCaptor<LinkUpJobResponse> snapshots =
        ArgumentCaptor.forClass(LinkUpJobResponse.class);
    verify(stateManager, org.mockito.Mockito.atLeastOnce())
        .applySnapshot(eq(execution), snapshots.capture(), anyString());
    LinkUpJobResponse aggregate =
        snapshots.getAllValues().get(snapshots.getAllValues().size() - 1);

    assertThat(aggregate.getStatus()).isEqualTo("FAILED");
    assertThat(aggregate.getErrorCode()).isEqualTo("FAN_OUT_RETRY_UNSUPPORTED");
    assertThat(aggregate.getTransitions().path("strategy").asText()).isEqualTo("FAN_OUT");
    assertThat(aggregate.getTransitions().path("units").size()).isEqualTo(2);
    assertThat(
            aggregate
                .getCommitSummary()
                .path("successfullyCommittedRecordCount")
                .asLong())
        .isEqualTo(10L);
  }

  @Test
  void persistsDispatchIntentBeforeFirstRemoteMutation() throws Exception {
    OfflineExecutionPlan plan = plan();
    OfflineJobExecution execution = execution();
    LinkUpNodeResponse node = new LinkUpNodeResponse();
    node.setInstanceId("worker-1");
    when(linkUpClient.node()).thenReturn(node);
    when(linkUpClient.submit(anyString(), anyString(), eq(1), any(JsonNode.class)))
        .thenReturn(response("job", "SUCCEEDED", 1L));

    coordinator.submit(execution, plan);

    InOrder order = inOrder(stateManager, linkUpClient);
    order.verify(stateManager)
        .applySnapshot(
            eq(execution),
            any(LinkUpJobResponse.class),
            eq("FAN_OUT_PLAN_READY"));
    order.verify(stateManager)
        .applySnapshot(
            eq(execution),
            any(LinkUpJobResponse.class),
            eq("FAN_OUT_UNIT_DISPATCHING"));
    order.verify(linkUpClient)
        .submit(
            eq("yak-offline-test-f0001"),
            eq("idem:fanout:0001"),
            eq(1),
            any(JsonNode.class));
  }

  @Test
  void reconcileFindsDispatchingChildAndOnlyResumesProvenUnsentUnit() throws Exception {
    OfflineExecutionPlan plan = plan();
    OfflineJobExecution execution = execution();
    execution.setEngineSnapshotJson(snapshotWithFirstDispatching(execution, plan));
    when(batchRepository.findById(77L)).thenReturn(Optional.of(batch(plan)));
    when(linkUpClient.findByExternalExecutionId("yak-offline-test-f0001"))
        .thenReturn(response("job-1", "SUCCEEDED", 10L));
    when(linkUpClient.findByExternalExecutionId("yak-offline-test-f0002"))
        .thenThrow(new LinkUpRequestException(404, "NOT_FOUND", "missing"));
    when(linkUpClient.submit(
            eq("yak-offline-test-f0002"),
            eq("idem:fanout:0002"),
            eq(1),
            any(JsonNode.class)))
        .thenReturn(response("job-2", "SUCCEEDED", 20L));

    coordinator.reconcile(execution);

    verify(linkUpClient, never())
        .submit(
            eq("yak-offline-test-f0001"),
            eq("idem:fanout:0001"),
            eq(1),
            any(JsonNode.class));
    verify(linkUpClient)
        .submit(
            eq("yak-offline-test-f0002"),
            eq("idem:fanout:0002"),
            eq(1),
            any(JsonNode.class));

    ArgumentCaptor<LinkUpJobResponse> snapshots =
        ArgumentCaptor.forClass(LinkUpJobResponse.class);
    verify(stateManager)
        .applySnapshot(eq(execution), snapshots.capture(), eq("FAN_OUT_RECONCILED"));
    assertThat(snapshots.getValue().getStatus()).isEqualTo("SUCCEEDED");
    assertThat(
            snapshots
                .getValue()
                .getCommitSummary()
                .path("successfullyCommittedRecordCount")
                .asLong())
        .isEqualTo(30L);
  }

  @Test
  void reconcileNeverSubmitsCreatedChildrenAfterCancellationIntent() throws Exception {
    OfflineExecutionPlan plan = plan();
    OfflineJobExecution execution = execution();
    execution.setCancellationRequested(true);
    when(batchRepository.findById(77L)).thenReturn(Optional.of(batch(plan)));

    coordinator.reconcile(execution);

    verify(linkUpClient, never())
        .submit(anyString(), anyString(), eq(1), any(JsonNode.class));
    verify(linkUpClient, never()).findByExternalExecutionId(anyString());

    ArgumentCaptor<LinkUpJobResponse> snapshots =
        ArgumentCaptor.forClass(LinkUpJobResponse.class);
    verify(stateManager)
        .applySnapshot(eq(execution), snapshots.capture(), eq("FAN_OUT_RECONCILED"));
    assertThat(snapshots.getValue().getStatus()).isEqualTo("CANCELED");
    assertThat(snapshots.getValue().getTransitions().path("units").size()).isEqualTo(2);
  }

  private String snapshotWithFirstDispatching(
      OfflineJobExecution execution,
      OfflineExecutionPlan plan) throws Exception {
    List<OfflineFanOutUnitState> states = new ArrayList<>();
    for (OfflineExecutionPlan.Unit unit : plan.units()) {
      states.add(OfflineFanOutUnitState.initial(execution, unit, mapper));
    }
    OfflineFanOutUnitState first = states.get(0);
    first.apply(first.localResponse(OfflineExecutionStatus.SUBMITTED, null, null));
    return mapper.writeValueAsString(
        new OfflineFanOutSnapshotAggregator(mapper).aggregate(execution, states));
  }

  private OfflineExecutionPlan plan() throws Exception {
    JsonNode first =
        mapper.readTree(
            """
            {"apiVersion":"link-up/v1","kind":"BatchSyncJob","source":{"options":{}},"sink":{"options":{}}}
            """);
    JsonNode second = first.deepCopy();
    return new OfflineExecutionPlan(
        OfflineExecutionStrategy.FAN_OUT,
        List.of(
            new OfflineExecutionPlan.Unit("0001", "business.orders", "ods.orders", first),
            new OfflineExecutionPlan.Unit(
                "0002", "business.order_item", "ods.order_item", second)));
  }

  private OfflineJobExecution execution() {
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(99L);
    execution.setBatchId(77L);
    execution.setJobDefinitionId(10L);
    execution.setDefinitionVersion(1);
    execution.setExternalExecutionId("yak-offline-test");
    execution.setIdempotencyKey("idem");
    execution.setStatus("CREATED");
    execution.setStateVersion(1L);
    execution.setCancellationRequested(false);
    return execution;
  }

  private BatchExecution batch(OfflineExecutionPlan plan) {
    return new BatchExecution(
        77L,
        10L,
        BatchKey.manual("fanout-test"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        new ExecutionSnapshot(
            "{}",
            1,
            new RetryPolicySnapshot(3, 1),
            "digest",
            codec.encodeJson(plan)),
        BatchStatus.RUNNING,
        List.of());
  }

  private LinkUpJobResponse response(String jobId, String status, long committed) {
    LinkUpJobResponse response = new LinkUpJobResponse();
    response.setJobId(jobId);
    response.setWorkerInstanceId("worker-1");
    response.setStatus(status);
    response.setStateVersion(2L);
    response.setMetrics(mapper.createObjectNode());
    response.setPipelines(mapper.createArrayNode());
    response.setCommitSummary(
        mapper.createObjectNode().put("successfullyCommittedRecordCount", committed));
    return response;
  }
}
