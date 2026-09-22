package io.yak.ops.business.semantic.preset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.repository.SemanticPresetTemplateRepository;
import io.yak.ops.business.semantic.repository.SemanticStandardRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** 预置初始化规则单元测试:幂等(已有编码跳过)、复制映射、is_preset 标记。 */
class SemanticPresetServiceTest {

  private SemanticPresetTemplateRepository templateRepository;
  private SemanticStandardRepository standardRepository;
  private BusinessAuditService auditService;
  private AuditOperationHandle audit;
  private SemanticPresetService service;

  @BeforeEach
  void setUp() {
    templateRepository = Mockito.mock(SemanticPresetTemplateRepository.class);
    standardRepository = Mockito.mock(SemanticStandardRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    audit = Mockito.mock(AuditOperationHandle.class);
    lenient().when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    service = new SemanticPresetService(templateRepository, standardRepository, auditService);
  }

  @Test
  void initializeSkipsExistingCodesAndCopiesTemplates() {
    PresetTemplate existing =
        new PresetTemplate(
            1L,
            StandardKind.NAMING,
            "ods_table_prefix",
            "ODS 前缀",
            null,
            10,
            new Standard.KindFields("TABLE", null, "^ods_", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null));
    PresetTemplate fresh =
        new PresetTemplate(
            2L,
            StandardKind.TYPE,
            "amount",
            "金额",
            null,
            10,
            new Standard.KindFields(null, null, null, null, "AMOUNT", "DECIMAL(18,2)",
                "{\"mysql\":[\"DECIMAL\"]}", null, null, null, null, null, null, null, null, null,
                null));
    when(templateRepository.findAll()).thenReturn(List.of(existing, fresh));
    when(standardRepository.existsByCode(StandardKind.NAMING, "ods_table_prefix")).thenReturn(true);
    when(standardRepository.existsByCode(StandardKind.TYPE, "amount")).thenReturn(false);
    when(standardRepository.insert(any(), eq("tester")))
        .thenAnswer(invocation -> invocation.getArgument(0));

    int created = service.initialize("tester");

    assertEquals(1, created);
    ArgumentCaptor<Standard> captor = ArgumentCaptor.forClass(Standard.class);
    verify(standardRepository, times(1)).insert(captor.capture(), eq("tester"));
    Standard inserted = captor.getValue();
    assertEquals(StandardKind.TYPE, inserted.kind());
    assertEquals("amount", inserted.code());
    assertTrue(inserted.preset());
    assertEquals("DECIMAL(18,2)", inserted.fields().stdType());
  }

  @Test
  void initializedReflectsProjectRowCount() {
    when(standardRepository.countByProject()).thenReturn(0L);
    assertFalse(service.initialized());
    when(standardRepository.countByProject()).thenReturn(3L);
    org.junit.jupiter.api.Assertions.assertTrue(service.initialized());
  }
}
