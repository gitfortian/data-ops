package io.yak.ops.business.lifecycle.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 存储快照大小解析单测(ticket 87):"1.23 GB" 文本形态优先,纯数值单元格兜底。 */
class StorageSnapshotServiceTest {

  private static final double GB = 1024d * 1024 * 1024;

  @Test
  void parsesUnitFormCells() {
    assertThat(StorageSnapshotService.parseSize(List.of("tbl", "1.23 GB")))
        .isEqualTo(Math.round(1.23 * GB));
    assertThat(StorageSnapshotService.parseSize(List.of("512 MB")))
        .isEqualTo(Math.round(512 * GB / 1024));
    assertThat(StorageSnapshotService.parseSize(List.of("2 TB")))
        .isEqualTo(Math.round(2d * 1024 * GB));
    assertThat(StorageSnapshotService.parseSize(List.of("1024 KB")))
        .isEqualTo(1024 * 1024L);
  }

  @Test
  void unitParsingIsCaseInsensitiveWithSpacing() {
    assertThat(StorageSnapshotService.parseSize(List.of("7.5gb")))
        .isEqualTo(Math.round(7.5 * GB));
    assertThat(StorageSnapshotService.parseSize(List.of("7.5 gb")))
        .isEqualTo(Math.round(7.5 * GB));
  }

  @Test
  void numericCellFallbackWhenNoUnitText() {
    assertThat(StorageSnapshotService.parseSize(List.of("tbl", 123456789L)))
        .isEqualTo(123456789L);
  }

  @Test
  void unitFormWinsOverNumericCell() {
    assertThat(StorageSnapshotService.parseSize(List.of(1L, "1 GB")))
        .isEqualTo(Math.round(GB));
  }

  @Test
  void unparseableRowsReturnNull() {
    assertThat(StorageSnapshotService.parseSize(List.of("abc"))).isNull();
    assertThat(StorageSnapshotService.parseSize(List.of())).isNull();
    assertThat(StorageSnapshotService.parseSize(java.util.Arrays.asList((Object) null)))
        .isNull();
  }

  @Test
  void showDataIsProbedBeforeStandardView() {
    // 顺序不能反:Doris 的 DATA_LENGTH 恒 0,标准视图打底会产出"成功但全为 0"的假快照。
    List<String> statements = StorageSnapshotService.statementsFor("dwd");
    assertThat(statements).hasSize(2);
    assertThat(statements.get(0)).isEqualTo("SHOW DATA FROM `dwd`");
    assertThat(statements.get(1))
        .contains("information_schema.TABLES")
        .contains("TABLE_SCHEMA = 'dwd'")
        .contains("DATA_LENGTH");
  }

  @Test
  void unsafeDatabaseNamesAreRejected() {
    for (String db : new String[] {"dwd' OR '1'='1", "dwd; drop table x", "dwd` x", ""}) {
      assertThatThrownBy(() -> StorageSnapshotService.statementsFor(db))
          .as(db)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
