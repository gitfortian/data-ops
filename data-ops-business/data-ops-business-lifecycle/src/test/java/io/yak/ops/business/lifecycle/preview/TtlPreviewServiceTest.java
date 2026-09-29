package io.yak.ops.business.lifecycle.preview;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.lifecycle.preview.TtlPreviewService.PartitionSplit;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 分区归类纯函数单测(ticket 84):热/冷/将删边界与分区名解析。 */
class TtlPreviewServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

  @Test
  void classifiesHotColdDeletedByDayThresholds() {
    PartitionSplit split = TtlPreviewService.classify(
        List.of("p20260917", "p20260911", "p20260819", "p20260818"),
        TODAY, 7, 30, 30);
    assertThat(split.total()).isEqualTo(4);
    assertThat(split.hotCount()).isEqualTo(2);
    assertThat(split.coldCount()).isEqualTo(1);
    assertThat(split.deletedCount()).isEqualTo(1);
    assertThat(split.deletedPartitions()).containsExactly("p20260818");
    assertThat(split.hotRange()).isEqualTo("p20260911 ~ p20260917");
    assertThat(split.estimated()).isFalse();
    assertThat(split.estimatedReason()).isNull();
  }

  @Test
  void boundaryDaysUseInclusiveHotAndStrictDelete() {
    // daysAgo==hotDays 算热;daysAgo==destroyDays 不删(仅 >destroy 才删)
    PartitionSplit split = TtlPreviewService.classify(
        List.of("p20260911", "p20260819"), TODAY, 7, 30, 30);
    assertThat(split.hotCount()).isEqualTo(1);
    assertThat(split.coldCount()).isEqualTo(1);
    assertThat(split.deletedCount()).isZero();
  }

  @Test
  void permanentPolicyNeverDeletes() {
    PartitionSplit split = TtlPreviewService.classify(
        List.of("p20200101", "p20260917"), TODAY, 7, 30, null);
    assertThat(split.deletedCount()).isZero();
    assertThat(split.hotCount()).isEqualTo(1);
    assertThat(split.coldCount()).isEqualTo(1);
  }

  @Test
  void unparseableNamesExcludedAndFlagged() {
    PartitionSplit split = TtlPreviewService.classify(
        List.of("p20260917", "p_default", "dt=2026/09/10"), TODAY, 7, 30, 30);
    assertThat(split.total()).isEqualTo(2);
    assertThat(split.estimatedReason()).contains("无法解析");
  }

  @Test
  void deletedListCappedAtFifty() {
    List<String> old = new java.util.ArrayList<>();
    for (int i = 1; i <= 60; i++) {
      old.add(String.format("p2025%02d%02d", (i - 1) / 28 + 1, (i - 1) % 28 + 1));
    }
    PartitionSplit split = TtlPreviewService.classify(old, TODAY, null, null, 30);
    assertThat(split.deletedCount()).isEqualTo(60);
    assertThat(split.deletedPartitions()).hasSize(50);
  }

  @Test
  void parsesCommonPartitionNameShapes() {
    LocalDate expected = LocalDate.of(2026, 9, 18);
    assertThat(TtlPreviewService.parsePartitionDate("p20260918")).isEqualTo(expected);
    assertThat(TtlPreviewService.parsePartitionDate("20260918")).isEqualTo(expected);
    assertThat(TtlPreviewService.parsePartitionDate("dt=2026-09-18")).isEqualTo(expected);
    assertThat(TtlPreviewService.parsePartitionDate("202609"))
        .isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(TtlPreviewService.parsePartitionDate("2026"))
        .isEqualTo(LocalDate.of(2026, 1, 1));
  }

  @Test
  void rejectsNonDateNames() {
    assertThat(TtlPreviewService.parsePartitionDate("p_default")).isNull();
    assertThat(TtlPreviewService.parsePartitionDate("abc")).isNull();
    assertThat(TtlPreviewService.parsePartitionDate(null)).isNull();
    assertThat(TtlPreviewService.parsePartitionDate("  ")).isNull();
  }
}
