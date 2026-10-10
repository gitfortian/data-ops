package io.yak.ops.business.semantic.adoption;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** Project-scoped receipt persistence; unique task/candidate key is DB arbitration. */
@Mapper
public interface AdoptionReceiptMapper extends BaseMapper<AdoptionReceiptPO> {}
