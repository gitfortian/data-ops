/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto;

import lombok.Generated;

public class PageParamDTO {
    private int page = 1;
    private int size = 10;

    @Generated
    public PageParamDTO() {
    }

    @Generated
    public int getPage() {
        return this.page;
    }

    @Generated
    public int getSize() {
        return this.size;
    }

    @Generated
    public void setPage(int page) {
        this.page = page;
    }

    @Generated
    public void setSize(int size) {
        this.size = size;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof PageParamDTO)) {
            return false;
        }
        PageParamDTO other = (PageParamDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (this.getPage() != other.getPage()) {
            return false;
        }
        return this.getSize() == other.getSize();
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof PageParamDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        result = result * 59 + this.getPage();
        result = result * 59 + this.getSize();
        return result;
    }

    @Generated
    public String toString() {
        return "PageParamDTO(page=" + this.getPage() + ", size=" + this.getSize() + ")";
    }
}

