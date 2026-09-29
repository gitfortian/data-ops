package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.infrastructure.repository.MdmChangeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmDistributionRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSourceRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSubscriptionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 主数据治理单元测试(ticket 61)。 */
class MdmGovernanceServiceTest {

  private MdmEntityService entityService;
  private MdmSourceRepository sourceRepository;
  private MdmRecordRepository recordRepository;
  private MdmDistributionRepository distributionRepository;
  private MdmSubscriptionRepository subscriptionRepository;
  private MdmChangeRepository changeRepository;
  private MdmGovernanceService service;

  @BeforeEach
  void setUp() {
    entityService = Mockito.mock(MdmEntityService.class);
    sourceRepository = Mockito.mock(MdmSourceRepository.class);
    recordRepository = Mockito.mock(MdmRecordRepository.class);
    distributionRepository = Mockito.mock(MdmDistributionRepository.class);
    subscriptionRepository = Mockito.mock(MdmSubscriptionRepository.class);
    changeRepository = Mockito.mock(MdmChangeRepository.class);
    lenient().when(entityService.get(1L)).thenReturn(
        new MdmEntity(1L, "customer", "客户", MdmEntityStatus.ACTIVE,
            "admin", "desc", "admin", LocalDateTime.now(), LocalDateTime.now()));
    service = new MdmGovernanceService(
        entityService, sourceRepository, recordRepository,
        distributionRepository, subscriptionRepository, changeRepository);
  }

  @Test
  void getSummaryAggregatesAllCards() {
    when(sourceRepository.listByEntity(1L)).thenReturn(List.of(Mockito.mock(), Mockito.mock()));
    when(recordRepository.countActiveByEntity(1L)).thenReturn(100L);
    when(distributionRepository.countActiveByEntity(1L)).thenReturn(3L);
    when(subscriptionRepository.countActiveByEntity(1L)).thenReturn(5L);
    when(changeRepository.countPending(1L)).thenReturn(2L);

    var summary = service.getSummary(1L);

    assertEquals(1L, summary.entityId());
    assertEquals(2, summary.sourceCount());
    assertEquals(100L, summary.activeRecords());
    assertEquals(3L, summary.distributionTargets());
    assertEquals(5L, summary.subscribers());
    assertEquals(2L, summary.pendingChanges());
  }

  @Test
  void getSummaryReturnsMinusOneOnFailure() {
    when(sourceRepository.listByEntity(1L)).thenThrow(new RuntimeException("fail"));
    when(recordRepository.countActiveByEntity(1L)).thenReturn(0L);
    when(distributionRepository.countActiveByEntity(1L)).thenReturn(0L);
    when(subscriptionRepository.countActiveByEntity(1L)).thenReturn(0L);
    when(changeRepository.countPending(1L)).thenReturn(0L);

    var summary = service.getSummary(1L);

    assertEquals(-1L, summary.sourceCount());
    assertEquals(0L, summary.activeRecords());
  }

  @Test
  void isOwnerReturnsTrueWhenMatching() {
    assertTrue(service.isOwner(1L, "admin"));
  }

  @Test
  void isOwnerReturnsFalseWhenMismatch() {
    assertEquals(false, service.isOwner(1L, "other"));
  }

  @Test
  void isOwnerReturnsTrueWhenNoOwner() {
    when(entityService.get(2L)).thenReturn(
        new MdmEntity(2L, "product", "产品", MdmEntityStatus.ACTIVE,
            null, null, null, LocalDateTime.now(), LocalDateTime.now()));
    assertTrue(service.isOwner(2L, "anyone"));
  }
}
