package io.yak.ops.business.modeling.mapping;

/**
 * 转换表达式语法校验(ticket 19):白名单字符集 + 括号配平 + 禁止终结符与
 * DML/DDL 关键字。非完整解析器——语法防呆,不执行(决策 D3 的映射延伸)。
 */
public final class TransformExpressionValidator {

  private static final java.util.regex.Pattern ALLOWED =
      java.util.regex.Pattern.compile("^[A-Za-z0-9_$.+\\-*/%()<>=!,\\s']+$");

  private static final java.util.List<String> FORBIDDEN =
      java.util.List.of(
          "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "TRUNCATE",
          "GRANT", "REVOKE", "EXEC", "EXECUTE", "UNION", ";", "--", "/*");

  private TransformExpressionValidator() {}

  /** @return 校验通过返回 null;否则返回用户可读的错误信息。 */
  public static String validate(String expression) {
    if (expression == null || expression.isBlank()) {
      return null; // 空表达式合法(直通映射)
    }
    String expr = expression.trim();
    if (expr.length() > 1000) {
      return "表达式长度不能超过 1000 个字符";
    }
    if (!ALLOWED.matcher(expr).matches()) {
      return "表达式包含非法字符";
    }
    int depth = 0;
    for (char c : expr.toCharArray()) {
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth < 0) {
          return "括号不配平：多余的右括号";
        }
      }
    }
    if (depth != 0) {
      return "括号不配平：缺少右括号";
    }
    String upper = expr.toUpperCase(java.util.Locale.ROOT);
    for (String token : FORBIDDEN) {
      if (upper.contains(token)) {
        return "表达式禁止包含 " + token;
      }
    }
    return null;
  }
}
