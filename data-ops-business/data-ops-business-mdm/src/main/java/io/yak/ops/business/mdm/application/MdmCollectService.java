package io.yak.ops.business.mdm.application;

import io.yak.framework.common.PagingData;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.mdm.collect.LandingDdl;
import io.yak.ops.business.mdm.collect.LandingJobFactory;
import io.yak.ops.business.mdm.domain.collect.MdmCollectLink;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.source.MdmSource;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmCollectLinkRepository;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionQueryDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobExecutionQueryDTO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobDefinitionVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionVO;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.execution.OfflineJobExecutionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 采集落地链路编排(R1,review P0-1.7):已确认来源 → 平台库预建落地表 → 复用
 * 数据集成(saveGuide/execute,零改动)注册离线全量落地任务 → 存反查锚点
 * {@code yak_mdm_collect_link};采集状态按 jobDefinitionId 实时向 sync 反查,
 * MDM 不冗余执行态(D-M10/D-M11 边界不变).
 */
@Slf4j
@Component
public class MdmCollectService {

  private final MdmCollectLinkRepository linkRepository;
  private final MdmSourceService sourceService;
  private final MdmEntityService entityService;
  private final DataSourceCatalogReader catalogReader;
  private final ObjectProvider<OfflineJobDefinitionService> definitionServices;
  private final ObjectProvider<OfflineJobExecutionService> executionServices;
  private final JdbcTemplate businessJdbc;

  public MdmCollectService(
      MdmCollectLinkRepository linkRepository,
      MdmSourceService sourceService,
      MdmEntityService entityService,
      DataSourceCatalogReader catalogReader,
      ObjectProvider<OfflineJobDefinitionService> definitionServices,
      ObjectProvider<OfflineJobExecutionService> executionServices,
      @Qualifier("yakBusinessDataSource") DataSource businessDataSource) {
    this.linkRepository = linkRepository;
    this.sourceService = sourceService;
    this.entityService = entityService;
    this.catalogReader = catalogReader;
    this.definitionServices = definitionServices;
    this.executionServices = executionServices;
    this.businessJdbc = new JdbcTemplate(businessDataSource);
  }

  /**
   * 一键生成落地任务(幂等:同来源已有链路直接返回)。步骤:读源表列结构 →
   * 平台库预建落地表 → saveGuide 注册 GUIDE_SINGLE 全量任务 → 落 link。
   */
  public MdmCollectLink generateLandingTask(Long sourceId, Long sinkDatasourceId, String operator) {
    Optional<MdmCollectLink> existing = linkRepository.findBySourceId(sourceId);
    if (existing.isPresent()) {
      return existing.get();
    }
    MdmSource source = sourceService.get(sourceId);
    MdmEntity entity = entityService.get(source.entityId());
    if (sinkDatasourceId == null || sinkDatasourceId <= 0) {
      throw new MdmException(MdmErrorCode.LANDING_TASK_CREATE_FAILED, "请选择落地目标数据源");
    }
    List<CatalogColumn> columns = readSourceColumns(source);
    String landingTable = landingTable(entity, sourceId);
    String businessDb = businessDatabase();
    preCreateLandingTable(businessDb, landingTable, columns);

    String jobName = jobName(entity, sourceId);
    String sourceQualified =
        LandingJobFactory.qualifiedName(source.database(), source.schema(), source.table());
    String sinkQualified = LandingJobFactory.qualifiedName(businessDb, null, landingTable);
    Long definitionId =
        saveGuide(
            existingDefinitionId(jobName), jobName, source.datasourceId(), sourceQualified,
            sinkDatasourceId, sinkQualified, columns);
    return linkRepository.insert(
        new MdmCollectLink(
            null, entity.id(), sourceId, source.datasourceId(), landingTable, definitionId,
            operator, null, null),
        operator);
  }

  /** 手动触发一次落地运行(saveGuide 后任务为 OFFLINE,需先上线再执行;异步)。 */
  public OfflineJobExecutionVO run(Long sourceId) {
    MdmCollectLink link = requiredLink(sourceId);
    OfflineJobDefinitionService definitions = orSyncDisabled(definitionServices);
    OfflineJobExecutionService executions = orSyncDisabled(executionServices);
    OfflineJobDefinitionVO definition = definitions.get(link.jobDefinitionId());
    if (definition == null) {
      throw new MdmException(
          MdmErrorCode.LANDING_TASK_CREATE_FAILED, "落地任务定义已不存在,请删除链路后重新生成");
    }
    if (!"ONLINE".equalsIgnoreCase(definition.getReleaseState())) {
      definitions.online(link.jobDefinitionId());
    }
    try {
      return executions.execute(link.jobDefinitionId());
    } catch (RuntimeException exception) {
      if (exception instanceof MdmException mdmException) {
        throw mdmException;
      }
      // 如"已有运行中的 BatchExecution,不能重复提交""无法连接 Link-Up Server"等,原样透出
      throw new MdmException(
          MdmErrorCode.LANDING_TASK_CREATE_FAILED,
          StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : "触发落地运行失败",
          exception);
    }
  }

