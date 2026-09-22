package io.yak.ops.business.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.business.lineage.controller.v1.converter.LineageViewConverter;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LineageViewMapperTest {

  private final LineageViewConverter converter = new LineageViewConverter();

  @Test
  void httpViewsKeepLongIdentifiersAsStrings() {
    long assetId = 9_007_199_254_740_993L;
    long parentId = 9_007_199_254_740_994L;
    LineageAsset asset = new LineageAsset(
        assetId,
        "dataset:1",
        LineageAssetType.DATASET,
        "Dataset",
        "TEST",
        "1",
        parentId,
        null,
        null,
        null,
        null,
        null,
        null,
        Instant.EPOCH,
        Instant.EPOCH);

    var view = converter.asset(asset);

    assertEquals(String.valueOf(assetId), view.id());
    assertEquals(String.valueOf(parentId), view.parentAssetId());
  }

  @Test
  void relationViewsKeepEndpointIdentifiersAsStrings() {
    LineageRelation relation = new LineageRelation(
        9_007_199_254_740_995L,
        9_007_199_254_740_993L,
        9_007_199_254_740_994L,
        LineageRelationType.CONSUMES,
        "TEST",
        "1",
        null,
        BigDecimal.ONE,
        "v1",
        Instant.EPOCH,
        null,
        Instant.EPOCH,
        Instant.EPOCH);

    var view = converter.relation(relation);

    assertEquals("9007199254740995", view.id());
    assertEquals("9007199254740993", view.sourceAssetId());
    assertEquals("9007199254740994", view.targetAssetId());
  }
}
