# Yak Ops Resource Requirements

本文件定义 `yak-ops-business-resource` 必须长期保持的业务行为与架构边界。它描述“Resource 模块负责什么、哪些语义不能被重构破坏”，不记录临时迁移步骤。

## 1. Scope

Resource 模块负责：

- Resource Namespace：目录、文件身份、父子关系、名称、逻辑路径与树形查询；
- Resource Content：上传、在线创建、替换、文本读取/编辑与下载；
- Resource Storage Routing：按 `ResourceStorageType` 路由到已安装 Storage Plugin；
- Resource Resolution：把资源物化为经过 checksum 校验的本地临时文件，供 Task Runtime 等外部消费者使用；
- Resource Change Propagation：在业务事务提交后向可选 `ResourceFileSyncProvider` 传播资源变更；
- Resource Metadata Persistence：维护 `yak_ops_resource` 当前元数据与 current revision。

Resource 模块不负责：

- 实现 Local / MinIO / HDFS 等具体存储系统；
- 保存任意历史 Resource 内容版本；
- 让 Git 或其它同步目标成为 Resource Namespace 的事实来源；
- 跨存储类型移动资源；
- 把外部 Storage I/O 纳入长数据库事务。

## 2. Namespace

### 2.1 Identity and hierarchy

- `ResourceNode.id` 是 Resource 当前业务身份；
- `parentId`、`name`、`fullPath` 和 `nodeType` 共同描述当前 Namespace 状态；
- 根目录使用逻辑父标识 `0`，不作为普通可下载 Resource 存储对象；
- 同一父目录下名称必须唯一；
- 目录和文件均参与 Namespace，但文件内容能力只适用于 FILE 节点。

### 2.2 Name and path safety

- 资源名称不能为空；
- 名称不能包含 `/`、`\\`、NUL、`.` 或 `..` 等路径穿越形式；
- 逻辑路径统一以 `/` 开头；
- Storage Path 由逻辑 Resource Path 稳定映射，不允许 Controller 或 Storage Plugin 自行定义另一套 Namespace。

### 2.3 Move and rename

- rename / move 必须校验目标父目录、同名冲突和后代循环；
- 当前禁止跨 `ResourceStorageType` move；
- 目录 move 必须同步更新所有后代的 `fullPath` 与 `storagePath`；
- move 的现有一致性顺序必须保持：先完成物理 Storage Move，再提交 Metadata 更新；Metadata 更新失败时尝试回滚物理路径。

### 2.4 Delete

- 目录删除包含当前节点及所有后代 Metadata；
- Metadata 删除事务先提交；
- 物理对象删除在 commit 后执行；
- commit 后 Storage 删除失败只能记录运行证据，不能把已提交的 Namespace Metadata 静默恢复成旧状态。

## 3. Content

### 3.1 Create and upload

- 上传二进制文件和在线创建文本资源都必须先验证大小、名称和父目录；
- 物理对象先写 Storage，Metadata 后写 Repository；
- Metadata 创建失败时必须 best-effort 删除刚创建的物理对象，避免无主 Storage Object；
- `MultipartFile` 只属于 HTTP 输入边界，不能进入 Repository 或 Domain contract。

### 3.2 Replace and edit

- 替换文件、在线编辑必须只作用于 FILE 节点；
- 在线文本读写受 editable suffix 与 editable max bytes 策略限制；
- 内容变更必须更新当前 size / checksum / contentType / revision 等现有 Metadata；
- SHA-256 是当前 Resource 内容完整性校验算法。

### 3.3 Read and download

- 文本内容读取保留 `skipLineNum + limit` 语义和单次最大 2000 行约束；
- 下载返回当前 Resource 的文件名、content type、size 与 InputStream；
- Reader 不修改 Namespace、Revision 或外部同步状态。

## 4. Storage

### 4.1 Ownership

Storage Plugin 只拥有物理字节和目录对象，不拥有 Resource identity / Namespace / revision truth。

业务子系统必须通过：

```text
Namespace / Content
        -> ResourceStorageGateway
        -> StorageOperatorGatewayAdapter
        -> ResourceStorageRegistry
        -> StorageOperator SPI
```

调用 Storage。`namespace` 和 `content` 不得直接依赖 `StorageOperator`。

