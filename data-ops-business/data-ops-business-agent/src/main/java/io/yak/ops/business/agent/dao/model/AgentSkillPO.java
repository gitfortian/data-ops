package io.yak.ops.business.agent.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Data;

/** 智能体技能 PO（skills 在线管理持久化：yak_agent_skill）。 */
@Data
@TableName("yak_agent_skill")
public class AgentSkillPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 技能标识（全局唯一逻辑名，SkillBox 按此启停，乐观锁唯一键）。 */
  private String skillId;

  private String name;

  private String description;

  /** 技能元数据（JSON 字符串；能力标签等）。 */
  private String metadataJson;

  /** 技能正文（instructions，注入 System Prompt）。 */
  private String content;

  /** ENABLED / DISABLED（在线启停持久态）。 */
  private String status;

  /** 乐观版本：并发编辑防静默覆盖（MyBatis-Plus @Version）。 */
  @Version
  private Integer version;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}