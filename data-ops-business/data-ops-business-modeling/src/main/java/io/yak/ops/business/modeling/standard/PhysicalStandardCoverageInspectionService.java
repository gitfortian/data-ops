package io.yak.ops.business.modeling.standard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureFingerprint;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.semantic.api.BusinessProcess;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.common.enums.modeling.ModelingErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Today's physical DWD/DIM standard-field coverage. Evidence only: not a
 * stricter publish gate, not an approved exemption, not historical provenance.
 * Semantic owns the field truth; Modeling owns the physical design.
 */
@Service
@RequiredArgsConstructor
public class PhysicalStandardCoverageInspectionService {
  private static final int MAX_COLUMNS = 1000;
  private final ModelRepository models;
  private final ModelStructureService structures;
  private final ProcessApi semantic;
  private final ObjectMapper json;

  public record ColumnEvidence(Long columnId, String columnName, String dataType,
                               Long stdFieldId, String standardName, String semanticRole,
                               String standardType, String physicalRole, String aggregateFunc,
                               String status, String explanation) {}
  public record Report(Long modelId, String modelCode, String layerCode, String modelStatus,
                       Long processId, String processName, String status,
                       String structureSha256, String evidenceSha256, int columnCount,
                       int enabledCount, int missingCount, int invalidCount, int reviewCount,
                       List<ColumnEvidence> columns, List<String> findings,
                       List<String> limitations) {}
  private record StandardFact(Long id, String code, String status, String role, String type,
                              Integer version) {}
  private record Evidence(Long modelId, String modelCode, String layer, String status,
                          Long processId, String processCode, String processName,
                          String processGrain, String structureSha,
                          List<ColumnEvidence> columns, List<StandardFact> standards) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Report inspect(Long modelId) {
    if (modelId == null || modelId <= 0) throw invalid("缺少有效物理模型 ID");
    Model model = models.findById(modelId).orElseThrow(() ->
        new ModelingException(ModelingErrorCode.NOT_FOUND, "物理模型不存在或不属于当前项目"));
    if (model.isRecycled() || !("DWD".equalsIgnoreCase(model.layerCode())
        || "DIM".equalsIgnoreCase(model.layerCode()))) {
      throw invalid("当前仅提供未回收 DWD/DIM 的标准字段设计覆盖核验");
    }
    StructureView structure = structures.get(modelId);
    if (structure == null || !Objects.equals(structure.modelId(), modelId)) {
      throw invalid("物理结构归属与所选模型不一致");
    }
    List<StructureView.ColumnView> physical = structure.columns();
    if (physical == null || physical.size() > MAX_COLUMNS) {
      throw invalid("物理模型字段证据不可用或超过核验上限");
    }

    List<String> findings = new ArrayList<>();
    BusinessProcess process = null;
    if (model.processId() == null) {
      findings.add("模型未绑定正式业务过程，不能解释 DWD/DIM 的业务粒度");
    } else {
      List<BusinessProcess> authorized = semantic.listProcesses(null);
      if (authorized == null) throw invalid("业务过程目录不可用，不能宣称标准覆盖完整");
      process = authorized.stream().filter(item ->
          Objects.equals(item.id(), model.processId())).findFirst().orElse(null);
      if (process == null) {
        findings.add("业务过程当前不可访问或已不存在：" + model.processId());
      } else if (!StringUtils.hasText(process.grain())) {
        findings.add("业务过程尚未明确粒度，不能从物理字段名称自动推断");
      }
    }

    Map<Long, StandardField> standards = new HashMap<>();
    Set<Long> physicalIds = new HashSet<>();
    Set<String> names = new HashSet<>();
    List<ColumnEvidence> rows = new ArrayList<>();
    int covered = 0, missing = 0, invalid = 0, review = 0;
    for (StructureView.ColumnView col : physical) {
      if (col == null || col.id() == null || col.id() <= 0
          || !StringUtils.hasText(col.columnName())
          || !physicalIds.add(col.id())
          || !names.add(col.columnName().toLowerCase(Locale.ROOT))) {
        throw invalid("物理列身份无效或重复，不能形成可信覆盖结果");
      }
      Long ref = col.stdFieldId();
      String status, explanation, standardName = null, semanticRole = null, standardType = null;
      if (ref == null || ref <= 0) {
        status = "MISSING_STANDARD";
        explanation = "物理字段没有正式 StandardFieldRef；技术字段豁免尚未获批，不自动视为合格";
        missing++;
        findings.add("未关联正式标准：" + col.columnName());
      } else {
        // Never infer identity from the display name. Owner errors (authorization,
        // unavailable catalog, etc.) must propagate, never become an empty match.
        StandardField standard = standards.computeIfAbsent(ref, semantic::getField);
        if (standard == null || !standard.isEnabled() || !Objects.equals(standard.id(), ref)) {
          status = "INVALID_STANDARD";
          explanation = "所引标准 ID 不存在、当前停用或返回身份不符；不可自动改绑同名字段";
          invalid++;
          findings.add("标准引用不可用：" + col.columnName() + " (#" + ref + ")");
        } else {
          standardName = standard.name();
          semanticRole = standard.role();
          standardType = standard.dataType();
          if (StringUtils.hasText(col.aggregateFunc())) {
            status = "AGGREGATE_REVIEW";
            explanation = "物理字段配置了聚合表达式，须人工核实业务粒度及度量定义；Semantic 角色不自动等于物理聚合角色";
            review++;
            findings.add("存在需要人工审阅的聚合含义：" + col.columnName());
          } else {
            status = "ENABLED_NOW";
            explanation = "引用的正式标准字段目前可读且启用；不表示历史标准版本被冻结或物理类型已获兼容判定";
            covered++;
          }
        }
      }
      rows.add(new ColumnEvidence(col.id(), col.columnName(), col.dataType(),
          ref, standardName, semanticRole, standardType, col.fieldRole(),
          col.aggregateFunc(), status, explanation));
    }

    if (rows.isEmpty()) findings.add("物理草稿尚无字段，无法计算标准字段覆盖");
    // Repeatable evidence independent of column DAO timestamps and scan order.
    List<ColumnEvidence> ordered = rows.stream()
        .sorted(Comparator.comparing(ColumnEvidence::columnName, String.CASE_INSENSITIVE_ORDER))
        .toList();
    List<StandardFact> facts = standards.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> {
          StandardField field = e.getValue();
          return field == null ? new StandardFact(e.getKey(), null, "NOT_FOUND", null, null, null)
              : new StandardFact(e.getKey(), field.code(), field.status(), field.role(),
                  field.dataType(), field.version());
        }).toList();
    String structureSha = StructureFingerprint.of(structure);
    String evidenceSha = digest(new Evidence(modelId, model.code(), model.layerCode(),
        model.status().name(), model.processId(), process == null ? null : process.code(),
        process == null ? null : process.name(), process == null ? null : process.grain(),
        structureSha, ordered, facts));
    return new Report(modelId, model.code(), model.layerCode(), model.status().name(),
        model.processId(), process == null ? null : process.name(),
        findings.isEmpty() ? "COVERED_NOW" : "NEEDS_REVIEW",
        structureSha, evidenceSha, rows.size(), covered, missing, invalid, review,
        List.copyOf(rows), List.copyOf(findings),
        List.of("只读核验当前设计、当前授权业务过程及 Semantic 标准状态，不代表任何发布版本的历史标准引用",
            "当前标准启用不构成 DWD/DIM 物理类型兼容、逻辑→物理映射批准或聚合口径确认",
            "非 ODS 全列强制标准及技术列豁免尚待 PD-010 正式决策；本报告绝不修改现行发布资格",
            "模型结构已保存/发布不代表表已部署、数据已执行、质量通过或可被合法消费"));
  }

  private String digest(Evidence evidence) {
    try {
      byte[] bytes = json.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsString(evidence).getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
      throw new IllegalStateException("无法生成标准覆盖证据指纹", ex);
    }
  }
  private static ModelingException invalid(String message) {
    return new ModelingException(ModelingErrorCode.INVALID_COLUMN, message);
  }
}
