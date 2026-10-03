package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.mdm.dao.model.MdmSourcePO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the master data source binding. */
@Mapper
public interface MdmSourceMapper extends BaseMapper<MdmSourcePO> {
}
