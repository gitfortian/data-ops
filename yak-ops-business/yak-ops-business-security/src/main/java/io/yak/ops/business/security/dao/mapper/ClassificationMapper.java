package io.yak.ops.business.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.security.DsecClassificationPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for asset classification tags. */
@Mapper
public interface ClassificationMapper extends BaseMapper<DsecClassificationPO> {}
