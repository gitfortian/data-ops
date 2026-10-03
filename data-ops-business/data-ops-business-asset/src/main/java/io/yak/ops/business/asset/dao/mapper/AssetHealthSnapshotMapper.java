package io.yak.ops.business.asset.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.asset.dao.model.AssetHealthSnapshotPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetHealthSnapshotMapper extends BaseMapper<AssetHealthSnapshotPO> {}