  /** 按实体(或全量)列采集链路状态:任务状态 + 最近一次 SUCCEEDED 运行反查。 */
  public List<CollectStatus> listStatus(Long entityId) {
    List<MdmCollectLink> links =
        entityId == null ? linkRepository.listAll() : linkRepository.listByEntity(entityId);
    List<CollectStatus> result = new ArrayList<>(links.size());
    for (MdmCollectLink link : links) {
      result.add(statusOf(link));
    }
    return result;
  }

  public Optional<MdmCollectLink> findLink(Long sourceId) {
    return linkRepository.findBySourceId(sourceId);
  }

  private CollectStatus statusOf(MdmCollectLink link) {
    String jobName = null;
    String releaseState = null;
    String lastJobStatus = null;
    OfflineJobDefinitionService definitions = definitionServices.getIfAvailable();
    if (definitions != null) {
      try {
        OfflineJobDefinitionVO definition = definitions.get(link.jobDefinitionId());
        if (definition != null) {
          jobName = definition.getJobName();
          releaseState = definition.getReleaseState();
          lastJobStatus = definition.getLastJobStatus();
        }
      } catch (RuntimeException exception) {
        log.warn("落地任务定义反查失败, jobDefinitionId={}", link.jobDefinitionId(), exception);
      }
    }
    OfflineJobExecutionVO lastSuccess = lastSucceeded(link.jobDefinitionId());
    String sourceTable = null;
    try {
      sourceTable = sourceService.get(link.sourceId()).table();
    } catch (MdmException exception) {
      log.warn("采集链路对应来源已不存在, linkId={} sourceId={}", link.id(), link.sourceId());
    }
    return new CollectStatus(
        link.id(),
        link.entityId(),
        link.sourceId(),
        sourceTable,
        link.datasourceId(),
        link.landingTable(),
        link.jobDefinitionId(),
        jobName,
        releaseState,
        lastJobStatus,
        lastSuccess == null ? null : lastSuccess.getEndTime(),
        lastSuccess == null ? null : lastSuccess.getSinkCommittedRecordCount(),
        link.createTime());
  }

  /** 最近一次成功运行:sync 实例分页按 id 倒序,status=SUCCEEDED,pageSize=1。 */
  private OfflineJobExecutionVO lastSucceeded(Long jobDefinitionId) {
    OfflineJobExecutionService executions = executionServices.getIfAvailable();
    if (executions == null) {
      return null;
    }
    try {
      OfflineJobExecutionQueryDTO query = new OfflineJobExecutionQueryDTO();
      query.setCurrent(1);
      query.setPageSize(1);
      query.setJobDefinitionId(jobDefinitionId);
      query.setStatus("SUCCEEDED");
      PagingData<OfflineJobExecutionVO> page = executions.page(query);
      List<OfflineJobExecutionVO> records = page == null ? List.of() : page.getBizData();
      return records == null || records.isEmpty() ? null : records.get(0);
    } catch (RuntimeException exception) {
      log.warn("最近成功采集反查失败, jobDefinitionId={}", jobDefinitionId, exception);
      return null;
    }
  }

  private List<CatalogColumn> readSourceColumns(MdmSource source) {
    List<CatalogColumn> columns;
    try {
      columns =
          catalogReader.listColumns(
              source.datasourceId(), source.database(), source.schema(), source.table());
    } catch (RuntimeException exception) {
      throw new MdmException(
          MdmErrorCode.LANDING_TASK_CREATE_FAILED,
          "读取源表列结构失败: " + source.table(),
          exception);
    }
    if (columns == null || columns.isEmpty()) {
      throw new MdmException(
          MdmErrorCode.LANDING_TASK_CREATE_FAILED, "源表无列结构: " + source.table());
    }
    for (CatalogColumn column : columns) {
      if (!LandingDdl.isSafeIdentifier(column.name())) {
        throw new MdmException(
            MdmErrorCode.LANDING_TASK_CREATE_FAILED,
            "源列名不支持自动落地(仅字母数字下划线): " + column.name());
      }
    }
    return columns;
  }

