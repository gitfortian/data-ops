package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.mdm.MdmDedupIgnorePO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the dedup "ignore group" ledger. */
@Mapper
public interface MdmDedupIgnoreMapper extends BaseMapper<MdmDedupIgnorePO> {}
