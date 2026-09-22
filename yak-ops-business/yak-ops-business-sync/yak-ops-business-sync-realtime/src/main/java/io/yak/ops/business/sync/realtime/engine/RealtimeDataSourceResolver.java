package io.yak.ops.business.sync.realtime.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourceConnection;
import java.net.URI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Resolves source and sink independently; it intentionally does not reuse the offline resolver. */
@Component
public class RealtimeDataSourceResolver {

  private final DataSourceRepository repository;
  private final DataSourcePluginRegistry plugins;
  private final ObjectMapper json;

  public RealtimeDataSourceResolver(
      DataSourceRepository repository,
      DataSourcePluginRegistry plugins,
      @Qualifier("realtimeObjectMapper") ObjectMapper json) {
    this.repository = repository;
    this.plugins = plugins;
    this.json = json;
  }

  public ResolvedCdcPipeline resolve(CdcPipelineSpec spec) {
    DataSourceDefinition source = find(spec.sourceDataSourceRef(), "Source");
    DataSourceDefinition sink = find(spec.sinkDataSourceRef(), "Sink");
    requireRole(source, Role.SOURCE);
    requireRole(sink, Role.SINK);
    return new ResolvedCdcPipeline(endpoint(source, Role.SOURCE), endpoint(sink, Role.SINK));
  }

  /** Resolves credentials only for the lifetime of one Flink CDC CLI submission. */
  public RealtimeDeployRequest.CredentialBindings resolveCredentials(CdcPipelineSpec spec) {
    DataSourceDefinition source = find(spec.sourceDataSourceRef(), "Source");
    DataSourceDefinition sink = find(spec.sinkDataSourceRef(), "Sink");
    requireRole(source, Role.SOURCE);
    requireRole(sink, Role.SINK);

    RealtimeDeployRequest.CredentialBinding sourceCredential = credential(source, Role.SOURCE);
    try {
      return new RealtimeDeployRequest.CredentialBindings(
          sourceCredential, credential(sink, Role.SINK));
    } catch (RuntimeException exception) {
      sourceCredential.close();
      throw exception;
    }
  }

  private DataSourceDefinition find(Long id, String role) {
    return repository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException(role + " 数据源不存在：" + id));
  }

  private void requireRole(DataSourceDefinition definition, Role role) {
    DataSourceDbType type = definition.getDbType();
    if (role == Role.SOURCE && type != DataSourceDbType.MYSQL) {
      throw new IllegalArgumentException("实时同步 Source 仅支持 MySQL 数据源");
    }
    if (role == Role.SINK
        && type != DataSourceDbType.MYSQL
        && type != DataSourceDbType.POSTGRE_SQL) {
      throw new IllegalArgumentException("实时同步 Sink 仅支持 MySQL 或 PostgreSQL 数据源");
    }
  }

  private ResolvedCdcPipeline.Endpoint endpoint(DataSourceDefinition definition, Role role) {
    DataSourceConnection connection = connection(definition, role);
    HostPort hostPort = hostPort(connection.normalizedJson(), connection.jdbcUrl());
    return new ResolvedCdcPipeline.Endpoint(
        definition.getId(),
        definition.getName(),
        definition.getDbType(),
        hostPort.host(),
        hostPort.port(),
        connection.jdbcUrl(),
        connection.driverClassName(),
        connection.username(),
        connection.database());
  }

  private RealtimeDeployRequest.CredentialBinding credential(
      DataSourceDefinition definition, Role role) {
    DataSourceConnection connection = connection(definition, role);
    return new RealtimeDeployRequest.CredentialBinding(
        connection.username(), connection.password());
  }

  private DataSourceConnection connection(DataSourceDefinition definition, Role role) {
    String connectionJson = definition.getConnectionParams();
    if (!StringUtils.hasText(connectionJson)) {
      connectionJson = definition.getOriginalJson();
    }
    if (!StringUtils.hasText(connectionJson)) {
      throw new IllegalArgumentException(role + " 数据源缺少连接参数");
    }

    DataSourceConnection connection =
        plugins.get(definition.getDbType()).parseConnection(connectionJson);
    if (!StringUtils.hasText(connection.password())) {
      throw new IllegalArgumentException(role + " 数据源未配置密码");
    }
    if (!StringUtils.hasText(connection.username())
        || !StringUtils.hasText(connection.jdbcUrl())
        || !StringUtils.hasText(connection.driverClassName())
        || (role == Role.SOURCE && !StringUtils.hasText(connection.database()))) {
      throw new IllegalArgumentException(role + " 数据源连接参数不完整");
    }

    return connection;
  }

  private HostPort hostPort(String normalizedJson, String jdbcUrl) {
    try {
      JsonNode root = json.readTree(normalizedJson);
      String host = root.path("host").asText(null);
      int port = root.path("port").asInt(0);
      if (StringUtils.hasText(host) && port > 0) {
        return new HostPort(host, port);
      }
    } catch (Exception ignored) {
      // JDBC URL is the compatibility fallback; connection values must never be logged here.
    }

    String uriText = jdbcUrl == null ? "" : jdbcUrl.replaceFirst("^jdbc:", "");
    try {
      URI uri = URI.create(uriText);
      if (StringUtils.hasText(uri.getHost()) && uri.getPort() > 0) {
        return new HostPort(uri.getHost(), uri.getPort());
      }
    } catch (IllegalArgumentException ignored) {
      // Produce one role-neutral validation error below without exposing connection values.
    }
    throw new IllegalArgumentException("无法从数据源连接参数解析 host/port");
  }

  private enum Role {
    SOURCE,
    SINK
  }

  private record HostPort(String host, int port) {}
}
