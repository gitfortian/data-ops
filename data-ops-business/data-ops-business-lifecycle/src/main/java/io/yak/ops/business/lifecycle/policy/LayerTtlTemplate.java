package io.yak.ops.business.lifecycle.policy;

import io.yak.ops.common.enums.lifecycle.LifecycleEnums.Granularity;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 分层默认策略预置模板(requirement 3.2)。D1:分层旧字段 lifecycle_days 存在时,
 * 以它覆盖预置的销毁值(不丢失用户已配置的生命周期)。
 */
public final class LayerTtlTemplate {

  public record TemplateValues(Integer hotDays, Integer coldDays, Integer destroyDays) {}

  private static final Map<String, TemplateValues> PRESETS = new LinkedHashMap<>();

  static {
    PRESETS.put("ODS", new TemplateValues(7, 30, 90));
    PRESETS.put("DIM", new TemplateValues(null, null, null));
    PRESETS.put("DWD", new TemplateValues(30, 180, 730));
    PRESETS.put("DWS", new TemplateValues(90, 365, 1095));
    PRESETS.put("ADS", new TemplateValues(365, 730, null));
  }

  /** 预置模板(按层编码,保持 ODS→ADS 展示顺序)。 */
  public static Map<String, TemplateValues> presets() {
    return PRESETS;
  }

  /** 该层的预置值;lifecycleDays 非空时覆盖销毁值。未知分层按"永久"。 */
  public static TemplateValues forLayer(String layerCode, Integer lifecycleDays) {
    TemplateValues preset =
        PRESETS.getOrDefault(layerCode == null ? "" : layerCode.toUpperCase(), new TemplateValues(null, null, null));
    if (lifecycleDays != null && lifecycleDays > 0) {
      Integer destroy = lifecycleDays;
      Integer cold = preset.coldDays() == null ? null : Math.min(preset.coldDays(), destroy);
      Integer hot = preset.hotDays() == null ? null : Math.min(preset.hotDays(), cold == null ? destroy : cold);
      return new TemplateValues(hot, cold, destroy);
    }
    return preset;
  }

  /** 模板默认粒度:全部 DAY(D8)。 */
  public static Granularity defaultGranularity() {
    return Granularity.DAY;
  }

  private LayerTtlTemplate() {}
}
