package io.yak.ops.business.sync.realtime.repository.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** JSON codec for persistence-only realtime configuration columns. */
@Component
public class RealtimeJsonCodec {

  private final ObjectMapper objectMapper;

  public RealtimeJsonCodec(@Qualifier("realtimeObjectMapper") ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("无法序列化实时同步数据", exception);
    }
  }

  public CdcPipelineSpec readSpec(String value) {
    if (!StringUtils.hasText(value)) return null;
    try {
      return objectMapper.readValue(value, CdcPipelineSpec.class);
    } catch (Exception exception) {
      throw new IllegalArgumentException("实时同步 Spec 无效", exception);
    }
  }

  public ComputeEnvironment.RuntimeConfig readRuntimeConfig(String value) {
    return read(value, ComputeEnvironment.RuntimeConfig.class, "运行环境配置无法解析");
  }

  public ComputeEnvironmentSnapshot readEnvironmentSnapshot(String value) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException("实时同步部署缺少运行环境快照");
    }
    return read(value, ComputeEnvironmentSnapshot.class, "实时同步运行环境快照无效");
  }

  private <T> T read(String value, Class<T> type, String message) {
    try {
      return objectMapper.readValue(value, type);
    } catch (Exception exception) {
      throw new IllegalStateException(message, exception);
    }
  }
}
