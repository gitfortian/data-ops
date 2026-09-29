package io.yak.ops.business.metadata.api;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * 源域交出的一份目录投影（ticket 118 的实时读侧；plan §3.2b）。
 *
 * <p>字段分两半，这条线就是"目录不是第二真相"（§1.3）：
 * <ul>
 *   <li>{@code displayName}/{@code summary}/{@code attributes} 等——<b>目录里有的</b>，
 *       登记通道写进去、详情用来对齐"目录是否落后"。</li>
 *   <li>{@code extra}——<b>目录里绝不有的</b>：模型的列清单、指标的公式、标准的字典项。
 *       这些一改就漂移且已有 owner，抄一份就是第二真相；只在本接口被实时调用时存在于内存与响应里。</li>
 * </ul>
 *
 * <p>工单 130 的 {@code RegisterCommand} 与本类字段完全重叠（多的只有 {@code operator}），
 * 135 开工第一步是把两者收成同一个 DTO，不再长两套投影定义。
 */
@Data
public class EntityProjection {

  /** 源域内主键（如模型 id 的字符串形式）。 */
  private String sourceId;

  /** 源域拼出的资产键；元数据不替源域拼键。 */
  private String assetKey;

  private String name;
  private String displayName;
  private String summary;
  private String ownerUser;

  /** 业务域 id 逗号串（共表列 {@code domain_ids} 的既有格式）。 */
  private String domainIds;

  private String layerCode;

  /** 父实体的源域主键（对账侧解析成目录 id）。 */
  private String parentSourceId;

  /** 源域产出的内容指纹；push 与对账必须同一函数。 */
  private String sourceHash;

  private LocalDateTime sourceUpdatedAt;

  /** 键为 {@code field_def} 登记的字段名；写入侧未登记字段会被属性编解码器拒绝（49012）。 */
  private Map<String, Object> attributes = new LinkedHashMap<>();

  /** 只给详情看的源域事实（列清单、公式、分区…），<b>永不落目录</b>。 */
  private Map<String, Object> extra = new LinkedHashMap<>();
}
