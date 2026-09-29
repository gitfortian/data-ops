package io.yak.ops.business.mdm.application;

import io.yak.framework.common.PageData;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import io.yak.ops.business.mdm.domain.collect.MdmCollectLink;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.source.MdmSource;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmAttributeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmCollectLinkRepository;
import io.yak.ops.business.quality.asset.QualityTableAssetCommand;
import io.yak.ops.business.quality.asset.QualityTableAssetManager;
import io.yak.ops.business.quality.asset.QualityTableAssetReader;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.TableAsset;
import io.yak.ops.business.quality.domain.QualityDomain.Template;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.execution.QualityExecutionManager;
import io.yak.ops.business.quality.execution.QualityExecutionReceipt;
import io.yak.ops.business.quality.monitor.QualityMonitorCommand;
import io.yak.ops.business.quality.monitor.QualityMonitorManager;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.business.quality.template.QualityTemplateReader;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 主数据质量复用(R3,quality 零改动):落地表自动注册 quality 表资产(四元组的
 * sink 数据源实时反查采集链路 job,与 R2 configJson 同口径),一键创建
 * 「PK 重复/PK 必填/必填属性」监控(原生 COLUMN_UNIQUE/COLUMN_NOT_NULL 模板,
 * 阈值走质量模块默认口径),执行与结果反查全部走 quality 服务,MDM 不落任何
 * 执行态(D-M11 边界不变).
 */
@Slf4j
@Component
public class MdmQualityService {

  static final String TEMPLATE_UNIQUE = "COLUMN_UNIQUE";
  static final String TEMPLATE_NOT_NULL = "COLUMN_NOT_NULL";

  private final MdmEntityService entityService;
  private final MdmSourceService sourceService;
  private final MdmAttributeRepository attributeRepository;
  private final MdmCollectLinkRepository linkRepository;
  private final MdmCollectService collectService;
  private final DataSourceReader dataSourceReader;
  private final ObjectProvider<OfflineJobDefinitionService> definitionServices;
  private final ObjectProvider<QualityTableAssetManager> assetManagers;
  private final ObjectProvider<QualityTableAssetReader> assetReaders;
  private final ObjectProvider<QualityMonitorManager> monitorManagers;
  private final ObjectProvider<QualityMonitorReader> monitorReaders;
  private final ObjectProvider<QualityTemplateReader> templateReaders;
  private final ObjectProvider<QualityExecutionManager> executionManagers;

  public MdmQualityService(
      MdmEntityService entityService,
      MdmSourceService sourceService,
      MdmAttributeRepository attributeRepository,
      MdmCollectLinkRepository linkRepository,
      MdmCollectService collectService,
      DataSourceReader dataSourceReader,
      ObjectProvider<OfflineJobDefinitionService> definitionServices,
      ObjectProvider<QualityTableAssetManager> assetManagers,
      ObjectProvider<QualityTableAssetReader> assetReaders,
      ObjectProvider<QualityMonitorManager> monitorManagers,
      ObjectProvider<QualityMonitorReader> monitorReaders,
      ObjectProvider<QualityTemplateReader> templateReaders,
      ObjectProvider<QualityExecutionManager> executionManagers) {
    this.entityService = entityService;
    this.sourceService = sourceService;
    this.attributeRepository = attributeRepository;
    this.linkRepository = linkRepository;
    this.collectService = collectService;
    this.dataSourceReader = dataSourceReader;
    this.definitionServices = definitionServices;
    this.assetManagers = assetManagers;
    this.assetReaders = assetReaders;
    this.monitorManagers = monitorManagers;
    this.monitorReaders = monitorReaders;
    this.templateReaders = templateReaders;
    this.executionManagers = executionManagers;
  }

