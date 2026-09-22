# Yak Ops Resource Review Guide

Resource 代码评审不只检查“能不能工作”，还要确认 Namespace、Content、Storage 和外部投影没有重新混成一套模糊状态。

## 1. Truth Ownership

每个 PR 先回答：新增状态由谁拥有？

- Resource identity / parent / name / path -> Resource Namespace / Repository；
- current revision / checksum / size / contentType -> Resource Metadata；
- physical bytes -> Storage Plugin；
- temporary local file -> Resolution；
- Git / external sync state -> external projection。

出现同一事实被两个组件都当成 authoritative source 时应阻止合并。

## 2. Revision Review

检查任何与 `version` 有关的代码：

- 是否仍把它定义为 current revision / fencing？
- 是否错误实现了“任意历史 version 都可下载”的假象？
- requested version mismatch 是否明确失败？
- 内容/路径 mutation 是否沿用当前 revision 递增语义？

如果需求真的是历史版本，必须单独引入 immutable Version/Blob 设计，而不是扩展当前字段。

## 3. Namespace Review

目录/路径相关变更检查：

- 同 parent name uniqueness 是否仍存在？
- 名称是否经过 `ResourceNamePolicy`？
- 路径是否通过 `ResourcePath` 计算？
- move 是否禁止移动到自身/后代？
- directory move 是否更新 descendants？
- cross-storage move 是否仍明确拒绝？
- Namespace Manager 是否绕过 Repository/Gateway 直接访问 DAO/StorageOperator？

## 4. Content Review

上传、替换、在线编辑检查：

- `MultipartFile` 是否停在 Controller mapper/binary source boundary？
- size/editable/content-type 约束是否由 Content Policy 统一处理？
- SHA-256 是否仍是 checksum contract？
- Content Manager 是否只通过 `ResourceStorageGateway` 读写 Storage？
- Content Reader 是否保持无 mutation？
- FILE/DIRECTORY capability 校验是否仍存在？

## 5. Consistency Review

### Create / Upload

必须保持：

```text
Storage write -> Metadata insert
Metadata failure -> best-effort Storage cleanup
```

### Move

必须保持：

```text
Storage move -> Metadata/descendant update
Metadata failure -> best-effort Storage rollback
```

### Delete

必须保持：

```text
Metadata delete commit -> Storage delete afterCommit
```

不要在结构重构中擅自交换这些顺序。

## 6. Storage Boundary Review

检查 Namespace / Content：

- 不允许 import `StorageOperator`；
- 不允许直接 catch `StoragePluginException`；
- 只能使用 `ResourceStorageGateway` / `ResourceStorageLifecycle`；
- 新 Storage capability 应先扩展 Resource-owned Gateway，再由 Adapter 翻译到 SPI。

检查 Registry：

- duplicate type 是否仍失败；
- default storage 是否有明确 resolution；
- Reader 是否只返回 `ResourceStoragePlugin`，不泄漏 Operator。

## 7. Resolution Review

检查 `resolution`：

- 是否只走 Namespace/Content read-side？
- 是否绕过 read-side 直接访问 Repository/DAO？
- revision fencing 是否存在？
- stream copy 是否同步计算 SHA-256？
- checksum mismatch 是否失败并清理 temp dir？
- 失败路径是否 best-effort cleanup？
- Resolution 是否意外产生 Resource mutation？

## 8. Sync Review

检查：

- mutation 是否经 `ResourceChangeDispatcher`；
- 是否在 transaction commit 后传播；
- provider failure 是否只记录 warning/运行证据；
- Sync Provider 是否试图成为 Resource Metadata owner；
- Namespace/Content 是否直接依赖具体 Provider。

## 9. Controller Review

Controller 只做：

- HTTP validation/annotation；
- DTO -> Command/BinarySource mapping；
- 调用明确的 Manager/Reader；
- Domain -> VO mapping；
- download stream -> response。

Controller 不做：

- Repository/DAO 查询；
- StorageOperator 调用；
- Path/revision 业务决策；
- transaction choreography；
- Sync provider 调度。

HTTP Exception Advice 也属于 Controller boundary。

## 10. Persistence Review

- Repository contract 是否仍只暴露 Domain / shared `PageData`？
- Repository Adapter 是否显式处理 Domain <-> PO translation？
- DAO 是否没有 DTO/VO？
- PO 是否没有进入业务子系统？
- 新 SQL 是否真的需要 XML，而不是为了“分层完整”制造无价值抽象？
- `yak_ops_resource` 与现有 Flyway history 是否未被结构重构误改？

## 11. Package / Role Review

看到新类时根据职责选择：

| Role | Review question |
|---|---|
| Manager | 它是否真正拥有一个 mutation lifecycle？ |
| Reader | 是否保持无副作用？ |
| Resolver | 是否只做 reference/materialization resolution？ |
| Policy | 是否只做约束/决策，不持有 mutable truth？ |
| Gateway | 是否是 Resource-owned external capability Port？ |
| Adapter | 是否只翻译边界模型？ |
| Registry | 是否只管理已安装 capability？ |
| Lifecycle | 是否只处理 compensation/afterCommit lifecycle？ |
| Dispatcher | 是否只传播已完成变化？ |
| Repository | 是否只表达 Domain persistence？ |

出现 `Support/Helper/Utils/Common/Base/ServiceImpl` 时，应要求作者解释无法归类的真实原因。

## 12. Compatibility Review

Stage 2 / architecture-only PR 默认不得改变：

- `/api/v1/resources/**`；
- permission annotations/codes；
- DTO/VO JSON；
- `yak_ops_resource` schema；
- Flyway baseline/history table；
- StorageOperator SPI；
- ResourceResolver / ResourceDownloadProvider / ResourceFileSyncProvider SPI；
- upload/create/move/delete consistency order；
- current revision semantics；
- SHA-256 verification。

## 13. Test Review

至少检查：

- focused behavior test；
- `ResourceDependencyBoundaryTest`；
- `ResourceLayeringConventionTest`；
- `ResourceCodeStyleConventionTest`；
- `ResourceRoleConventionTest`；
- Controller contract test；
- Domain revision/path tests；
- Storage registry tests。

架构测试失败时优先修设计，不要直接把 forbidden list 或 allowed matrix 放宽。

## 14. Final Consistency Gate

合并前确认以下五项表达同一套架构：

```text
Code
  <-> REQUIREMENTS.md
  <-> DOMAIN.md
  <-> ARCHITECTURE.md
  <-> DEPENDENCIES.md
  <-> Executable Architecture Tests
```

任一处不一致都视为 Stage 2 未完成。