### 4.2 Registry

- 同一个 `ResourceStorageType` 只能注册一个 Storage Operator；
- 未安装当前配置的 Storage Plugin 时必须明确失败；
- `ResourceStorageReader` 只暴露当前已安装插件的 Resource 领域视图，不返回 HTTP VO 或 StorageOperator。

## 5. Resource Revision

`Resource.version` 的定义是 **current revision / fencing value**。

```text
resourceId = 42
version    = 5
```

表示 Resource 42 当前处于 revision 5。

它不意味着系统保存并可读取：

```text
revision 1
revision 2
revision 3
revision 4
```

因此：

- 当前模块不是 historical version store；
- `ResourceDownloadProvider.download(id, version)` 只允许请求版本与当前 revision 相等；
- requested revision 与 current revision 不一致必须明确失败；
- 未来如需历史内容版本，必须单独设计 immutable Resource Version / Blob 模型，不得把当前字段语义偷偷扩展。

## 6. Resolution

`ResourceResolver` 是外部 Runtime 消费 Resource 的适配边界。

必须保持：

```text
Resource id[/revision]
      -> current metadata/content read side
      -> temporary local directory
      -> stream copy + SHA-256
      -> checksum validation
      -> ResolvedResource
```

- Resolution 不直接访问 DAO / Mapper；
- Resolution 不拥有 Resource Metadata；
- checksum 不匹配必须删除临时目录并失败；
- 失败路径应 best-effort 清理临时目录；
- 指定 revision 时沿用 current-revision fencing 语义。

## 7. Resource Sync

Resource Sync 是提交后的 **best-effort propagation**：

```text
Resource mutation transaction
          -> commit
          -> ResourceChangeDispatcher
          -> ResourceFileSyncProvider(s)
```

必须保持：

- CREATED / UPDATED / MOVED / DELETED 变更在 commit 后传播；
- Provider 失败不能回滚已经提交的 Resource Metadata；
- Sync Provider 不能成为 Namespace 或 Revision 的第二事实来源；
- Sync Context 只携带传播所需的 Resource identity / path / storage / revision 信息。

## 8. Persistence

当前 Resource Metadata 只维护：

```text
yak_ops_resource
```

要求：

- `ResourceRepository` 是业务持久化 Port，只暴露 Domain / shared `PageData`；
- `ResourceRepositoryAdapter` 负责 Domain <-> PO 映射；
- DAO / Mapper 不接收 HTTP DTO，也不返回 HTTP VO；
- MyBatis-Plus 细节不能泄漏到 Namespace / Content / Resolution；
- Resource 继续复用 Yak Business DataSource / SqlSessionFactory / TransactionManager；
- Resource Flyway 继续使用独立 `yak_resource_schema_history` 历史边界。

## 9. HTTP Compatibility

架构治理不得顺手改变：

- `/api/v1/resources/**` 路径；
- 现有 Permission Code；
- Request DTO / Response VO JSON contract；
- `bizData + pagination` 分页输出；
- 上传、下载和在线内容接口的现有 HTTP 语义。

HTTP DTO / VO 映射只允许存在于 Controller Boundary。

## 10. SPI Compatibility

不得在纯架构重构中破坏：

- `StorageOperator`；
- `ResourceResolver`；
- `ResourceDownloadProvider`；
- `ResourceFileSyncProvider`；
- 对应的 `ResolvedResource` / `ResourceDownloadResult` / Sync Context contract。

Breaking SPI 变更必须独立版本化和迁移。

## 11. Architecture Invariants

长期必须满足：

- package structure 表达 Namespace / Content / Storage / Resolution / Sync 职责；
- production 不重新创建 `service/common/helper/utils/util/base/persistence` 业务大桶；
- Controller 不直接访问 Repository / DAO / Storage SPI；
- Namespace / Content 不直接访问 DAO / PO / HTTP model / StorageOperator；
- Resolution 只通过 Resource read-side 与跨模块 SPI 工作；
- Repository 下面才允许 PO / MyBatis；
- Domain 不依赖 Spring Web、MyBatis、HTTP DTO/VO/PO；
- Resource package dependency graph 必须保持无环；
- 上述约束由 executable architecture tests 保护。