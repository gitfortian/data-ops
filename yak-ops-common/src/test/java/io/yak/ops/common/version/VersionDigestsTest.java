package io.yak.ops.common.version;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VersionDigestsTest {

  @Test
  void canonicalJsonIgnoresKeyOrder() {
    Map<String, Object> a = new LinkedHashMap<>();
    a.put("z", 1);
    a.put("a", 2);
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("a", 2);
    b.put("z", 1);
    assertThat(VersionDigests.canonicalJson(a)).isEqualTo(VersionDigests.canonicalJson(b));
    assertThat(VersionDigests.sha256OfCanonical(a)).isEqualTo(VersionDigests.sha256OfCanonical(b));
  }

  @Test
  void explicitNullDiffersFromMissingKey() {
    Map<String, Object> withNull = Map.of("a", 1, "b", "");
    Map<String, Object> filled = Map.of("a", 1, "b", "x");
    assertThat(VersionDigests.sha256OfCanonical(withNull))
        .isNotEqualTo(VersionDigests.sha256OfCanonical(filled));
  }

  @Test
  void sha256HexMatchesKnownVector() {
    // echo -n "" | sha256sum
    assertThat(VersionDigests.sha256Hex(""))
        .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }

  @Test
  void sha256FieldsIsolatesAmbiguousConcatenation() {
    assertThat(VersionDigests.sha256Fields("ab", "c"))
        .isNotEqualTo(VersionDigests.sha256Fields("a", "bc"));
    assertThat(VersionDigests.sha256Fields((String) null))
        .isEqualTo(VersionDigests.sha256Fields(new String[] {null}));
  }
}
