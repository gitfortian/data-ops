package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.mdm.MdmAttributePO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the master data attribute. */
@Mapper
public interface MdmAttributeMapper extends BaseMapper<MdmAttributePO> {
}