  /** 落地表质量状态(资产注册/监控/最近检查结果均为 quality 实时反查),逐项容错. */
  public List<LandingQualityStatus> status(Long entityId) {
    List<LandingContext> contexts = landingContexts(entityId);
    List<LandingQualityStatus> result = new ArrayList<>(contexts.size());
    for (LandingContext context : contexts) {
      Monitor monitor = findMonitor(context);
      result.add(
          new LandingQualityStatus(
              context.landingTable(),
              context.sinkDatasourceId(),
              context.sinkDatasourceName(),
              context.database(),
              findAsset(context) != null,
              monitor == null ? null : monitor.id(),
              monitor == null ? null : monitor.name(),
              monitor != null && monitor.enabled(),
              monitor == null ? 0 : monitor.ruleCount(),
              monitor == null || monitor.lastResult() == null
                  ? null
                  : monitor.lastResult().name(),
              monitor == null ? null : monitor.lastExecutionNo(),
              monitor == null ? null : monitor.lastRunTime()));
    }
    return result;
  }

  /** 一键体检:注册落地表资产 → 缺监控则按模板创建 → 执行(幂等,可反复点击). */
  public List<LandingCheckReceipt> check(Long entityId, String operator) {
    QualityTableAssetManager assets = qualityDisabled(assetManagers);
    QualityMonitorManager monitors = qualityDisabled(monitorManagers);
    QualityExecutionManager executions = qualityDisabled(executionManagers);
    List<LandingContext> contexts = landingContexts(entityId);
    if (contexts.isEmpty()) {
      throw new MdmException(
          MdmErrorCode.SOURCE_NOT_LANDED, "请先在「主数据识别」页为来源生成采集落地任务");
    }
    Map<String, Long> templateIds = templateIds();
    List<LandingCheckReceipt> receipts = new ArrayList<>(contexts.size());
    for (LandingContext context : contexts) {
      receipts.add(checkOne(context, templateIds, assets, monitors, executions, operator));
    }
    return receipts;
  }

  private LandingCheckReceipt checkOne(
      LandingContext context,
      Map<String, Long> templateIds,
      QualityTableAssetManager assets,
      QualityMonitorManager monitors,
      QualityExecutionManager executions,
      String operator) {
    try {
      assets.register(
          new QualityTableAssetCommand.Register(
              context.sinkDatasourceId(),
              context.sinkDatasourceName(),
              context.database(),
              List.of(
                  new QualityTableAssetCommand.Item(
                      null, null, context.landingTable(), "TABLE", "MDM 主数据落地表"))),
          operator);
    } catch (RuntimeException exception) {
      throw new MdmException(
          MdmErrorCode.QUALITY_CHECK_FAILED,
          "落地表质量资产注册失败: " + message(exception, context.landingTable()),
          exception);
    }
    Monitor monitor = findMonitor(context);
    if (monitor == null) {
      monitor = createMonitor(context, templateIds, monitors, operator);
    }
    try {
      QualityExecutionReceipt receipt = executions.run(monitor.id(), operator);
      return new LandingCheckReceipt(context.landingTable(), monitor.id(), receipt.executionNo());
    } catch (RuntimeException exception) {
      throw new MdmException(
          MdmErrorCode.QUALITY_CHECK_FAILED,
          "质量检查执行失败: " + message(exception, context.landingTable()),
          exception);
    }
  }

