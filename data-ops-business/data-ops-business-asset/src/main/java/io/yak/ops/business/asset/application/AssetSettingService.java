package io.yak.ops.business.asset.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.asset.dao.mapper.AssetSettingMapper;
import io.yak.ops.business.asset.dao.model.AssetSettingPO;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 模块级 KV 设置(gone_window_days / reconcile_last_*)读写。 */
@Service
@RequiredArgsConstructor
public class AssetSettingService {

  private final AssetSettingMapper settingMapper;

  public String get(Long projectId, String key) {
    AssetSettingPO po = settingMapper.selectOne(settingScope(projectId, key));
    return po == null ? null : po.getSettingValue();
  }

  public int getInt(Long projectId, String key, int defaultValue) {
    String raw = get(projectId, key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static LambdaQueryWrapper<AssetSettingPO> settingScope(Long projectId, String key) {
    return new LambdaQueryWrapper<AssetSettingPO>()
        .eq(AssetSettingPO::getProjectId, projectId)
        .eq(AssetSettingPO::getSettingKey, key);
  }

  public void put(Long projectId, String key, String value) {
    AssetSettingPO po = settingMapper.selectOne(settingScope(projectId, key));
    if (po == null) {
      po = new AssetSettingPO();
      po.setProjectId(projectId);
      po.setSettingKey(key);
      po.setSettingValue(value);
      po.setUpdateTime(LocalDateTime.now());
      settingMapper.insert(po);
    } else {
      po.setSettingValue(value);
      po.setUpdateTime(LocalDateTime.now());
      settingMapper.updateById(po);
    }
  }
}
