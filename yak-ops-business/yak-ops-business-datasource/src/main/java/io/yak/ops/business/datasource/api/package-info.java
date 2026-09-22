/**
 * 数据源模块的对外契约：跨模块端口（{@code DataSourceReferenceProvider}）与对外事件
 * （{@code DataSourceChangedEvent}）。
 *
 * <p>本包不 import 数据源模块的任何内部 package，因此下游只依赖这里就能保持稳定；契约内容可以加字段，但改名或改语义需要先确认下游。
 *
 * <p>订阅 {@code DataSourceChangedEvent} 请使用
 * {@code @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)}；普通 {@code @EventListener}
 * 会在事务提交前触发，下游会看到回滚前的数据源状态。
 */
package io.yak.ops.business.datasource.api;
