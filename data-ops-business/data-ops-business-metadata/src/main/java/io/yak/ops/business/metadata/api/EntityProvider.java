package io.yak.ops.business.metadata.api;

import java.util.Optional;

/**
 * 投影实体的源域供给 SPI（ticket 118，plan §3.2b；与 asset 的 {@code AssetProvider} 同构）。
 *
 * <p>由各源域（modeling / semantic / metric）实现并注册为 Spring Bean，本模块<b>只收集、只调用</b>，
 * 绝不 import 任何源域内部包——依赖方向是"源域依赖元数据的 api 包"，反过来的那条边会让
 * 元数据从"下层事实供给方"变成依赖图的中心（plan §5.2 的理由）。
 *
 * <p>实现约束：
 * <ol>
 *   <li>只读本域既有 Service，绝不写别人的表；</li>
 *   <li>{@code assetKey} 由源域交出并在本域既有键生成器里生成，元数据不替它拼键（§3.2b 硬约束 4）；</li>
 *   <li>把源域业务内容（列清单、公式）放进 {@link EntityProjection#getExtra()}，
 *       <b>不要</b>放进 {@code attributes}——后者是目录里真存着的那一袋，混进来就是第二真相。</li>
 * </ol>
 *
 * <p>本票只用 {@link #refresh(String)}：详情聚合的实时块。定时对账用的游标批量
 * （{@code cursorList}）随工单 135 一起加，届时与 {@code RegisterCommand} 的合流也在那一步。
 */
public interface EntityProvider {

  /** 本 provider 供给的实体类型名（= {@code type_def.type_name}）。 */
  String typeName();

  /**
   * 单实体实时刷新：详情聚合与复核用，<b>不经过</b>一轮对账或重跑登记。
   *
   * <p>源已删除或不存在时返回 {@code empty}（那是"源域没有"，不是失败）；
   * 源域自身出错请抛异常——调用方按分区容错把<b>这一块</b>降级，不会把它误读成实体不存在。
   */
  Optional<EntityProjection> refresh(String sourceId);
}
