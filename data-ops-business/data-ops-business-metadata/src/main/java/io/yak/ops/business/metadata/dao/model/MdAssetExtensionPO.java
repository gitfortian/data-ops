package io.yak.ops.business.metadata.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 稀有大字段侧表。主键是 (asset_id, extension) 的复合键，指向共表 yak_metadata_asset 的主键
 * （无物理外键）。MyBatis-Plus 不支持复合主键，故按 (asset_id, extension) 条件读写。
 */
@Data
@TableName("yak_md_asset_extension")
public class MdAssetExtensionPO {

  @TableId(type = IdType.INPUT)
  private Long assetId;

  /** 点分名 &lt;typeName&gt;.&lt;fieldName&gt;。 */
  private String extension;

  /** 产该值的类型版本，便于排查漂移。 */
  private String jsonSchema;

  /** 值本体。列名 `json` 是 MySQL 关键字，必须反引号。 */
  @TableField("`json`")
  private String json;

  private LocalDateTime updateTime;
}
