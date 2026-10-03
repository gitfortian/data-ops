package io.yak.ops.business.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.security.dao.model.DsecClassificationPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for asset classification tags. */
@Mapper
public interface ClassificationMapper extends BaseMapper<DsecClassificationPO> {}
