package io.yak.ops.common.bean.po.asset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 资产-标签关系(物理删,批量打标/去标事务内完成)。 */
@Data
@TableName("yak_asset_tag_rel")
public class AssetTagRelPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private Long assetId;
  private Long tagId;
  private String createdBy;
  private LocalDateTime createTime;
}
