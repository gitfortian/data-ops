package io.yak.ops.business.sync.realtime.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Cross-field invariants that cannot be expressed with bean-validation annotations alone. */
@Component
public class CdcPipelineSpecValidator {

  public void validate(CdcPipelineSpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("实时同步 Spec 不能为空");
    }
    if (spec.sourceDataSourceRef() == null || spec.sinkDataSourceRef() == null) {
      throw new IllegalArgumentException("Source 与 Sink 数据源引用不能为空");
    }
    if (spec.sourceDataSourceRef().equals(spec.sinkDataSourceRef())) {
      throw new IllegalArgumentException("Source 与 Sink 不能引用同一个数据源");
    }
    if (spec.tables() == null || spec.tables().isEmpty()) {
      throw new IllegalArgumentException("至少需要一条表映射规则");
    }
    if (spec.sink() == null) {
      throw new IllegalArgumentException("Sink 参数不能为空");
    }
    if (!spec.sink().strictReplaySafety()) {
      throw new IllegalArgumentException("一期必须启用 strict Replay Safety");
    }

    Set<String> routes = new HashSet<>();
    for (CdcPipelineSpec.TableRoute route : spec.tables()) {
      if (route == null || !StringUtils.hasText(route.sourceTable()) || route.matchMode() == null) {
        throw new IllegalArgumentException("Source 表规则无效");
      }
      if (route.keyColumns() == null || route.keyColumns().isEmpty()) {
        throw new IllegalArgumentException("表规则必须声明至少一个主键字段");
      }
      if (!routes.add(route.sourceTable() + "\u0000" + route.sinkTable())) {
        throw new IllegalArgumentException("存在重复的表映射规则");
      }
      if (route.matchMode() == CdcPipelineSpec.MatchMode.EXACT
          && (route.sourceTable().contains(".") || route.sourceTable().contains(","))) {
        throw new IllegalArgumentException("精确 Source 表名必须是数据源库内的单个表名");
      }
      if (route.matchMode() == CdcPipelineSpec.MatchMode.REGEX) {
        if (route.sourceTable().contains("\\.") || route.sourceTable().contains("\\,")) {
          throw new IllegalArgumentException("Source 表正则中的字面量点号/逗号请使用字符类 [.] / [,]");
        }
        try {
          Pattern.compile(route.sourceTable());
        } catch (PatternSyntaxException exception) {
          throw new IllegalArgumentException("Source 表匹配正则无效：" + route.sourceTable());
        }
      }
      if (!StringUtils.hasText(route.sinkTable()) || route.sinkTable().contains("\n")) {
        throw new IllegalArgumentException("Sink 表名无效");
      }
    }
  }
}
