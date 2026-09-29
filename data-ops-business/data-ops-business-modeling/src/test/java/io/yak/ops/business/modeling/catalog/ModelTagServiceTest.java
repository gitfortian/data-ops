package io.yak.ops.business.modeling.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.modeling.domain.ModelingTag;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelTagRepository;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Tag rules: project-unique names and cascade detach on delete. */
class ModelTagServiceTest {

  private ModelTagRepository repository;
  private BusinessAuditService auditService;
  private AuditOperationHandle audit;
  private ModelTagService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(ModelTagRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    audit = Mockito.mock(AuditOperationHandle.class);
    when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    service = new ModelTagService(repository, auditService);
  }

  @Test
  void createTrimsAndRejectsDuplicateName() {
    when(repository.existsByName("核心")).thenReturn(true);

    assertThatThrownBy(() -> service.create(" 核心 "))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.DUPLICATE_CODE));

    when(repository.existsByName("核心")).thenReturn(false);
    when(repository.insert("核心")).thenReturn(new ModelingTag(5L, "核心", null));

    ModelingTag created = service.create(" 核心 ");

    assertThat(created.id()).isEqualTo(5L);
    verify(audit).event(org.mockito.ArgumentMatchers.eq(AuditEventType.RESOURCE_CREATED), any(), any());
  }

  @Test
  void deleteDetachesRelationsAndAudits() {
    when(repository.findById(5L)).thenReturn(Optional.of(new ModelingTag(5L, "核心", null)));
    when(repository.deleteById(5L)).thenReturn(true);

    service.delete(5L);

    verify(repository).deleteById(5L);
    verify(audit).event(org.mockito.ArgumentMatchers.eq(AuditEventType.RESOURCE_DELETED), any(), any());
  }

  @Test
  void deleteUnknownTagFailsWithNotFound() {
    when(repository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(99L))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.NOT_FOUND));
  }
}
