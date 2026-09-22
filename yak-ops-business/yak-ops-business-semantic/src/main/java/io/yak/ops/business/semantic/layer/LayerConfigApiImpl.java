package io.yak.ops.business.semantic.layer;

import io.yak.ops.business.semantic.api.LayerConfigApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Default LayerConfigApi implementation backed by the layer repository. */
@Component
@RequiredArgsConstructor
public class LayerConfigApiImpl implements LayerConfigApi {

  private final SemanticLayerService layerService;

  @Override
  public List<WarehouseLayer> listLayers() {
    return layerService.list();
  }

  @Override
  public WarehouseLayer resolve(Long layerId) {
    return layerService.get(layerId);
  }

  @Override
  public WarehouseLayer resolveByCode(String layerCode) {
    if (layerCode == null || layerCode.isBlank()) {
      return null;
    }
    return layerService
        .list()
        .stream()
        .filter(layer -> layerCode.equalsIgnoreCase(layer.code()))
        .findFirst()
        .orElse(null);
  }
}
