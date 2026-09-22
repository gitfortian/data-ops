package io.yak.ops.business.modeling.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.modeling.api.ModelingModelApi;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelDirectoryRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.repository.ModelTagRepository;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/** Catalog rules and audit behaviour of the modeling facade. */
class ModelCatalogServiceTest {

  private ModelRepository repository;
  private ModelTagRepository tagRepository;
  private ModelDirectoryRepository directoryRepository;
  private io.yak.ops.business.semantic.api.ProcessApi processApi;
  private BusinessAuditService auditService;
  private AuditOperationHandle audit;
  private ModelCatalogService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(ModelRepository.class);
    tagRepository = Mockito.mock(ModelTagRepository.class);
    directoryRepository = Mockito.mock(ModelDirectoryRepository.class);
    processApi = Mockito.mock(io.yak.ops.business.semantic.api.ProcessApi.class);
    auditService = Mockito.mock(BusinessAuditService.class);
    audit = Mockito.mock(AuditOperationHandle.class);
    when(auditService.start(any(AuditOperationRequest.class))).thenReturn(audit);
    service =
        new ModelCatalogService(
            repository,
            tagRepository,
            directoryRepository,
            processApi,
            Mockito.mock(io.yak.ops.business.semantic.api.LayerConfigApi.class),
            auditService);
  }

  @Test
  void createResolvesDialectChecksCodeAndInserts() {
    when(repository.existsByCode("dim_user")).thenReturn(false);
    when(repository.insert(any(Model.class), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0, Model.class));

    Model created = service.create("用户维度表", "dim_user", "mysql", "描述", "alice", null, null, null);

    assertThat(created.code()).isEqualTo("dim_user");
    assertThat(created.dialect()).isEqualTo(ModelDialect.MYSQL);
    verify(repository).insert(any(Model.class), ArgumentMatchers.eq("alice"));
    verify(audit).event(eq(AuditEventType.RESOURCE_CREATED), anyString(), any());
    verify(audit).success(anyString());
    verify(audit, never()).failure(anyString(), any());
  }

  @Test
  void createRejectsUnknownDialectAndAuditsFailure() {
    assertThatThrownBy(() -> service.create("用户维度表", "dim_user", "EXCEL", null, "alice", null, null, null))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.INVALID_DIALECT));

    verify(repository, never()).insert(any(), anyString());
    verify(audit).failure(ArgumentMatchers.eq("MODELING_MODEL_CREATE_FAILED"), any());
  }

  @Test
  void createRejectsDuplicateCodeWithinProject() {
    when(repository.existsByCode("dim_user")).thenReturn(true);

    assertThatThrownBy(() -> service.create("用户维度表", "dim_user", "MYSQL", null, "alice", null, null, null))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.DUPLICATE_CODE));

    verify(repository, never()).insert(any(), anyString());
    verify(audit).failure(ArgumentMatchers.eq("MODELING_MODEL_CREATE_FAILED"), any());
  }

  @Test
  void createPlacesModelInAutoDirectoryOfSelectedDomain() {
    when(repository.existsByCode("dwd_trade_order")).thenReturn(false);
    when(repository.insert(any(Model.class), anyString()))
        .thenAnswer(
            invocation ->
                invocation
                    .getArgument(0, Model.class)
                    .withPersisted(42L, "alice", null, null));
    when(processApi.listDomains())
        .thenReturn(
            List.of(
                new io.yak.ops.business.semantic.domain.BusinessDomain(
                    7L, "trade", "交易", 0L, null, null, 0, null, null, null)));
    when(directoryRepository.insert(null, "交易", 7L))
        .thenReturn(directory(9L, null, "交易"));

    Model created =
        service.create(
            "交易订单明细", "dwd_trade_order", "mysql", null, "alice", null, null, null, null, null,
            null, 7L);

    assertThat(created.directoryId()).isEqualTo(9L);
    assertThat(created.domainId()).isEqualTo(7L);
    verify(repository).updateDirectory(42L, 9L, "alice");
  }

  @Test
  void deleteMovesModelToRecycleBinWithOperatorAndAudits() {
    Model existing = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
    when(repository.findById(42L)).thenReturn(Optional.of(existing));
    when(repository.deleteById(42L, "bob")).thenReturn(true);

    service.delete(42L, "bob");

    verify(repository).deleteById(42L, "bob");
    verify(audit).resource("42", "用户维度表");
    verify(audit).event(eq(AuditEventType.RESOURCE_DELETED), anyString(), any());
    verify(audit).success(anyString());
  }

  @Test
  void restoreRejectsWhenCodeTakenByLiveModel() {
    Model recycled = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
    when(repository.findDeletedById(42L)).thenReturn(Optional.of(recycled));
    when(repository.existsByCode("dim_user")).thenReturn(true);

    assertThatThrownBy(() -> service.restore(42L, "bob"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.DUPLICATE_CODE));

    verify(repository, never()).restoreById(42L, "bob");
    verify(audit).failure(ArgumentMatchers.eq("MODELING_MODEL_RESTORE_FAILED"), any());
  }

  @Test
  void restoreMovesModelBackAndAudits() {
    Model recycled = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
    when(repository.findDeletedById(42L)).thenReturn(Optional.of(recycled));
    when(repository.existsByCode("dim_user")).thenReturn(false);
    when(repository.restoreById(42L, "bob")).thenReturn(true);

    service.restore(42L, "bob");

    verify(repository).restoreById(42L, "bob");
    verify(audit).event(eq(AuditEventType.RESOURCE_UPDATED), anyString(), any());
    verify(audit).success(anyString());
  }

  @Test
  void purgeDetachesTagsAndPhysicallyDeletes() {
    Model recycled = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
    when(repository.findDeletedById(42L)).thenReturn(Optional.of(recycled));
    when(repository.purgeById(42L)).thenReturn(true);

    service.purge(42L);

    verify(tagRepository).replaceModelTags(42L, null);
    verify(repository).purgeById(42L);
    verify(audit).event(eq(AuditEventType.RESOURCE_DELETED), anyString(), any());
    verify(audit).success(anyString());
  }

  @Test
  void deleteUnknownModelFailsWithNotFound() {
    when(repository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(99L, "bob"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.NOT_FOUND));

    verify(auditService, never()).start(any(AuditOperationRequest.class));
  }

  @Test
  void assignDirectoryUpdatesAndAudits() {
    Model existing = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
    when(repository.findById(42L)).thenReturn(Optional.of(existing));
    when(directoryRepository.findById(7L))
        .thenReturn(
            Optional.of(new io.yak.ops.business.modeling.domain.ModelingDirectory(
                7L, null, "维度", null, null, null)));
    when(repository.updateDirectory(eq(42L), eq(7L), eq("carol"))).thenReturn(true);

    service.assignDirectory(42L, 7L, "carol");

    verify(repository).updateDirectory(42L, 7L, "carol");
    verify(audit).event(eq(AuditEventType.RESOURCE_UPDATED), anyString(), any());
    verify(audit).success(anyString());
  }

  @Test
  void assignTagsReplacesRelationAndAudits() {
    Model existing = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
    when(repository.findById(42L)).thenReturn(Optional.of(existing));
    when(tagRepository.findById(1L)).thenReturn(Optional.of(new io.yak.ops.business.modeling.domain.ModelingTag(1L, "核心", null)));
    when(tagRepository.findById(2L)).thenReturn(Optional.of(new io.yak.ops.business.modeling.domain.ModelingTag(2L, "交易", null)));

    service.assignTags(42L, java.util.List.of(1L, 2L));

    verify(tagRepository).replaceModelTags(42L, java.util.List.of(1L, 2L));
    verify(audit).event(eq(AuditEventType.RESOURCE_UPDATED), anyString(), any());
    verify(audit).success(anyString());
  }

  @Test
  void assignTagsRejectsUnknownTagBeforeWriting() {
    Model existing = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
    when(repository.findById(42L)).thenReturn(Optional.of(existing));
    when(tagRepository.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.assignTags(42L, java.util.List.of(1L, 9L)))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.NOT_FOUND));

    verify(tagRepository, never()).replaceModelTags(any(), anyList());
    verify(audit).failure(ArgumentMatchers.eq("MODELING_MODEL_ASSIGN_TAGS_FAILED"), any());
  }

  @Test
  void assignDirectoryRejectsUnknownDirectory() {
    Model existing = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
    when(repository.findById(42L)).thenReturn(Optional.of(existing));
    when(directoryRepository.findById(77L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.assignDirectory(42L, 77L, "carol"))
        .isInstanceOfSatisfying(
            ModelingException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ModelingErrorCode.NOT_FOUND));

    verify(repository, never()).updateDirectory(any(), any(), any());
    verify(audit).failure(ArgumentMatchers.eq("MODELING_MODEL_ASSIGN_DIRECTORY_FAILED"), any());
  }

  @Test
  void assignDirectoryAllowsUncategorized() {
    Model existing = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
    when(repository.findById(42L)).thenReturn(Optional.of(existing));
    when(repository.updateDirectory(eq(42L), eq(null), eq("carol"))).thenReturn(true);

    service.assignDirectory(42L, null, "carol");

    verify(repository).updateDirectory(42L, null, "carol");
    verify(directoryRepository, never()).findById(any());
  }

  @Test
  void restoreRehomesToUncategorizedWhenOriginalDirectoryDeleted() {
    // directoryId=7 的原目录已不存在 → 恢复后应回退未分类
    Model recycled =
        new Model(
            42L, "dim_user", "用户维度表", ModelDialect.MYSQL, null, null, "alice", null, null,
            null, null, 7L, null, "bob", null);
    when(repository.findDeletedById(42L)).thenReturn(Optional.of(recycled));
    when(repository.existsByCode("dim_user")).thenReturn(false);
    when(repository.restoreById(42L, "bob")).thenReturn(true);
    when(directoryRepository.findById(7L)).thenReturn(Optional.empty());

    service.restore(42L, "bob");

    verify(repository).updateDirectory(42L, null, "bob");
    verify(audit).success(anyString());
  }

  @Test
  void updateBasicsValidatesDialectAndAudits() {
    Model existing = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, "旧描述")
        .withPersisted(42L, "alice", null, null);
    when(repository.findById(42L)).thenReturn(Optional.of(existing));
    when(repository.updateBasics(eq(42L), eq("用户维表"), eq("POSTGRESQL"), eq("新描述"), eq("bob")))
        .thenReturn(true);

    service.updateBasics(
        42L,
        new ModelingModelApi.UpdateRequest(
            " 用户维表 ", "POSTGRESQL", " 新描述 ", null, null, null, null, null, null, null),
        "bob");

    verify(repository).updateBasics(42L, "用户维表", "POSTGRESQL", "新描述", "bob");
    // null 引用字段 = 不修改：不得触碰目录/业务域/过程/来源绑定
    verify(repository, never()).updateDirectory(anyLong(), any(), any());
    verify(repository, never()).updateDomain(anyLong(), any(), any());
    verify(repository, never()).assignProcess(anyLong(), any(), any(), any());
    verify(repository, never()).assignSource(anyLong(), any(), any(), any(), any());
    verify(audit).event(eq(AuditEventType.RESOURCE_UPDATED), anyString(), any());
    verify(audit).success(anyString());
  }

  @Test
  void updateBasicsPersistsDomainWhenProvided() {
    Model existing = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
    when(repository.findById(42L)).thenReturn(Optional.of(existing));
    when(repository.updateBasics(eq(42L), anyString(), anyString(), any(), anyString()))
        .thenReturn(true);
    io.yak.ops.business.semantic.domain.BusinessDomain domain =
        new io.yak.ops.business.semantic.domain.BusinessDomain(
            7L, "trade", "交易", 0L, null, null, 0, null, null, null);
    when(processApi.listDomains()).thenReturn(List.of(domain));
    when(directoryRepository.insert(null, "交易", 7L))
        .thenReturn(directory(9L, null, "交易"));

    service.updateBasics(
        42L,
        new ModelingModelApi.UpdateRequest(
            "用户维度表", null, null, null, null, 7L, null, null, null, null),
        "bob");

    verify(repository).updateDomain(42L, 7L, "bob");
    // 目录跟随业务域：自动创建同名目录并把模型落进去
    verify(repository).updateDirectory(42L, 9L, "bob");
  }

  @Test
  void updateBasicsReusesDomainBoundDirectory() {
    Model existing = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
    when(repository.findById(42L)).thenReturn(Optional.of(existing));
    when(repository.updateBasics(eq(42L), anyString(), anyString(), any(), anyString()))
        .thenReturn(true);
    when(processApi.listDomains())
        .thenReturn(
            List.of(
                new io.yak.ops.business.semantic.domain.BusinessDomain(
                    7L, "trade", "交易", 0L, null, null, 0, null, null, null)));
    when(directoryRepository.findByDomainId(7L))
        .thenReturn(Optional.of(directory(9L, null, "交易")));

    service.updateBasics(
        42L,
        new ModelingModelApi.UpdateRequest(
            "用户维度表", null, null, null, null, 7L, null, null, null, null),
        "bob");

    verify(directoryRepository, never()).insert(any(), anyString(), any());
    verify(repository).updateDirectory(42L, 9L, "bob");
  }

  @Test
  void updateBasicsBindsParentChainAndFollowsDomainRename() {
    Model existing = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
    when(repository.findById(42L)).thenReturn(Optional.of(existing));
    when(repository.updateBasics(eq(42L), anyString(), anyString(), any(), anyString()))
        .thenReturn(true);
    when(processApi.listDomains())
        .thenReturn(
            List.of(
                new io.yak.ops.business.semantic.domain.BusinessDomain(
                    1L, "trd", "交易域", 0L, null, null, 0, null, null, null),
                new io.yak.ops.business.semantic.domain.BusinessDomain(
                    7L, "trade", "下单", 1L, null, null, 0, null, null, null)));
    // 父域目录已存在；子域目录已绑定但域改过名
    when(directoryRepository.findByDomainId(1L)).thenReturn(Optional.of(directory(3L, null, "交易域")));
    when(directoryRepository.findByDomainId(7L)).thenReturn(Optional.of(directory(9L, 3L, "旧下单")));

    service.updateBasics(
        42L,
        new ModelingModelApi.UpdateRequest(
            "用户维度表", null, null, null, null, 7L, null, null, null, null),
        "bob");

    verify(directoryRepository).updateName(9L, "下单");
    verify(repository).updateDirectory(42L, 9L, "bob");
  }

  @Test
  void updateBasicsClearsDirectoryWhenDomainRemoved() {
    Model existing = Model.create("dim_user", "用户维度表", ModelDialect.MYSQL, null)
        .withPersisted(42L, "alice", null, null);
    when(repository.findById(42L)).thenReturn(Optional.of(existing));
    when(repository.updateBasics(eq(42L), anyString(), anyString(), any(), anyString()))
        .thenReturn(true);
    when(repository.updateDirectory(eq(42L), eq(null), eq("bob"))).thenReturn(true);

    service.updateBasics(
        42L,
        new ModelingModelApi.UpdateRequest(
            "用户维度表", null, null, null, null, 0L, null, null, null, null),
        "bob");

    verify(repository).updateDirectory(42L, null, "bob");
    verify(repository).updateDomain(42L, null, "bob");
    verify(directoryRepository, never()).insert(any(), anyString(), any());
  }

  private io.yak.ops.business.modeling.domain.ModelingDirectory directory(
      long id, Long parentId, String name) {
    return new io.yak.ops.business.modeling.domain.ModelingDirectory(id, parentId, name, null, null, 7L);
  }
}
