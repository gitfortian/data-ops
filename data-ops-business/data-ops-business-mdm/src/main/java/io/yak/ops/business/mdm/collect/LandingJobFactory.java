package io.yak.ops.business.mdm.collect;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobBasicDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobChannelDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobColumnMappingDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobEndpointDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobMappingDTO;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * 落地任务定义工厂(R1):产出数据集成 GUIDE_SINGLE 全量落地的最小合法
 * {@code OfflineJobDefinitionDTO}(sync 零改动,仅按其公开契约构造)。
 * 落地表由 MDM 预建,因此 sink.autoCreateTable=false;全量刷新语义用 writeMode=overwrite。
 */
public final class LandingJobFactory {

  public static final String MODE_GUIDE_SINGLE = "GUIDE_SINGLE";
  public static final String CONNECTOR_JDBC = "jdbc";
  public static final String WRITE_MODE_OVERWRITE = "overwrite";

  private LandingJobFactory() {}

  /**
   * @param definitionId 复用已存在任务定义时传入(更新),新建传 null
   * @param sourceQualifiedTable 源端限定表名(database[.schema].table 点号串)
   * @param sinkQualifiedTable 落地表限定名(平台库 database.table)
   */
  public static OfflineJobDefinitionDTO build(
      Long definitionId,
      String jobName,
      Long sourceDatasourceId,
      String sourceQualifiedTable,
      Long sinkDatasourceId,
      String sinkQualifiedTable,
      List<CatalogColumn> columns) {
    OfflineJobDefinitionDTO dto = new OfflineJobDefinitionDTO();
    dto.setId(definitionId);

    OfflineJobBasicDTO basic = new OfflineJobBasicDTO();
    basic.setJobName(jobName);
    basic.setJobDesc("MDM 采集落地任务(主数据模块自动生成,数据集成执行)");
    basic.setMode(MODE_GUIDE_SINGLE);
    dto.setBasic(basic);

    OfflineJobEndpointDTO source = new OfflineJobEndpointDTO();
    source.setConnectorId(CONNECTOR_JDBC);
    source.setDataSourceId(String.valueOf(sourceDatasourceId));
    ObjectNode sourceConfig = JsonNodeFactory.instance.objectNode();
    sourceConfig.put("table", sourceQualifiedTable);
    source.setConfig(sourceConfig);
    dto.setSource(source);

    OfflineJobEndpointDTO sink = new OfflineJobEndpointDTO();
    sink.setConnectorId(CONNECTOR_JDBC);
    sink.setDataSourceId(String.valueOf(sinkDatasourceId));
    ObjectNode sinkConfig = JsonNodeFactory.instance.objectNode();
    sinkConfig.put("table", sinkQualifiedTable);
    sinkConfig.put("autoCreateTable", false);
    sinkConfig.put("writeMode", WRITE_MODE_OVERWRITE);
    sink.setConfig(sinkConfig);
    dto.setSink(sink);

    dto.setChannel(new OfflineJobChannelDTO());

    OfflineJobMappingDTO mapping = new OfflineJobMappingDTO();
    mapping.setColumns(
        columns.stream()
            .map(
                column -> {
                  OfflineJobColumnMappingDTO pair = new OfflineJobColumnMappingDTO();
                  pair.setSource(column.name());
                  pair.setTarget(column.name());
                  return pair;
                })
            .toList());
    dto.setMapping(mapping);
    return dto;
  }

  /** database/schema 可选,拼 Link-Up 认识的点号限定名。 */
  public static String qualifiedName(String database, String schema, String table) {
    StringBuilder name = new StringBuilder();
    if (StringUtils.hasText(database)) {
      name.append(database).append('.');
    }
    if (StringUtils.hasText(schema)) {
      name.append(schema).append('.');
    }
    return name.append(table).toString();
  }
}
