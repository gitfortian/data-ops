package io.yak.ops.business.modeling.view;

import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.business.semantic.api.BusinessProcess;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Business-process mainline view (ticket 47): process list comes from the
 * semantic SPI; coverage aggregation stays in modeling (server-side grouping
 * over project-scoped model rows).
 */
@Component
public class MainlineViewService {

  private final ProcessApi processApi;
  private final ModelRepository modelRepository;
  private final io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper modelMapper;
  private final io.yak.ops.core.project.CurrentProject currentProject;

  public MainlineViewService(
      ProcessApi processApi,
      ModelRepository modelRepository,
      io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper modelMapper,
      io.yak.ops.core.project.CurrentProject currentProject) {
    this.processApi = processApi;
    this.modelRepository = modelRepository;
    this.modelMapper = modelMapper;
    this.currentProject = currentProject;
  }

  /** 每个业务过程的各层覆盖。 */
  public record LayerCoverage(
      String layerCode, int modelCount, List<ModelBrief> models) {}

  public record ModelBrief(Long modelId, String code, String name, String status, String layerCode) {}

  public record ProcessCoverage(
      Long processId,
      String processCode,
      String processName,
      Long domainId,
      int totalModels,
      List<LayerCoverage> layers) {}

  public List<ProcessCoverage> mainline() {
    List<BusinessProcess> processes = processApi.listProcesses(null);
    List<ProcessCoverage> coverage = new ArrayList<>();
    for (BusinessProcess process : processes) {
      coverage.add(coverageOf(process));
    }
    return coverage;
  }

  public ProcessCoverage coverageOf(Long processId) {
    return processApi
        .listProcesses(null)
        .stream()
        .filter(process -> process.id().equals(processId))
        .findFirst()
        .map(this::coverageOf)
        .orElseThrow(
            () ->
                new io.yak.ops.business.modeling.exception.ModelingException(
                    ModelingErrorCode.NOT_FOUND, String.valueOf(processId)));
  }

  private ProcessCoverage coverageOf(BusinessProcess process) {
    List<Long> modelIds = modelRepository.modelIdsByProcess(process.id());
    // 按层聚合(服务端查询已项目绑定;分组在内存中仅针对该过程的模型行)。
    Map<String, List<ModelBrief>> byLayer = new LinkedHashMap<>();
    for (Long modelId : modelIds) {
      io.yak.ops.common.bean.po.modeling.ModelingModelPO po =
          modelMapper
              .selectList(
                  new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                      io.yak.ops.common.bean.po.modeling.ModelingModelPO>()
                      .eq(io.yak.ops.common.bean.po.modeling.ModelingModelPO::getId, modelId))
                  .stream()
                  .findFirst()
                  .orElse(null);
      if (po == null) {
        continue;
      }
      String layerCode = po.getLayerCode() == null ? "未分层" : po.getLayerCode();
      byLayer
          .computeIfAbsent(layerCode, key -> new ArrayList<>())
          .add(
              new ModelBrief(
                  po.getId(), po.getModelCode(), po.getModelName(), po.getStatus(), layerCode));
    }
    List<LayerCoverage> layers = new ArrayList<>();
    for (Map.Entry<String, List<ModelBrief>> entry : byLayer.entrySet()) {
      layers.add(new LayerCoverage(entry.getKey(), entry.getValue().size(), entry.getValue()));
    }
    return new ProcessCoverage(
        process.id(), process.code(), process.name(), process.domainId(), modelIds.size(),
        layers);
  }
}
