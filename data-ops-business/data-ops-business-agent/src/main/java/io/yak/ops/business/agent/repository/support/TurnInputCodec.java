package io.yak.ops.business.agent.repository.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.agent.domain.TurnInput;

/** {@link TurnInput} 的持久化投影 codec。只服务于 turn.payload_json 存取，不是业务契约。 */
public final class TurnInputCodec {

  private static final ObjectMapper JSON = new ObjectMapper();

  private TurnInputCodec() {}

  public static String encode(TurnInput input) {
    try {
      return JSON.writeValueAsString(input);
    } catch (Exception e) {
      throw new IllegalStateException("turn input encode failed", e);
    }
  }

  public static TurnInput decode(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalStateException("turn payload is blank");
    }
    try {
      return JSON.readValue(json, TurnInput.class);
    } catch (Exception e) {
      throw new IllegalStateException("turn input decode failed", e);
    }
  }
}
