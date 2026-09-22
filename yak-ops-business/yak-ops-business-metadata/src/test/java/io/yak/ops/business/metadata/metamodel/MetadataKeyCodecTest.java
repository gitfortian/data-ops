package io.yak.ops.business.metadata.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 键与 FQN 派生（plan §10 测试 13）。
 *
 * <p>这里的哈希值是<b>跨语言钉死</b>的：{@code md5("table:1:trade_db.orders")} 由 Python 独立算出后写进断言，
 * 换实现（Java/MySQL/前端）必须得到同一个串——同一实体在两处算出两个哈希，就是血缘图裂成两个节点，
 * 而<b>没有任何 DDL 会报错</b>（目录侧不新增唯一键，plan §2.3 后果 4）。
 */
class MetadataKeyCodecTest {

  @Test
  void hashIsLowercasedAndCaseInsensitive() {
    assertThat(MetadataKeyCodec.fqnHash("table:1:trade_db.orders"))
        .isEqualTo("cb55fddd916921bfa10e43b55309f0ac");
    assertThat(MetadataKeyCodec.fqnHash("TABLE:1:trade_db.ORDERS"))
        .isEqualTo(MetadataKeyCodec.fqnHash("table:1:trade_db.orders"));
    assertThat(MetadataKeyCodec.fqnHash("modeling:model:12"))
        .isEqualTo("7ec39321585db366d853279651b0dcc0");
  }

  @Test
  void blankKeyHasNoHashRatherThanAFakeOne() {
    // 遗留行的 fqn_hash 就是 NULL，含义"早于目录机制"；填 '' 会在唯一索引上互相挤（plan §2.3 后果 3）。
    assertThat(MetadataKeyCodec.fqnHash(null)).isNull();
    assertThat(MetadataKeyCodec.fqnHash("  ")).isNull();
  }

  @Test
  void emptySegmentsDoNotLeaveStraySeparators() {
    Map<String, String> context = Map.of("databaseName", "trade_db", "tableName", "orders");
    assertThat(
            MetadataKeyCodec.renderFqn(
                "{databaseName}.{schemaName}.{tableName}", ".", context))
        .isEqualTo("trade_db.orders");
  }

  @Test
  void literalSegmentsSurviveAndSeparatorIsTheTypesOwn() {
    assertThat(
            MetadataKeyCodec.renderFqn(
                "{layerCode}_{modelCode}", "_", Map.of("layerCode", "DWD", "modelCode", "trade")))
        .isEqualTo("DWD_trade");
    // 模式里的字面量不归分隔符管："." 原样留着，只是不再被当成切分点。
    assertThat(
            MetadataKeyCodec.renderFqn(
                "{layerCode}.{modelCode}", "_", Map.of("layerCode", "DWD", "modelCode", "trade")))
        .isEqualTo("DWD.trade");
  }

  @Test
  void unresolvedPlaceholdersAreReportedNotSilentlyDropped() {
    assertThat(
            MetadataKeyCodec.unresolvedPlaceholders(
                "{databaseName}.{tableName}", Map.of("tableName", "orders")))
        .containsExactly("databaseName");
    assertThat(
            MetadataKeyCodec.unresolvedPlaceholders(
                "{databaseName}.{tableName}", Map.of("databaseName", "db", "tableName", "t")))
        .isEmpty();
  }

  @Test
  void keyProblemsCoverEveryWayAProviderCanHandOverBadKeys() {
    assertThat(MetadataKeyCodec.keyProblem("table:", null))
        .isEqualTo(MetadataKeyCodec.KeyProblem.BLANK);
    assertThat(MetadataKeyCodec.keyProblem("table:", "x".repeat(513)))
        .isEqualTo(MetadataKeyCodec.KeyProblem.TOO_LONG);
    assertThat(MetadataKeyCodec.keyProblem(null, "table:1"))
        .isEqualTo(MetadataKeyCodec.KeyProblem.PREFIX_NOT_DEFINED);
    assertThat(MetadataKeyCodec.keyProblem("modeling:model:", "model:1"))
        .isEqualTo(MetadataKeyCodec.KeyProblem.PREFIX_MISMATCH);
    assertThat(MetadataKeyCodec.keyProblem("table:", "table:1:trade_db.public.orders"))
        .isEqualTo(MetadataKeyCodec.KeyProblem.NONE);
  }
}
