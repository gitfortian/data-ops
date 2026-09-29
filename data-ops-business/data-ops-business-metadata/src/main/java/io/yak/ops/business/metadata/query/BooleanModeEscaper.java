package io.yak.ops.business.metadata.query;

import java.util.Arrays;
import java.util.List;

/**
 * {@code MATCH … AGAINST(… IN BOOLEAN MODE)} 的集中转义（plan §4.3）。
 *
 * <p>BOOLEAN MODE 下 {@code + - > < ( ) ~ * " @} 都是操作符，用户的搜索词里出现它们
 * 不该变成查询语法（这是搜索接口的注入面）。MySQL 给的合法出路是**短语引号**：
 * 双引号包裹后内部字符全部按字面处理，唯一需要再处理的是 {@code "} 本身——按 MySQL
 * 惯例双写为 {@code ""}。
 *
 * <p>因此这里不做"逐个字符前面加反斜杠"那种看似安全的伪转义：BOOLEAN MODE 的
 * {@code \} 不是通用转义符，加了反而制造新的解释。规则只有一条，且被
 * {@code BooleanModeEscaperTest} 全枚举守护。
 */
public final class BooleanModeEscaper {

  /** 必须被中和的 9 个操作符，写死在此供测试全枚举。 */
  static final List<Character> RESERVED = Arrays.asList('+', '-', '>', '<', '(', ')', '~', '*', '"', '@');

  /**
   * 把单个搜索词转成 BOOLEAN MODE 的字面短语。
   *
   * @return 形如 {@code "term"}；term 内的 {@code "} 双写
   */
  public static String quoteTerm(String term) {
    return '"' + term.replace("\"", "\"\"") + '"';
  }

  /**
   * 把用户原始 {@code q} 编译成 BOOLEAN MODE 查询串：按空白切词，每词独立加引号，词间空格
   * （= 语义 OR，召回优先；排序不承诺相关性，口径见 plan §4.5 规则 3）。
   *
   * @return 空白输入返回 {@code ""}（上层据此走浏览模式）
   */
  public static String toBooleanQuery(String q) {
    if (q == null || q.isBlank()) {
      return "";
    }
    return Arrays.stream(q.trim().split("\\s+"))
        .filter(term -> !term.isEmpty())
        .map(BooleanModeEscaper::quoteTerm)
        .reduce((left, right) -> left + " " + right)
        .orElse("");
  }

  /** 是否短到一个 n-gram 都凑不齐（ngram token_size=2），必须降级 LIKE。 */
  public static boolean shorterThanNgram(String q) {
    return q != null && !q.isBlank() && q.trim().codePointCount(0, q.trim().length()) < 2;
  }

  private BooleanModeEscaper() {}
}
