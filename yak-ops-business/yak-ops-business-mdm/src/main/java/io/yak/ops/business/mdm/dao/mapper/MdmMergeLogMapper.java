package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.mdm.MdmMergeLogPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the master data merge log. */
@Mapper
public interface MdmMergeLogMapper extends BaseMapper<MdmMergeLogPO> {
}