  private Monitor createMonitor(
      LandingContext context,
      Map<String, Long> templateIds,
      QualityMonitorManager monitors,
      String operator) {
    Long uniqueId = templateIds.get(TEMPLATE_UNIQUE);
    Long notNullId = templateIds.get(TEMPLATE_NOT_NULL);
    if (uniqueId == null || notNullId == null) {
      throw new MdmException(
          MdmErrorCode.QUALITY_CHECK_FAILED, "数据质量内建模板缺失: COLUMN_UNIQUE/COLUMN_NOT_NULL");
    }
    List<QualityMonitorCommand.Rule> rules = new ArrayList<>();
    rules.add(
        new QualityMonitorCommand.Rule(
            uniqueId, "主键重复检查(" + context.entityCode() + ")", context.pkColumn(),
            null, null, null, List.of(), null, true));
    rules.add(
        new QualityMonitorCommand.Rule(
            notNullId, "主键必填检查(" + context.entityCode() + ")", context.pkColumn(),
            null, null, null, List.of(), null, true));
    for (String column : context.requiredNonNullColumns()) {
      if (column.equals(context.pkColumn())) {
        continue;
      }
      rules.add(
          new QualityMonitorCommand.Rule(
              notNullId, "必填检查(" + column + ")", column,
              null, null, null, List.of(), null, true));
    }
    try {
      return monitors.create(
          new QualityMonitorCommand.Save(
              "MDM落地体检-" + context.landingTable(),
              "主数据模块一键创建(quality 复用): PK 唯一 + 必填属性非空",
              context.sinkDatasourceId(),
              context.sinkDatasourceName(),
              context.database(),
              null,
              context.landingTable(),
              null,
              operator,
              true,
              new QualityMonitorCommand.Settings(
                  RunMode.MANUAL, null, null, null, null,
                  RuleFailureAction.CONTINUE, false, null, null, AlertLevel.WARNING, false),
              rules));
    } catch (RuntimeException exception) {
      throw new MdmException(
          MdmErrorCode.QUALITY_CHECK_FAILED,
          "质量监控创建失败: " + message(exception, context.landingTable()),
          exception);
    }
  }

  private Map<String, Long> templateIds() {
    QualityTemplateReader reader = qualityDisabled(templateReaders);
    List<Template> templates =
        reader.list(new QualityQuery.Template(null, null, null)).records();
    return templates.stream()
        .filter(template -> template.id() != null && template.code() != null)
        .collect(Collectors.toMap(Template::code, Template::id, (a, b) -> a));
  }

  /** 实体全部落地链路 → 质量四元组上下文(血缘登记复用同一解析). */
  List<LandingContext> landingContexts(Long entityId) {
    MdmEntity entity = entityService.get(entityId);
    List<MdmAttribute> attributes = attributeRepository.listByEntity(entityId);
    List<MdmCollectLink> links = linkRepository.listByEntity(entityId);
    if (links.isEmpty()) {
      return List.of();
    }
    MdmAttribute pk =
        attributes.stream()
            .filter(a -> a.type() == MdmAttributeType.PK)
            .findFirst()
            .orElseThrow(
                () -> new MdmException(MdmErrorCode.PK_ATTRIBUTE_MISSING, entity.code()));
    String database = collectService.businessDatabase();
    List<LandingContext> result = new ArrayList<>(links.size());
    for (MdmCollectLink link : links) {
      MdmSource source;
      try {
        source = sourceService.get(link.sourceId());
      } catch (MdmException exception) {
        log.warn("落地链路对应来源已不存在, linkId={}", link.id());
        continue;
      }
      Long sinkDatasourceId = sinkDatasourceId(link);
      String pkColumn = landingColumn(source.fieldMapping(), pk.code());
      List<String> requiredColumns =
          attributes.stream()
              .filter(a -> a.required() && a.type() != MdmAttributeType.PK)
              .map(a -> landingColumn(source.fieldMapping(), a.code()))
              .filter(column -> column.equals(pkColumn) == false)
              .distinct()
              .toList();
      result.add(
          new LandingContext(
              entity.code(),
              database,
              sinkDatasourceId,
              sinkDatasourceName(sinkDatasourceId),
              link.id(),
              source.datasourceId(),
              source.database(),
              source.schema(),
              source.table(),
              link.landingTable(),
              pkColumn,
              requiredColumns));
    }
    return result;
  }

  private Long sinkDatasourceId(MdmCollectLink link) {
    OfflineJobDefinitionService definitions = definitionServices.getIfAvailable();
    if (definitions == null) {
      throw new MdmException(
          MdmErrorCode.QUALITY_CHECK_FAILED, "数据集成(离线同步)模块未启用,无法反查落地目标数据源");
    }
    Long sinkId = null;
    try {
      var definition = definitions.get(link.jobDefinitionId());
      sinkId = definition == null ? null : definition.getSinkDatasourceId();
    } catch (RuntimeException exception) {
      log.warn("落地任务 sink 数据源反查失败, jobDefinitionId={}", link.jobDefinitionId(), exception);
    }
    if (sinkId == null || sinkId <= 0) {
      throw new MdmException(
          MdmErrorCode.QUALITY_CHECK_FAILED,
          "无法反查落地目标数据源(数据集成任务可能已删除), jobDefinitionId=" + link.jobDefinitionId());
    }
    return sinkId;
  }

