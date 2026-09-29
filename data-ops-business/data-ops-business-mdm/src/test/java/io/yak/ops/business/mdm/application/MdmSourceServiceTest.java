package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.mdm.domain.source.MdmSource;
import io.yak.ops.business.mdm.domain.source.MdmSourceRole;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmEntityRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSourceRepository;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 主数据来源规则单元测试:扫描失败、重复绑定、表不存在。 */
class MdmSourceServiceTest {

  private MdmSourceRepository repository;
  private MdmEntityRepository entityRepository;
  private MdmEntityService entityService;
  private io.yak.ops.business.mdm.infrastructure.repository.MdmCollectLinkRepository collectLinkRepository;
  private DataSourceCatalogReader catalogReader;
  private BusinessAuditService auditService;
  private MdmSourceService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(MdmSourceRepository.class);
    entityRepository = Mockito.mock(MdmEntityRepository.class);
    entityService = Mockito.mock(MdmEntityService.class);
    collectLinkRepository =
        Mockito.mock(io.yak.ops.business.mdm.infrastructure.repository.MdmCollectLinkRepository.class);
    catalogReader = Mockito.mock(DataSourceCatalogReader.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    AuditOperationHandle audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    lenient().when(entityService.get(any())).thenReturn(null);
    lenient().when(entityRepository.findAll()).thenReturn(List.of());
    lenient().when(repository.listByDatasource(any())).thenReturn(List.of());
    lenient().when(collectLinkRepository.findBySourceId(any())).thenReturn(java.util.Optional.empty());
    service =
        new MdmSourceService(
            repository, entityRepository, entityService, collectLinkRepository, catalogReader, auditService);
  }

  @Test
  void scanFailsWhenDatasourceUnavailable() {
    when(catalogReader.searchTables(any(), any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("connection refused"));
    MdmException exception =
        assertThrows(MdmException.class, () -> service.scan(9L, null, null));
    assertEquals(MdmErrorCode.DATASOURCE_SCAN_FAILED, exception.getErrorCode());
  }

  @Test
  void confirmRejectsDuplicateSource() {
    when(repository.exists(1L, 9L, "crm", null, "customer")).thenReturn(true);
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.confirm(1L, 9L, "crm", null, "customer", MdmSourceRole.MAIN, null, "tester"));
    assertEquals(MdmErrorCode.DUPLICATE_SOURCE, exception.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void confirmRejectsMissingTable() {
    when(repository.exists(1L, 9L, "crm", null, "customer")).thenReturn(false);
    when(catalogReader.listColumns(any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("table not found"));
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.confirm(1L, 9L, "crm", null, "customer", MdmSourceRole.MAIN, null, "tester"));
    assertEquals(MdmErrorCode.DATASOURCE_SCAN_FAILED, exception.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void unbindThrowsNotFound() {
    when(repository.findById(99L)).thenReturn(java.util.Optional.empty());
    MdmException exception = assertThrows(MdmException.class, () -> service.unbind(99L));
    assertEquals(MdmErrorCode.SOURCE_NOT_FOUND, exception.getErrorCode());
  }

  @Test
  void unbindBlockedWhenCollectLinkExists() {
    MdmSource bound =
        new MdmSource(
            7L, 1L, 9L, "crm", null, "customer", null, MdmSourceRole.MAIN,
            MdmSource.STATUS_ENABLED, 0, "tester", null, null);
    when(repository.findById(7L)).thenReturn(java.util.Optional.of(bound));
    when(collectLinkRepository.findBySourceId(7L))
        .thenReturn(
            java.util.Optional.of(
                new io.yak.ops.business.mdm.domain.collect.MdmCollectLink(
                    1L, 1L, 7L, 9L, "mdm_landing_customer_7", 555L, "tester", null, null)));
    MdmException exception = assertThrows(MdmException.class, () -> service.unbind(7L));
    assertEquals(MdmErrorCode.SOURCE_REFERENCED, exception.getErrorCode());
    verify(repository, never()).deleteById(any());
  }

  @Test
  void scanReturnsTablesWithCandidates() {
    when(catalogReader.searchTables(any(), any(), any(), any(), any()))
        .thenReturn(
            List.of(
                new io.yak.ops.business.datasource.domain.catalog.CatalogTable(
                    "crm", null, "customer", "TABLE", "客户表"),
                new io.yak.ops.business.datasource.domain.catalog.CatalogTable(
                    "trade", null, "order", "TABLE", "订单表")));
    io.yak.ops.business.mdm.domain.entity.MdmEntity customer =
        new io.yak.ops.business.mdm.domain.entity.MdmEntity(
            1L, "customer", "客户",
            io.yak.ops.business.mdm.domain.entity.MdmEntityStatus.ACTIVE,
            null, null, "tester", java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
    when(entityRepository.findAll()).thenReturn(List.of(customer));
    List<MdmSourceService.TableCandidate> result = service.scan(9L, null, null);
    assertEquals(2, result.size());
    assertEquals("customer", result.get(0).name());
    assertEquals(1, result.get(0).candidates().size());
    assertEquals("customer", result.get(0).candidates().get(0).entityCode());
    assertEquals(0, result.get(1).candidates().size());
  }
}
