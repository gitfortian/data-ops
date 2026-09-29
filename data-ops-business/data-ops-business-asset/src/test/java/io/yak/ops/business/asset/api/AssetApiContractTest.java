package io.yak.ops.business.asset.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** api 包纯函数契约:指纹稳定性、游标钳制、页语义、描述符防御性拷贝。 */
class AssetApiContractTest {

  @Test
  void contentHashIsDeterministicAndOrderSensitive() {
    String a = AssetContentHash.of("name", "desc");
    assertEquals(a, AssetContentHash.of("name", "desc"));
    assertNotEquals(a, AssetContentHash.of("desc", "name"));
    assertEquals(64, a.length());
  }

  @Test
  void contentHashDistinguishesNullFromEmptyAndSplicing() {
    assertNotEquals(AssetContentHash.of("ab", null), AssetContentHash.of("ab", ""));
    assertNotEquals(AssetContentHash.of("a", "bc"), AssetContentHash.of("ab", "c"));
  }

  @Test
  void cursorQueryClampsLimit() {
    assertEquals(1, new AssetCursorQuery(1L, null, null, 0).limit());
    assertEquals(AssetCursorQuery.MAX_LIMIT,
        new AssetCursorQuery(1L, null, null, 9999).limit());
    assertEquals(100, new AssetCursorQuery(1L, null, null, 100).limit());
  }

  @Test
  void pageSemantics() {
    AssetPage empty = AssetPage.empty();
    assertTrue(empty.items().isEmpty());
    assertNull(empty.nextCursor());
    assertTrue(!empty.hasMore());
    AssetPage page = new AssetPage(List.of(), "42");
    assertTrue(page.hasMore());
  }

  @Test
  void descriptorCopiesExtraDefensively() {
    Map<String, String> extra = new HashMap<>();
    extra.put("k", "v");
    AssetDescriptor d = new AssetDescriptor("metric:1", "1", "n", null,
        AssetType.METRIC, null, null, null, LocalDateTime.now(), "h", extra);
    extra.put("k", "changed");
    assertEquals("v", d.extra().get("k"));
    assertThrows(UnsupportedOperationException.class, () -> d.extra().put("x", "y"));
  }
}
