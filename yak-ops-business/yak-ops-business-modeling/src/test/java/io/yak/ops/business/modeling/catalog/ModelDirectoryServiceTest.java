package io.yak.ops.business.modeling.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.modeling.domain.ModelingDirectory;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelDirectoryRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Directory rules: unique siblings, empty-directory deletion, cycle-free moves. */
class ModelDirectoryServiceTest {

  private ModelDirectoryRepository repository;
  private ModelRepository modelRepository;
  private BusinessAuditService auditService;
  private AuditOperationHandle audit;
  private ModelDirectoryService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(ModelDirectoryRepository.class);
    modelRepository = Mockito.mock(ModelRepository.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    audit = Mockito.mock(AuditOperationHandle.class);
    when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    service = new ModelDirectoryService(repository, modelRepository, auditService);
  }

  private ModelingDirectory dir(long id, Long parentId, String name) {
    return new ModelingDirectory(id, parentId, name, null, null, null);
  }

  @Test
  void createRequiresExistingParentAndUniqueSiblingName() {
    when(repository.findById(1L)).thenReturn(Optional.of(dir(1L, null, "维度")));
    when(repository.existsByName(1L, "用户")).thenReturn(true);

    assertThatThrownBy(() -> service.create(1L, " 用户 "))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.DUPLICATE_CODE));
    assertThatThrownBy(() -> service.create(99L, "用户"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.NOT_FOUND));

    when(repository.existsByName(1L, "用户")).thenReturn(false);
    when(repository.insert(1L, "用户")).thenReturn(dir(2L, 1L, "用户"));

    ModelingDirectory created = service.create(1L, " 用户 ");

    assertThat(created.id()).isEqualTo(2L);
    verify(audit).event(eq(AuditEventType.RESOURCE_CREATED), any(), any());
  }

  @Test
  void moveRejectsSelfAndDescendantTargets() {
    when(repository.findById(1L)).thenReturn(Optional.of(dir(1L, null, "维度")));
    when(repository.findById(2L)).thenReturn(Optional.of(dir(2L, 1L, "用户")));

    assertThatThrownBy(() -> service.move(1L, 1L))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ModelingErrorCode.INVALID_DIRECTORY_TARGET));
    // 2 是 1 的子孙：沿 parent 链从 2 走到 1。
    assertThatThrownBy(() -> service.move(1L, 2L))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ModelingErrorCode.INVALID_DIRECTORY_TARGET));
    verify(repository, org.mockito.Mockito.never()).updateParentId(eq(1L), any());
  }

  @Test
  void deleteRejectsDirectoryWithChildrenOrModels() {
    when(repository.findById(1L)).thenReturn(Optional.of(dir(1L, null, "维度")));
    when(repository.hasChildren(1L)).thenReturn(true);

    assertThatThrownBy(() -> service.delete(1L))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ModelingErrorCode.DIRECTORY_NOT_EMPTY));

    when(repository.hasChildren(1L)).thenReturn(false);
    when(modelRepository.countByDirectory(1L)).thenReturn(3L);

    assertThatThrownBy(() -> service.delete(1L))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ModelingErrorCode.DIRECTORY_NOT_EMPTY));

    when(modelRepository.countByDirectory(1L)).thenReturn(0L);
    when(repository.deleteById(1L)).thenReturn(true);
    service.delete(1L);
    verify(audit).event(eq(AuditEventType.RESOURCE_DELETED), any(), any());
  }
}
