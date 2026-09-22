package io.yak.ops.business.dataservice.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.dataservice.access.DataServiceClientIpResolver;
import io.yak.ops.business.dataservice.domain.DataServiceQueryResponse;
import io.yak.ops.business.dataservice.execution.DataServiceInvoker;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Data Service invocation plane.
 *
 * <p>This controller intentionally has no ProjectScope or Yak user RBAC requirement. External
 * callers identify a globally unique runtime path and are protected by the published service's
 * IP access policy plus NONE/API_KEY access contract instead of Yak console Project headers.</p>
 */
@Tag(name = "数据服务调用")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-service")
public class DataServiceInvocationController {

  private final DataServiceInvoker invoker;
  private final DataServiceClientIpResolver clientIpResolver;

  @Operation(summary = "调用已发布的数据服务")
  @GetMapping("/runtime/{*servicePath}")
  public Result<DataServiceQueryResponse> invoke(
      @PathVariable("servicePath") String servicePath,
      @RequestHeader(value = "X-API-Key", required = false) String apiKey,
      @RequestParam Map<String, String> parameters,
      HttpServletRequest request) {
    return Result.success(
        invoker.invoke(servicePath, parameters, apiKey, clientIpResolver.resolve(request)));
  }
}
