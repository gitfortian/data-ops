/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.message;

import lombok.Generated;

public class MessageReadDTO {
    private Long id;

    @Generated
    public MessageReadDTO() {
    }

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MessageReadDTO)) {
            return false;
        }
        MessageReadDTO other = (MessageReadDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        return !(this$id == null ? other$id != null : !((Object)this$id).equals(other$id));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof MessageReadDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "MessageReadDTO(id=" + this.getId() + ")";
    }
}

