package io.yak.ops.business.lineage.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.lineage.dao.model.LineageAssetPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus mapper for lineage assets. */
@Mapper
public interface LineageAssetMapper extends BaseMapper<LineageAssetPO> {
}
