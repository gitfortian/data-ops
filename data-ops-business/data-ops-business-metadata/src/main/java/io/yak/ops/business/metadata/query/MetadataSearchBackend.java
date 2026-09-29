package io.yak.ops.business.metadata.query;

/**
 * 检索存储的接缝（plan §4.4）：一期唯一实现是 {@link MysqlMetadataSearchBackend}。
 *
 * <p>接缝切在这里的依据是"换 ES 只加实现、不改调用方与源域挂钩"：本接口两侧交换的都是
 * 与存储无关的 {@link SearchPlan}/{@link SearchResult}——SQL 渲染所需的结构化谓词进、
 * 行与聚合出。ES 实现接到的仍是同一份 plan，届时把谓词翻成 query DSL 而非新增参数。
 */
public interface MetadataSearchBackend {

  /** 后端标识，进 explain（mysql | elasticsearch | …）。 */
  String name();

  SearchResult search(SearchPlan plan);
}
