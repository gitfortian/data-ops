# Yak Ops Resource Domain

## 1. Core Model

Resource 领域刻意区分“资源是谁”和“资源字节在哪里”。

```text
                         ResourceNode
                    metadata / namespace truth
                              |
              +---------------+---------------+
              |               |               |
              v               v               v
        ResourcePath    ResourceRevision   Storage Binding
        parent/name       version          storageType
        fullPath          checksum         storagePath
        nodeType          fileSize
                          contentType
                              |
                              v
                       Physical Bytes
                              |
                              v
                    ResourceStorageGateway
                              |
                              v
                       StorageOperator SPI
```

数据库和 Storage Plugin 是两个不同的事实边界，不能互相替代。

## 2. Truth Ownership

| Truth | Owner | 说明 |
|---|---|---|
| Resource ID | Resource Metadata / Repository | 长生命周期业务身份 |
| parentId / name / fullPath | Resource Namespace | 当前树结构与路径 |
| FILE / DIRECTORY | Resource Namespace | 当前节点类型 |
| storageType / storagePath | Resource Metadata | 物理对象定位 binding |
| current version | Resource Revision Metadata | 当前 fencing/revision |
| checksum / fileSize / contentType | Resource Revision Metadata | 当前内容描述 |
| physical bytes | Storage Plugin | Local / MinIO / HDFS 等实际字节 |
| installed storage capability | ResourceStorageRegistry | 已安装 StorageOperator 的运行时注册表 |
| temporary resolved file | Resolution | 一次消费过程的临时物化结果 |
| external Git/sync projection | Resource Sync Provider | Resource 真相的外部投影，不是 Owner |
| HTTP DTO / VO | Controller Boundary | 传输模型，不是 Domain |

## 3. ResourceNode

`ResourceNode` 表示 Resource 当前 Metadata/Namespace 状态。

核心属性分为四组：

### Identity / Namespace

```text
id
parentId
name
fullPath
nodeType
```

### Storage Binding

```text
storageType
storagePath
```

### Current Revision Metadata

```text
contentType
suffix
fileSize
checksum
version
```

### Projection / Audit Metadata

```text
gitSyncStatus
createTime
updateTime
description
```

`ResourceNode` 不包含真实文件 bytes。

## 4. ResourcePath

`ResourcePath` 是 immutable value object，统一表达逻辑 Resource Path。

规则：

- 统一以 `/` 开头；
- 非根路径去除尾部 `/`；
- `child(name)` 只接收已通过 Name Policy 的名称；
- `storagePath()` 把 `/jobs/demo.sql` 映射为 `jobs/demo.sql`；
- `isDescendantOf` 用于防止把目录移动到自身后代；
- suffix 从资源名称稳定推导。

Resource Path 是 Namespace 语义，不由具体 Storage Plugin 决定。

## 5. ResourceRevision

`ResourceRevision` 表示 **当前 Revision**，而不是历史版本实体。

```text
ResourceRevision(
    version,
    checksum,
    fileSize,
    contentType)
```

### Revision identity rule

```text
same resourceId + higher version
    = 同一个 Resource 的更新 revision

NOT

version 1/2/3/4/5
    = 五个仍然可以任意读取的 immutable historical blobs
```

当前 `download(resourceId, requestedVersion)` 是 fencing：

```text
requestedVersion == currentVersion
    -> 可以读取当前内容

requestedVersion != currentVersion
    -> version mismatch
```

如果未来需要历史版本，必须新增独立的 Version/Blob truth，而不能重新解释现在的 `version` 字段。

## 6. Directory vs File

### DIRECTORY

拥有：

- Namespace identity；
- child relationship；
- Storage directory binding；
- move/delete lifecycle。

不拥有：

- downloadable content；
- editable text content。

### FILE

在 Namespace identity 之外，还拥有当前内容 revision metadata，并可通过 Storage Gateway 读取/写入物理字节。

## 7. ResourceContent and ResourceDownload

`ResourceContent` 是在线文本读取的 read model：

```text
resourceId
fullPath
content
skipLineNum
lineCount
hasMore
```

它不是持久化实体。

`ResourceDownload` 是一次下载流：

```text
fileName
contentType
fileSize
InputStream
```

它同样不是持久化实体，也不应该进入 Repository contract。

## 8. ResourceStoragePlugin

`ResourceStoragePlugin` 是 Storage Registry 的领域视图：

```text
type
name
active
```

它只描述已安装能力，不暴露 `StorageOperator` 对象本身。

## 9. ResourceTreeNode

`ResourceTreeNode` 是树查询 read model，用于在 Domain/Read Side 组合 Resource 层级。

它不是第二份 Namespace Truth。真正的父子关系仍来自 Repository 中每个 `ResourceNode.parentId`。

## 10. Command Ownership

### Namespace Command

`ResourceNamespaceManager` 拥有：

- create directory；
- rename / metadata update；
- move；
- recursive metadata delete。

它可以编排 Repository、Storage Gateway、Storage Lifecycle 与 Change Dispatcher，但不直接操作 `StorageOperator`。

### Content Command

`ResourceContentManager` 拥有：

- upload；
- online create；
- replace file；
- update editable content。

它维护当前 content revision metadata，但不拥有 Namespace 查询实现。

## 11. Read Ownership

```text
ResourceNamespaceReader
    -> identity / child list / page / require FILE

ResourceTreeReader
    -> tree projection

ResourceContentReader
    -> text content / download stream

ResourceStorageReader
    -> installed storage plugin views
```

Reader 不产生业务 mutation。

## 12. Storage Consistency Semantics

Resource 不是分布式事务系统，因此当前通过明确的顺序与补偿语义维持一致性。

### Create / Upload

```text
validate
  -> write Storage
  -> insert Metadata
      -> success: afterCommit Sync
      -> failure: best-effort cleanup Storage object
```

### Move

```text
validate target
  -> move Storage object
  -> update Resource + descendants Metadata
      -> success: afterCommit Sync
      -> failure: best-effort move Storage back
```

### Delete

```text
delete Metadata in transaction
  -> commit
  -> delete Storage object best-effort
  -> propagate Sync best-effort
```

这些顺序本身是 Domain Contract，不能在普通结构重构中改变。

## 13. Resolution

Resolution 是消费适配层，不是 Resource Domain Owner。

```text
ResourceResolver SPI
        |
        v
ResourceResolverAdapter
        |
        v
ResourceDownloadProvider SPI
        |
        v
ResourceDownloadProviderAdapter
        |
        +-> ResourceNamespaceReader
        +-> ResourceContentReader
```

它负责：

- revision fencing；
- stream -> local temp file；
- SHA-256 verify；
- failure cleanup；
- 输出 `ResolvedResource`。

临时文件生命周期不能反向修改 Resource Metadata。

## 14. Sync

`ResourceChangeDispatcher` 把已经提交的 Resource 变化转成跨模块 Sync Context。

```text
Resource truth committed
       -> CREATED / UPDATED / MOVED / DELETED
       -> ResourceFileSyncProvider(s)
```

Sync 失败不会改变 Resource 真相。

## 15. Boundary Summary

必须长期保持：

```text
ResourceNode != Physical Bytes
ResourceRevision != Historical Version Store
ResourceTreeNode != Namespace Truth
ResolvedResource != ResourceNode
Sync Projection != Resource Truth
StorageOperator != Resource Business API
DTO / VO != Domain
PO != Domain
```
