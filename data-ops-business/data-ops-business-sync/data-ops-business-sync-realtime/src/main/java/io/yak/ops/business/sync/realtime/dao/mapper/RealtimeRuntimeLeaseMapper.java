package io.yak.ops.business.sync.realtime.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeRuntimeLeasePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RealtimeRuntimeLeaseMapper extends BaseMapper<RealtimeRuntimeLeasePO> {}
