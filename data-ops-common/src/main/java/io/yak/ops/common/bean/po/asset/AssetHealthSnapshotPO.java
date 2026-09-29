package io.yak.ops.common.bean.po.asset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 健康度快照(P2,按日聚合而非按资产,控制体量;资产级历史本期不存)。 */
@Data
@TableName("yak_asset_health_snapshot")
public class AssetHealthSnapshotPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private LocalDate snapshotDate;
  /** 分层(ALL=全项目汇总行)。 */
  private String layerCode;
  @TableField("grade_a_count")
  private Integer gradeACount;
  @TableField("grade_b_count")
  private Integer gradeBCount;
  @TableField("grade_c_count")
  private Integer gradeCCount;
  @TableField("grade_d_count")
  private Integer gradeDCount;
  private Integer publishedCount;
  private LocalDateTime createTime;
}
