/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package io.yak.framework.security.common.dto.message;

import lombok.Generated;

public class MessagePageQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String status;
    private String type;
    private Long projectId;
    private Long startTime;
    private Long endTime;

    @Generated
    public MessagePageQueryDTO() {
    }

    @Generated
    public Integer getPageNum() {
        return this.pageNum;
    }

    @Generated
    public Integer getPageSize() {
        return this.pageSize;
    }

    @Generated
    public String getStatus() {
        return this.status;
    }

    @Generated
    public String getType() {
        return this.type;
    }

    @Generated
    public Long getProjectId() {
        return this.projectId;
    }

    @Generated
    public Long getStartTime() {
        return this.startTime;
    }

    @Generated
    public Long getEndTime() {
        return this.endTime;
    }

    @Generated
    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    @Generated
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    @Generated
    public void setStatus(String status) {
        this.status = status;
    }

    @Generated
    public void setType(String type) {
        this.type = type;
    }

    @Generated
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    @Generated
    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    @Generated
    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MessagePageQueryDTO)) {
            return false;
        }
        MessagePageQueryDTO other = (MessagePageQueryDTO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Integer this$pageNum = this.getPageNum();
        Integer other$pageNum = other.getPageNum();
        if (this$pageNum == null ? other$pageNum != null : !((Object)this$pageNum).equals(other$pageNum)) {
            return false;
        }
        Integer this$pageSize = this.getPageSize();
        Integer other$pageSize = other.getPageSize();
        if (this$pageSize == null ? other$pageSize != null : !((Object)this$pageSize).equals(other$pageSize)) {
            return false;
        }
        Long this$projectId = this.getProjectId();
        Long other$projectId = other.getProjectId();
        if (this$projectId == null ? other$projectId != null : !((Object)this$projectId).equals(other$projectId)) {
            return false;
        }
        Long this$startTime = this.getStartTime();
        Long other$startTime = other.getStartTime();
        if (this$startTime == null ? other$startTime != null : !((Object)this$startTime).equals(other$startTime)) {
            return false;
        }
        Long this$endTime = this.getEndTime();
        Long other$endTime = other.getEndTime();
        if (this$endTime == null ? other$endTime != null : !((Object)this$endTime).equals(other$endTime)) {
            return false;
        }
        String this$status = this.getStatus();
        String other$status = other.getStatus();
        if (this$status == null ? other$status != null : !this$status.equals(other$status)) {
            return false;
        }
        String this$type = this.getType();
        String other$type = other.getType();
        return !(this$type == null ? other$type != null : !this$type.equals(other$type));
    }

    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof MessagePageQueryDTO;
    }

    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Integer $pageNum = this.getPageNum();
        result = result * 59 + ($pageNum == null ? 43 : ((Object)$pageNum).hashCode());
        Integer $pageSize = this.getPageSize();
        result = result * 59 + ($pageSize == null ? 43 : ((Object)$pageSize).hashCode());
        Long $projectId = this.getProjectId();
        result = result * 59 + ($projectId == null ? 43 : ((Object)$projectId).hashCode());
        Long $startTime = this.getStartTime();
        result = result * 59 + ($startTime == null ? 43 : ((Object)$startTime).hashCode());
        Long $endTime = this.getEndTime();
        result = result * 59 + ($endTime == null ? 43 : ((Object)$endTime).hashCode());
        String $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        String $type = this.getType();
        result = result * 59 + ($type == null ? 43 : $type.hashCode());
        return result;
    }

    @Generated
    public String toString() {
        return "MessagePageQueryDTO(pageNum=" + this.getPageNum() + ", pageSize=" + this.getPageSize() + ", status=" + this.getStatus() + ", type=" + this.getType() + ", projectId=" + this.getProjectId() + ", startTime=" + this.getStartTime() + ", endTime=" + this.getEndTime() + ")";
    }
}

