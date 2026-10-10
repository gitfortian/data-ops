package io.yak.ops.business.datasource.query;

import io.yak.ops.business.datasource.api.ProjectDataSourceReadApi;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import org.springframework.stereotype.Service;

/** Single source of datasource existence, delegates to the original DataSourceReader. */
@Service
@ConditionalOnDataSourceEnabled
public class ProjectDataSourceReadApiImpl implements ProjectDataSourceReadApi {
  private final DataSourceReader reader;
  public ProjectDataSourceReadApiImpl(DataSourceReader reader) {
    this.reader = reader;
  }
  @Override
  public void requireReadableSource(long dataSourceId) {
    reader.require(dataSourceId);
  }
}
