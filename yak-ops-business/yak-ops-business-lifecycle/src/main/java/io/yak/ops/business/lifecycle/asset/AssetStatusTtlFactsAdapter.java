package io.yak.ops.business.lifecycle.asset;

import io.yak.ops.business.asset.api.AssetStatusTtlFacts;
import io.yak.ops.business.lifecycle.binding.ModelTtlBindingService;
import io.yak.ops.business.lifecycle.binding.ModelTtlResolution;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.BindingSource;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * M2-1 状态条事实供给:按模型读 TTL 解析结果(继承链已折叠)。
 * 复用 {@link ModelTtlBindingService#resolve}(当前项目空间线程态),不可解析时返回空让资产侧降级。
 */
@Component
@RequiredArgsConstructor
public class AssetStatusTtlFactsAdapter implements AssetStatusTtlFacts {

  private final ModelTtlBindingService bindingService;

  @Override
  public Optional<TtlFacts> ttlFacts(String sourceId) {
    Long modelId = parseIdOrNull(sourceId);
    if (modelId == null) {
      return Optional.empty();
    }
    try {
      ModelTtlResolution resolution = bindingService.resolve(modelId);
      return Optional.of(new TtlFacts(
          resolution.bindingSource() != BindingSource.NONE,
          resolution.policy() == null ? null : resolution.policy().getPolicyCode(),
          resolution.bindingSource().name(),
          resolution.state() == null ? null : resolution.state().name()));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  private static Long parseIdOrNull(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
