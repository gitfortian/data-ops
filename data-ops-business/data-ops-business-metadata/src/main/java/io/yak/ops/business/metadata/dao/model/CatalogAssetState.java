package io.yak.ops.business.metadata.dao.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 按 {@code asset_key} 读回的<b>已存在行状态</b>，只带判增量要用的四列。
 *
 * <p>两个用法：写前读一遍用来分 NEW/CHANGED/UNCHANGED（{@code AssetUpsertRepository}），写后再读一遍
 * 拿 {@code id} 去挂变更流水。第二次不合并进第一次，是因为新行的 id 在写之前物理上还不存在。
 */
@Data
public class CatalogAssetState {

  private Long id;
  private String assetKey;
  /** HARVESTED|REGISTERED；决定本轮比哪把指纹（plan §3.3 分岔）。 */
  private String providerType;
  private String contentHash;
  private String sourceHash;
  /** 行上现存的源侧更新时间；登记通道"保序"比的就是它（ticket 130：更旧的命令只刷在场）。 */
  private LocalDateTime sourceUpdatedAt;
  /** 非空 = 这行曾被判 GONE，本轮回来了即复活。 */
  private LocalDateTime goneAt;
}
