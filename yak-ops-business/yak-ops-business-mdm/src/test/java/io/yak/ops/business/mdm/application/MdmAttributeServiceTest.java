package io.yak.ops.business.mdm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmAttributeRepository;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 主数据属性规则单元测试:编码唯一、PK 唯一、标准引用校验、码集校验。 */
class MdmAttributeServiceTest {

  private MdmAttributeRepository repository;
  private MdmEntityService entityService;
  private StandardQueryApi standardQueryApi;
  private BusinessAuditService auditService;
  private AuditOperationHandle audit;
  private MdmAttributeService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(MdmAttributeRepository.class);
    entityService = Mockito.mock(MdmEntityService.class);
    standardQueryApi = Mockito.mock(StandardQueryApi.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    lenient().when(entityService.get(any())).thenReturn(null);
    service = new MdmAttributeService(repository, entityService, standardQueryApi, auditService);
  }

  @Test
  void createRejectsDuplicateCode() {
    when(repository.existsByCode(1L, "cust_name")).thenReturn(true);
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create(1L, "cust_name", "客户名称", MdmAttributeType.ATTR,
                null, null, null, null, null, null, null, null, "tester"));
    assertEquals(MdmErrorCode.DUPLICATE_ATTR_CODE, exception.getErrorCode());
    verify(repository, never()).insert(any(), any());
  }

  @Test
  void createRejectsSecondPk() {
    when(repository.existsByCode(1L, "cust_pk")).thenReturn(false);
    when(repository.existsPk(1L)).thenReturn(true);
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create(1L, "cust_pk", "客户ID", MdmAttributeType.PK,
                null, null, null, null, null, null, null, null, "tester"));
    assertEquals(MdmErrorCode.PK_ATTRIBUTE_EXISTS, exception.getErrorCode());
  }

  @Test
  void createRejectsWrongKindTypeRef() {
    // std_type_id 指向 UNIT 标准 → 拒绝
    when(standardQueryApi.get(10L)).thenReturn(standard(10L, StandardKind.UNIT));
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create(1L, "cust_name", "客户名称", MdmAttributeType.ATTR,
                null, 10L, null, null, null, null, null, null, "tester"));
    assertEquals(MdmErrorCode.INVALID_STANDARD_REF, exception.getErrorCode());
  }

  @Test
  void createRejectsDisabledTypeRef() {
    when(standardQueryApi.get(11L)).thenReturn(standard(11L, StandardKind.TYPE, StandardStatus.DISABLED));
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create(1L, "cust_name", "客户名称", MdmAttributeType.ATTR,
                null, 11L, null, null, null, null, null, null, "tester"));
    assertEquals(MdmErrorCode.INVALID_STANDARD_REF, exception.getErrorCode());
  }

  @Test
  void createRejectsUnknownCodeSet() {
    when(standardQueryApi.existsCodeSet("gender")).thenReturn(false);
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.create(1L, "cust_gender", "客户性别", MdmAttributeType.ATTR,
                null, null, null, "gender", null, null, null, null, "tester"));
    assertEquals(MdmErrorCode.INVALID_STANDARD_REF, exception.getErrorCode());
  }

  @Test
  void createAcceptsValidRefs() {
    when(repository.existsByCode(1L, "cust_name")).thenReturn(false);
    when(standardQueryApi.get(12L)).thenReturn(standard(12L, StandardKind.TYPE));
    when(standardQueryApi.get(13L)).thenReturn(standard(13L, StandardKind.UNIT));
    when(standardQueryApi.get(14L)).thenReturn(standard(14L, StandardKind.SECURITY));
    when(standardQueryApi.existsCodeSet("gender")).thenReturn(true);
    when(repository.insert(any(), eq("tester")))
        .thenReturn(
            new MdmAttribute(
                1L, 1L, "cust_name", "客户名称", MdmAttributeType.ATTR, null,
                12L, 13L, "gender", 14L, false, null, 0,
                MdmAttribute.STATUS_ENABLED, "tester", LocalDateTime.now(), LocalDateTime.now()));
    MdmAttribute created =
        service.create(1L, "cust_name", "客户名称", MdmAttributeType.ATTR, null,
            12L, 13L, "gender", 14L, null, null, null, "tester");
    assertEquals(MdmAttributeType.ATTR, created.type());
    verify(repository).insert(any(), eq("tester"));
  }

  @Test
  void updateRejectsSwitchToPkWhenExists() {
    MdmAttribute existing = attribute(1L, MdmAttributeType.ATTR);
    when(repository.findById(1L)).thenReturn(Optional.of(existing));
    when(repository.existsPk(1L)).thenReturn(true);
    MdmException exception =
        assertThrows(
            MdmException.class,
            () -> service.update(1L, "客户名称", MdmAttributeType.PK,
                null, null, null, null, null, null, null, null));
    assertEquals(MdmErrorCode.PK_ATTRIBUTE_EXISTS, exception.getErrorCode());
    verify(repository, never()).update(any());
  }

  @Test
  void getThrowsNotFound() {
    when(repository.findById(99L)).thenReturn(Optional.empty());
    MdmException exception = assertThrows(MdmException.class, () -> service.get(99L));
    assertEquals(MdmErrorCode.ATTRIBUTE_NOT_FOUND, exception.getErrorCode());
  }

  private static Standard standard(Long id, StandardKind kind) {
    return standard(id, kind, StandardStatus.ENABLED);
  }

  private static Standard standard(Long id, StandardKind kind, StandardStatus status) {
    return new Standard(
        id, kind, "code" + id, "标准" + id, status, 1, 0, false, null,
        new Standard.KindFields(null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null),
        "tester", LocalDateTime.now(), LocalDateTime.now());
  }

  private static MdmAttribute attribute(Long id, MdmAttributeType type) {
    return new MdmAttribute(
        id, 1L, "cust_name", "客户名称", type, null, null, null, null, null,
        false, null, 0, MdmAttribute.STATUS_ENABLED, "tester", LocalDateTime.now(), LocalDateTime.now());
  }
}
