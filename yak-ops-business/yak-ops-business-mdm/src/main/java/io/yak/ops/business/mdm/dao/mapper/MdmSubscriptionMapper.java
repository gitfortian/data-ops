package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.mdm.MdmSubscriptionPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the master data subscription (ticket 59). */
@Mapper
public interface MdmSubscriptionMapper extends BaseMapper<MdmSubscriptionPO> {
}
