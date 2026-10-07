# V14 工程验证与真实验收

范围：[F-022](../../../product/features/F-022-agent-report-safe-delivery.md)，交付 [IMPLEMENTATION_V14](../../IMPLEMENTATION_V14.md)。本版须在 V13 后合并，最终 PR/CI 链接提交前补充。

本版 [PR #331](https://github.com/gitfortian/data-ops/pull/331) 记录最终头与完整 Product/Architecture、后端/MySQL、前端、发行包 CI；合并不改变真实验收状态。

## 工程回归

安全导出验证标题/元数据转义、正文脚本/事件/外部资源/表单剔除、静态 CSP、GFM 表格、ECharts JSON 保留、原 Markdown 与文件名。报告页覆盖同一授权详情/ID、同步防双击、取消/卸载/权限失去、对象 URL 释放、详情关闭/切换/晚错误/失败重试、列表乱序/失败重试、读/删权限及删除后当前查询刷新。

完整前端、类型门禁、生产构建/manifest、Product/Architecture 与最终发行 CI 为合并条件。实际下载、离线文件浏览不以 jsdom 替代。

本地完整前端 134 suites / 712 tests 全通过（本版新增导出/报告读取 18 例）；类型门禁保留原 139 项诊断，无新增；生产构建/manifest、Product 基线 24 Features、Java/前端架构边界通过。后端运行代码沿用 V13 已通过的 Agent262例/本地 MySQL 条件跳过1例，最终 MySQL/完整发行包由 PR CI 核验。完整 Jest 退出码为0，出现原有一秒退出提示，新增报告测试另用 detectOpenHandles 核查。

报告与导出两套测试在 detectOpenHandles 下 18 例全部通过、正常退出且未报告残留句柄；没有使用 forceExit。

## 真实验收

RE01～RE07 全部 **PENDING**：真实报告语义、登录态本人权限、浏览器 HTML/Markdown 下载、离线打开/图表配置可读、源链接与源审计独立核验。旧 G/T/QP/RA/SC/AF/HE/NR/GP/LC/SL/EV 待办保留。按用户要求先工程交付，Feature 保持 IMPLEMENTING。

真实执行逐例记录部署/模型、用户/报告/会话 ID、原正文与文件核对、源权限拒绝/删除回读及观察结果；导出文件不等于新增分享权限或已发布报告。
