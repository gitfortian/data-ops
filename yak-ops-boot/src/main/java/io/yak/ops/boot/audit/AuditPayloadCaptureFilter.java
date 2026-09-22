package io.yak.ops.boot.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * 为写请求套一层有限长度的请求体缓存（票 03），供审计兜底在 recordPayload=true 时读取。
 * 只包装不消费；缓存上限即包装构造参数，超出部分正常透传。
 */
public class AuditPayloadCaptureFilter extends OncePerRequestFilter {

  static final int MAX_CACHE_BYTES = 16 * 1024;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (isWrappable(request)) {
      filterChain.doFilter(new ContentCachingRequestWrapper(request, MAX_CACHE_BYTES), response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean isWrappable(HttpServletRequest request) {
    if (request instanceof ContentCachingRequestWrapper) {
      return false;
    }
    String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase();
    return switch (method) {
      case "POST", "PUT", "PATCH", "DELETE" -> true;
      default -> false;
    };
  }
}
