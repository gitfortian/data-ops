package io.yak.ops.business.semantic.api;

import io.yak.ops.business.semantic.api.WarehouseLayer;
import java.util.List;

/**
 * Read-only SPI for modeling: warehouse layer config (tickets 38/44).
 * Implementations must not leak internal types.
 */
public interface LayerConfigApi {

  /** 全部分层配置(按 sort_order)。 */
  List<WarehouseLayer> listLayers();

  /** 单层解析;不存在时抛业务异常(NOT_FOUND)。 */
  WarehouseLayer resolve(Long layerId);

  /** 按分层编码解析(忽略大小写);不存在返回 null(38 降级语义,不抛异常)。 */
  WarehouseLayer resolveByCode(String layerCode);
}
