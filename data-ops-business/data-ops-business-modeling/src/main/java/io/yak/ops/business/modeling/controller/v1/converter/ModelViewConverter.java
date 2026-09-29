package io.yak.ops.business.modeling.controller.v1.converter;

import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.ops.business.modeling.controller.v1.vo.ModelVO;
import io.yak.ops.business.modeling.domain.Model;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Converts modeling domain objects to API view objects. */
@Component
public class ModelViewConverter {

  public ModelVO toView(Model model) {
    return toView(model, Map.of(), Map.of(), Map.of());
  }

  /** 回收站等无名称解析上下文的分页。 */
  public PagingData<ModelVO> page(PageData<Model> page) {
    return page(page, Map.of(), Map.of(), Map.of());
  }

  /** 分层/业务过程/业务域/目标库名称由服务端解析:processNames、domainNames 为 id→名称,layerDbNames 为 分层→库名。 */
  public ModelVO toView(
      Model model,
      Map<Long, String> processNames,
      Map<String, String> layerDbNames,
      Map<Long, String> domainNames) {
    return new ModelVO(
        model.id(),
        model.code(),
        model.name(),
        model.dialect() == null ? null : model.dialect().name(),
        model.description(),
        model.status() == null ? null : model.status().name(),
        model.owner(),
        model.createTime(),
        model.updateTime(),
        model.layerCode(),
        model.processId(),
        model.processId() == null ? null : processNames.get(model.processId()),
        model.layerCode() == null ? null : layerDbNames.get(model.layerCode()),
        model.directoryId(),
        model.tagIds(),
        model.deletedBy(),
        model.deletedTime(),
        model.sourceDatasourceId(),
        model.sourceDatabase(),
        model.sourceTable(),
        model.statPeriod(),
        model.appCode(),
        model.appName(),
        model.importMode(),
        model.sourceModelId(),
        model.publishedVersionId(),
        model.latestVersionNo(),
        model.domainId(),
        model.domainId() == null ? null : domainNames.get(model.domainId()),
        model.updatedBy());
  }

  public PagingData<ModelVO> page(
      PageData<Model> page,
      Map<Long, String> processNames,
      Map<String, String> layerDbNames,
      Map<Long, String> domainNames) {
    return PagingData.from(page.map(model -> toView(model, processNames, layerDbNames, domainNames)));
  }
}
