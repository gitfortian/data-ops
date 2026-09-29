package io.yak.ops.business.metadata.api;

/**
 * 源域把内部实体投影进统一目录的<b>唯一</b>入口（ticket 130，plan §3.2b）。
 *
 * <p>三条使用约定，缺一条都会造成难以排查的脏数据：
 * <ol>
 *   <li><b>只在源域自己的事务提交之后调用</b>。事务内登记 = 业务回滚但目录留一行再也对不上的
 *       投影（plan §9 T20 的反例之一）。
 *   <li><b>本接口永不抛异常</b>——登记失败不许拖垮业务保存（反例之二）。失败落
 *       {@code yak_md_register_retry} 由 worker 重放；连排队都失败只剩 ERROR 日志，
 *       最终兜底是 ticket 135 的对账副通道，不是这条接口。
 *   <li><b>不传 projectId</b>：归属一律取服务端 {@code CurrentProject}（§0.9），源域与前端都无权自报。
 * </ol>
 *
 * <p>登记与物理采集共用同一个 upsert 与软删入口（{@code AssetUpsertRepository}），差别只在
 * {@code provider_type='REGISTERED'} 与指纹取 {@code source_hash}——判增量的实现全模块只有一份。
 */
public interface MetadataRegistrationApi {

  /** 幂等 upsert：同一 {@code (project, assetKey)} 重复登记不产生第二行。 */
  void register(RegisterCommand command);

  /**
   * 撤销某源在某类型下登记的<b>全部</b>实体（软删，不物理删，§2.4.5）。
   *
   * <p>实现是先读回在场登记的键、再逐个走三参撤销——所以它等价于"源域整类投影下线"，
   * 单个实体删除请用 {@link #unregister(String, String, String)}（那条才有可重放的键）。
   */
  void unregister(String typeName, String sourceId);

  /**
   * 撤销单个实体。{@code assetKey} 是 outbox 行的 NOT NULL 列，也是"重放时撤销谁"的唯一答案——
   * 这是本接口对规划形状（plan §3.2b 只有两参）的唯一偏差，理由记录在模块 REQUIREMENTS.md。
   *
   * <p>{@code assetKey} 为空串/NULL 时退化为两参的批量语义。
   */
  void unregister(String typeName, String sourceId, String assetKey);
}
