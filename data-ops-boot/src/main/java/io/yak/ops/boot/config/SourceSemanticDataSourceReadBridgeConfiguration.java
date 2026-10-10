package io.yak.ops.boot.config;

import io.yak.ops.business.metadata.api.PhysicalSourceAccessPort;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.query.DataSourceReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boot composition is the only authorized bridge from a public cross-domain read SPI
 * to the existing Datasource query owner. Metadata never imports the internal reader.
 * Project-scoped existence is evaluated afresh for every call.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
public class SourceSemanticDataSourceReadBridgeConfiguration {
  @Bean
  public PhysicalSourceAccessPort physicalSourceAccessPort(DataSourceReader reader) {
    return id -> { reader.require(id); };
  }
}
