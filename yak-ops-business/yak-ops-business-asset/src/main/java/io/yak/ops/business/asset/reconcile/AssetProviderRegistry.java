package io.yak.ops.business.asset.reconcile;

import io.yak.ops.business.asset.api.AssetProvider;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 多 bean 收集:源域模块把 {@link AssetProvider} 实现注册为 Spring Bean,本注册表按键聚合。
 * asset 模块不 import 任何源域内部包(D6/SPI 纪律),运行时发现即可。
 */
@Component
public class AssetProviderRegistry {

  private final Map<AssetSourceType, AssetProvider> providers =
      new EnumMap<>(AssetSourceType.class);

  public AssetProviderRegistry(ObjectProvider<AssetProvider> discovered) {
    discovered.stream().forEach(p -> {
      AssetProvider previous = providers.putIfAbsent(p.sourceType(), p);
      if (previous != null) {
        throw new IllegalStateException("Duplicate AssetProvider for " + p.sourceType()
            + ": " + previous.getClass().getName() + " / " + p.getClass().getName());
      }
    });
  }

  public Optional<AssetProvider> find(AssetSourceType sourceType) {
    return Optional.ofNullable(providers.get(sourceType));
  }

  public Map<AssetSourceType, AssetProvider> all() {
    return Map.copyOf(providers);
  }

  public Collection<AssetSourceType> registeredTypes() {
    return providers.keySet();
  }
}
