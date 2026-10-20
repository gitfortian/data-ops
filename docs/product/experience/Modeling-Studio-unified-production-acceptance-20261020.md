# 数仓建模统一工作台 · 正式 React 整页交付验收基线

日期：2026-10-20。范围：data-ops 已签收用户故事 #513、IA-550、R12-DWD、R13-DWS/ADS。一个整体交付 PR；不以静态 HTML 或 API 单点完成代替整个界面的完成。

## 用户可见成果

1. 模型总览：统一页头和导航、三步真实任务入口、已配置分层切换、服务端分页模型库、搜索筛选和项目上下文。
2. 逻辑模型：业务过程上下文、草稿目录、实体属性与关系设计、版本冻结、DWD 映射回读。
3. 物理模型：将十个平铺 Tab 整合成「设计工作台」「版本与关联」「模型资料」；保留原有业务 URL 和未保存修改保护。
4. ODS：源表核对、ODS 目标、保存及返回来源；资产治理不是强制中转。
5. DWD/DIM：保留真实物理字段编辑、映射和标准核验、历史与授权出口。
6. DWS/ADS：完整六步专项设计是主工作区域；底层物理表结构次级按需展开，核验报告能反向展开定位字段。
7. 所有页面共享项目 SiteLayout 和同一 UI token，不生成假阶段完成度，不新增重复 Owner。

## 实际代码落点

- data-ops-ui/src/pages/modeling/index.tsx
- data-ops-ui/src/pages/modeling/logical.tsx
- data-ops-ui/src/pages/modeling/unified/index.tsx
- data-ops-ui/src/pages/modeling/detail.tsx
- data-ops-ui/src/pages/modeling/components/ModelingStudioHeader.tsx
- data-ops-ui/src/pages/modeling/components/ModelingStudioHeader.less
- data-ops-ui/src/pages/modeling/components/ModelingStartGuide.tsx

## 全页验收检查

| 场景 | 通过条件 |
|---|---|
| /modeling | 真实目录、搜索/分层、权限、空态和创建/回读正常 |
| /modeling/logical | 逻辑草稿保存、业务关系、冻结和映射工作区可操作 |
| /modeling/models/:id | 新版统一任务区可见，旧深链按当前权限保护，不丢未保存更改 |
| ODS | 源表定位、保存、返回、重开同一映射 |
| DWD/DIM | 逐列标准缺口定位、映射冻结和历史真实回读 |
| DWS | 真实服务端 V0/固定样本 V1/未执行 V2 证据边界准确 |
| ADS | 输出合同保存/历史回读/未连接证据真实 |
| UI | 1440/1024/768/390 px 实际浏览器截图与交互、键盘焦点、表格响应式 |
| 工程 | Scoped Jest、TS baseline、release build、Product Guard、Required Quality 全绿 |

## 禁止越界与完成定义

本 PR 只对已具备领域后端能力的正式 UI 进行整体重排。R14-2 / PR #635 仍 Draft，不能借 UI 设计打开生产发布门禁，不新增未经授权的 DataDev、Runtime、SQL/DDL 或目标数据写入。设计版本不代表部署和消费授权。

浏览器实际验证、全 CI、设计与功能的逐页对照均完成后才能申请 UX 产品签收；只有静态测试通过不得声称整页交付完成。