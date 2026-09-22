package io.yak.ops.business.agent.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 仅在 AI 分析智能体模块显式启用（yak.agent.enabled=true）时装配相关 Bean。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(
    prefix = "yak.agent",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public @interface ConditionalOnAgentEnabled {}
