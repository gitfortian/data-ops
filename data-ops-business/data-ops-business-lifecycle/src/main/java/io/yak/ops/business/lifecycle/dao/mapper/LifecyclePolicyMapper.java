package io.yak.ops.business.lifecycle.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.lifecycle.dao.model.LifecyclePolicyPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LifecyclePolicyMapper extends BaseMapper<LifecyclePolicyPO> {}
