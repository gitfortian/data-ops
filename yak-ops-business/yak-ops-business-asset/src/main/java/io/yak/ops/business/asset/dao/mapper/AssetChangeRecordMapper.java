package io.yak.ops.business.asset.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.asset.AssetChangeRecordPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetChangeRecordMapper extends BaseMapper<AssetChangeRecordPO> {}
