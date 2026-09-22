package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.mdm.MdmCleanRulePO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the master data cleansing rule. */
@Mapper
public interface MdmCleanRuleMapper extends BaseMapper<MdmCleanRulePO> {
}
