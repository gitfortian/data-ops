package io.yak.ops.business.mdm.application;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogTable;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.source.MdmSource;
import io.yak.ops.business.mdm.domain.source.MdmSourceRole;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.identification.CandidateMatcher;
import io.yak.ops.business.mdm.identification.CandidateMatcher.CandidateHint;
import io.yak.ops.business.mdm.infrastructure.repository.MdmCollectLinkRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmEntityRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSourceRepository;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the master data identification rules: datasource table scan (reusing the
 * datasource public contract), candidate matching against project entities,
 * confirm into {@code mdm_source}, and unbind. Delete reference checks for
 * collection config (54) arrive with its owning ticket (empty hook today).
 */
@Component
public class MdmSourceService {

  /** 与 DedupSql/MdmMasterSqlGenerator 同口径的安全列名. */
  private static final Pattern SAFE_COLUMN = Pattern.compile("[A-Za-z0-9_]{1,64}");

  private final MdmSourceRepository repository;
  private final MdmEntityRepository entityRepository;
  private final MdmEntityService entityService;
  private final MdmCollectLinkRepository collectLinkRepository;
  private final DataSourceCatalogReader catalogReader;
  private final BusinessAuditService auditService;

  public MdmSourceService(
      MdmSourceRepository repository,
      MdmEntityRepository entityRepository,
      MdmEntityService entityService,
      MdmCollectLinkRepository collectLinkRepository,
      DataSourceCatalogReader catalogReader,
      BusinessAuditService auditService) {
    this.repository = repository;
    this.entityRepository = entityRepository;
    this.entityService = entityService;
    this.collectLinkRepository = collectLinkRepository;
    this.catalogReader = catalogReader;
    this.auditService = auditService;
  }

  public List<MdmSource> listSources(Long entityId) {
    return entityId == null ? repository.listAll() : repository.listByEntity(entityId);
  }

  /** 扫描数据源表清单并标注候选/已确认;数据源不可用时给出明确错误。 */
  public List<TableCandidate> scan(Long datasourceId, String keyword, Integer limit) {
    final List<CatalogTable> tables;
    try {
      tables = catalogReader.searchTables(datasourceId, null, null, keyword, limit);
    } catch (RuntimeException exception) {
      throw new MdmException(
          MdmErrorCode.DATASOURCE_SCAN_FAILED,
          exception.getMessage() == null ? String.valueOf(datasourceId) : exception.getMessage(),
          exception);
    }
    List<MdmEntity> entities = entityRepository.findAll();
    Set<TableKey> boundKeys =
        repository.listByDatasource(datasourceId).stream()
            .map(source -> new TableKey(source.datasourceId(), source.database(), source.schema(), source.table()))
            .collect(Collectors.toSet());
    return tables.stream()
        .map(
            table ->
                new TableCandidate(
                    table.database(),
                    table.schema(),
                    table.name(),
                    table.type(),
                    table.remarks(),
                    CandidateMatcher.match(table.name(), entities),
                    boundKeys.contains(
                        new TableKey(datasourceId, table.database(), table.schema(), table.name()))))
        .toList();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MdmSource confirm(
      Long entityId,
      Long datasourceId,
      String database,
      String schema,
      String table,
      MdmSourceRole role,
      Map<String, String> fieldMapping,
      String operator) {
    entityService.get(entityId);
    MdmSourceRole resolvedRole = role == null ? MdmSourceRole.MAIN : role;
    validateTableExists(datasourceId, database, schema, table);
    validateFieldMapping(fieldMapping);
    if (repository.exists(entityId, datasourceId, database, schema, table)) {
      throw new MdmException(MdmErrorCode.DUPLICATE_SOURCE, table);
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_SOURCE_CONFIRM",
                "Confirm master data source",
                "MDM_SOURCE",
                null,
                table,
                "APPLICATION",
                Map.of("entityId", String.valueOf(entityId), "role", resolvedRole.name())));
    try {
      MdmSource inserted =
          repository.insert(
              new MdmSource(
                  null,
                  entityId,
                  datasourceId,
                  database,
                  schema,
                  table,
                  fieldMapping,
                  resolvedRole,
                  MdmSource.STATUS_ENABLED,
                  0,
                  operator,
                  null,
                  null),
              operator);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_CREATED,
          "Master data source confirmed",
          Map.of(),
          "Master data source confirmed");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("MDM_SOURCE_CONFIRM_FAILED", exception);
      throw exception;
    }
  }

  public MdmSource get(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new MdmException(MdmErrorCode.SOURCE_NOT_FOUND, String.valueOf(id)));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void unbind(Long id) {
    MdmSource existing = get(id);
    // R1 引用校验:已生成采集落地链路的来源禁止解绑(落地表/任务定义保留在数据集成侧)。
    if (isReferenced(existing.id())) {
      throw new MdmException(MdmErrorCode.SOURCE_REFERENCED, existing.table());
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_SOURCE_UNBIND",
                "Unbind master data source",
                "MDM_SOURCE",
                String.valueOf(id),
                existing.table(),
                "APPLICATION",
                Map.of()));
    try {
      if (!repository.deleteById(id)) {
        throw new MdmException(MdmErrorCode.DELETE_FAILED, String.valueOf(id));
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_DELETED,
          "Master data source unbound",
          Map.of(),
          "Master data source unbound");
    } catch (RuntimeException exception) {
      audit.failure("MDM_SOURCE_UNBIND_FAILED", exception);
      throw exception;
    }
  }

  /** 引用校验:存在采集落地链路(yak_mdm_collect_link)即阻断解绑。 */
  private boolean isReferenced(Long sourceId) {
    return collectLinkRepository.findBySourceId(sourceId).isPresent();
  }

  /** field_mapping 口径校验:键=属性编码非空,值=源列名需为安全标识符(防注入)。 */
  private static void validateFieldMapping(Map<String, String> fieldMapping) {
    if (fieldMapping == null || fieldMapping.isEmpty()) {
      return;
    }
    for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        throw new MdmException(MdmErrorCode.INVALID_FIELD_MAPPING, "映射的属性编码不能为空");
      }
      if (entry.getValue() == null || !SAFE_COLUMN.matcher(entry.getValue()).matches()) {
        throw new MdmException(
            MdmErrorCode.INVALID_FIELD_MAPPING,
            entry.getKey() + " → " + entry.getValue());
      }
    }
  }

  private void validateTableExists(Long datasourceId, String database, String schema, String table) {
    try {
      catalogReader.listColumns(datasourceId, database, schema, table);
    } catch (RuntimeException exception) {
      throw new MdmException(
          MdmErrorCode.DATASOURCE_SCAN_FAILED,
          "源表不存在或数据源不可用: " + table,
          exception);
    }
  }

  /** 扫描结果:表 + 候选实体 + 是否已绑定。 */
  public record TableCandidate(
      String database,
      String schema,
      String name,
      String type,
      String remarks,
      List<CandidateHint> candidates,
      boolean confirmed) {}

  private record TableKey(Long datasourceId, String database, String schema, String table) {}
}
