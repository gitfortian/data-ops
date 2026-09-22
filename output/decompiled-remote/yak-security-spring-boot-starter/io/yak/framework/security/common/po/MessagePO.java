/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.baomidou.mybatisplus.annotation.TableField
 *  com.baomidou.mybatisplus.annotation.TableName
 *  lombok.Generated
 */
package io.yak.framework.security.common.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.framework.security.common.po.BasePO;
import java.util.Date;
import lombok.Generated;

@TableName(value="yak_security_message")
public class MessagePO
extends BasePO {
    private String title;
    private String summary;
    private String content;
    @TableField(value="message_type")
    private String type;
    @TableField(value="message_level")
    private String level;
    @TableField(value="message_scope")
    private String scope;
    private Long projectId;
    private String sourceType;
    private String sourceId;
    private String actionPath;
    private Boolean readTag;
    private Date readTime;
    private Long oplogId;
    private Long userId;

    @Generated
    public String getTitle() {
        return this.title;
    }

    @Generated
    public String getSummary() {
        return this.summary;
    }

    @Generated
    public String getContent() {
        return this.content;
    }

    @Generated
    public String getType() {
        return this.type;
    }

    @Generated
    public String getLevel() {
        return this.level;
    }

    @Generated
    public String getScope() {
        return this.scope;
    }

    @Generated
    public Long getProjectId() {
        return this.projectId;
    }

    @Generated
    public String getSourceType() {
        return this.sourceType;
    }

    @Generated
    public String getSourceId() {
        return this.sourceId;
    }

    @Generated
    public String getActionPath() {
        return this.actionPath;
    }

    @Generated
    public Boolean getReadTag() {
        return this.readTag;
    }

    @Generated
    public Date getReadTime() {
        return this.readTime;
    }

    @Generated
    public Long getOplogId() {
        return this.oplogId;
    }

    @Generated
    public Long getUserId() {
        return this.userId;
    }

    @Generated
    public void setTitle(String title) {
        this.title = title;
    }

    @Generated
    public void setSummary(String summary) {
        this.summary = summary;
    }

    @Generated
    public void setContent(String content) {
        this.content = content;
    }

    @Generated
    public void setType(String type) {
        this.type = type;
    }

    @Generated
    public void setLevel(String level) {
        this.level = level;
    }

    @Generated
    public void setScope(String scope) {
        this.scope = scope;
    }

    @Generated
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    @Generated
    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    @Generated
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    @Generated
    public void setActionPath(String actionPath) {
        this.actionPath = actionPath;
    }

    @Generated
    public void setReadTag(Boolean readTag) {
        this.readTag = readTag;
    }

    @Generated
    public void setReadTime(Date readTime) {
        this.readTime = readTime;
    }

    @Generated
    public void setOplogId(Long oplogId) {
        this.oplogId = oplogId;
    }

    @Generated
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @Override
    @Generated
    public String toString() {
        return "MessagePO(super=" + super.toString() + ", title=" + this.getTitle() + ", summary=" + this.getSummary() + ", type=" + this.getType() + ", level=" + this.getLevel() + ", scope=" + this.getScope() + ", projectId=" + this.getProjectId() + ", sourceType=" + this.getSourceType() + ", sourceId=" + this.getSourceId() + ", actionPath=" + this.getActionPath() + ", readTag=" + this.getReadTag() + ", readTime=" + String.valueOf(this.getReadTime()) + ", oplogId=" + this.getOplogId() + ", userId=" + this.getUserId() + ")";
    }

    @Override
    @Generated
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MessagePO)) {
            return false;
        }
        MessagePO other = (MessagePO)o;
        if (!other.canEqual(this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        Long this$projectId = this.getProjectId();
        Long other$projectId = other.getProjectId();
        if (this$projectId == null ? other$projectId != null : !((Object)this$projectId).equals(other$projectId)) {
            return false;
        }
        Boolean this$readTag = this.getReadTag();
        Boolean other$readTag = other.getReadTag();
        if (this$readTag == null ? other$readTag != null : !((Object)this$readTag).equals(other$readTag)) {
            return false;
        }
        Long this$oplogId = this.getOplogId();
        Long other$oplogId = other.getOplogId();
        if (this$oplogId == null ? other$oplogId != null : !((Object)this$oplogId).equals(other$oplogId)) {
            return false;
        }
        Long this$userId = this.getUserId();
        Long other$userId = other.getUserId();
        if (this$userId == null ? other$userId != null : !((Object)this$userId).equals(other$userId)) {
            return false;
        }
        String this$title = this.getTitle();
        String other$title = other.getTitle();
        if (this$title == null ? other$title != null : !this$title.equals(other$title)) {
            return false;
        }
        String this$summary = this.getSummary();
        String other$summary = other.getSummary();
        if (this$summary == null ? other$summary != null : !this$summary.equals(other$summary)) {
            return false;
        }
        String this$content = this.getContent();
        String other$content = other.getContent();
        if (this$content == null ? other$content != null : !this$content.equals(other$content)) {
            return false;
        }
        String this$type = this.getType();
        String other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
            return false;
        }
        String this$level = this.getLevel();
        String other$level = other.getLevel();
        if (this$level == null ? other$level != null : !this$level.equals(other$level)) {
            return false;
        }
        String this$scope = this.getScope();
        String other$scope = other.getScope();
        if (this$scope == null ? other$scope != null : !this$scope.equals(other$scope)) {
            return false;
        }
        String this$sourceType = this.getSourceType();
        String other$sourceType = other.getSourceType();
        if (this$sourceType == null ? other$sourceType != null : !this$sourceType.equals(other$sourceType)) {
            return false;
        }
        String this$sourceId = this.getSourceId();
        String other$sourceId = other.getSourceId();
        if (this$sourceId == null ? other$sourceId != null : !this$sourceId.equals(other$sourceId)) {
            return false;
        }
        String this$actionPath = this.getActionPath();
        String other$actionPath = other.getActionPath();
        if (this$actionPath == null ? other$actionPath != null : !this$actionPath.equals(other$actionPath)) {
            return false;
        }
        Date this$readTime = this.getReadTime();
        Date other$readTime = other.getReadTime();
        return !(this$readTime == null ? other$readTime != null : !((Object)this$readTime).equals(other$readTime));
    }

    @Override
    @Generated
    protected boolean canEqual(Object other) {
        return other instanceof MessagePO;
    }

    @Override
    @Generated
    public int hashCode() {
        int PRIME = 59;
        int result = super.hashCode();
        Long $projectId = this.getProjectId();
        result = result * 59 + ($projectId == null ? 43 : ((Object)$projectId).hashCode());
        Boolean $readTag = this.getReadTag();
        result = result * 59 + ($readTag == null ? 43 : ((Object)$readTag).hashCode());
        Long $oplogId = this.getOplogId();
        result = result * 59 + ($oplogId == null ? 43 : ((Object)$oplogId).hashCode());
        Long $userId = this.getUserId();
        result = result * 59 + ($userId == null ? 43 : ((Object)$userId).hashCode());
        String $title = this.getTitle();
        result = result * 59 + ($title == null ? 43 : $title.hashCode());
        String $summary = this.getSummary();
        result = result * 59 + ($summary == null ? 43 : $summary.hashCode());
        String $content = this.getContent();
        result = result * 59 + ($content == null ? 43 : $content.hashCode());
        String $type = this.getType();
        result = result * 59 + ($type == null ? 43 : $type.hashCode());
        String $level = this.getLevel();
        result = result * 59 + ($level == null ? 43 : $level.hashCode());
        String $scope = this.getScope();
        result = result * 59 + ($scope == null ? 43 : $scope.hashCode());
        String $sourceType = this.getSourceType();
        result = result * 59 + ($sourceType == null ? 43 : $sourceType.hashCode());
        String $sourceId = this.getSourceId();
        result = result * 59 + ($sourceId == null ? 43 : $sourceId.hashCode());
        String $actionPath = this.getActionPath();
        result = result * 59 + ($actionPath == null ? 43 : $actionPath.hashCode());
        Date $readTime = this.getReadTime();
        result = result * 59 + ($readTime == null ? 43 : ((Object)$readTime).hashCode());
        return result;
    }
}

