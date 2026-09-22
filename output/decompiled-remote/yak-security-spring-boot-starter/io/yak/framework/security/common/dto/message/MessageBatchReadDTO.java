/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.message;

import java.util.List;
import lombok.Generated;

public class MessageBatchReadDTO {
    private List<Long> ids;

    @Generated
    public MessageBatchReadDTO() {
    }

    @Generated
    public List<Long> getIds() {
        return this.ids;
    }

    @Generated
    public void setIds(List<Long> ids) {
        this.ids = ids;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MessageBatchReadDTO)) {
            return false;
        }
        MessageBatchReadDTO other = (MessageBatchReadDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        List<Long> this$ids = this.getIds();
        List<Long> other$ids = other.getIds();
        return !(this$ids == null ? other$ids != null : !((Object)this$ids).equals(other$ids));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof MessageBatchReadDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        List<Long> $ids = this.getIds();
        result = result * 59 + ($ids == null ? 43 : ((Object)$ids).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "MessageBatchReadDTO(ids=" + String.valueOf(this.getIds()) + ")";
    }
}

