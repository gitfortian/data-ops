package io.yak.ops.business.modeling.derive;

import io.yak.ops.business.semantic.api.WarehouseLayer;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 派生按目标分层解析上游(ticket 49):字段继承来源取决于目标分层,而不是一律取源表对应的 ODS 模型。
 *
 * <p>分层依赖与建模语义(与需求文档、47 主线视图、43 分层映射一致):
 * <pre>
 * ODS ← 源表结构复制 + 技术字段(08 逆向导入,不经派生)
 * DWD ← 依赖 ODS,清洗/规范化/退化                  INHERIT:按 source_* 反查 ODS 模型
 * DIM ← 依赖 ODS,代理键 + SCD                      INHERIT + 约定列(50)
 * DWS ← 依赖 DWD,选维度 + 度量(聚合)                AGGREGATE:上游 = 同过程 DWD 模型(51)
 * ADS ← 依赖 DWS/指标,面向应用反推                  APPLICATION:上游 = 同过程 DWS 模型(52)
 * </pre>
 *
 * <p>阻断仍是**服务端硬校验**:上游语义未就绪(该过程还没有上层模型)或该层不由派生承担(ODS)
 * 时直接阻断并给出原因。分层编码来自 37 分层配置、项目可自定义,故按"编码 + 能力矩阵"判定,
 * 未登记的自定义分层按不支持处理。
 */
@Component
public class DeriveLayerPolicy {

  public static final String ODS = "ODS";
  public static final String DWD = "DWD";
  public static final String DIM = "DIM";
  public static final String DWS = "DWS";
  public static final String ADS = "ADS";

  /** 派生模式:决定上游定位方式与字段角色语义。 */
  public enum DeriveMode {
    /** 明细继承:上游是数据源表对应的 ODS 模型(字段原样继承)。 */
    INHERIT,
    /** 结构化聚合:上游是同过程的 DWD 模型(维度分组 + 度量聚合)。 */
    AGGREGATE,
    /** 应用反推:上游是同过程的 DWS 模型(应用绑定 + 逐列来源)。 */
    APPLICATION
  }

  /** 一次分层判定:上游分层 + 派生模式 + 是否可派生 + 不支持时的原因。 */
  public record LayerSupport(
      String targetLayer, String upstreamLayer, DeriveMode mode, boolean supported, String reason) {}

  /** 该模式是否为聚合/应用层(决定字段角色、主键与 19 映射策略)。 */
  public static boolean isAggregateMode(DeriveMode mode) {
    return mode == DeriveMode.AGGREGATE || mode == DeriveMode.APPLICATION;
  }

  /**
   * 判定目标分层可否派生:DWD/DIM 从 ODS 继承;DWS 聚合 DWD;ADS 反推 DWS;
   * ODS 走逆向导入;未登记分层不支持。
   */
  public LayerSupport resolve(WarehouseLayer target) {
    String code = target.code() == null ? "" : target.code().trim().toUpperCase(Locale.ROOT);
    return switch (code) {
      case DWD -> new LayerSupport(code, ODS, DeriveMode.INHERIT, true, null);
      case DIM -> new LayerSupport(code, ODS, DeriveMode.INHERIT, true, null);
      case DWS -> new LayerSupport(code, DWD, DeriveMode.AGGREGATE, true, null);
      case ADS -> new LayerSupport(code, DWS, DeriveMode.APPLICATION, true, null);
      case ODS ->
          new LayerSupport(
              code, null, DeriveMode.INHERIT, false,
              "ODS 模型由逆向导入(08)从源表生成,不走派生;请用「新建模型 → 逆向导入」");
      default ->
          new LayerSupport(
              code, null, DeriveMode.INHERIT, false,
              "分层 " + code + " 不在派生的分层能力矩阵中(当前支持 DWD/DIM/DWS/ADS);"
                  + "如需在该层派生,请先在分层能力矩阵中登记其上游与建模语义");
    };
  }
}
