/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.resource.type;

import io.yak.framework.security.common.dto.PageParamDTO;
import io.yak.framework.security.common.dto.resource.MByRQueryDTO;
import lombok.Generated;

public class ResourceTypeQueryDTO
extends PageParamDTO {
    private String typeName;

    public ResourceTypeQueryDTO(MByRQueryDTO queryDTO) {
        this.setPage(queryDTO.getPage());
        this.setSize(queryDTO.getSize());
        this.typeName = queryDTO.getName();
    }

    @Generated
    public String getTypeName() {
        return this.typeName;
    }

    @Generated
    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    @Override
    @Generated
    public String toString() {
        return "ResourceTypeQueryDTO(typeName=" + this.getTypeName() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ResourceTypeQueryDTO)) {
            return false;
        }
        ResourceTypeQueryDTO other = (ResourceTypeQueryDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        String this$typeName = this.getTypeName();
        String other$typeName = other.getTypeName();
        return !(this$typeName == null ? other$typeName != null : !this$typeName.equals(other$typeName));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof ResourceTypeQueryDTO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        String $typeName = this.getTypeName();
        result = result * 59 + ($typeName == null ? 43 : $typeName.hashCode());
        return result;
    }
}

