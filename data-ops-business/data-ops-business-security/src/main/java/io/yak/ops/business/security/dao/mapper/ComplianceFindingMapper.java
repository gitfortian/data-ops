package io.yak.ops.business.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.security.DsecComplianceFindingPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for compliance check findings. */
@Mapper
public interface ComplianceFindingMapper extends BaseMapper<DsecComplianceFindingPO> {}
