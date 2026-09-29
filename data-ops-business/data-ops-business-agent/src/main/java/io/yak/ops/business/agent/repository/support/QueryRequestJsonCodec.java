package io.yak.ops.business.agent.repository.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.agent.domain.DatasetQuerySpec;

/** 查询规格 -> 持久化投影 JSON。仅服务于 yak_agent_query_log.request_json 列。 */
public final class QueryRequestJsonCodec {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static String write(DatasetQuerySpec spec) {
    try {
      return MAPPER.writeValueAsString(spec);
    } catch (Exception e) {
      // 投影失败不阻断业务，留痕降级为不可读请求
      return "{\"error\":\"serialize_failed\"}";
    }
  }

  private QueryRequestJsonCodec() {}
}
