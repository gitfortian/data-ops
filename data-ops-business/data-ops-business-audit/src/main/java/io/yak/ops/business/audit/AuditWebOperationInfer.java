package io.yak.ops.business.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 由 HTTP method + 匹配路径模板推导兜底审计三元组（类型/名称/资源）。
 * 纯函数、零框架依赖：调用方传入 Spring 的 BEST_MATCHING_PATTERN（含 {var} 占位），
 * 保证同一路由族推导出稳定词汇表；读语义路径返回 null 表示无需兜底留痕。
 */
public final class AuditWebOperationInfer {

  /** 推导结果三元组。 */
  public record InferredWebOperation(String operationType, String operationName, String resourceType) {}

  private static final Set<String> READ_LIKE_SEGMENTS =
      Set.of(
          "page", "query", "list", "search", "count", "tree", "trees", "options", "detail",
          "selector", "overview", "resolve", "suggest", "diff", "compare", "download-template",
          "template", "history", "versions", "references", "preview-diff");

  private static final Map<String, String> VERB_LABELS = verbLabels();

  private AuditWebOperationInfer() {}

  /**
   * @param httpMethod 大写 HTTP 方法（GET/HEAD 等只读方法应由调用方先行排除）
   * @param pathPattern 最佳匹配路径模板，如 /api/v1/modeling/models/{id}/publish
   * @return 推导三元组；null 表示该端点属读语义或不可解析，跳过兜底审计
   */
  public static InferredWebOperation infer(String httpMethod, String pathPattern) {
    if (httpMethod == null || httpMethod.isBlank() || pathPattern == null || pathPattern.isBlank()) {
      return null;
    }
    List<String> segments = staticSegments(pathPattern);
    if (segments.size() < 2 || !"api".equalsIgnoreCase(segments.get(0))) {
      return null;
    }
    int cursor = 1;
    if (segments.get(1).matches("v\\d+")) {
      cursor = 2;
    }
    if (cursor >= segments.size()) {
      return null;
    }
    String module = segments.get(cursor);
    List<String> rest = segments.subList(cursor + 1, segments.size());
    if (!rest.isEmpty() && READ_LIKE_SEGMENTS.contains(last(rest))) {
      return null;
    }
    String trailingVerb = !rest.isEmpty() && VERB_LABELS.containsKey(last(rest)) ? last(rest) : null;
    List<String> resourceCandidates =
        trailingVerb == null ? rest : rest.subList(0, rest.size() - 1);
    String resource = resourceCandidates.isEmpty() ? null : last(resourceCandidates);

    String action;
    String actionLabel;
    if (trailingVerb != null) {
      action = token(trailingVerb);
      actionLabel = VERB_LABELS.get(trailingVerb);
    } else {
      action = actionForMethod(httpMethod.toUpperCase(Locale.ROOT));
      actionLabel = action == null ? httpMethod.toUpperCase(Locale.ROOT) : methodLabel(action);
      if (action == null) {
        return null;
      }
    }
    String operationType = joinTokens(token(module), token(resource), action);
    String readableResource = module + (resource == null ? "" : "/" + resource);
    String operationName = actionLabel + " " + readableResource;
    String resourceType = token(module) + (resource == null ? "" : "_" + token(resource));
    return new InferredWebOperation(operationType, operationName, resourceType);
  }

  /** 只读方法（Web 兜底只覆盖写接口）。 */
  public static boolean readOnlyMethod(String httpMethod) {
    if (httpMethod == null) {
      return true;
    }
    return switch (httpMethod.toUpperCase(Locale.ROOT)) {
      case "GET", "HEAD", "OPTIONS", "TRACE" -> true;
      default -> false;
    };
  }

  private static String actionForMethod(String method) {
    return switch (method) {
      case "POST" -> "CREATE";
      case "PUT", "PATCH" -> "UPDATE";
      case "DELETE" -> "DELETE";
      default -> null;
    };
  }

  private static String methodLabel(String action) {
    return switch (action) {
      case "CREATE" -> "新增";
      case "UPDATE" -> "更新";
      case "DELETE" -> "删除";
      default -> action;
    };
  }

  private static List<String> staticSegments(String pathPattern) {
    String trimmed = pathPattern.replaceAll("^/+", "").replaceAll("/+$", "");
    if (trimmed.isEmpty()) {
      return List.of();
    }
    return java.util.Arrays.stream(trimmed.split("/"))
        .filter(segment -> !segment.isEmpty())
        .filter(segment -> !segment.startsWith("{"))
        .toList();
  }

  private static String token(String segment) {
    if (segment == null) {
      return null;
    }
    return segment.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
  }

  private static String joinTokens(String... tokens) {
    StringBuilder builder = new StringBuilder();
    for (String token : tokens) {
      if (token == null || token.isBlank()) {
        continue;
      }
      if (!builder.isEmpty()) {
        builder.append('_');
      }
      builder.append(token);
    }
    return builder.toString();
  }

  private static String last(List<String> values) {
    return values.get(values.size() - 1);
  }

  private static Map<String, String> verbLabels() {
    Map<String, String> labels = new LinkedHashMap<>();
    labels.put("publish", "发布");
    labels.put("unpublish", "下架");
    labels.put("offline", "下线");
    labels.put("online", "上线");
    labels.put("submit", "提交");
    labels.put("approve", "审批通过");
    labels.put("reject", "驳回");
    labels.put("withdraw", "撤回");
    labels.put("execute", "执行");
    labels.put("run", "运行");
    labels.put("trigger", "触发");
    labels.put("test", "测试");
    labels.put("test-connection", "测试连接");
    labels.put("enable", "启用");
    labels.put("disable", "停用");
    labels.put("start", "启动");
    labels.put("stop", "停止");
    labels.put("pause", "暂停");
    labels.put("resume", "恢复");
    labels.put("retry", "重试");
    labels.put("cancel", "取消");
    labels.put("dispatch", "下发");
    labels.put("rollback", "回滚");
    labels.put("revert", "还原");
    labels.put("import", "导入");
    labels.put("import-ddl", "导入DDL");
    labels.put("export", "导出");
    labels.put("copy", "复制");
    labels.put("clone", "克隆");
    labels.put("bind", "绑定");
    labels.put("unbind", "解绑");
    labels.put("apply", "应用");
    labels.put("revoke", "撤销");
    labels.put("grant", "授权");
    labels.put("reset", "重置");
    labels.put("refresh", "刷新");
    labels.put("reload", "重载");
    labels.put("sync", "同步");
    labels.put("validate", "校验");
    labels.put("check", "检查");
    labels.put("verify", "验证");
    labels.put("preview", "预览");
    labels.put("upload", "上传");
    labels.put("install", "安装");
    labels.put("uninstall", "卸载");
    labels.put("login", "登录");
    labels.put("logout", "登出");
    labels.put("register", "注册");
    labels.put("migrate", "迁移");
    labels.put("lock", "锁定");
    labels.put("unlock", "解锁");
    labels.put("archive", "归档");
    labels.put("restore", "恢复");
    return Map.copyOf(labels);
  }

  /** 动词表只读视图（契约测试与语义补齐票核对用）。 */
  public static Set<String> knownVerbs() {
    return VERB_LABELS.keySet();
  }
}
