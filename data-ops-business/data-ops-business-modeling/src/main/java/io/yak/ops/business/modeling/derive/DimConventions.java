package io.yak.ops.business.modeling.derive;

import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.structure.StructureDialectCatalog;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 维表约定字段(ticket 50):目标分层为 DIM 时按维表约定默认补代理键与 SCD 生效起止列。
 *
 * <p>这些列是**结构性约定**,不是业务字段:不写 43/19 映射(无标准字段、无上游来源列),
 * 也不计入治理率——否则约定列会让 DIM 的治理率天然偏低、失去可比性。默认纳入但可改名/可取消
 * (与目标层技术列 {@code process_time/event_time} 不同:那是管道必需,约定列是建模约定)。
 *
 * <p>类型必须按目标方言解析:实测方言类型目录里 DATETIME 在 PostgreSQL 不存在、BIGINT/BOOLEAN
 * 在 Oracle 不存在,故按"类型偏好 + 方言支持"取第一个可用的类型;都取不到则阻断并指明列名,
 * 不生成不可用 DDL。
 */
@Component
public class DimConventions {

  /** SCD 类型:SCD1 只补代理键;SCD2 另补生效起止与是否当前。 */
  public static final String SCD1 = "SCD1";
  public static final String SCD2 = "SCD2";

  /** 一条约定列定义(已按方言解析出可用类型)。 */
  public record ConventionField(
      String name, String dataType, Integer length, String note, boolean surrogateKey) {}

  /** 类型候选:类型名 + 该类型需要的长度(仅 CHAR 之类要求长度的类型需要)。 */
  private record TypeChoice(String dataType, Integer length) {}

  private static final List<TypeChoice> SURROGATE_TYPES =
      List.of(
          new TypeChoice("BIGINT", null),
          new TypeChoice("INT64", null),
          new TypeChoice("NUMBER", null),
          new TypeChoice("INT", null),
          new TypeChoice("INTEGER", null));

  private static final List<TypeChoice> TIMESTAMP_TYPES =
      List.of(
          new TypeChoice("DATETIME", null),
          new TypeChoice("TIMESTAMP", null),
          new TypeChoice("DATE", null));

  private static final List<TypeChoice> FLAG_TYPES =
      List.of(
          new TypeChoice("BOOLEAN", null),
          new TypeChoice("TINYINT", null),
          new TypeChoice("SMALLINT", null),
          new TypeChoice("CHAR", 1),
          new TypeChoice("INT", null));

  /** 规范化 SCD 类型:空/未知按 SCD1(不补起止列,最保守)。 */
  public static String normalizeScdType(String scdType) {
    if (SCD2.equalsIgnoreCase(scdType == null ? "" : scdType.trim())) {
      return SCD2;
    }
    return SCD1;
  }

  /**
   * 解析维表约定列:始终含代理键;SCD2 另含生效起止与是否当前。
   *
   * @param dialect 目标方言(决定具体类型)
   * @param processCode 业务过程编码(代理键命名来源:dim_&lt;过程编码&gt;_sk)
   * @param scdType SCD1/SCD2(空按 SCD1)
   */
  public List<ConventionField> resolve(ModelDialect dialect, String processCode, String scdType) {
    if (dialect == null) {
      return List.of();
    }
    List<ConventionField> fields = new ArrayList<>();
    String normalizedCode = processCode == null ? "" : processCode.trim();
    String surrogateName =
        normalizedCode.isEmpty() ? "dim_sk" : "dim_" + normalizedCode + "_sk";
    fields.add(resolveOne(dialect, surrogateName, "代理键(维度唯一键)", SURROGATE_TYPES, true));
    if (SCD2.equals(normalizeScdType(scdType))) {
      fields.add(resolveOne(dialect, "start_time", "生效时间(SCD2)", TIMESTAMP_TYPES, false));
      fields.add(resolveOne(dialect, "end_time", "失效时间(SCD2,空=当前有效)", TIMESTAMP_TYPES, false));
      fields.add(resolveOne(dialect, "is_current", "是否当前版本(SCD2)", FLAG_TYPES, false));
    }
    return fields;
  }

  private ConventionField resolveOne(
      ModelDialect dialect,
      String name,
      String note,
      List<TypeChoice> candidates,
      boolean surrogateKey) {
    for (TypeChoice candidate : candidates) {
      if (StructureDialectCatalog.lookupType(dialect, candidate.dataType()) != null) {
        return new ConventionField(name, candidate.dataType(), candidate.length(), note, surrogateKey);
      }
    }
    throw new ModelingException(
        ModelingErrorCode.INVALID_COLUMN,
        "维表约定列 " + name + " 在 " + dialect.name() + " 方言下没有可用的类型(候选:"
            + candidates.stream().map(TypeChoice::dataType).toList()
            + ")，请在该方言的类型目录中补充后再派生");
  }

  /** 是否维表约定列名(供前端/测试对齐)。 */
  public static boolean isConventionName(String columnName) {
    if (columnName == null) {
      return false;
    }
    String name = columnName.trim().toLowerCase(Locale.ROOT);
    return name.endsWith("_sk") || "start_time".equals(name) || "end_time".equals(name)
        || "is_current".equals(name);
  }
}
