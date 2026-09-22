package io.yak.ops.business.mdm.controller.v1.converter;

import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.mdm.application.MdmEntityService;
import io.yak.ops.business.mdm.controller.v1.vo.MdmSourceVO;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.source.MdmSource;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts the master data source domain object to its view with display names. */
@Component
public class MdmSourceViewConverter {

  private final MdmEntityService entityService;
  private final DataSourceReader dataSourceReader;

  public MdmSourceViewConverter(MdmEntityService entityService, DataSourceReader dataSourceReader) {
    this.entityService = entityService;
    this.dataSourceReader = dataSourceReader;
  }

  public MdmSourceVO toView(MdmSource source) {
    String entityCode = String.valueOf(source.entityId());
    String entityName = "-";
    try {
      MdmEntity entity = entityService.get(source.entityId());
      entityCode = entity.code();
      entityName = entity.name();
    } catch (RuntimeException ignored) {
      // 实体已删除时降级展示 ID
    }
    String datasourceName = String.valueOf(source.datasourceId());
    try {
      DataSourceDefinition definition = dataSourceReader.require(source.datasourceId());
      datasourceName = definition.getName();
    } catch (RuntimeException ignored) {
      // 数据源已删除时降级展示 ID
    }
    return new MdmSourceVO(
        source.id(),
        source.entityId(),
        entityCode,
        entityName,
        source.datasourceId(),
        datasourceName,
        source.database(),
        source.schema(),
        source.table(),
        source.fieldMapping(),
        source.role() == null ? null : source.role().name(),
        source.status(),
        source.createTime());
  }

  public List<MdmSourceVO> toViews(List<MdmSource> sources) {
    return sources.stream().map(this::toView).toList();
  }
}
