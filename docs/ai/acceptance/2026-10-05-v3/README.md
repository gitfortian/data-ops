# 第三版自动化与待验收记录

日期：2026-10-05。Feature F-011：IMPLEMENTING；真实模型按用户确认单列待完成。自动化通过不代表真实模型验收。

本轮工程证据：

- 真正 AgentScope + 脚本模型：禁止/未知工具不调用委托、DENIED 与 toolCallId 进入 StateStore 历史。
- 两任务并行、原子预占、持久化故障、HITL 冻结预算恢复、目标变更拒绝、晚到取消回调、模型输入超限；错误目标在身份/源域调用前拒绝。
- 同题集 12 个任务策略 probe；其他工具/候选/授权沿用已有回归。
- Skill 活目录和加载、更新/停用/删除/冷启动、保留逻辑 ID、版本 CAS / 过时编辑 / 删除不复活。
- 配置启动/动态/整型溢出/未接入展示与 API 拒绝；前端清空/未编辑保存/预留只读/Skill 编辑版本。
- Node 执行器：SSE 分块、脱敏、未知 usage、未完成流取消、无绑定零请求；离线全部 NOT_RUN。

执行命令：

```sh
mvn -pl data-ops-business/data-ops-business-agent -am test
cd data-ops-ui
npm exec jest -- src/pages/ai-agent --runInBand
npm run check:types
npm run build
```

Windows Maven 测试使用 `-DargLine=-Djdk.net.unixdomain.tmpdir=D:/tianxy/code/data-ops` 规避 Unix domain socket 临时路径限制。另执行 scripts/ai Node 测试/离线题集、Product baseline、后端/前端依赖边界及 diff whitespace 检查。

CI 沿用 Architecture 完整后端、完整前端与 distribution；新增 MySQL SDK budget 重新装配恢复与主体隔离测试，只在 ARCHITECTURE_MYSQL_URL 隔离数据库启用；本机没有数据库时明确 skip。官方 SDK 通用 JSON 序列化在本机验证，真实部署数据库/重启仍属于 T5。

真实模型、账号安全联调、专家事实/澄清/任务完成评分、原源审计、G1～G12 / T1～T9、多轮对照、采纳/保存/耗时/费用：**PENDING，未执行**。后续按 evaluation/README 绑定测试环境，用原应用 trace 核验实际配置哈希和服务器部署版本。
