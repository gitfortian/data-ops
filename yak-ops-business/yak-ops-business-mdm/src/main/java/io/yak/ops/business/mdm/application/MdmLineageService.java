package io.yak.ops.business.mdm.application;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.registration.LineageRegistrationService;
import io.yak.ops.business.lineage.registration.LineageRegistrationService.RegisterAssetCommand;
import io.yak.ops.business.lineage.registration.LineageRegistrationService.RegisterRelationCommand;
import io.yak.ops.business.mdm.application.MdmQualityService.LandingContext;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import io.yak.ops.common.util.metadata.PhysicalTableAssetKey;
import io.yak.ops.core.project.CurrentProject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 主数据血缘登记(R3,lineage 零改动):登记三段物理表血缘
 * 源表 → 落地表 → {@code yak_mdm_record},资产键与元数据/开发血缘共用
 * {@link PhysicalTableAssetKey} 口径(逐字一致,避免同一表裂成两个节点);
 * sourceType 统一 MDM,证据 ID 挂采集链路,可反复同步(幂等 upsert).
 */
@Slf4j
@Component
public class MdmLineageService {

  static final String SOURCE_TYPE = "MDM";
  static final String RECORD_TABLE = "yak_mdm_record";

  private final MdmQualityService qualityService;
  private final CurrentProject currentProject;
  private final ObjectProvider<LineageRegistrationService> registrationServices;

  public MdmLineageService(
      MdmQualityService qualityService,
      CurrentProject currentProject,
      ObjectProvider<LineageRegistrationService> registrationServices) {
    this.qualityService = qualityService;
    this.currentProject = currentProject;
    this.registrationServices = registrationServices;
  }

  /** 同步实体三段血缘(登记 N 资产 + 2N 关系),返回计数供前端提示. */
  public LineageSyncReceipt sync(Long entityId) {
    LineageRegistrationService registration = registrationServices.getIfAvailable();
    if (registration == null) {
      throw new MdmException(MdmErrorCode.LINEAGE_REGISTER_FAILED, "数据血缘模块未启用");
    }
    List<LandingContext> contexts = qualityService.landingContexts(entityId);
    if (contexts.isEmpty()) {
      throw new MdmException(
          MdmErrorCode.SOURCE_NOT_LANDED, "请先在「主数据识别」页为来源生成采集落地任务");
    }
    Long projectId = currentProject.requireProjectId();
    String recordKey =
        tableKey(contexts.get(0).sinkDatasourceId(), contexts.get(0).database(), null, RECORD_TABLE);

    Map<String, RegisterAssetCommand> assets = new LinkedHashMap<>();
    for (LandingContext context : contexts) {
      String sourceKey =
          tableKey(
              context.sourceDatasourceId(),
              context.sourceDatabase(),
              context.sourceSchema(),
              context.sourceTable());
      String landingKey =
          tableKey(context.sinkDatasourceId(), context.database(), null, context.landingTable());
      putAsset(
          assets,
          new RegisterAssetCommand(
              sourceKey,
              LineageAssetType.TABLE,
              context.sourceTable(),
              SOURCE_TYPE,
              "mdm-collect-" + context.linkId(),
              null,
              String.valueOf(context.sourceDatasourceId()),
              normalize(context.sourceDatabase()),
              normalize(context.sourceSchema()),
              normalize(context.sourceTable()),
              null,
              null,
              projectId));
      putAsset(
          assets,
          new RegisterAssetCommand(
              landingKey,
              LineageAssetType.TABLE,
              context.landingTable(),
              SOURCE_TYPE,
              "mdm-collect-" + context.linkId(),
              null,
              String.valueOf(context.sinkDatasourceId()),
              normalize(context.database()),
              "",
              normalize(context.landingTable()),
              null,
              null,
              projectId));
    }
    putAsset(
        assets,
        new RegisterAssetCommand(
            recordKey,
            LineageAssetType.TABLE,
            RECORD_TABLE,
            SOURCE_TYPE,
            "mdm-entity-" + entityId,
            null,
            String.valueOf(contexts.get(0).sinkDatasourceId()),
            normalize(contexts.get(0).database()),
            "",
            RECORD_TABLE,
            null,
            null,
            projectId));

    Map<String, LineageAsset> registered = registration.registerAssetsBatch(
        new ArrayList<>(assets.values()), 50);

    List<RegisterRelationCommand> relations = new ArrayList<>();
    for (LandingContext context : contexts) {
      String sourceKey =
          tableKey(
              context.sourceDatasourceId(),
              context.sourceDatabase(),
              context.sourceSchema(),
              context.sourceTable());
      String landingKey =
          tableKey(context.sinkDatasourceId(), context.database(), null, context.landingTable());
      relations.add(
          relation(sourceKey, landingKey, registered,
              "mdm-collect-" + context.linkId(), "MDM 采集落地(" + context.landingTable() + ")", projectId));
      relations.add(
          relation(landingKey, recordKey, registered,
              "mdm-entity-" + entityId, "MDM 主数据加工(实体 " + context.entityCode() + ")", projectId));
    }
    registration.registerRelationsBatch(relations, 50);
    return new LineageSyncReceipt(assets.size(), relations.size());
  }

  private static void putAsset(
      Map<String, RegisterAssetCommand> assets, RegisterAssetCommand command) {
    assets.putIfAbsent(command.assetKey(), command);
  }

  private static RegisterRelationCommand relation(
      String upstreamKey,
      String downstreamKey,
      Map<String, LineageAsset> assets,
      String evidenceSourceId,
      String expression,
      Long projectId) {
    LineageAsset upstream = assets.get(upstreamKey);
    LineageAsset downstream = assets.get(downstreamKey);
    if (upstream == null || downstream == null) {
      throw new MdmException(
          MdmErrorCode.LINEAGE_REGISTER_FAILED,
          "血缘资产登记结果缺失: " + upstreamKey + " / " + downstreamKey);
    }
    return new RegisterRelationCommand(
        upstream.id(),
        downstream.id(),
        LineageRelationType.DERIVES_FROM,
        SOURCE_TYPE,
        evidenceSourceId,
        expression,
        null,
        null,
        Instant.now(),
        null,
        projectId);
  }

  /** 资产键入参按血缘口径预归一(trim+小写,空→空串),本模块负责生成规范值. */
  static String tableKey(Long datasourceId, String database, String schema, String table) {
    return PhysicalTableAssetKey.of(
        String.valueOf(datasourceId), normalize(database), normalize(schema), normalize(table));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  /** 血缘同步回执. */
  public record LineageSyncReceipt(int assetCount, int relationCount) {}
}
