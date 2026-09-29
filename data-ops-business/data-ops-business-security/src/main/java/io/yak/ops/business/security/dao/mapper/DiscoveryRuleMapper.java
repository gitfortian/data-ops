package io.yak.ops.business.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.security.DsecDiscoveryRulePO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for sensitive-data discovery rules. */
@Mapper
public interface DiscoveryRuleMapper extends BaseMapper<DsecDiscoveryRulePO> {}
