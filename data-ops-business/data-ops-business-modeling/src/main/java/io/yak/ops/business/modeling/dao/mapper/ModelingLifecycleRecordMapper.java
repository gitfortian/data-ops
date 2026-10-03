package io.yak.ops.business.modeling.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.modeling.dao.model.ModelLifecycleRecordPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelingLifecycleRecordMapper extends BaseMapper<ModelLifecycleRecordPO> {
}
