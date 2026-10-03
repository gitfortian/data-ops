package io.yak.ops.business.asset.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 业务标签字典(跨域;与指标标签/security 分级并列)。 */
@Data
@TableName("yak_asset_tag")
public class AssetTagPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  /** 自动生成 tag_{ts},可改,项目内唯一。 */
  private String tagCode;
  private String tagName;
  /** 预置色板下拉。 */
  private String color;
  private String description;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Boolean deleted;
}
