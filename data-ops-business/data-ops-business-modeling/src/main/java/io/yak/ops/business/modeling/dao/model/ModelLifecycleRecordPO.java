package io.yak.ops.business.modeling.dao.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("yak_modeling_lifecycle_record")
public class ModelLifecycleRecordPO {

    private Long id;
    private String objectType;
    private Long objectId;
    private String fromStatus;
    private String toStatus;
    private String operator;
    private String reason;
    private LocalDateTime createTime;
}
