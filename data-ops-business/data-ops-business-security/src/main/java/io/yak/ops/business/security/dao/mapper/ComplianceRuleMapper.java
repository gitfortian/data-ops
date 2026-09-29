package io.yak.ops.business.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.security.DsecComplianceRulePO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for compliance rules. */
@Mapper
public interface ComplianceRuleMapper extends BaseMapper<DsecComplianceRulePO> {}
