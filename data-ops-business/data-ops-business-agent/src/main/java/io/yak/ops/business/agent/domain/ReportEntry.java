package io.yak.ops.business.agent.domain;

import java.time.LocalDateTime;

/** 报告条目（不含正文）。内容真相归 yak_agent_report，正文按需单独读取。 */
public record ReportEntry(
    long id,
    String sessionId,
    String title,
    LocalDateTime createTime,
    LocalDateTime updateTime) {}
