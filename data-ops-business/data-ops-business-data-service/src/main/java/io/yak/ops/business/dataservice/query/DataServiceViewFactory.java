package io.yak.ops.business.dataservice.query;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceViewFactory {

  private static final String RUNTIME_PREFIX = "/api/v1/data-service/runtime";
  private final DataServiceParameterNameReader parameterNameReader;

  public DataServiceView view(DataServiceDefinition definition) {
    if (definition == null) return null;
    DataServiceSettings settings = definition.settings();
    SourceReference source = definition.sourceReference();
    return new DataServiceView(
        definition.id(), settings.name(), settings.path(), RUNTIME_PREFIX + settings.path(),
        definition.runtimeSnapshot().dataSourceId(), definition.runtimeSnapshot().sql(),
        parameterNameReader.parameterNames(definition.runtimeSnapshot().sql()), settings.maxRows(),
        settings.timeoutSeconds(), settings.enabled(), definition.authMode().name(), settings.description(),
        source.sourceType(), source.sourceRef(), source.sourceRevisionId(), source.sourceRevisionNo(),
        definition.createTime(), definition.updateTime(), settings.paginationEnabled());
  }
}
