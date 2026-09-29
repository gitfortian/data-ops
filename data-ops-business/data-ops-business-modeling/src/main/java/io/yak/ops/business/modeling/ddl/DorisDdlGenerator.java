package io.yak.ops.business.modeling.ddl;

import org.springframework.stereotype.Component;

/** Doris 模板（ticket 10）：主键模型走 UNIQUE KEY。 */
@Component
public class DorisDdlGenerator extends AbstractOlapDdlGenerator {

  @Override
  public String dialect() {
    return "DORIS";
  }

  @Override
  protected String keyClauseName() {
    return "UNIQUE KEY";
  }
}
