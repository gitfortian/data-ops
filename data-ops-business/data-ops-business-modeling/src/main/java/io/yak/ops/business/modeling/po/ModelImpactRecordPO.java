package io.yak.ops.business.modeling.po;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ModelImpactRecordPO {
    private Long id;
    private String sourceObjectType;
    private Long sourceObjectId;
    private String targetObjectType;
    private Long targetObjectId;
    private String impactType;
    private String description;
    private LocalDateTime createTime;
}
