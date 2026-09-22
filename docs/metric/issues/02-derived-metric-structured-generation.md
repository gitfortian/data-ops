# 02: 派生指标结构化生成(修饰词受控 + 自动组装)

**对应需求:** 指标中心盘点 §3.2/§6(P0)| 阶段: P0

**What to build:** 当前派生指标是**登记式**:表单只选一个原子指标+手填 dimConstraint 自由文本,MetricEditModal 选中原子后仅带出业务过程,单位/口径只读展示**不写入派生自身字段**,派生的 measureExpr/filterExpr/modelId 均为空;全仓无"修饰词/业务限定"受控对象(semantic 六类标准中无修饰词类)。"原子+修饰词=派生"的方法论未兑现,并连带导致 MTR→MOD 反推草稿吃不到派生(§3.10)。本单:①修饰词/业务限定升格为受控对象——裁决归 semantic 新增一类标准,或 metric 内结构化条件模型(字段+运算符+值);②派生定义由"原子表达式+限定条件"**自动组装**出 measureExpr/filterExpr,口径/单位真正继承;③详情页展示继承链与限定条件(展示半 step 可与 06 合流)。

**模块归属:** **跨模块**(视裁决)——metric(结构化条件与组装逻辑)+ semantic(若修饰词入标准体系则涉其 CRUD/选择器)

**Blocked by:** 裁决先行(修饰词归属);与 06 共详情页展示面,建议 06 先行打底

**Status:** 代码完成(2026-09-22，单测 35/35 绿+tsc 基线 182 不变；页面实测随批次统一验收)

**硬性约束(不可打破):** 遵守 [gap-backlog 批次约束](gap-backlog-2026-09.md);存量派生指标(自由文本 dimConstraint)必须兼容不迁移失败——新字段可空、旧文本保留展示;派生仍限引用 ATOMIC(现校验不动);组装产物(表达式)需可被 03 编译消费,格式定稿即契约。

- [x] 裁决:修饰词=**metric 内结构化条件**(字段+运算符+值),不入 semantic 标准体系——结论已落 05 乱象清单 R-11(04 图文字版);理由:限定条件是取数条件而非标准模板,入标准体系侵入面大收益低,跨指标复用需求出现时再升级(qualifiers_json 即迁移来源)
- [x] 派生表单:MetricEditModal 结构化限定条件编辑器(Form.List 行=字段 Select(候选=原子来源模型字段,能选不填)+运算符 Select(=/!=/>/>=/</<=/IN/LIKE/BETWEEN)+值 Input),自由文本 dimConstraint 输入框移除,存量值降级为只读展示框(添加结构化限定后替代)
- [x] 后端:V3 迁移 `qualifiers_json JSON NULL`;`DerivedMetricAssembler` 组装——measureExpr/modelId 继承原子、filterExpr=原子 filter AND 限定条件编译(标准 SQL 谓词,数字裸写/字符串单引号转义/IN 逗号列表/BETWEEN 双边界),caliberId/unitId/calRule/domainId/processId 留空时继承并落库;qualifiersJson 空=不组装(存量登记式原样),原子缺 measureExpr 拒绝组装;VO/快照/依赖登记同步
- [x] 详情页:限定条件 chips(JSON→Tag 组)、DERIVED 增"度量表达式(组装)/过滤条件(组装)"行、计算规则按组装产物展示、引用原子指标链已有(06 已接 refMetric 跳转);存量自由文本标注"(存量自由文本)"
- [x] 建模反推:metricDraft 不按类型过滤、上游取 modelId、度量取 measureExpr——组装落库后派生自动进入覆盖面,零改动(§3.10 同源短板闭环)
- [x] 单测:DerivedMetricAssemblerTest 9 例(组装正确性/算子编译契约/继承优先级/原子无表达式拒绝/JSON 容错/存量自由文本兼容两分支/空白占位行不算限定);MetricCatalogServiceTest 14 例、全模块 35/35 绿
- [ ] 待实测(批次统一):新建派生选原子+加限定→详情可见组装表达式与 chips;编辑存量自由文本派生不丢旧值;该派生出现在建模反推草稿
