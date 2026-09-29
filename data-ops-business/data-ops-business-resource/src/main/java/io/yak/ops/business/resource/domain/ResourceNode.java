package io.yak.ops.business.resource.domain;

import io.yak.ops.common.enums.resource.ResourceNodeType;
import io.yak.ops.common.enums.resource.ResourceStorageType;
import java.time.LocalDateTime;
import java.util.Objects;

/** Resource directory/file metadata domain object, independent from persistence and HTTP models. */
public class ResourceNode {

  private Long id;
  private Long projectId;
  private Long parentId;
  private String name;
  private String fullPath;
  private ResourceNodeType nodeType;
  private ResourceStorageType storageType;
  private String storagePath;
  private String contentType;
  private String suffix;
  private Long fileSize;
  private String checksum;
  private String description;
  private Integer version;
  private String gitSyncStatus;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getProjectId() {
    return projectId;
  }

  public void setProjectId(Long projectId) {
    if (projectId != null && projectId <= 0L) {
      throw new IllegalArgumentException("projectId must be positive");
    }
    this.projectId = projectId;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getFullPath() {
    return fullPath;
  }

  public void setFullPath(String fullPath) {
    this.fullPath = fullPath;
  }

  public ResourceNodeType getNodeType() {
    return nodeType;
  }

  public void setNodeType(ResourceNodeType nodeType) {
    this.nodeType = nodeType;
  }

  public ResourceStorageType getStorageType() {
    return storageType;
  }

  public void setStorageType(ResourceStorageType storageType) {
    this.storageType = storageType;
  }

  public String getStoragePath() {
    return storagePath;
  }

  public void setStoragePath(String storagePath) {
    this.storagePath = storagePath;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public String getSuffix() {
    return suffix;
  }

  public void setSuffix(String suffix) {
    this.suffix = suffix;
  }

  public Long getFileSize() {
    return fileSize;
  }

  public void setFileSize(Long fileSize) {
    this.fileSize = fileSize;
  }

  public String getChecksum() {
    return checksum;
  }

  public void setChecksum(String checksum) {
    this.checksum = checksum;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public String getGitSyncStatus() {
    return gitSyncStatus;
  }

  public void setGitSyncStatus(String gitSyncStatus) {
    this.gitSyncStatus = gitSyncStatus;
  }

  public LocalDateTime getCreateTime() {
    return createTime;
  }

  public void setCreateTime(LocalDateTime createTime) {
    this.createTime = createTime;
  }

  public LocalDateTime getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(LocalDateTime updateTime) {
    this.updateTime = updateTime;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof ResourceNode other)) {
      return false;
    }
    return Objects.equals(id, other.id)
        && Objects.equals(projectId, other.projectId)
        && Objects.equals(parentId, other.parentId)
        && Objects.equals(name, other.name)
        && Objects.equals(fullPath, other.fullPath)
        && nodeType == other.nodeType
        && storageType == other.storageType
        && Objects.equals(storagePath, other.storagePath)
        && Objects.equals(contentType, other.contentType)
        && Objects.equals(suffix, other.suffix)
        && Objects.equals(fileSize, other.fileSize)
        && Objects.equals(checksum, other.checksum)
        && Objects.equals(description, other.description)
        && Objects.equals(version, other.version)
        && Objects.equals(gitSyncStatus, other.gitSyncStatus)
        && Objects.equals(createTime, other.createTime)
        && Objects.equals(updateTime, other.updateTime);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        projectId,
        parentId,
        name,
        fullPath,
        nodeType,
        storageType,
        storagePath,
        contentType,
        suffix,
        fileSize,
        checksum,
        description,
        version,
        gitSyncStatus,
        createTime,
        updateTime);
  }

  @Override
  public String toString() {
    return "ResourceNode{" +
        "id=" + id +
        ", projectId=" + projectId +
        ", parentId=" + parentId +
        ", name='" + name + '\'' +
        ", fullPath='" + fullPath + '\'' +
        ", nodeType=" + nodeType +
        ", storageType=" + storageType +
        ", storagePath='" + storagePath + '\'' +
        ", contentType='" + contentType + '\'' +
        ", suffix='" + suffix + '\'' +
        ", fileSize=" + fileSize +
        ", checksum='" + checksum + '\'' +
        ", description='" + description + '\'' +
        ", version=" + version +
        ", gitSyncStatus='" + gitSyncStatus + '\'' +
        ", createTime=" + createTime +
        ", updateTime=" + updateTime +
        '}';
  }
}
