package io.yak.ops.business.quality.dao.mapper;

import io.yak.ops.common.bean.po.quality.QualityMonitorSettingPO;
import io.yak.ops.common.bean.po.quality.QualityTableAssetPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 需要数据库原子语义而不适合拆成多次 BaseMapper CRUD 的写操作。 */
@Mapper
public interface QualityWriteMapper {
  Long lockMonitor(
      @Param("projectId") long projectId,
      @Param("monitorId") long monitorId);

  int upsertTableAsset(QualityTableAssetPO asset);

  int upsertMonitorSetting(QualityMonitorSettingPO setting);
}
