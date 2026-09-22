package io.yak.ops.common.bean.po.asset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 跨域资产目录(树,物化路径)。 */
@Data
@TableName("yak_asset_directory")
public class AssetDirectoryPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  /** 自动生成 {父编码}_{序号},可改,项目内唯一。 */
  private String dirCode;
  private String dirName;
  /** 根=0。 */
  private Long parentId;
  /** 物化路径 /1/4/9/。 */
  private String path;
  private Integer sortOrder;
  private String iconKey;
  private String description;
  /** 1=模板初始化产生(可改不可删)。 */
  private Boolean builtin;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Boolean deleted;
}
