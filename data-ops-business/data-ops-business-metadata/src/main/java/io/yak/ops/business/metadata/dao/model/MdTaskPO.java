package io.yak.ops.business.metadata.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 治理待办。"同一目标同类只允许一条开放行"由 open_marker 表达：
 * 未办结恒 0（互斥），办结时由服务写入自身 id（彼此相异）。
 * 办结语句必须是 SET resolved_at=?, open_marker=id——漏刷 marker 会被库层 CHECK 拒（3819）。
 */
@Data
@TableName("yak_md_task")
public class MdTaskPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  /** FILL_COMMENT|CONFIRM_LABEL|FIX_CONFORMANCE|REVIEW_GONE。 */
  private String taskType;
  private Long assetId;
  /** EntityStatus 7 值，默认 Unprocessed（区分"没人看过"与"看过但不合格"）。 */
  private String entityStatus;
  private String assignee;
  private String createdBy;
  private LocalDate dueDate;
  private LocalDateTime resolvedAt;
  private String resolveNote;
  private Long openMarker;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
