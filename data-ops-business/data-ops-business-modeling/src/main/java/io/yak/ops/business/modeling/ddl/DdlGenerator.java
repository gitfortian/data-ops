package io.yak.ops.business.modeling.ddl;

/**
 * One dialect template of the CREATE TABLE generator (ticket 09). Implement
 * one per dialect and register it as a bean; DdlService picks by dialect.
 * Generators only render text — executing the script is out of scope (D3).
 */
public interface DdlGenerator {

  /** Dialect name matching ModelDialect.name(). */
  String dialect();

  String generate(DdlModel model);
}
