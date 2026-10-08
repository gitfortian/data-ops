package io.yak.ops.business.semantic.domain;

import io.yak.ops.business.semantic.api.BusinessDomain;
import io.yak.ops.business.semantic.api.SemanticStructureReferenceReader;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.business.semantic.repository.SemanticDomainRepository;
import io.yak.ops.business.semantic.repository.SemanticProcessRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns the business-domain tree rules: code format/uniqueness, delete
 * blocking on children (business-process checks arrive with 34), and cycle
 * prevention when moving nodes. Tree assembly is also a service duty so the
 * repositories stay flat.
 */
@Component
public class BusinessDomainService {

  private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

  private final SemanticDomainRepository repository;
  private final SemanticProcessRepository processRepository;
  private final BusinessAuditService auditService;
  private final List<SemanticStructureReferenceReader> referenceReaders;

  @Autowired
  public BusinessDomainService(
      SemanticDomainRepository repository,
      SemanticProcessRepository processRepository,
      BusinessAuditService auditService,
      ObjectProvider<SemanticStructureReferenceReader> referenceReaders) {
    this(repository, processRepository, auditService,
        referenceReaders.orderedStream().toList());
  }

  /** Compatibility constructor for existing focused service tests. */
  public BusinessDomainService(
      SemanticDomainRepository repository,
      SemanticProcessRepository processRepository,
      BusinessAuditService auditService) {
    this(repository, processRepository, auditService, List.of());
  }

  BusinessDomainService(
      SemanticDomainRepository repository,
      SemanticProcessRepository processRepository,
      BusinessAuditService auditService,
      List<SemanticStructureReferenceReader> referenceReaders) {
    this.repository = repository;
    this.processRepository = processRepository;
    this.auditService = auditService;
    this.referenceReaders = List.copyOf(referenceReaders);
  }

  /** 全量业务域树(空父集装配为根;排序已在仓库层保证)。 */
  public List<DomainNode> tree() {
    List<BusinessDomain> all = repository.findAll();
    Map<Long, List<DomainNode>> childrenByParent = new HashMap<>();
    for (BusinessDomain domain : all) {
      childrenByParent
          .computeIfAbsent(domain.parentId(), key -> new ArrayList<>())
          .add(new DomainNode(domain.id(), domain.code(), domain.name(), domain.owner(),
              domain.description(), domain.sortOrder(), new ArrayList<>()));
    }
    return assembleChildren(BusinessDomain.ROOT_PARENT_ID, childrenByParent);
  }

