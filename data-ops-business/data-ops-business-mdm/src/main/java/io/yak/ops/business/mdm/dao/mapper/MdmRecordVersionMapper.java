package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.mdm.MdmRecordVersionPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for master data record version snapshots (R4). */
@Mapper
public interface MdmRecordVersionMapper extends BaseMapper<MdmRecordVersionPO> {
}