  void preCreateLandingTable(String database, String table, List<CatalogColumn> columns) {
    try {
      businessJdbc.execute(LandingDdl.createTable(database, table, columns));
    } catch (RuntimeException exception) {
      throw new MdmException(
          MdmErrorCode.LANDING_TASK_CREATE_FAILED, "落地表预建失败: " + table, exception);
    }
  }

  public String businessDatabase() {
    String database = businessJdbc.queryForObject("SELECT DATABASE()", String.class);
    if (!LandingDdl.isSafeIdentifier(database)) {
      throw new MdmException(
          MdmErrorCode.LANDING_TASK_CREATE_FAILED, "无法确定平台业务库名: " + database);
    }
    return database;
  }

  /** 崩溃自愈:link 未落库但任务定义重名时,复用既有定义(更新路径)。 */
  private Long existingDefinitionId(String jobName) {
    OfflineJobDefinitionService definitions = definitionServices.getIfAvailable();
    if (definitions == null) {
      throw syncDisabled();
    }
    try {
      OfflineJobDefinitionQueryDTO query = new OfflineJobDefinitionQueryDTO();
      query.setCurrent(1);
      query.setPageSize(10);
      query.setJobName(jobName);
      PagingData<OfflineJobDefinitionVO> page = definitions.page(query);
      List<OfflineJobDefinitionVO> records = page == null ? List.of() : page.getBizData();
      if (records == null) {
        return null;
      }
      return records.stream()
          .filter(vo -> jobName.equals(vo.getJobName()))
          .map(OfflineJobDefinitionVO::getId)
          .findFirst()
          .orElse(null);
    } catch (RuntimeException exception) {
      log.warn("按名称查找既有落地任务失败,按新建处理, jobName={}", jobName, exception);
      return null;
    }
  }

  private Long saveGuide(
      Long definitionId,
      String jobName,
      Long sourceDatasourceId,
      String sourceQualified,
      Long sinkDatasourceId,
      String sinkQualified,
      List<CatalogColumn> columns) {
    OfflineJobDefinitionService definitions = definitionServices.getIfAvailable();
    if (definitions == null) {
      throw syncDisabled();
    }
    OfflineJobDefinitionDTO dto =
        LandingJobFactory.build(
            definitionId, jobName, sourceDatasourceId, sourceQualified, sinkDatasourceId,
            sinkQualified, columns);
    try {
      return definitions.saveGuide(dto);
    } catch (RuntimeException exception) {
      throw new MdmException(
          MdmErrorCode.LANDING_TASK_CREATE_FAILED,
          StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : jobName,
          exception);
    }
  }

  private static <T> T orSyncDisabled(ObjectProvider<T> provider) {
    T service = provider.getIfAvailable();
    if (service == null) {
      throw new MdmException(
          MdmErrorCode.LANDING_TASK_CREATE_FAILED, "数据集成(离线同步)模块未启用");
    }
    return service;
  }

  private MdmException syncDisabled() {
    return new MdmException(MdmErrorCode.LANDING_TASK_CREATE_FAILED, "数据集成(离线同步)模块未启用");
  }

  private MdmCollectLink requiredLink(Long sourceId) {
    return linkRepository
        .findBySourceId(sourceId)
        .orElseThrow(() -> new MdmException(MdmErrorCode.COLLECT_LINK_NOT_FOUND, "sourceId=" + sourceId));
  }

  /** 落地表名:mdm_landing_{实体编码}_{来源ID};实体编码不安全时回退 mdm_landing_s{来源ID}。 */
  static String landingTable(MdmEntity entity, Long sourceId) {
    String code = entity.code();
    if (LandingDdl.isSafeIdentifier(code)) {
      return "mdm_landing_" + code + "_" + sourceId;
    }
    return "mdm_landing_s" + sourceId;
  }

  /** 任务名与落地表同源规则,保证 project 内唯一且可反查(崩溃自愈)。 */
  static String jobName(MdmEntity entity, Long sourceId) {
    String code = entity.code();
    if (LandingDdl.isSafeIdentifier(code)) {
      return "mdm_collect_" + code + "_" + sourceId;
    }
    return "mdm_collect_s" + sourceId;
  }

  /** 采集链路状态(实时反查,不冗余落库)。 */
  public record CollectStatus(
      Long linkId,
      Long entityId,
      Long sourceId,
      String sourceTable,
      Long datasourceId,
      String landingTable,
      Long jobDefinitionId,
      String jobName,
      String releaseState,
      String lastJobStatus,
      String lastSuccessTime,
      Long lastSuccessRows,
      java.time.LocalDateTime linkCreateTime) {}
}