  private List<DomainNode> assembleChildren(
      Long parentId, Map<Long, List<DomainNode>> childrenByParent) {
    List<DomainNode> nodes = childrenByParent.getOrDefault(parentId, List.of());
    for (DomainNode node : nodes) {
      node.children().addAll(assembleChildren(node.id(), childrenByParent));
    }
    return nodes;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public BusinessDomain create(
      Long parentId, String code, String name, String owner, String description, Integer sortOrder,
      String operator) {
    validateCode(code);
    validateName(name);
    Long resolvedParent = parentId == null ? BusinessDomain.ROOT_PARENT_ID : parentId;
    if (resolvedParent != BusinessDomain.ROOT_PARENT_ID) {
      repository
          .findById(resolvedParent)
          .orElseThrow(
              () -> new SemanticException(SemanticErrorCode.NOT_FOUND, "父业务域不存在"));
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_DOMAIN_CREATE",
                "Create business domain",
                "SEMANTIC_DOMAIN",
                null,
                code,
                "APPLICATION",
                Map.of()));
    try {
      if (repository.existsByCode(code)) {
        throw new SemanticException(SemanticErrorCode.DUPLICATE_CODE, code);
      }
      BusinessDomain inserted =
          repository.insert(
              new BusinessDomain(null, code, name, resolvedParent, owner, description,
                  sortOrder == null ? 0 : sortOrder, operator, null, null),
              operator);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Business domain created",
          Map.of(),
          "Business domain created");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_DOMAIN_CREATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void update(Long id, String name, String owner, String description, Integer sortOrder) {
    BusinessDomain existing = get(id);
    validateName(name);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_DOMAIN_UPDATE",
                "Update business domain",
                "SEMANTIC_DOMAIN",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of()));
    try {
      repository.update(
          new BusinessDomain(existing.id(), existing.code(), name, existing.parentId(), owner,
              description, sortOrder == null ? existing.sortOrder() : sortOrder,
              existing.createdBy(), existing.createTime(), existing.updateTime()));
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Business domain updated",
          Map.of(),
          "Business domain updated");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_DOMAIN_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  /** 拖拽改父/排序:目标父不能是自己或自己的子孙(环检测)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void move(Long id, Long targetParentId, Integer sortOrder, String operator) {
    repository.lockTree();
    BusinessDomain existing = get(id);
    Long resolvedParent = targetParentId == null ? BusinessDomain.ROOT_PARENT_ID : targetParentId;
    if (resolvedParent.equals(existing.id())) {
      throw new SemanticException(SemanticErrorCode.INVALID_MOVE, "不能移动到自身之下");
    }
    if (!resolvedParent.equals(BusinessDomain.ROOT_PARENT_ID)) {
      repository.findById(resolvedParent)
          .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, "父业务域不存在"));
    }
    if (!resolvedParent.equals(BusinessDomain.ROOT_PARENT_ID)
        && isDescendant(resolvedParent, existing.id())) {
      throw new SemanticException(SemanticErrorCode.INVALID_MOVE, "不能移动到自己的子孙节点之下");
    }
    int resolvedSortOrder = sortOrder == null ? existing.sortOrder() : sortOrder;
    if (resolvedParent.equals(existing.parentId()) && resolvedSortOrder == existing.sortOrder()) return;
    AuditOperationHandle audit = auditService.start(
        new AuditOperationRequest(
            "SEMANTIC_DOMAIN_MOVE", "Move business domain", "SEMANTIC_DOMAIN",
            String.valueOf(id), existing.code(), "APPLICATION",
            Map.of("fromParentId", String.valueOf(existing.parentId()),
                "toParentId", String.valueOf(resolvedParent),
                "fromSortOrder", String.valueOf(existing.sortOrder()),
                "toSortOrder", String.valueOf(resolvedSortOrder))));
    try {
      if (!repository.move(id, resolvedParent, resolvedSortOrder)) {
        throw new SemanticException(SemanticErrorCode.NOT_FOUND, String.valueOf(id));
      }
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED, "Business domain moved",
          Map.of("parentId", String.valueOf(resolvedParent), "sortOrder", String.valueOf(resolvedSortOrder)),
          "Business domain moved");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_DOMAIN_MOVE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    BusinessDomain existing = get(id);
    // 引用校验:子域(33)+业务过程(34)。
    if (repository.existsByParent(id)) {
      throw new SemanticException(SemanticErrorCode.DOMAIN_REFERENCED, "存在子业务域");
    }
    if (processRepository.existsByDomain(id)) {
      throw new SemanticException(SemanticErrorCode.DOMAIN_REFERENCED, "存在业务过程引用");
    }
    // Modeling owns model.domainId; include recoverable models in the count.
    for (SemanticStructureReferenceReader reader : referenceReaders) {
      if (reader.countDomainReferences(id) > 0) {
        throw new SemanticException(SemanticErrorCode.DOMAIN_REFERENCED,
            "存在建模或其它消费模块引用，请先解除关联");
      }
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "SEMANTIC_DOMAIN_DELETE",
                "Delete business domain",
                "SEMANTIC_DOMAIN",
                String.valueOf(id),
                existing.code(),
                "APPLICATION",
                Map.of()));
    try {
      if (!repository.deleteById(id)) {
        throw new SemanticException(SemanticErrorCode.DELETE_FAILED, String.valueOf(id));
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_DELETED,
          "Business domain deleted",
          Map.of(),
          "Business domain deleted");
    } catch (RuntimeException exception) {
      audit.failure("SEMANTIC_DOMAIN_DELETE_FAILED", exception);
      throw exception;
    }
  }

  public BusinessDomain get(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new SemanticException(SemanticErrorCode.NOT_FOUND, String.valueOf(id)));
  }

  private boolean isDescendant(Long candidateId, Long ancestorId) {
    List<BusinessDomain> all = repository.findAll();
    Map<Long, Long> parentById = new HashMap<>();
    for (BusinessDomain domain : all) {
      parentById.put(domain.id(), domain.parentId());
    }
    Long cursor = candidateId;
    Set<Long> visited = new HashSet<>();
    while (cursor != null && !cursor.equals(BusinessDomain.ROOT_PARENT_ID)) {
      if (!visited.add(cursor)) {
        throw new SemanticException(SemanticErrorCode.INVALID_MOVE, "现有业务域存在环，请先修复层级");
      }
      if (cursor.equals(ancestorId)) {
        return true;
      }
      cursor = parentById.get(cursor);
    }
    return false;
  }

  private static void validateCode(String code) {
    if (!StringUtils.hasText(code) || !CODE_PATTERN.matcher(code).matches()) {
      throw new SemanticException(SemanticErrorCode.INVALID_CODE, code);
    }
  }

  private static void validateName(String name) {
    if (!StringUtils.hasText(name)) {
      throw new SemanticException(SemanticErrorCode.INVALID_NAME, "业务域名称不能为空");
    }
  }

  /** 树节点视图(域内定义,controller 直接复用)。 */
  public record DomainNode(
      Long id,
      String code,
      String name,
      String owner,
      String description,
      int sortOrder,
      List<DomainNode> children) {}
}
