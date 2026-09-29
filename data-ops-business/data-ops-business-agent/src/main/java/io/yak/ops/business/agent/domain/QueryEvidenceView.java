package io.yak.ops.business.agent.domain;

import java.util.List;

/** 数据集查询证据视图：查询运行时的客观结果（含执行 SQL），供模型引用与前端渲染。 */
public record QueryEvidenceView(
    String queryId,
    List<String> columns,
    List<List<Object>> rows,
    int returnedRows,
    boolean truncated,
    long elapsedMillis,
    String sql) {}
