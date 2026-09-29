package io.yak.ops.business.agent.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.agent.catalog.FieldWhitelistValidator;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.domain.DatasetQuerySpec;
import io.yak.ops.business.agent.domain.QueryEvidenceRecord;
import io.yak.ops.business.agent.repository.QueryLogRepository;
import io.yak.ops.business.dataset.DatasetQueryResult;
import io.yak.ops.business.dataset.DatasetQueryService;
import io.yak.ops.core.execution.sql.SqlExecutionColumn;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 查询网关行为测试：limit 钳制、证据留痕成功/失败/守卫拒绝三路（DOMAIN 全量留痕契约）。 */
class DatasetQueryGatewayTest {

  private DatasetQueryService queryService;
  private QueryLogRepository queryLogRepository;
  private FieldWhitelistValidator whitelistValidator;
  private DatasetQueryGateway gateway;

  @BeforeEach
  void setUp() {
    queryService = mock(DatasetQueryService.class);
    queryLogRepository = mock(QueryLogRepository.class);
    whitelistValidator = mock(FieldWhitelistValidator.class);
    gateway =
        new DatasetQueryGateway(queryService, whitelistValidator, queryLogRepository,
            new AgentProperties());
  }

  @Test
  void successRecordsEvidenceAndReturnsView() {
    DatasetQueryResult result =
        new DatasetQueryResult(
            null, 7L, 1L, 1,
            List.of(),
            List.of(new SqlExecutionColumn("region", "region", "VARCHAR", 12, true)),
            List.of(List.of("east", 100)),
            1, false, 5);
    when(queryService.query(eq(7L), any())).thenReturn(result);

    var view =
        gateway.execute(
            "s1",
            new DatasetQuerySpec(
                7L,
                List.of("region"),
                List.of(new DatasetQuerySpec.Metric("amount", DatasetQuerySpec.Aggregation.SUM)),
                List.of(), List.of(), 50));

    assertEquals("region", view.columns().get(0));
    assertEquals("east", view.rows().get(0).get(0));
    assertEquals(1, view.returnedRows());

    ArgumentCaptor<QueryEvidenceRecord> captor = ArgumentCaptor.forClass(QueryEvidenceRecord.class);
    verify(queryLogRepository).record(captor.capture());
    assertEquals("s1", captor.getValue().sessionId());
    assertEquals(QueryEvidenceRecord.Status.SUCCESS, captor.getValue().status());
    assertEquals(7L, captor.getValue().datasetId());
    assertTrue(captor.getValue().requestJson().contains("\"datasetId\":7"));
  }

  @Test
  void failureRecordsFailedEvidenceAndRethrows() {
    when(queryService.query(any(Long.class), any()))
        .thenThrow(new IllegalArgumentException("数据集查询失败"));

    var spec =
        new DatasetQuerySpec(9L, List.of(), List.of(), List.of(), List.of(), null);
    assertThrows(IllegalArgumentException.class, () -> gateway.execute("s2", spec));

    ArgumentCaptor<QueryEvidenceRecord> captor = ArgumentCaptor.forClass(QueryEvidenceRecord.class);
    verify(queryLogRepository).record(captor.capture());
    assertEquals(QueryEvidenceRecord.Status.FAILED, captor.getValue().status());
    assertTrue(captor.getValue().errorMessage().contains("数据集查询失败"));
  }

  @Test
  void whitelistRejectionRecordsRejectedEvidence() {
    doThrow(new IllegalArgumentException(
            "[FIELD_WHITELIST_REJECTED] 非法字段引用 [7 字段引用]: 'bogus' 不存在于数据集 7..."))
        .when(whitelistValidator).requireKnownFields(eq(7L), anyList(), anyString());

    var spec = new DatasetQuerySpec(7L, List.of("bogus"), List.of(), List.of(), List.of(), null);
    assertThrows(IllegalArgumentException.class, () -> gateway.execute("s3", spec));

    ArgumentCaptor<QueryEvidenceRecord> captor = ArgumentCaptor.forClass(QueryEvidenceRecord.class);
    verify(queryLogRepository).record(captor.capture());
    assertEquals(QueryEvidenceRecord.Status.REJECTED, captor.getValue().status());
    assertTrue(captor.getValue().errorMessage().contains("[FIELD_WHITELIST_REJECTED]"));
  }

  @Test
  void limitIsClampedToConfiguredMax() {
    AgentProperties properties = new AgentProperties();
    properties.getQuery().setMaxLimit(10);
    properties.getQuery().setDefaultLimit(5);
    DatasetQueryGateway strictGateway =
        new DatasetQueryGateway(queryService, whitelistValidator, queryLogRepository, properties);
    AtomicReference<Integer> appliedLimit = new AtomicReference<>();
    when(queryService.query(any(Long.class), any()))
        .thenAnswer(invocation -> {
          appliedLimit.set(invocation.getArgument(1, io.yak.ops.business.dataset.DatasetQueryRequest.class).limit());
          return emptyResult();
        });

    strictGateway.execute("s3", new DatasetQuerySpec(1L, List.of(), List.of(), List.of(), List.of(), 500));

    assertEquals(10, appliedLimit.get());
  }

  private static DatasetQueryResult emptyResult() {
    return new DatasetQueryResult(null, 1L, 1L, 1, List.of(), List.of(), List.of(), 0, false, 0);
  }
}
