package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.mdm.dao.model.MdmDistributionPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the master data distribution config (ticket 58). */
@Mapper
public interface MdmDistributionMapper extends BaseMapper<MdmDistributionPO> {
}
