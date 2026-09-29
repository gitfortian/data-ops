package io.yak.ops.business.semantic.api;

import io.yak.ops.business.semantic.api.Standard;
import java.util.Collection;
import java.util.Map;

/** Read-only standard lookup SPI (ticket 44: std_type resolution; ticket 52: MDM reference). */
public interface StandardQueryApi {

  /** 单个标准;不存在返回 null。 */
  Standard get(Long standardId);

  /** 批量标准标签 `名称（编码）`(id → 标签),供消费方列表展示;不存在/无 id 不返回。 */
  Map<Long, String> labels(Collection<Long> ids);

  /** 项目内是否存在启用码集(ENABLED 且 code_set_code 匹配的 CODE 行),供码集引用校验。 */
  boolean existsCodeSet(String codeSetCode);
}
