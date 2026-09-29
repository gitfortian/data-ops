package io.yak.ops.common.bean.po.asset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 浏览流水(活跃度数据源;只写不读明细,聚合走 SQL;保留 90 天定时清理)。 */
@Data
@TableName("yak_asset_view_record")
public class AssetViewRecordPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private Long assetId;
  /** 平台用户标识。 */
  private String viewer;
  private LocalDateTime viewTime;
  /** 入口来源(catalog/search/detail 等)。 */
  private String entry;
}
