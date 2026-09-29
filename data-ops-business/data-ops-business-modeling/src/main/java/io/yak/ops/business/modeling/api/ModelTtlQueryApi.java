package io.yak.ops.business.modeling.api;

import java.util.List;

/**
 * 模型 TTL 只读 SPI:供 lifecycle 模块解析下发目标(表名/分区/方言/分层)。
 * 消费方不得直读 modeling 的表或 dao;既有 ModelQueryApi 签名不改,按 SPI 纪律新增。
 */
public interface ModelTtlQueryApi {

  /** 按 id 解析 TTL 目标模型;不存在/已删除抛 ModelingException(NOT_FOUND)。 */
  TtlModelSource resolve(Long modelId);

  /** 当前项目全部模型(监控列表用;含未配分区的模型)。 */
  List<TtlModelSource> listAll();

  /**
   * @param tableName 物理表名,已按 model_code 兜底
   * @param partitionType 分区类型(按方言),空=无分区配置
   * @param partitionColumnsJson 分区列 JSON 数组
   */
  record TtlModelSource(
      Long id,
      String code,
      String name,
      String layerCode,
      String dialect,
      String tableName,
      String partitionType,
      String partitionColumnsJson) {}
}
