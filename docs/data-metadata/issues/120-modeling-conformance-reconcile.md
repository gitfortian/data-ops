# Ticket 120：modeling 符合性对账（消费方 #1，全方案 ROI 最高的一节）

**对应需求：** 元数据建模 | **阶段：** P3 | **模块：** **modeling**（判定规则属建模域知识）+ metadata（只出 API）

**What to build：** 模型列 vs 物理列的三方差异报告 + 发布前校验挂钩。**"标准落地校验"从口号变成可执行的地方**——此前指出的"标准落地校验缺失"，缺的就是物理元数据这块拼图。

**Blocked by：** 118、119

**为什么不放在元数据模块**（plan §5.2）：元数据只出物理列（事实）。判定规则放元数据会让它反向依赖 semantic + modeling，破坏"元数据是下层事实供给方"的定位。

**差异矩阵（按列）**
| 差异 | 判据 | 处置 |
|---|---|---|
| `MISSING_IN_PHYSICAL` | 模型有、物理无 | **阻断发布** |
| `MISSING_IN_MODEL` | 物理有、模型无 | 提示补登（不阻断）+ 生成一条 `yak_md_task` |
| `TYPE_MISMATCH` | 两侧都有、型不兼容 | 警告，附兼容表（`int→bigint` OK，反向不 OK） |
| `NULLABILITY_MISMATCH` | 物理 NOT NULL、模型允许空 | 警告 |
| `PK_MISMATCH` | 主键标记不一致 | 提示 |
| `COMMENT_EMPTY` | 物理无注释 | 计入治理缺口（不阻断） |
| `STD_UNGOVERNED` | 未命中标准字段 | 生成 `yak_md_task` 待办 |

**验收清单**
- [ ] **复用既有 `StandardFieldMatcher`，禁止另写一份匹配规则**
- [ ] **沿用它的 `isAuthoritative()` 口径**（`modeling/governance/StandardFieldMatcher.java:35-38`：只有 `exact`/`comment` 算命中，`fuzzy` 只作建议）。该类 javadoc 明写"故意不做同类型即命中"——*"错标的治理状态是静默的；留成未命中可被修正，代价远小于误标"*。**这个判断必须继承，不得为了匹配率放宽**
- [ ] 差异矩阵 API + 发布前校验挂钩：加一个物理库中不存在的列 → 报 `MISSING_IN_PHYSICAL` 且**发布被阻断**；物理库新增一列 → 报 `MISSING_IN_MODEL`、不阻断、生成待办
- [ ] 模糊匹配出的标准字段**只能作为建议**出现，**不得自动落库为关联**
- [ ] 元数据侧只提供 `type_name` 过滤的读接口，**不新建任何比对表**
