package io.yak.ops.business.asset.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.asset.AssetItemPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetItemMapper extends BaseMapper<AssetItemPO> {}
