package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.mdm.dao.model.MdmChangePO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the master data change/approval (ticket 60). */
@Mapper
public interface MdmChangeMapper extends BaseMapper<MdmChangePO> {
}
