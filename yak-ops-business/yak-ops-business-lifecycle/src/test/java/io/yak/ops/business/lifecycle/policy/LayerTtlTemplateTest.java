package io.yak.ops.business.lifecycle.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.lifecycle.policy.LayerTtlTemplate.TemplateValues;
import io.yak.ops.common.enums.lifecycle.LifecycleEnums.Granularity;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 分层预置模板单测(requirement 3.2 + D1 lifecycle_days 覆盖语义)。 */
class LayerTtlTemplateTest {

  @Test
  void presetsMatchDesignTable() {
    assertThat(LayerTtlTemplate.presets().keySet())
        .containsExactly("ODS", "DIM", "DWD", "DWS", "ADS");
    assertThat(LayerTtlTemplate.presets().get("ODS"))
        .isEqualTo(new TemplateValues(7, 30, 90));
    assertThat(LayerTtlTemplate.presets().get("DIM"))
        .isEqualTo(new TemplateValues(null, null, null));
    assertThat(LayerTtlTemplate.presets().get("DWD"))
        .isEqualTo(new TemplateValues(30, 180, 730));
    assertThat(LayerTtlTemplate.presets().get("DWS"))
        .isEqualTo(new TemplateValues(90, 365, 1095));
    assertThat(LayerTtlTemplate.presets().get("ADS"))
        .isEqualTo(new TemplateValues(365, 730, null));
  }

  @Test
  void layerLookupIsCaseInsensitive() {
    assertThat(LayerTtlTemplate.forLayer("dwd", null))
        .isEqualTo(new TemplateValues(30, 180, 730));
  }

  @Test
  void lifecycleDaysOverridesDestroyAndClampsUpperStages() {
    // DWD 旧配置 30 天:销毁=30,冷=min(180,30)=30,热=min(30,冷=30)=30
    assertThat(LayerTtlTemplate.forLayer("DWD", 30))
        .isEqualTo(new TemplateValues(30, 30, 30));
    // ODS 旧配置 15 天:冷=min(30,15)=15,热=min(7,15)=7
    assertThat(LayerTtlTemplate.forLayer("ODS", 15))
        .isEqualTo(new TemplateValues(7, 15, 15));
  }

  @Test
  void overrideKeepsPermanentStagesNull() {
    // DIM 预置全永久:覆盖只落在销毁,热/冷保持 null
    assertThat(LayerTtlTemplate.forLayer("DIM", 60))
        .isEqualTo(new TemplateValues(null, null, 60));
  }

  @Test
  void nonPositiveLifecycleDaysKeepsPreset() {
    assertThat(LayerTtlTemplate.forLayer("ODS", 0))
        .isEqualTo(new TemplateValues(7, 30, 90));
    assertThat(LayerTtlTemplate.forLayer("ODS", null))
        .isEqualTo(new TemplateValues(7, 30, 90));
  }

  @Test
  void unknownLayerTreatedAsPermanent() {
    assertThat(LayerTtlTemplate.forLayer("ODS_X", null))
        .isEqualTo(new TemplateValues(null, null, null));
    assertThat(LayerTtlTemplate.forLayer(null, null))
        .isEqualTo(new TemplateValues(null, null, null));
  }

  @Test
  void defaultGranularityIsDay() {
    assertThat(LayerTtlTemplate.defaultGranularity()).isEqualTo(Granularity.DAY);
  }

  @Test
  void presetsAreExposedInDisplayOrder() {
    assertThat(List.copyOf(LayerTtlTemplate.presets().keySet()))
        .startsWith("ODS").endsWith("ADS");
  }
}
