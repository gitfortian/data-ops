package io.yak.ops.business.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.security.dao.model.DsecMaskingAlgorithmPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for masking algorithm dictionary. */
@Mapper
public interface MaskingAlgorithmMapper extends BaseMapper<DsecMaskingAlgorithmPO> {}
