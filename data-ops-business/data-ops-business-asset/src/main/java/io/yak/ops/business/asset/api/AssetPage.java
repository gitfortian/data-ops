package io.yak.ops.business.asset.api;

import java.util.List;

/** 一批游标结果;nextCursor=null 表示已遍历完。 */
public record AssetPage(List<AssetDescriptor> items, String nextCursor) {

  public AssetPage {
    items = items == null ? List.of() : List.copyOf(items);
  }

  public static AssetPage empty() {
    return new AssetPage(List.of(), null);
  }

  public boolean hasMore() {
    return nextCursor != null;
  }
}
