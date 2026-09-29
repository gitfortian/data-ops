package io.yak.ops.business.metadata.query;

import java.util.List;
import java.util.Map;

/**
 * 编译后的搜索计划：后端接缝（{@link MetadataSearchBackend}）唯一的输入。
 *
 * <p>三套谓词分开携带是本票最容易写错的一处区分（plan §4.2）：
 * <ul>
 *   <li>{@code facetPredicates} = 基础 + queryFilter + q —— <b>聚合计数含它</b>；
 *   <li>{@code rowPredicates} = 上面那些 + postFilter + 游标 —— 只作用于行；
 *   <li>{@code rollupPredicates} = 不含 index 收窄的基础 + queryFilter + q —— 供
 *       "命中 N 列"跨面聚合（§4.6：列不进默认面，但要把它的命中还回来）。
 * </ul>
 * 把 postFilter 错带进 facet、或把 index 错带进 rollup，都属契约违反。
 */
public record SearchPlan(
    Long projectId,
    /** 谓词之外的全局具名参数（projectId、权重 CASE 的绑定值）。 */
    Map<String, Object> extraParams,
    List<SqlPredicate> rowPredicates,
    List<SqlPredicate> facetPredicates,
    List<SqlPredicate> rollupPredicates,
    /** 非 null 时额外跑一条"列命中按父表聚合"（默认面不含 tableColumn 才有意义）。 */
    Long columnRollupTypeId,
    /** true = 命中行带父实体（列带其表），一批 id 一次回查，绝不逐行 N+1。 */
    boolean getHierarchy,
    /** ORDER BY 表达式（builder 从白名单渲染出的字面量，不含任何用户输入）。 */
    String orderBySql,
    /** 游标值表达式：SELECT 出来用于回 nextSearchAfter；relevance 排序时为 null（不提供游标）。 */
    String sortValueExpr,
    /** searchAfter 游标谓词（只进行集）；null = 首页。 */
    SqlPredicate cursorPredicate,
    boolean degradedToLike,
    String booleanQuery,
    int size,
    int offset,
    List<String> explainNotes) {

  static final String CURSOR_SEPARATOR = "|";
}
