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
 * Read-only physical standard-field and referenced-standard evidence for DWD/DIM/DWS/ADS. Evidence only: not a
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
  private final StandardQueryApi standardQuery;
  private final ObjectMapper json;

  public record StandardReferenceEvidence(
      String referenceKind, String referenceKey, Long standardId, String code, String name,
      String status, Integer version, String contentSha256, String explanation) {}
  public record ColumnEvidence(Long columnId, String columnName, String dataType,
                               Long stdFieldId, String standardName, String semanticRole,
                               String standardType, String physicalRole, String aggregateFunc,
                               String status, String explanation,
                               List<StandardReferenceEvidence> standardReferences) {}
  public record Report(Long modelId, String modelCode, String layerCode, String modelStatus,
                       Long processId, String processName, String status,
                       String structureSha256, String evidenceSha256, int columnCount,
                       int enabledCount, int missingCount, int invalidCount, int reviewCount,
                       int standardReferenceIssueCount,
                       List<ColumnEvidence> columns, List<String> findings,
                       List<String> limitations) {}
  private record StandardFact(Long id, String code, String status, String role, String type,
                              Integer version) {}
  private record StandardReferenceFact(Long id, String kind, String code, String status,
                                       int version, Standard.KindFields fields) {}
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
    int covered = 0, missing = 0, invalid = 0, review = 0, referenceIssues = 0;
    for (StructureView.ColumnView col : physical) {
      if (col == null || col.id() == null || col.id() <= 0
          || !StringUtils.hasText(col.columnName())
          || !physicalIds.add(col.id())
          || !names.add(col.columnName().toLowerCase(Locale.ROOT))) {
        throw invalid("物理列身份无效或重复，不能形成可信覆盖结果");
      }
      Long ref = col.stdFieldId();
      String status, explanation, standardName = null, semanticRole = null, standardType = null;
      StandardField referencedField = null;
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
          referencedField = standard;
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
      List<StandardReferenceEvidence> referenceEvidence = referencedField == null
          ? List.of() : inspectStandardReferences(referencedField, col.columnName(), findings);
      referenceIssues += (int) referenceEvidence.stream()
          .filter(e -> !Set.of("NOT_CONFIGURED", "ENABLED_NOW").contains(e.status()))
          .count();
      rows.add(new ColumnEvidence(col.id(), col.columnName(), col.dataType(),
          ref, standardName, semanticRole, standardType, col.fieldRole(),
          col.aggregateFunc(), status, explanation, referenceEvidence));
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
        referenceIssues, List.copyOf(rows), List.copyOf(findings),
        List.of("只读核验当前已保存的非 ODS 物理结构与 Semantic 标准字段状态；仅 DWD/DIM 同时核验当前业务过程/粒度",
            "本报告同时读取字段引用的 TYPE/UNIT/CALIBER/CODE/SECURITY 标准当前状态及内容指纹；未配置不自动判失败，适用性须由正式合同决定",
            "本报告只证明当前标准字段引用可读且启用，不证明历史版本冻结了标准证据，也不证明物理类型兼容",
            "DWS 粒度、精确上游版本及 MetricVersionRef 必须由 DWS 专项合同核验；本报告不替代这些检查",
            "ADS 消费场景、脱敏/列权限、消费者授权及 Usage Evidence 必须由 ADS/Consumption 专项合同核验",
            "非 ODS 发布阻断规则与技术列例外仍以 PD-010 正式状态及相关 Owner 签收为准；本报告不改变发布资格",
            "模型结构已保存/发布不代表表已部署、数据已执行、质量通过或可被合法消费"));
  }

  private List<StandardReferenceEvidence> inspectStandardReferences(
      StandardField field, String columnName, List<String> findings) {
    List<StandardReferenceEvidence> evidence = new ArrayList<>();
    evidence.add(inspectStandardReference("TYPE", field.stdTypeId(), StandardKind.TYPE,
        columnName, findings));
    evidence.add(inspectStandardReference("UNIT", field.stdUnitId(), StandardKind.UNIT,
        columnName, findings));
    evidence.add(inspectStandardReference("CALIBER", field.stdCaliberId(), StandardKind.CALIBER,
        columnName, findings));
    evidence.add(inspectCodeSetReference(field.stdCodeSetCode(), columnName, findings));
    evidence.add(inspectStandardReference("SECURITY", field.stdSecurityId(), StandardKind.SECURITY,
        columnName, findings));
    return List.copyOf(evidence);
  }

  private StandardReferenceEvidence inspectStandardReference(
      String referenceKind, Long id, StandardKind expectedKind,
      String columnName, List<String> findings) {
    if (id == null || id <= 0) {
      return new StandardReferenceEvidence(referenceKind, null, null, null, null,
          "NOT_CONFIGURED", null, null,
          "未配置此类数据标准；是否必需由正式适用性规则决定，本只读核验不自行推断");
    }
    Standard standard = standardQuery.get(id);
    if (standard == null || !Objects.equals(standard.id(), id)
        || standard.kind() != expectedKind) {
      findings.add("数据标准引用无效：" + columnName + " → " + referenceKind + " #" + id
          + "（不存在、无权读取或标准类别不匹配）");
      return new StandardReferenceEvidence(referenceKind, String.valueOf(id), id,
          standard == null ? null : standard.code(), standard == null ? null : standard.name(),
          "INVALID_REFERENCE", standard == null ? null : standard.version(), null,
          "引用目标不存在、不可访问或类别不匹配；不按名称自动重绑");
    }
    String contentSha = digest(new StandardReferenceFact(standard.id(), standard.kind().name(),
        standard.code(), standard.status() == null ? null : standard.status().name(),
        standard.version(), standard.fields()));
    if (standard.status() != StandardStatus.ENABLED) {
      findings.add("数据标准当前不可用：" + columnName + " → " + referenceKind + " #" + id
          + "（状态 " + standard.status() + "）");
      return new StandardReferenceEvidence(referenceKind, String.valueOf(id), id,
          standard.code(), standard.name(), "DISABLED", standard.version(), contentSha,
          "标准存在但当前未启用；此为当前状态，不修改历史发布版本");
    }
    return new StandardReferenceEvidence(referenceKind, String.valueOf(id), id,
        standard.code(), standard.name(), "ENABLED_NOW", standard.version(), contentSha,
        "类别、项目内身份与当前启用状态匹配；内容指纹仅代表当前读取，不代表历史冻结证据");
  }

  private StandardReferenceEvidence inspectCodeSetReference(
      String codeSetCode, String columnName, List<String> findings) {
    if (!StringUtils.hasText(codeSetCode)) {
      return new StandardReferenceEvidence("CODE", null, null, null, null,
          "NOT_CONFIGURED", null, null,
          "未配置码集引用；是否必需由正式适用性规则决定，本只读核验不自行推断");
    }
    StandardQueryApi.CodeSetReference codeSet = standardQuery.codeSetReference(codeSetCode);
    if (codeSet == null || !Objects.equals(codeSet.codeSetCode(), codeSetCode)
        || codeSet.itemCount() <= 0 || "NOT_FOUND".equals(codeSet.status())) {
      findings.add("码集引用无效：" + columnName + " → CODE " + codeSetCode
          + "（码集不存在或当前项目不可访问）");
      return new StandardReferenceEvidence("CODE", codeSetCode, null, codeSetCode, null,
          "INVALID_REFERENCE", null, codeSet == null ? null : codeSet.contentSha256(),
          "当前项目中未找到此码集；不按相似编码自动改绑");
    }
    if (!"ENABLED".equals(codeSet.status())) {
      findings.add("码集当前不可完整使用：" + columnName + " → CODE " + codeSetCode
          + "（状态 " + codeSet.status() + "）");
      return new StandardReferenceEvidence("CODE", codeSetCode, null, codeSetCode, null,
          "MIXED".equals(codeSet.status()) ? "MIXED" : "DISABLED", null,
          codeSet.contentSha256(), "码集包含停用项或状态不一致；请在 Semantic 中复核完整码集");
    }
    return new StandardReferenceEvidence("CODE", codeSetCode, null, codeSetCode, null,
        "ENABLED_NOW", null, codeSet.contentSha256(),
        "码集当前所有项目内条目均启用；内容指纹用于发现变化，不代表历史冻结证据");
  }

  private String digest(Object evidence) {
    try {
      byte[] bytes = json.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsString(evidence).getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
      throw new IllegalStateException("无法生成标准引用证据指纹", ex);
    }
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
