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
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.StandardStatus;
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
 * Current non-ODS physical standard-field and linked data-standard evidence.
 * Read-only only: not a publish gate, approved exemption, or historical provenance.
 * Semantic owns standard truth; Modeling owns physical design.
 */
@Service
@RequiredArgsConstructor
public class PhysicalStandardCoverageInspectionService {
  private static final int MAX_COLUMNS = 1000;
  private final ModelRepository models;
  private final ModelStructureService structures;
  private final ProcessApi semantic;
  private final StandardQueryApi standardQuery;
  private final ObjectMapper json;

  public record StandardReferenceEvidence(Long id, String kind, String code, String name,
                                          String status, Integer version,
                                          String evidenceStatus, String definition,
                                          String explanation) {}
  public record DataStandardEvidence(String role, StandardReferenceEvidence typeStandard,
                                     StandardReferenceEvidence unitStandard,
                                     StandardReferenceEvidence caliberStandard,
                                     String codeSetCode, String codeSetStatus,
                                     StandardReferenceEvidence securityStandard,
                                     String status, boolean missingRequiredReference,
                                     boolean invalidReference, String explanation) {}
  public record ColumnEvidence(Long columnId, String columnName, String dataType,
                               Long stdFieldId, String standardName, String semanticRole,
                               String standardType, String physicalRole, String aggregateFunc,
                               String status, String explanation,
                               DataStandardEvidence dataStandards) {}
  public record Report(Long modelId, String modelCode, String layerCode, String modelStatus,
                       Long processId, String processName, String status,
                       String structureSha256, String evidenceSha256, int columnCount,
                       int enabledCount, int missingCount, int invalidCount, int reviewCount,
                       int missingDataStandardCount, int invalidDataStandardCount,
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
    String layerCode = model.layerCode() == null
        ? "" : model.layerCode().toUpperCase(Locale.ROOT);
    if (model.isRecycled()
        || !Set.of("DWD", "DIM", "DWS", "ADS").contains(layerCode)) {
      throw invalid("当前仅提供未回收 DWD/DIM/DWS/ADS 的只读标准字段覆盖核验");
    }
    // Only DWD/DIM require the current business-process/grain evidence in this
    // diagnostic. DWS grain/Metric pins and ADS consumer contracts have their
    // own authoritative inspectors and must not be inferred here.
    boolean processSemanticsRequired = "DWD".equals(layerCode) || "DIM".equals(layerCode);
    Long evidenceProcessId = processSemanticsRequired ? model.processId() : null;
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
    if (processSemanticsRequired) {
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
    }

    Map<Long, StandardField> standards = new HashMap<>();
    Set<Long> physicalIds = new HashSet<>();
    Set<String> names = new HashSet<>();
    List<ColumnEvidence> rows = new ArrayList<>();
    int covered = 0, missing = 0, invalid = 0, review = 0;
    int missingDataStandard = 0, invalidDataStandard = 0;
    for (StructureView.ColumnView col : physical) {
      if (col == null || col.id() == null || col.id() <= 0
          || !StringUtils.hasText(col.columnName())
          || !physicalIds.add(col.id())
          || !names.add(col.columnName().toLowerCase(Locale.ROOT))) {
        throw invalid("物理列身份无效或重复，不能形成可信覆盖结果");
      }
      Long ref = col.stdFieldId();
      String status, explanation, standardName = null, semanticRole = null, standardType = null;
      DataStandardEvidence dataStandards = null;
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
          dataStandards = inspectDataStandards(standard);
          if (dataStandards.missingRequiredReference()) {
            missingDataStandard++;
          }
          if (dataStandards.invalidReference()) {
            invalidDataStandard++;
          }
          if (dataStandards.missingRequiredReference() || dataStandards.invalidReference()
              || "NEEDS_REVIEW".equals(dataStandards.status())) {
            findings.add("关联数据标准需要复核：" + col.columnName() + "（"
                + dataStandards.explanation() + "）");
          }
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
          col.aggregateFunc(), status, explanation, dataStandards));
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
        model.status().name(), evidenceProcessId, process == null ? null : process.code(),
        process == null ? null : process.name(), process == null ? null : process.grain(),
        structureSha, ordered, facts));
    return new Report(modelId, model.code(), model.layerCode(), model.status().name(),
        evidenceProcessId, process == null ? null : process.name(),
        findings.isEmpty() ? "COVERED_NOW" : "NEEDS_REVIEW",
        structureSha, evidenceSha, rows.size(), covered, missing, invalid, review,
        missingDataStandard, invalidDataStandard,
        List.copyOf(rows), List.copyOf(findings),
        List.of("只读核验当前已保存的非 ODS 物理结构、StandardField 与其当前关联的数据标准引用；仅 DWD/DIM 同时核验当前业务过程/粒度",
            "TYPE 对所有标准字段必需；UNIT 对 METRIC 必需；CALIBER 对 METRIC 选填；CODE 码集与 SECURITY 对 DIMENSION 选填，规则依据 Semantic 当前字段合同",
            "本报告读取当前标准状态/版本，不证明历史物理版本冻结了当时标准证据，也不替代物理类型兼容审查",
            "DWS 粒度、精确上游版本及 MetricVersionRef 必须由 DWS 专项合同核验；本报告不替代这些检查",
            "ADS 消费场景、脱敏/列权限、消费者授权及 Usage Evidence 必须由 ADS/Consumption 专项合同核验",
            "非 ODS 发布阻断规则与技术列例外仍以 PD-010 正式状态及相关 Owner 签收为准；本报告不改变发布资格",
            "模型结构已保存/发布不代表表已部署、数据已执行、质量通过或可被合法消费"));
  }

  private DataStandardEvidence inspectDataStandards(StandardField field) {
    List<String> issues = new ArrayList<>();
    boolean missingRequired = false;
    boolean invalidReference = false;
    boolean needsReview = !StandardField.isValidRole(field.role());

    StandardReferenceEvidence type = inspectStandardReference(
        field.stdTypeId(), StandardKind.TYPE, true, "类型标准");
    missingRequired |= "MISSING_REQUIRED_REFERENCE".equals(type.evidenceStatus());
    invalidReference |= "INVALID_REFERENCE".equals(type.evidenceStatus());
    if (missingRequired || invalidReference) issues.add(type.explanation());

    StandardReferenceEvidence unit = null;
    StandardReferenceEvidence caliber = null;
    StandardReferenceEvidence security = null;
    String codeSetStatus = "NOT_APPLICABLE";
    String codeSetCode = null;
    if ("METRIC".equals(field.role())) {
      unit = inspectStandardReference(field.stdUnitId(), StandardKind.UNIT, true, "单位标准");
      caliber = inspectStandardReference(
          field.stdCaliberId(), StandardKind.CALIBER, false, "口径标准");
      if ("MISSING_REQUIRED_REFERENCE".equals(unit.evidenceStatus())) missingRequired = true;
      if ("INVALID_REFERENCE".equals(unit.evidenceStatus())) invalidReference = true;
      if ("MISSING_REQUIRED_REFERENCE".equals(unit.evidenceStatus())
          || "INVALID_REFERENCE".equals(unit.evidenceStatus())) issues.add(unit.explanation());
      if ("INVALID_REFERENCE".equals(caliber.evidenceStatus())) {
        invalidReference = true;
        issues.add(caliber.explanation());
      }
    } else if ("DIMENSION".equals(field.role())) {
      codeSetCode = field.stdCodeSetCode();
      if (!StringUtils.hasText(codeSetCode)) {
        codeSetStatus = "NOT_CONFIGURED";
      } else if (standardQuery.existsCodeSet(codeSetCode)) {
        codeSetStatus = "ENABLED_NOW";
      } else {
        codeSetStatus = "INVALID_REFERENCE";
        invalidReference = true;
        issues.add("码集不存在或已停用：" + codeSetCode);
      }
      security = inspectStandardReference(
          field.stdSecurityId(), StandardKind.SECURITY, false, "安全标准");
      if ("INVALID_REFERENCE".equals(security.evidenceStatus())) {
        invalidReference = true;
        issues.add(security.explanation());
      }
    }

    if (needsReview) issues.add("标准字段角色不在当前 Semantic 合同允许范围内：" + field.role());
    String status = needsReview || (missingRequired && invalidReference) ? "NEEDS_REVIEW"
        : missingRequired ? "MISSING_REQUIRED_REFERENCE"
        : invalidReference ? "INVALID_REFERENCE" : "COMPLETE_NOW";
    String explanation = issues.isEmpty()
        ? "当前字段适用的数据标准引用均可读取且启用；不代表物理类型兼容或正式发布获批"
        : String.join("；", issues);
    return new DataStandardEvidence(field.role(), type, unit, caliber,
        codeSetCode, codeSetStatus, security, status, missingRequired,
        invalidReference, explanation);
  }

  private StandardReferenceEvidence inspectStandardReference(
      Long id, StandardKind expectedKind, boolean required, String label) {
    if (id == null || id <= 0) {
      return new StandardReferenceEvidence(id, expectedKind.name(), null, null, null, null,
          required ? "MISSING_REQUIRED_REFERENCE" : "NOT_CONFIGURED", null,
          required ? "缺少必需的" + label + "引用" : "未配置选填的" + label);
    }
    Standard value = standardQuery.get(id);
    String definition = value == null ? null : standardDefinition(value, expectedKind);
    if (value == null || !Objects.equals(value.id(), id) || value.kind() != expectedKind
        || value.status() != StandardStatus.ENABLED || !StringUtils.hasText(definition)) {
      return new StandardReferenceEvidence(id, expectedKind.name(),
          value == null ? null : value.code(), value == null ? null : value.name(),
          value == null || value.status() == null ? null : value.status().name(),
          value == null ? null : value.version(), "INVALID_REFERENCE", definition,
          label + "引用不存在、类别不匹配、当前停用或必需定义缺失；不可自动改绑同名标准");
    }
    return new StandardReferenceEvidence(value.id(), value.kind().name(), value.code(),
        value.name(), value.status().name(), value.version(), "ENABLED_NOW", definition,
        "当前引用的" + label + "存在、类别匹配且启用");
  }

  private static String standardDefinition(Standard standard, StandardKind expectedKind) {
    if (standard.fields() == null) return null;
    return switch (expectedKind) {
      case TYPE -> standard.fields().stdType();
      case UNIT -> standard.fields().unitCode();
      case CALIBER -> standard.fields().calRule();
      case SECURITY -> StringUtils.hasText(standard.fields().levelCode())
          && StringUtils.hasText(standard.fields().maskRule())
          ? standard.fields().levelCode() + " / " + standard.fields().maskRule() : null;
      case CODE -> standard.fields().codeSetCode();
      case NAMING -> standard.fields().ruleExpr();
    };
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
