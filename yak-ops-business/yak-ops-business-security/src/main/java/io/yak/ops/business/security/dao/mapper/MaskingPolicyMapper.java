package io.yak.ops.business.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.security.DsecMaskingPolicyPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for masking policies. */
@Mapper
public interface MaskingPolicyMapper extends BaseMapper<DsecMaskingPolicyPO> {}
