package io.yak.ops.business.semantic.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.SemanticFieldApi;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticFieldRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessFieldRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessRepository;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 标准字段角色适配单元测试(2026-09-16):类型引用必填、METRIC 必填单位/口径、无意义引用归零。 */
class SemanticFieldServiceTest {

  private static final long TYPE_STANDARD_ID = 7L;

  private SemanticFieldRepository repository;
  private SemanticStandardRepository standardRepository;
  private BusinessAuditService auditService;
  private AuditOperationHandle audit;
  private SemanticFieldService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(SemanticFieldRepository.class);
    standardRepository = Mockito.mock(SemanticStandardRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    lenient().when(standardRepository.findById(TYPE_STANDARD_ID)).thenReturn(Optional.of(typeStandard()));
    lenient().when(standardRepository.findById(11L)).thenReturn(Optional.of(
        new Standard(11L, StandardKind.UNIT, "unit", "单位", StandardStatus.ENABLED, 1, 0, false, null, null, "tester", null, null)));
    lenient().when(standardRepository.findById(21L)).thenReturn(Optional.of(
        new Standard(21L, StandardKind.SECURITY, "security", "安全", StandardStatus.ENABLED, 1, 0, false, null, null, "tester", null, null)));
    lenient().when(standardRepository.findById(12L)).thenReturn(Optional.of(caliberStandard()));
    lenient().when(standardRepository.existsEnabledByCodeSet(any())).thenReturn(true);
    lenient().when(repository.existsByCode(any())).thenReturn(false);
    lenient().when(repository.insert(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    service =
        new SemanticFieldService(
            repository,
            Mockito.mock(SemanticProcessFieldRepository.class),
            Mockito.mock(SemanticProcessRepository.class),
            standardRepository,
            auditService);
  }

  @Test
  void createRequiresTypeForEveryRole() {
    for (String role : new String[] {"PROCESS", "DIMENSION", "METRIC"}) {
      SemanticException exception = assertThrows(SemanticException.class,
          () -> service.create(createRequest(role, null, 11L, 12L, null, null), "tester"));
      assertEquals(SemanticErrorCode.ROLE_FIELD_REQUIRED, exception.getErrorCode());
    }
    verify(repository, Mockito.never()).insert(any(), any());
  }

  @Test
  void createMetricRequiresUnitButCaliberIsOptional() {
    assertThrows(SemanticException.class,
        () -> service.create(createRequest("METRIC", TYPE_STANDARD_ID, null, 12L, null, null), "tester"));
    service.create(createRequest("METRIC", TYPE_STANDARD_ID, 11L, null, null, null), "tester");
    ArgumentCaptor<StandardField> captor = ArgumentCaptor.forClass(StandardField.class);
    verify(repository).insert(captor.capture(), any());
    assertEquals(11L, captor.getValue().stdUnitId());
    assertNull(captor.getValue().stdCaliberId());
  }

  @Test
  void createMetricRejectsNonCaliberKindRef() {
    lenient().when(standardRepository.findById(99L))
        .thenReturn(Optional.of(typeStandard())); // TYPE 标准
    SemanticException exception =
        assertThrows(
            SemanticException.class,
            () -> service.create(createRequest("METRIC", TYPE_STANDARD_ID, 11L, 99L, null, null), "tester"));
    assertEquals(SemanticErrorCode.INVALID_KIND, exception.getErrorCode());
  }

  @Test
  void createMetricDropsCodeSetAndSecurityRefs() {
    service.create(createRequest("METRIC", TYPE_STANDARD_ID, 11L, 12L, "order_status", 21L), "tester");
    ArgumentCaptor<StandardField> captor = ArgumentCaptor.forClass(StandardField.class);
    verify(repository).insert(captor.capture(), any());
    assertEquals(11L, captor.getValue().stdUnitId());
    assertEquals(12L, captor.getValue().stdCaliberId());
    assertNull(captor.getValue().stdCodeSetCode());
    assertNull(captor.getValue().stdSecurityId());
  }

  @Test
  void createDimensionDropsUnitAndCaliberRefs() {
    service.create(createRequest("DIMENSION", TYPE_STANDARD_ID, 11L, 12L, "order_status", 21L), "tester");
    ArgumentCaptor<StandardField> captor = ArgumentCaptor.forClass(StandardField.class);
    verify(repository).insert(captor.capture(), any());
    assertNull(captor.getValue().stdUnitId());
    assertNull(captor.getValue().stdCaliberId());
    assertEquals("order_status", captor.getValue().stdCodeSetCode());
    assertEquals(21L, captor.getValue().stdSecurityId());
  }

  @Test
  void createProcessDropsAllNonTypeRefs() {
    service.create(createRequest("PROCESS", TYPE_STANDARD_ID, 11L, 12L, "order_status", 21L), "tester");
    ArgumentCaptor<StandardField> captor = ArgumentCaptor.forClass(StandardField.class);
    verify(repository).insert(captor.capture(), any());
    assertNull(captor.getValue().stdUnitId());
    assertNull(captor.getValue().stdCaliberId());
    assertNull(captor.getValue().stdCodeSetCode());
    assertNull(captor.getValue().stdSecurityId());
  }

  private static Standard typeStandard() {
    return new Standard(
        TYPE_STANDARD_ID,
        StandardKind.TYPE,
        "amount",
        "金额",
        StandardStatus.ENABLED,
        1,
        0,
        true,
        null,
        new Standard.KindFields(null, null, null, null, "amount", "decimal(18,2)", null,
            null, null, null, null, null, null, null, null, null, null),
        "tester",
        null,
        null);
  }

  private static Standard caliberStandard() {
    return new Standard(
        12L,
        StandardKind.CALIBER,
        "gmv",
        "GMV",
        StandardStatus.ENABLED,
        1,
        0,
        true,
        null,
        new Standard.KindFields(null, null, null, null, null, null, null,
            null, null, null, null, null, "gmv", "sum(amount)", "成交总额",
            null, null),
        "tester",
        null,
        null);
  }

  private static SemanticFieldApi.CreateRequest createRequest(
      String role, Long stdTypeId, Long stdUnitId, Long stdCaliberId, String stdCodeSetCode, Long stdSecurityId) {
    return new SemanticFieldApi.CreateRequest(
        "order_amount", "订单金额", role, null, stdTypeId, stdUnitId, stdCaliberId,
        stdCodeSetCode, stdSecurityId, "订单应付金额");
  }
}
