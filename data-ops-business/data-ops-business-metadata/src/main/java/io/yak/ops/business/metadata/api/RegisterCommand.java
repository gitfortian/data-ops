package io.yak.ops.business.metadata.api;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * 一次登记的全部输入（ticket 130，plan §3.2b）。与 ticket 135 的 {@code EntityProjection}
 * <b>共用本 DTO</b>：push 与对账两条通道各自定义一份投影字段，漂移只是时间问题。
 *
 * <p>刻意<b>没有</b> {@code projectId}：归属取服务端可信上下文（§0.9）。也刻意<b>没有</b>
 * 内容正文：目录登记的是投影，不是源域实体的副本。
 *
 * <p>本对象会被整份序列化进 {@code yak_md_register_retry.payload} 供 worker 重放
 * （重放不回查源域，避免"排队时实体又被改了"的时序依赖），因此保持为可 JSON 化的普通 Java 类型。
 */
@Data
public class RegisterCommand {

  /** = {@code yak_md_type_def.type_name}；决定键前缀、字段登记与指纹口径。 */
  private String typeName;

  /** 源域内主键（如模型 id）；unregister 与对账回查都按它圈行。 */
  private String sourceId;

  /** 源域拼出的资产键——元数据<em>不替源域拼键</em>（§3.2b 硬约束 4），只校验前缀与长度。 */
  private String assetKey;

  private String name;
  private String displayName;
  private String summary;

  /** 投影归属三列（目录侧新增写列，HARVESTED 通道永远交 NULL）。 */
  private String ownerUser;

  /** 业务域 id 逗号串（共表列 {@code domain_ids} 的既有格式）。 */
  private String domainIds;

  private String layerCode;

  /** 父实体的资产键（同目录内解析成 id；解析不到留空，由对账通道补齐）。 */
  private String parentAssetKey;

  /** 源域产出的内容指纹；push 与对账必须同一函数，否则两通道互相把对方的行判成 CHANGED。 */
  private String sourceHash;

  /** 保序令牌：比目录行现值旧的命令只刷在场、不改内容。NOT NULL（兼 outbox 去重键列）。 */
  private LocalDateTime sourceUpdatedAt;

  /** 键为 field_def 登记的<b>字段名</b>；未登记字段被 {@code MetadataAttributeCodec} 拒绝（49012）。 */
  private Map<String, Object> attributes = new LinkedHashMap<>();

  /** 源域当前用户名；为空时按机器写落 {@code system}（{@code changed_by} NOT NULL）。 */
  private String operator;
}
