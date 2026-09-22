/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.vo.message;

import io.yak.framework.security.common.vo.message.MessageVO;
import java.util.List;
import lombok.Generated;

public class MessagePageVO {
    private List<MessageVO> records;
    private long total;

    @Generated
    public List<MessageVO> getRecords() {
        return this.records;
    }

    @Generated
    public long getTotal() {
        return this.total;
    }

    @Generated
    public void setRecords(List<MessageVO> records) {
        this.records = records;
    }

    @Generated
    public void setTotal(long total) {
        this.total = total;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MessagePageVO)) {
            return false;
        }
        MessagePageVO other = (MessagePageVO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (this.getTotal() != other.getTotal()) {
            return false;
        }
        List<MessageVO> this$records = this.getRecords();
        List<MessageVO> other$records = other.getRecords();
        return !(this$records == null ? other$records != null : !((Object)this$records).equals(other$records));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof MessagePageVO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        long $total = this.getTotal();
        result = result * 59 + (int)($total >>> 32 ^ $total);
        List<MessageVO> $records = this.getRecords();
        result = result * 59 + ($records == null ? 43 : ((Object)$records).hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "MessagePageVO(records=" + String.valueOf(this.getRecords()) + ", total=" + this.getTotal() + ")";
    }

    @Generated
    public MessagePageVO(List<MessageVO> records, long total) {
        this.records = records;
        this.total = total;
    }
}

