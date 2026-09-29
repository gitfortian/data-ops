package io.yak.ops.business.dataservice.execution;

public record DataServicePagination(
    int pageNum,
    int pageSize,
    boolean returnTotalNum,
    long offset) {}
