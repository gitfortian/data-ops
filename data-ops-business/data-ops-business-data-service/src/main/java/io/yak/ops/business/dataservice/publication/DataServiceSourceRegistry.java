package io.yak.ops.business.dataservice.publication;

import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnDataSourceEnabled
public class DataServiceSourceRegistry {

  private final Map<String, DataServiceSourceProvider> providers;

  public DataServiceSourceRegistry(List<DataServiceSourceProvider> sourceProviders) {
    Map<String, DataServiceSourceProvider> discovered = new LinkedHashMap<>();
    for (DataServiceSourceProvider provider :
        sourceProviders == null ? List.<DataServiceSourceProvider>of() : sourceProviders) {
      String type = normalizeSourceType(provider.sourceType());
      DataServiceSourceProvider duplicate = discovered.putIfAbsent(type, provider);
      if (duplicate != null) throw new IllegalStateException("数据服务发布来源类型重复：" + type);
    }
    providers = Map.copyOf(discovered);
  }

  public DataServiceSourceProvider require(String sourceType) {
    String normalized = normalizeSourceType(sourceType);
    DataServiceSourceProvider provider = providers.get(normalized);
    if (provider == null) throw new IllegalArgumentException("不支持的数据服务发布来源：" + normalized);
    return provider;
  }

  public DataServiceSourceProvider find(String sourceType) {
    if (!StringUtils.hasText(sourceType)) return null;
    return providers.get(normalizeSourceType(sourceType));
  }

  public String normalizeSourceType(String sourceType) {
    if (!StringUtils.hasText(sourceType)) throw new IllegalArgumentException("sourceType 不能为空");
    String normalized = sourceType.trim().toUpperCase(Locale.ROOT);
    if (normalized.length() > 64) throw new IllegalArgumentException("sourceType 不能超过 64 个字符");
    return normalized;
  }
}
