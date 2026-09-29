package io.yak.ops.business.lifecycle.generate;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.lifecycle.generate.TtlStatementGenerator.TtlTarget;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.Granularity;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.StorageType;
import org.junit.jupiter.api.Test;

class TtlStatementGeneratorTest {

  private static final TtlTarget TARGET = new TtlTarget("doris", "ods_db", "dwd_trade_order_detail");

  @Test
  void dorisDayGranularityMatchesDesignGoldenStatement() {
    TtlStatement s = TtlStatementGenerator.generate(
        Granularity.DAY, 7, 30, 30, TARGET);

    assertThat(s.storageType()).isEqualTo(StorageType.DORIS);
    assertThat(s.writable()).isTrue();
    assertThat(s.statement()).isEqualTo("""
        ALTER TABLE `ods_db`.`dwd_trade_order_detail`
        SET (
            "dynamic_partition.enable" = "true",
            "dynamic_partition.time_unit" = "DAY",
            "dynamic_partition.start" = "-30",
            "dynamic_partition.end" = "3",
            "dynamic_partition.prefix" = "p",
            "dynamic_partition.hot_partition_num" = "7"
        );""");
  }

  @Test
  void dorisPermanentPolicyDisablesDynamicRecycling() {
    TtlStatement s = TtlStatementGenerator.generate(
        Granularity.DAY, null, null, null, TARGET);

    assertThat(s.statement()).contains("\"dynamic_partition.enable\" = \"false\"");
    assertThat(s.statement()).doesNotContain("dynamic_partition.start");
    assertThat(s.note()).contains("永久保留");
  }

  @Test
  void dorisMonthGranularityConvertsDaysUpward() {
    TtlStatement s = TtlStatementGenerator.generate(
        Granularity.MONTH, 7, null, 90, new TtlTarget("DORIS", null, "tbl"));

    assertThat(s.statement()).contains("\"dynamic_partition.time_unit\" = \"MONTH\"");
    assertThat(s.statement()).contains("\"dynamic_partition.start\" = \"-3\"");
    assertThat(s.statement()).contains("\"dynamic_partition.hot_partition_num\" = \"1\"");
    assertThat(s.statement()).startsWith("ALTER TABLE `tbl`");
  }

  @Test
  void paimonGeneratesExpirationOptions() {
    TtlStatement s = TtlStatementGenerator.generate(
        Granularity.DAY, 30, 180, 730, new TtlTarget("paimon", "dwd", "t"));

    assertThat(s.storageType()).isEqualTo(StorageType.PAIMON);
    assertThat(s.writable()).isTrue();
    assertThat(s.statement())
        .contains("'partition.expiration-time' = '730 d'")
        .contains("'partition.expiration-check-interval' = '1 d'")
        .contains("'partition.timestamp-formatter' = 'yyyyMMdd'");
  }

  @Test
  void paimonPermanentIsNoOpAndNotWritable() {
    TtlStatement s = TtlStatementGenerator.generate(
        Granularity.DAY, null, null, null, new TtlTarget("apache_paimon", "d", "t"));

    assertThat(s.storageType()).isEqualTo(StorageType.PAIMON);
    assertThat(s.writable()).isFalse();
    assertThat(s.statement()).startsWith("--");
  }

  @Test
  void unknownDialectGeneratesCopyOnlyStatement() {
    TtlStatement s = TtlStatementGenerator.generate(
        Granularity.DAY, 7, null, 30, new TtlTarget("trino", "d", "t"));

    assertThat(s.storageType()).isEqualTo(StorageType.UNSUPPORTED);
    assertThat(s.writable()).isFalse();
    assertThat(s.note()).contains("trino").contains("仅可复制");
    assertThat(s.statement()).contains("dynamic_partition");
  }

  @Test
  void identifiersAreStrippedOfBackticks() {
    TtlStatement s = TtlStatementGenerator.generate(
        Granularity.DAY, null, null, 30, new TtlTarget("doris", "d`b", "t`bl`; DROP x"));

    // 内嵌反引号全部剥离,标识符定界无法被提前闭合
    assertThat(s.qualifiedTable()).isEqualTo("`db`.`tbl; DROP x`");
    assertThat(s.statement()).startsWith("ALTER TABLE `db`.`tbl; DROP x`");
    assertThat(s.statement().split("`", -1)).hasSize(5);
  }

  @Test
  void starrocksDialectMapsToDoris() {
    assertThat(TtlStatementGenerator.storageTypeOf("StarRocks")).isEqualTo(StorageType.DORIS);
    assertThat(TtlStatementGenerator.storageTypeOf(null)).isEqualTo(StorageType.UNSUPPORTED);
  }
}
