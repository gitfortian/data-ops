package io.yak.ops.business.dataservice.execution;

import io.yak.ops.business.dataservice.access.DataServiceAuthorizer;
import io.yak.ops.business.dataservice.access.DataServiceForbiddenException;
import io.yak.ops.business.dataservice.access.DataServiceRateLimitException;
import io.yak.ops.business.dataservice.access.DataServiceUnauthorizedException;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceQueryResponse;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AccessContext;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.runtime.LocalDataServiceRuntime;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceInvoker {
  private static final Logger LOGGER = LoggerFactory.getLogger(DataServiceInvoker.class);
  private static final int DEFAULT_PAGE_SIZE = 20;
  private final DataServiceReader reader;
  private final DataServiceAuthorizer authorizer;
  private final DataServiceSqlCompiler compiler;
  private final DataServiceQueryExecutor queryExecutor;
  private final LocalDataServiceRuntime runtime;
  private final DataServiceInvocationRecorder recorder;
  private final ProjectContextScope projectContextScope;

  public DataServiceQueryResponse test(Long id, Map<String, String> parameters) {
    return execute(reader.require(id), parameters, AccessContext.console(), false);
  }

  public DataServiceQueryResponse invoke(String servicePath, Map<String, String> parameters, String rawApiKey) {
    return invoke(servicePath, parameters, rawApiKey, null);
  }

  public DataServiceQueryResponse invoke(
      String servicePath,
      Map<String, String> parameters,
      String rawApiKey,
      String clientIp) {
    DataServiceDefinition definition = reader.requireByPath(normalizePath(servicePath));
    ProjectContext projectContext = requireProjectContext(definition);
    return projectContextScope.call(
        projectContext,
        () -> invokeInProject(definition, parameters, rawApiKey, clientIp));
  }

  private DataServiceQueryResponse invokeInProject(
      DataServiceDefinition definition,
      Map<String, String> parameters,
      String rawApiKey,
      String clientIp) {
    if (!definition.settings().enabled()) {
      throw new IllegalStateException("数据服务未启用：" + definition.settings().path());
    }
    AccessContext access;
    try {
      access = authorizer.authorize(definition, rawApiKey, clientIp);
    } catch (DataServiceForbiddenException exception) {
      audit(definition, parameters, false, 0L, 0, safeMessage(exception), AccessContext.publicAccess());
      throw exception;
    } catch (DataServiceUnauthorizedException | DataServiceRateLimitException exception) {
      audit(definition, parameters, false, 0L, 0, safeMessage(exception), AccessContext.rejectedApiKey());
      throw exception;
    }
    return execute(definition, parameters, access, true);
  }

  private ProjectContext requireProjectContext(DataServiceDefinition definition) {
    Long projectId = definition == null ? null : definition.projectId();
    if (projectId == null || projectId <= 0L) {
      throw new IllegalStateException("数据服务缺少有效的 Project Space 归属：apiId="
          + (definition == null ? null : definition.id()));
    }
    return new ProjectContext(projectId, null);
  }

  private DataServiceQueryResponse execute(
      DataServiceDefinition definition, Map<String, String> parameters, AccessContext access, boolean resilient) {
    long started = System.nanoTime();
    try {
      DataServicePagination pagination = pagination(definition, parameters);
      Map<String, String> sqlParameters = sqlParameters(parameters, pagination != null);
      DataServiceSqlCompiler.CompiledSql compiled = compiler.compile(definition.runtimeSnapshot().sql(), sqlParameters);
      DataServiceQueryResponse response;
      if (resilient) {
        List<Object> bindings = new ArrayList<>(compiled.parameters());
        if (pagination != null) {
          bindings.add("__yak_page_num=" + pagination.pageNum());
          bindings.add("__yak_page_size=" + pagination.pageSize());
          bindings.add("__yak_return_total=" + pagination.returnTotalNum());
        }
        String cacheKey = runtime.cacheKey(runtimeNamespace(definition), compiled.sql(), bindings);
        response = runtime.execute(definition.id(), definition.runtimePolicy(), cacheKey,
            () -> queryExecutor.execute(definition, compiled, pagination));
      } else {
        response = queryExecutor.execute(definition, compiled, pagination);
      }
      audit(definition, parameters, true, response.durationMs(), response.rowCount(), null, access);
      return response;
    } catch (RuntimeException exception) {
      audit(definition, parameters, false, elapsedMs(started), 0, safeMessage(exception), access);
      throw exception;
    }
  }

  /** Invocation audit is evidence, not the business result. */
  private void audit(
      DataServiceDefinition definition,
      Map<String, String> parameters,
      boolean success,
      long durationMs,
      int rowCount,
      String errorMessage,
      AccessContext access) {
    try {
      recorder.record(definition, parameters, success, durationMs, rowCount, errorMessage, access);
    } catch (RuntimeException auditFailure) {
      LOGGER.warn(
          "Data Service invocation audit failed: apiId={}, success={}, cause={}",
          definition == null ? null : definition.id(),
          success,
          safeMessage(auditFailure));
    }
  }

  /** Persisted generation and runtime shape form the node-local cache namespace. */
  String runtimeNamespace(DataServiceDefinition definition) {
    SourceReference source = definition.sourceReference();
    RuntimePolicy policy = definition.runtimePolicy();
    return new StringBuilder("api=")
        .append(definition.id())
        .append("|source=")
        .append(source.sourceType())
        .append(':')
        .append(source.sourceRef())
        .append("|revision=")
        .append(source.sourceRevisionId())
        .append(':')
        .append(source.sourceRevisionNo())
        .append("|generation=")
        .append(definition.runtimeGeneration())
        .append("|maxRows=")
        .append(definition.settings().maxRows())
        .append("|pagination=")
        .append(definition.settings().paginationEnabled())
        .append("|cache=")
        .append(policy.cacheEnabled())
        .append(':')
        .append(policy.cacheTtlSeconds())
        .append(':')
        .append(policy.cacheMaxEntries())
        .toString();
  }

  private DataServicePagination pagination(DataServiceDefinition definition, Map<String, String> parameters) {
    if (!definition.settings().paginationEnabled()) return null;
    Map<String, String> values = parameters == null ? Map.of() : parameters;
    int pageNum = positiveInt(values.get("pageNum"), 1, "pageNum");
    int pageSize = positiveInt(values.get("pageSize"), DEFAULT_PAGE_SIZE, "pageSize");
    if (pageSize > definition.settings().maxRows()) {
      throw new IllegalArgumentException("pageSize 不能超过服务最大返回行数 " + definition.settings().maxRows());
    }
    boolean returnTotal = booleanValue(values.get("returnTotalNum"), true, "returnTotalNum");
    return new DataServicePagination(pageNum, pageSize, returnTotal, (long) (pageNum - 1) * pageSize);
  }

  private Map<String, String> sqlParameters(Map<String, String> parameters, boolean paginationEnabled) {
    if (parameters == null || parameters.isEmpty()) return Map.of();
    if (!paginationEnabled) return parameters;
    Map<String, String> result = new LinkedHashMap<>(parameters);
    result.remove("pageNum");
    result.remove("pageSize");
    result.remove("returnTotalNum");
    return result;
  }

  private int positiveInt(String raw, int fallback, String name) {
    if (!StringUtils.hasText(raw)) return fallback;
    try {
      int value = Integer.parseInt(raw.trim());
      if (value <= 0) throw new NumberFormatException();
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(name + " 必须是大于 0 的整数");
    }
  }

  private boolean booleanValue(String raw, boolean fallback, String name) {
    if (!StringUtils.hasText(raw)) return fallback;
    if ("true".equalsIgnoreCase(raw) || "1".equals(raw)) return true;
    if ("false".equalsIgnoreCase(raw) || "0".equals(raw)) return false;
    throw new IllegalArgumentException(name + " 必须是 true/false 或 1/0");
  }

  private String normalizePath(String path) {
    if (!StringUtils.hasText(path)) throw new IllegalArgumentException("服务路径不能为空");
    String value = path.trim();
    if (!value.startsWith("/")) value = "/" + value;
    value = value.replaceAll("/{2,}", "/");
    if (value.length() > 1 && value.endsWith("/")) value = value.substring(0, value.length() - 1);
    if (!value.matches("/[A-Za-z0-9._~/-]+")) {
      throw new IllegalArgumentException("服务路径仅支持字母、数字、-、_、. 和 /：" + value);
    }
    return value;
  }

  private long elapsedMs(long started) {
    return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
  }

  private String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    return StringUtils.hasText(message) ? message : "数据服务调用失败";
  }
}
