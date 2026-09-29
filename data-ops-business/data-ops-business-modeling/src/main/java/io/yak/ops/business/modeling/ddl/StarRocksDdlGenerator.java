package io.yak.ops.business.modeling.ddl;

import org.springframework.stereotype.Component;

/** StarRocks 模板（ticket 10）：与 Doris 同族，主键模型用 PRIMARY KEY 而非 UNIQUE KEY。 */
@Component
public class StarRocksDdlGenerator extends AbstractOlapDdlGenerator {

  @Override
  public String dialect() {
    return "STARROCKS";
  }

  @Override
  protected String keyClauseName() {
    return "PRIMARY KEY";
  }
}
