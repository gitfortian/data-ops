package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.mdm.MdmEntityPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the master data entity. */
@Mapper
public interface MdmEntityMapper extends BaseMapper<MdmEntityPO> {
}
