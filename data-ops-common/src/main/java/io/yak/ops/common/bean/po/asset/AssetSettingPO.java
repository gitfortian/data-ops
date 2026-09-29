package io.yak.ops.common.bean.po.asset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 资产模块设置(KV:SOURCE_GONE 窗口天数、对账开关等)。 */
@Data
@TableName("yak_asset_setting")
public class AssetSettingPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private String settingKey;
  private String settingValue;
  private LocalDateTime updateTime;
}
