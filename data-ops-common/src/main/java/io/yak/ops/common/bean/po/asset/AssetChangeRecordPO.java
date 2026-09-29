package io.yak.ops.common.bean.po.asset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 盘点变更记录(对账产生;确认=用快照新值覆盖台账展示字段)。 */
@Data
@TableName("yak_asset_change_record")
public class AssetChangeRecordPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  /** 关联台账(NEW 时即已建行)。 */
  private Long assetId;
  /** NEW/META_CHANGED/SOURCE_GONE/REAPPEARED。 */
  private String changeType;
  /** 字段级前后差异(JSON);GONE 记录消失时间。 */
  private String diff;
  /** OPEN/CONFIRMED/IGNORED。 */
  private String handleStatus;
  /** 源域新值快照(确认前详情页提示"源域已变")。 */
  private String snapshotNew;
  private String handledBy;
  private LocalDateTime handledAt;
  private String createdBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Boolean deleted;
}
