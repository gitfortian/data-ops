package io.yak.ops.business.semantic.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.SemanticStandardApi;
import io.yak.ops.business.semantic.api.StandardCaptureApi;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.catalog.StandardCatalogService;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 沉淀为标准单元测试:幂等返回既有、透传 catalog.create、类别校验。 */
class SemanticCaptureServiceTest {

  private StandardCatalogService catalogService;
  private SemanticStandardRepository standardRepository;
  private BusinessAuditService auditService;
  private AuditOperationHandle audit;
  private SemanticCaptureService service;

  @BeforeEach
  void setUp() {
    catalogService = Mockito.mock(StandardCatalogService.class);
    standardRepository = Mockito.mock(SemanticStandardRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    service = new SemanticCaptureService(catalogService, standardRepository, auditService);
  }

  @Test
  void captureReturnsExistingWhenCodeTaken() {
    Standard existing =
        new Standard(
            11L,
            StandardKind.TYPE,
            "varchar128",
            "短文本",
            StandardStatus.ENABLED,
            1,
            0,
            false,
            null,
            SemanticCaptureServiceTestSupport.typeFields(),
            "tester",
            null,
            null);
    when(standardRepository.findByCode(StandardKind.TYPE, "varchar128"))
        .thenReturn(Optional.of(existing));

    StandardCaptureApi.CaptureResult result =
        service.capture(new StandardCaptureApi.CaptureRequest(
            "TYPE", "varchar128", "短文本", null, "VARCHAR", "VARCHAR(128)", null, null, null,
            null, null, null, null, null, null, null, null, "model-1", "user_name"));

    assertFalse(result.created());
    assertEquals(11L, result.standardId());
    Mockito.verify(catalogService, Mockito.never()).create(any(), any());
  }

  @Test
  void captureCreatesThroughCatalogService() {
    when(standardRepository.findByCode(StandardKind.TYPE, "varchar128"))
        .thenReturn(Optional.empty());
    when(catalogService.create(any(SemanticStandardApi.CreateRequest.class), eq("capture:user_name")))
        .thenAnswer(
            invocation -> {
              SemanticStandardApi.CreateRequest request = invocation.getArgument(0);
              return new Standard(
                  12L,
                  StandardKind.TYPE,
                  request.code(),
                  request.name(),
                  StandardStatus.ENABLED,
                  1,
                  0,
                  false,
                  request.description(),
                  SemanticCaptureServiceTestSupport.typeFields(),
                  "capture:user_name",
                  null,
                  null);
            });

    StandardCaptureApi.CaptureResult result =
        service.capture(new StandardCaptureApi.CaptureRequest(
            "TYPE", "varchar128", "短文本", null, "VARCHAR", "VARCHAR(128)", null, null, null,
            null, null, null, null, null, null, null, null, "model-1", "user_name"));

    assertTrue(result.created());
    assertEquals(12L, result.standardId());
    ArgumentCaptor<SemanticStandardApi.CreateRequest> captor =
        ArgumentCaptor.forClass(SemanticStandardApi.CreateRequest.class);
    verify(catalogService).create(captor.capture(), eq("capture:user_name"));
    assertEquals("VARCHAR(128)", captor.getValue().stdType());
  }

  @Test
  void captureRejectsUnknownKind() {
    SemanticException exception =
        assertThrows(
            SemanticException.class,
            () -> service.capture(new StandardCaptureApi.CaptureRequest(
                "BAD", "x", "X", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null)));
    assertEquals(io.yak.ops.common.enums.semantic.SemanticErrorCode.INVALID_KIND, exception.getErrorCode());
  }
}