  private String sinkDatasourceName(Long datasourceId) {
    try {
      DataSourceDefinition definition = dataSourceReader.require(datasourceId);
      if (definition != null && StringUtils.hasText(definition.getName())) {
        return definition.getName();
      }
    } catch (RuntimeException exception) {
      log.warn("数据源名称解析失败, datasourceId={}", datasourceId, exception);
    }
    return String.valueOf(datasourceId);
  }

  private Monitor findMonitor(LandingContext context) {
    QualityMonitorReader reader = monitorReaders.getIfAvailable();
    if (reader == null) {
      return null;
    }
    try {
      PageData<Monitor> page =
          reader.page(
              new QualityQuery.Monitor(
                  1, 50, context.landingTable(), context.sinkDatasourceId(),
                  context.database(), true, null, false, null, null, null));
      List<Monitor> records = page == null ? null : page.records();
      if (records == null) {
        return null;
      }
      return records.stream()
          .filter(
              monitor ->
                  context.landingTable().equalsIgnoreCase(monitor.tableName())
                      && context.sinkDatasourceId().equals(monitor.dataSourceId()))
          .findFirst()
          .orElse(null);
    } catch (RuntimeException exception) {
      log.warn("质量监控反查失败, landingTable={}", context.landingTable(), exception);
      return null;
    }
  }

  private TableAsset findAsset(LandingContext context) {
    QualityTableAssetReader reader = assetReaders.getIfAvailable();
    if (reader == null) {
      return null;
    }
    try {
      PageData<TableAsset> page =
          reader.page(
              new QualityQuery.TableAsset(
                  1, 50, context.sinkDatasourceId(), context.database(), true,
                  null, false, context.landingTable()));
      List<TableAsset> records = page == null ? null : page.records();
      if (records == null) {
        return null;
      }
      return records.stream()
          .filter(
              asset ->
                  context.landingTable().equalsIgnoreCase(asset.tableName())
                      && context.sinkDatasourceId().equals(asset.dataSourceId()))
          .findFirst()
          .orElse(null);
    } catch (RuntimeException exception) {
      log.warn("质量表资产反查失败, landingTable={}", context.landingTable(), exception);
      return null;
    }
  }

  /** 属性编码对应落地列:field_mapping 优先,未配置回退同名(落地表按源列名建列). */
  static String landingColumn(Map<String, String> fieldMapping, String attributeCode) {
    String column = fieldMapping == null ? null : fieldMapping.get(attributeCode);
    return StringUtils.hasText(column) ? column : attributeCode;
  }

  private static String message(RuntimeException exception, String fallback) {
    return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : fallback;
  }

  private static <T> T qualityDisabled(ObjectProvider<T> provider) {
    T service = provider.getIfAvailable();
    if (service == null) {
      throw new MdmException(MdmErrorCode.QUALITY_MODULE_DISABLED, "");
    }
    return service;
  }

  /** 单落地表体检回执. */
  public record LandingCheckReceipt(String landingTable, Long monitorId, String executionNo) {}

  /** 落地表质量状态(实时反查,不落库). */
  public record LandingQualityStatus(
      String landingTable,
      Long datasourceId,
      String datasourceName,
      String database,
      boolean assetRegistered,
      Long monitorId,
      String monitorName,
      boolean monitorEnabled,
      int ruleCount,
      String lastResult,
      String lastExecutionNo,
      LocalDateTime lastRunTime) {}

  record LandingContext(
      String entityCode,
      String database,
      Long sinkDatasourceId,
      String sinkDatasourceName,
      Long linkId,
      Long sourceDatasourceId,
      String sourceDatabase,
      String sourceSchema,
      String sourceTable,
      String landingTable,
      String pkColumn,
      List<String> requiredNonNullColumns) {}
}
