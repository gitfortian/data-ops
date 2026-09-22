/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.common;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class PageData<T> {
    private final List<T> records;
    private final long total;
    private final long pages;
    private final long pageNo;
    private final long pageSize;

    public PageData(List<T> records, long total, long pages, long pageNo, long pageSize) {
        this.records = records == null ? List.of() : List.copyOf(records);
        this.total = total;
        this.pages = pages;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public List<T> records() {
        return this.records;
    }

    public long total() {
        return this.total;
    }

    public long pages() {
        return this.pages;
    }

    public long pageNo() {
        return this.pageNo;
    }

    public long pageSize() {
        return this.pageSize;
    }

    public static <T> PageData<T> of(List<T> records, long total, long pageNo, long pageSize) {
        long pages = pageSize <= 0L ? 0L : (total + pageSize - 1L) / pageSize;
        return new PageData<T>(records, total, pages, pageNo, pageSize);
    }

    public <R> PageData<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return new PageData<R>(this.records.stream().map(mapper).toList(), this.total, this.pages, this.pageNo, this.pageSize);
    }

    public static <T> PageData<T> empty(long pageNo, long pageSize) {
        return PageData.of(List.of(), 0L, pageNo, pageSize);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PageData)) {
            return false;
        }
        PageData that = (PageData)other;
        return this.total == that.total && this.pages == that.pages && this.pageNo == that.pageNo && this.pageSize == that.pageSize && this.records.equals(that.records);
    }

    public int hashCode() {
        return Objects.hash(this.records, this.total, this.pages, this.pageNo, this.pageSize);
    }

    public String toString() {
        return "PageData{records=" + String.valueOf(this.records) + ", total=" + this.total + ", pages=" + this.pages + ", pageNo=" + this.pageNo + ", pageSize=" + this.pageSize + "}";
    }
}

