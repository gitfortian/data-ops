/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.common;

import io.yak.framework.common.PageData;
import java.util.ArrayList;
import java.util.List;
import lombok.Generated;

public class PagingData<T> {
    private List<T> bizData;
    private Pagination pagination;

    public PagingData(PageData<T> pageData) {
        if (pageData == null) {
            this.bizData = new ArrayList<T>();
            this.pagination = Pagination.builder().total(0L).pages(0L).pageNo(1L).pageSize(0L).build();
            return;
        }
        this.bizData = new ArrayList<T>(pageData.records());
        this.pagination = Pagination.builder().total(pageData.total()).pages(pageData.pages()).pageNo(pageData.pageNo()).pageSize(pageData.pageSize()).build();
    }

    public static <T> PagingData<T> from(PageData<T> pageData) {
        return new PagingData<T>(pageData);
    }

    @Generated
    public List<T> getBizData() {
        return this.bizData;
    }

    @Generated
    public Pagination getPagination() {
        return this.pagination;
    }

    @Generated
    public void setBizData(List<T> bizData) {
        this.bizData = bizData;
    }

    @Generated
    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }

    @Generated
    public PagingData() {
    }

    @Generated
    public PagingData(List<T> bizData, Pagination pagination) {
        this.bizData = bizData;
        this.pagination = pagination;
    }

    @Generated
    public String toString() {
        return "PagingData(bizData=" + String.valueOf(this.getBizData()) + ", pagination=" + String.valueOf(this.getPagination()) + ")";
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof PagingData)) {
            return false;
        }
        PagingData other = (PagingData)o;
        if (!other.canEqual(this)) {
            return false;
        }
        List<T> this$bizData = this.getBizData();
        List<T> other$bizData = other.getBizData();
        if (this$bizData == null ? other$bizData != null : !((Object)this$bizData).equals(other$bizData)) {
            return false;
        }
        Pagination this$pagination = this.getPagination();
        Pagination other$pagination = other.getPagination();
        return !(this$pagination == null ? other$pagination != null : !((Object)this$pagination).equals(other$pagination));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof PagingData;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        List<T> $bizData = this.getBizData();
        result = result * 59 + ($bizData == null ? 43 : ((Object)$bizData).hashCode());
        Pagination $pagination = this.getPagination();
        result = result * 59 + ($pagination == null ? 43 : ((Object)$pagination).hashCode());
        return result;
    }

    public static class Pagination {
        private long total;
        private long pages;
        private long pageNo;
        private long pageSize;

        @Generated
        public static PaginationBuilder builder() {
            return new PaginationBuilder();
        }

        @Generated
        public long getTotal() {
            return this.total;
        }

        @Generated
        public long getPages() {
            return this.pages;
        }

        @Generated
        public long getPageNo() {
            return this.pageNo;
        }

        @Generated
        public long getPageSize() {
            return this.pageSize;
        }

        @Generated
        public void setTotal(long total) {
            this.total = total;
        }

        @Generated
        public void setPages(long pages) {
            this.pages = pages;
        }

        @Generated
        public void setPageNo(long pageNo) {
            this.pageNo = pageNo;
        }

        @Generated
        public void setPageSize(long pageSize) {
            this.pageSize = pageSize;
        }

        @Generated
        public String toString() {
            return "PagingData.Pagination(total=" + this.getTotal() + ", pages=" + this.getPages() + ", pageNo=" + this.getPageNo() + ", pageSize=" + this.getPageSize() + ")";
        }

        @Generated
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof Pagination)) {
                return false;
            }
            Pagination other = (Pagination)o;
            if (!other.canEqual(this)) {
                return false;
            }
            if (this.getTotal() != other.getTotal()) {
                return false;
            }
            if (this.getPages() != other.getPages()) {
                return false;
            }
            if (this.getPageNo() != other.getPageNo()) {
                return false;
            }
            return this.getPageSize() == other.getPageSize();
        }

        @Generated
        protected boolean canEqual(Object other) {
            return other instanceof Pagination;
        }

        @Generated
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            long $total = this.getTotal();
            result = result * 59 + (int)($total >>> 32 ^ $total);
            long $pages = this.getPages();
            result = result * 59 + (int)($pages >>> 32 ^ $pages);
            long $pageNo = this.getPageNo();
            result = result * 59 + (int)($pageNo >>> 32 ^ $pageNo);
            long $pageSize = this.getPageSize();
            result = result * 59 + (int)($pageSize >>> 32 ^ $pageSize);
            return result;
        }

        @Generated
        Pagination(long total, long pages, long pageNo, long pageSize) {
            this.total = total;
            this.pages = pages;
            this.pageNo = pageNo;
            this.pageSize = pageSize;
        }

        @Generated
        public static class PaginationBuilder {
            @Generated
            private long total;
            @Generated
            private long pages;
            @Generated
            private long pageNo;
            @Generated
            private long pageSize;

            @Generated
            PaginationBuilder() {
            }

            @Generated
            public PaginationBuilder total(long total) {
                this.total = total;
                return this;
            }

            @Generated
            public PaginationBuilder pages(long pages) {
                this.pages = pages;
                return this;
            }

            @Generated
            public PaginationBuilder pageNo(long pageNo) {
                this.pageNo = pageNo;
                return this;
            }

            @Generated
            public PaginationBuilder pageSize(long pageSize) {
                this.pageSize = pageSize;
                return this;
            }

            @Generated
            public Pagination build() {
                return new Pagination(this.total, this.pages, this.pageNo, this.pageSize);
            }

            @Generated
            public String toString() {
                return "PagingData.Pagination.PaginationBuilder(total=" + this.total + ", pages=" + this.pages + ", pageNo=" + this.pageNo + ", pageSize=" + this.pageSize + ")";
            }
        }
    }
}

