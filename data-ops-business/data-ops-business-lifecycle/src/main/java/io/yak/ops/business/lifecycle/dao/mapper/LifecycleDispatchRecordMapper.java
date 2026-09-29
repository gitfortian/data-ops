package io.yak.ops.business.lifecycle.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.lifecycle.LifecycleDispatchRecordPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LifecycleDispatchRecordMapper extends BaseMapper<LifecycleDispatchRecordPO> {}
