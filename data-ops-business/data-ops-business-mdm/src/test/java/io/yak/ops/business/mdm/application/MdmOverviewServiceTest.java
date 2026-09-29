package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.mdm.dao.MdmOverviewCardRow;
import io.yak.ops.business.mdm.dao.MdmOverviewTotalsRow;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.infrastructure.repository.MdmChangeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmDistributionRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmOverviewRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSubscriptionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 主数据总览单元测试(R7:有界聚合,不再遍历实体)。 */
class MdmOverviewServiceTest {

  private static final MdmOverviewTotalsRow TOTALS =
      new MdmOverviewTotalsRow(12L, 3059L, 2L, 4L, 3L, 6L, 5L, 9L, 1L);

  private MdmOverviewRepository overviewRepository;
  private MdmEntityService entityService;
  private MdmRecordRepository recordRepository;
  private MdmDistributionRepository distributionRepository;
  private MdmSubscriptionRepository subscriptionRepository;
  private MdmChangeRepository changeRepository;
  private MdmOverviewService service;

  @BeforeEach
  void setUp() {
    overviewRepository = mock(MdmOverviewRepository.class);
    entityService = Mockito.mock(MdmEntityService.class);
    recordRepository = Mockito.mock(MdmRecordRepository.class);
    distributionRepository = Mockito.mock(MdmDistributionRepository.class);
    subscriptionRepository = Mockito.mock(MdmSubscriptionRepository.class);
    changeRepository = Mockito.mock(MdmChangeRepository.class);
    service =
        new MdmOverviewService(
            overviewRepository,
            entityService,
            recordRepository,
            distributionRepository,
            subscriptionRepository,
            changeRepository);
  }

  private static MdmEntity entity(Long id, String code, String name) {
    return new MdmEntity(
        id, code, name, MdmEntityStatus.ACTIVE, null, null, null,
        LocalDateTime.now(), LocalDateTime.now());
  }

  @Test
  void overviewReadsAggregatesWithoutPerEntityQueries() {
    when(overviewRepository.totals()).thenReturn(TOTALS);
    when(overviewRepository.recentCards(10))
        .thenReturn(List.of(new MdmOverviewCardRow(1L, "customer", "客户", 3059L, 1L, 2L, 1L)));

    var result = service.getOverview(null);

    assertEquals(12L, result.totals().entities());
    assertEquals(3059L, result.totals().activeRecords());
    assertEquals(2L, result.totals().pendingChanges());
    assertEquals(4L, result.totals().cleanRules());
    assertEquals(3L, result.totals().distributionTargets());
    assertEquals(6L, result.totals().subscribers());
    assertEquals("customer", result.entities().get(0).entityCode());
    assertEquals(3059L, result.entities().get(0).activeRecords());
    // 总览绝不能再逐实体回查(旧实现 = 无界 list + N+1)。
    verifyNoInteractions(
        recordRepository, distributionRepository, subscriptionRepository, changeRepository);
  }

  @Test
  void pipelineNodesCarryRealCountsAndAttention() {
    when(overviewRepository.totals()).thenReturn(TOTALS);
    when(overviewRepository.recentCards(anyInt())).thenReturn(List.of());

    var pipeline = service.getOverview(5).pipeline();

    assertEquals(5, pipeline.size());
    assertEquals(List.of("COLLECT", "PROCESSING", "CLEANSE", "APPROVE", "DISTRIBUTE"),
        pipeline.stream().map(MdmOverviewService.PipelineNode::key).toList());
    assertEquals(5L, pipeline.get(0).count());
    // 加工任务活在数据开发域,MDM 无真相表 → -1(展示「-」),不伪造 0。
    assertEquals(-1L, pipeline.get(1).count());
    assertEquals(9L, pipeline.get(2).attention());
    assertEquals(2L, pipeline.get(3).attention());
    assertEquals(1L, pipeline.get(4).attention());
  }

  @Test
  void entityCardLimitIsBounded() {
    when(overviewRepository.totals()).thenReturn(TOTALS);
    when(overviewRepository.recentCards(anyInt())).thenReturn(List.of());

    service.getOverview(500);
    verify(overviewRepository).recentCards(MdmOverviewService.MAX_ENTITY_CARDS);

    service.getOverview(-3);
    verify(overviewRepository).recentCards(10);
  }

  @Test
  void totalsFailureIsShownAsUnavailableNotZero() {
    when(overviewRepository.totals()).thenThrow(new RuntimeException("db down"));
    when(overviewRepository.recentCards(anyInt())).thenReturn(List.of());

    var totals = service.getOverview(null).totals();

    assertEquals(-1L, totals.entities());
    assertEquals(-1L, totals.activeRecords());
  }

  @Test
  void singleEntityCardStillAggregatesIndependently() {
    when(entityService.get(1L)).thenReturn(entity(1L, "customer", "客户"));
    when(recordRepository.countActiveByEntity(1L)).thenReturn(42L);
    when(distributionRepository.countActiveByEntity(1L)).thenReturn(2L);
    when(subscriptionRepository.countActiveByEntity(1L)).thenReturn(3L);
    when(changeRepository.countPending(1L)).thenThrow(new RuntimeException("fail"));

    var card = service.getEntityCard(1L);

    assertEquals("customer", card.entityCode());
    assertEquals(42L, card.activeRecords());
    assertEquals(-1L, card.pendingChanges());
  }
}
