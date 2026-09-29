package io.yak.ops.business.sync.realtime.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Immutable published definition persistence record introduced by Stage 6 Wave 1. */
@Data
@TableName("yak_realtime_definition_version")
public class RealtimeDefinitionVersionPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long taskId;
  private Integer versionNo;
  private Integer sourceDraftRevision;
  private Long runtimeEnvironmentId;
  private String definitionJson;
  private String definitionDigest;
  private String sourceConfigDigest;
  private String domainMappingState;
  private LocalDateTime createTime;
}
