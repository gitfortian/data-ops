package io.yak.ops.business.sync.realtime.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Calculates the mutable Draft/source compatibility digest used by Draft/Publish CAS. */
@Component
public class RealtimeSourceConfigDigestCalculator {

  private final ObjectMapper json;

  public RealtimeSourceConfigDigestCalculator(
      @Qualifier("realtimeObjectMapper") ObjectMapper json) {
    this.json = json;
  }

  public String calculate(CdcPipelineSpec spec, long runtimeEnvironmentId) {
    return sha256(write(spec) + "\n@runtime-environment:" + runtimeEnvironmentId);
  }

  private String write(CdcPipelineSpec spec) {
    try {
      return json.writeValueAsString(spec);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("无法序列化实时同步 Spec", exception);
    }
  }

  private String sha256(String value) {
    try {
      byte[] bytes =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("无法计算 SHA-256 摘要", exception);
    }
  }
}
