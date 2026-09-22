package io.yak.ops.business.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.security.DsecDataCategoryPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the data category dictionary. */
@Mapper
public interface DataCategoryMapper extends BaseMapper<DsecDataCategoryPO> {}
