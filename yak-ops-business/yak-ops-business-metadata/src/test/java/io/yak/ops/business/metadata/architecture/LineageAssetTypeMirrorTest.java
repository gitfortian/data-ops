package io.yak.ops.business.metadata.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.common.constant.metadata.MetadataLineageAssetTypes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@code MetadataLineageAssetTypes} 与 lineage 真实枚举的逐项比对（plan §10 测试 13 ①）。
 *
 * <p>为什么在 CI 里扫源码而不是 import：{@code asset_type} 是 NOT NULL，且 lineage 读每行都做一次
 * {@code LineageAssetType.valueOf(...)}。写进一个它没有的常量名，行照样存得下，
 * 直到<b>别人</b>跑血缘查询才炸（§2.3 后果 1）。而 import 那个枚举违反 §0.3 的跨模块只走 api。
 * 所以本测试用文本解析拿真实常量集，让"lineage 改名/加值"这件事在 CI 先红。
 */
class LineageAssetTypeMirrorTest {

  private static final Path LINEAGE_ENUM =
      Path.of(
          "yak-ops-business-lineage",
          "src",
          "main",
          "java",
          "io",
          "yak",
          "ops",
          "business",
          "lineage",
          "domain",
          "LineageAssetType.java");
  private static final Pattern CONSTANT = Pattern.compile("^\\s*([A-Z][A-Z0-9_]*)\\s*[,;]?\\s*$");

  @Test
  void mirrorMatchesTheRealEnumExactly() throws IOException {
    List<String> actual = enumConstants();
    assertThat(actual).as("解析 lineage 枚举失败，先确认路径与格式").isNotEmpty();
    assertThat(actual)
        .as("lineage 的 LineageAssetType 与本镜像清单不一致：改一边必须同时改另一边")
        .containsExactlyInAnyOrderElementsOf(MetadataLineageAssetTypes.NAMES);
  }

  @Test
  void catalogSideTypesAreNowKnownToTheMirror() {
    // ticket 134 的前置：这三类实体登记时写 asset_type，lineage 读行要 valueOf 得中（后果 1）。
    assertThat(MetadataLineageAssetTypes.NAMES)
        .contains("DATABASE_SERVICE", "DATABASE", "DOMAIN");
    assertThat(MetadataLineageAssetTypes.isKnown("DATABASE_SERVICE")).isTrue();
    assertThat(MetadataLineageAssetTypes.isKnown("TABLE")).isTrue();
    assertThat(MetadataLineageAssetTypes.isKnown("TABLE_COLUMN")).isFalse();
    assertThat(MetadataLineageAssetTypes.isKnown(null)).isFalse();
  }

  private List<String> enumConstants() throws IOException {
    String source = Files.readString(moduleSibling(LINEAGE_ENUM), StandardCharsets.UTF_8);
    int body = source.indexOf('{');
    int end = source.indexOf(';', body);
    String constants = end < 0 ? source.substring(body + 1) : source.substring(body + 1, end);
    List<String> names = new ArrayList<>();
    for (String line : constants.split("\n")) {
      Matcher matcher = CONSTANT.matcher(line.trim());
      if (matcher.matches()) {
        names.add(matcher.group(1));
      }
    }
    return names;
  }

  /** 从本模块目录走到兄弟模块；maven 与 IDE 的工作目录不同，故按几个候选根依次找。 */
  private Path moduleSibling(Path relative) {
    List<Path> candidates =
        List.of(
            Path.of("..").resolve(relative),
            Path.of("yak-ops-business").resolve(relative),
            Path.of("..", "..", "yak-ops-business").resolve(relative));
    return candidates.stream()
        .map(path -> path.normalize().toAbsolutePath())
        .filter(Files::isRegularFile)
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("找不到 lineage 的 LineageAssetType.java，尝试过：" + candidates));
  }
}
