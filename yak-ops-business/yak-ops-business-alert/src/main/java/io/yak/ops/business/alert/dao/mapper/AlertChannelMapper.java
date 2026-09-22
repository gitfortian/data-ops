package io.yak.ops.business.alert.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.alert.AlertChannelPO;
import org.apache.ibatis.annotations.Mapper;

/** 告警渠道 MyBatis 映射接口。 */
@Mapper
public interface AlertChannelMapper extends BaseMapper<AlertChannelPO> {
}
