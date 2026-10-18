package io.yak.ops.business.modeling.derive;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.repository.ModelVersionRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureFingerprint;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.modeling.version.ModelPublishedStructureReader;
import io.yak.ops.common.version.VersionDigests;
import io.yak.ops.core.project.CurrentProject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Modeling-owned ADS output design contract. It never executes a query,
 * publishes a Metric, creates a Runtime task, or grants Dataset/Service access.
 */
@Component
public class AdsApplicationDesignService {
  private static final Pattern APP_CODE = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{1,63}$");
  private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");
  private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
  private static final Set<String> CONSUMER_TYPES = Set.of("REPORT", "DATASET", "API", "ANALYSIS");
  private static final Set<String> SOURCE_MODES = Set.of("DWS", "DWD");

  public record OutputField(String sourceColumn, String outputColumn, Long standardFieldId,
      String sensitivity) {}
  public record Request(String appCode, String appName, String owner, String purpose,
      String consumerType, String sourceMode, long upstreamModelId, long upstreamVersionId,
      int upstreamVersionNo, String upstreamChecksum, List<OutputField> outputFields,
      List<String> businessKeys, String filterExpression, String sortExpression,
      Long metricVersionId, String refreshTarget, Long expectedRevision) {}
  public record UpstreamPin(long modelId, long versionId, int versionNo, String checksum,
      String layerCode) {}
  public record Contract(String appCode, String appName, String owner, String purpose,
      String consumerType, String sourceMode, UpstreamPin upstream, List<OutputField> outputFields,
      List<String> businessKeys, String filterExpression, String sortExpression,
      Long metricVersionId, String refreshTarget) {}
  public record Saved(long modelId, long revision, String contractSha256, String contractJson,
      Contract contract, String updatedBy, String updatedAt) {}
  public record ValidationResult(boolean valid, List<String> blockers, List<String> warnings,
      String targetStructureFingerprint, Contract contract, String deliveryStatus) {}
  public record HistoryEntry(long modelId, long revision, String contractSha256,
      String contractJson, Contract contract, String updatedBy, String updatedAt) {}

  private final ModelRepository models;
  private final ModelVersionRepository versions;
  private final ModelStructureService structures;
  private final ModelPublishedStructureReader published;
  private final CurrentProject projects;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public AdsApplicationDesignService(ModelRepository models, ModelVersionRepository versions,
      ModelStructureService structures, ModelPublishedStructureReader published,
      CurrentProject projects, JdbcTemplate jdbc, ObjectMapper mapper) {
    this.models = models;
    this.versions = versions;
    this.structures = structures;
    this.published = published;
    this.projects = projects;
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public Optional<Saved> inspect(long modelId) {
    requireAds(modelId);
    return current(modelId);
  }

  public List<HistoryEntry> history(long modelId) {
    requireAds(modelId);
    return jdbc.query("SELECT model_id,revision,contract_sha256,contract_json,updated_by,updated_at "
        + "FROM yak_modeling_ads_application_revision WHERE project_id=? AND model_id=? "
        + "ORDER BY revision DESC LIMIT 50", (rs, row) -> decodeHistory(rs),
        projects.requireProjectId(), modelId);
  }

  public ValidationResult validate(long modelId, Request request) {
    Model target = requireAds(modelId);
    StructureView targetStructure = structures.get(modelId);
    List<String> blockers = new java.util.ArrayList<>();
    List<String> warnings = new java.util.ArrayList<>();
    Contract contract = validateAndFreeze(target, request, blockers, warnings, targetStructure);
    String fingerprint = StructureFingerprint.of(targetStructure);
    if (!blockers.isEmpty()) {
      return new ValidationResult(false, List.copyOf(blockers), List.copyOf(warnings),
          fingerprint, contract, "DELIVERY_NOT_CONFIGURED");
    }
    return new ValidationResult(true, List.of(), List.copyOf(warnings), fingerprint,
        contract, "DELIVERY_NOT_CONFIGURED");
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Saved save(long modelId, Request request, String operator) {
    if (!StringUtils.hasText(operator)) throw new IllegalArgumentException("操作人不能为空");
    Model target = requireAdsForUpdate(modelId);
    StructureView targetStructure = structures.getForUpdate(modelId);
    List<String> blockers = new java.util.ArrayList<>();
    List<String> warnings = new java.util.ArrayList<>();
    Contract contract = validateAndFreeze(target, request, blockers, warnings, targetStructure);
    if (!blockers.isEmpty()) throw new IllegalArgumentException(String.join("；", blockers));
    String json = encode(contract);
    String digest = VersionDigests.sha256Hex(json);
    long projectId = projects.requireProjectId();
    Optional<Saved> existing = current(modelId);
    if (existing.isPresent() && existing.get().contractSha256().equals(digest)) {
      return existing.get();
    }
    long currentRevision = existing.map(Saved::revision).orElse(0L);
    if (request.expectedRevision() == null
        || request.expectedRevision().longValue() != currentRevision) {
      throw new IllegalArgumentException("ADS 合同修订已变化，请重新读取最新版本后再保存");
    }
    long revision = currentRevision + 1L;
    if (existing.isEmpty()) {
      jdbc.update("INSERT INTO yak_modeling_ads_application_design "
          + "(project_id,model_id,revision,contract_sha256,contract_json,updated_by,updated_at) "
          + "VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)", projectId, modelId, revision, digest, json, operator);
    } else {
      jdbc.update("UPDATE yak_modeling_ads_application_design SET revision=?,contract_sha256=?,"
          + "contract_json=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE project_id=? AND model_id=?",
          revision, digest, json, operator, projectId, modelId);
    }
    jdbc.update("INSERT INTO yak_modeling_ads_application_revision "
        + "(project_id,model_id,revision,contract_sha256,contract_json,updated_by,updated_at) "
        + "VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)", projectId, modelId, revision, digest, json, operator);
    return current(modelId).orElseThrow(() -> new IllegalStateException("ADS 合同保存后无法回读"));
  }

  private Contract validateAndFreeze(Model target, Request request,
      List<String> blockers, List<String> warnings, StructureView targetStructure) {
    if (request == null) {
      blockers.add("ADS 应用合同不能为空");
      return null;
    }
    if (!StringUtils.hasText(request.appCode()) || !APP_CODE.matcher(request.appCode()).matches())
      blockers.add("应用编码必须以英文字母开头且只包含字母、数字、下划线或连字符");
    if (!StringUtils.hasText(request.appName())) blockers.add("应用名称不能为空");
    if (!StringUtils.hasText(request.owner())) blockers.add("必须明确业务负责人");
    if (!StringUtils.hasText(request.purpose())) blockers.add("必须说明应用用途");
    if (!CONSUMER_TYPES.contains(request.consumerType())) blockers.add("请选择有效消费类型");
    if (!SOURCE_MODES.contains(request.sourceMode())) blockers.add("来源只能选择已发布 DWS 或显式选择的 DWD");
    if (!SHA256.matcher(java.util.Objects.toString(request.upstreamChecksum(), "")).matches()) {
      blockers.add("上游版本校验和必须是合法 SHA-256");
    }
    if (!blockers.isEmpty()) return null;
    if ("DWD".equals(request.sourceMode()))
      warnings.add("当前为明细来源；不得套用 DWS 汇总指标或粒度。");
    Model upstream = models.findById(request.upstreamModelId()).orElse(null);
    if (upstream == null || upstream.status() == ModelStatus.DISABLED) {
      blockers.add("所选上游不存在、已禁用或不在当前项目内");
      return null;
    }
    if (!target.processId().equals(upstream.processId()))
      blockers.add("ADS 与上游必须属于同一业务过程");
    if (!request.sourceMode().equalsIgnoreCase(upstream.layerCode()))
      blockers.add("来源模式与上游实际分层不一致");
    if (!("DWS".equalsIgnoreCase(upstream.layerCode()) || "DWD".equalsIgnoreCase(upstream.layerCode())))
      blockers.add("ADS 只允许已发布 DWS（默认）或用户显式选择的 DWD");
    if (upstream.publishedVersionId() == null
        || !upstream.publishedVersionId().equals(request.upstreamVersionId())) {
      blockers.add("上游发布版本已变化；请显式审阅版本差异并重新确认，禁止自动切换 latest");
    }
    var version = versions.findById(request.upstreamVersionId(), request.upstreamModelId()).orElse(null);
    if (version == null || version.versionNo() != request.upstreamVersionNo()
        || !java.util.Objects.equals(request.upstreamChecksum(), version.checksum())) {
      blockers.add("精确上游版本身份/校验和不匹配");
    }
    if (!blockers.isEmpty()) return null;
    StructureView source = published.requirePublishedStructure(upstream.id());
    Map<String, StructureView.ColumnView> sourceColumns = source.columns().stream()
        .collect(java.util.stream.Collectors.toMap(c -> c.columnName().toLowerCase(Locale.ROOT),
            c -> c, (a, b) -> a));
    List<OutputField> fields = request.outputFields() == null ? List.of() : new java.util.ArrayList<>(request.outputFields());
    if (fields.isEmpty()) blockers.add("至少需要一个输出字段");
    Set<String> outputNames = new HashSet<>();
    Set<String> sourceNames = new HashSet<>();
    Map<String, StructureView.ColumnView> targetColumns = targetStructure.columns() == null ? Map.of()
        : targetStructure.columns().stream()
            .filter(c -> StringUtils.hasText(c.columnName()))
            .collect(java.util.stream.Collectors.toMap(
                c -> c.columnName().toLowerCase(Locale.ROOT), c -> c, (a, b) -> a));
    for (OutputField field : fields) {
      if (field == null || !StringUtils.hasText(field.sourceColumn())
          || !StringUtils.hasText(field.outputColumn())) {
        blockers.add("输出字段的来源列和目标列名不能为空");
        continue;
      }
      if (!IDENTIFIER.matcher(field.outputColumn()).matches())
        blockers.add("目标输出列名不合法：" + field.outputColumn());
      if (!outputNames.add(field.outputColumn().toLowerCase(Locale.ROOT)))
        blockers.add("目标输出列名重复：" + field.outputColumn());
      StructureView.ColumnView targetColumn =
          targetColumns.get(field.outputColumn().toLowerCase(Locale.ROOT));
      if (targetColumn == null) {
        blockers.add("输出列不存在于当前 ADS 物理结构：" + field.outputColumn());
      } else if (targetColumn.stdFieldId() == null
          || !targetColumn.stdFieldId().equals(field.standardFieldId())) {
        blockers.add("ADS 输出列必须绑定当前物理结构已治理的标准字段：" + field.outputColumn());
      }
      if (!sourceNames.add(field.sourceColumn().toLowerCase(Locale.ROOT)))
        warnings.add("同一来源列被映射多次：" + field.sourceColumn());
      StructureView.ColumnView sourceColumn =
          sourceColumns.get(field.sourceColumn().toLowerCase(Locale.ROOT));
      if (sourceColumn == null) {
        blockers.add("来源字段不在精确发布快照中：" + field.sourceColumn());
      } else if (sourceColumn.stdFieldId() == null) {
        blockers.add("上游来源字段必须绑定数据标准：" + field.sourceColumn());
      } else if (targetColumn != null && targetColumn.stdFieldId() != null
          && !sourceColumn.stdFieldId().equals(targetColumn.stdFieldId())) {
        blockers.add("来源字段与 ADS 输出字段绑定的数据标准不一致："
            + field.sourceColumn() + " → " + field.outputColumn());
      }
      if (field.sensitivity() == null || field.sensitivity().isBlank())
        warnings.add("输出字段未标记敏感等级：" + field.outputColumn());
    }
    Set<String> keySet = new HashSet<>();
    List<String> keys = request.businessKeys() == null ? List.of() : new java.util.ArrayList<>(request.businessKeys());
    if (keys.isEmpty()) blockers.add("必须声明输出业务键");
    for (String key : keys) {
      if (!StringUtils.hasText(key)) {
        blockers.add("业务键不能为空");
        continue;
      }
      if (!outputNames.contains(key.toLowerCase(Locale.ROOT)))
        blockers.add("业务键必须是已映射的输出字段：" + key);
      if (!keySet.add(key.toLowerCase(Locale.ROOT))) blockers.add("业务键重复：" + key);
    }
    if (!safeFilterExpression(request.filterExpression(), outputNames))
      blockers.add("过滤条件只允许输出字段 + 比较运算符 + 字面量；不支持任意 SQL");
    if (!safeSortExpression(request.sortExpression(), outputNames))
      blockers.add("排序只允许已映射输出字段及 ASC/DESC；不支持任意 SQL");
    if (!blockers.isEmpty()) return null;
    UpstreamPin pin = new UpstreamPin(upstream.id(), version.id(), version.versionNo(),
        version.checksum(), upstream.layerCode());
    return new Contract(request.appCode().trim(), request.appName().trim(), request.owner().trim(),
        request.purpose().trim(), request.consumerType(), request.sourceMode(), pin, fields, keys,
        request.filterExpression() == null ? "" : request.filterExpression().trim(),
        request.sortExpression() == null ? "" : request.sortExpression().trim(),
        request.metricVersionId(), request.refreshTarget() == null ? "" : request.refreshTarget().trim());
  }

  private boolean safeFilterExpression(String value, Set<String> outputNames) {
    if (!StringUtils.hasText(value)) return true;
    if (value.length() > 512 || value.contains(";") || value.contains("--")
        || value.contains("/*") || value.contains("*/")) return false;
    String[] clauses = value.split("(?i)\\s+AND\\s+");
    Pattern clause = Pattern.compile(
        "(?i)^([A-Za-z_][A-Za-z0-9_]*)\\s*(=|!=|<>|<=|>=|<|>|LIKE)\\s*(?:'[^']{0,256}'|[0-9]+(?:\\.[0-9]+)?|TRUE|FALSE|NULL)$");
    for (String item : clauses) {
      var matcher = clause.matcher(item.trim());
      if (!matcher.matches() || !outputNames.contains(matcher.group(1).toLowerCase(Locale.ROOT)))
        return false;
    }
    return true;
  }

  private boolean safeSortExpression(String value, Set<String> outputNames) {
    if (!StringUtils.hasText(value)) return true;
    if (value.length() > 512 || value.contains(";") || value.contains("--")
        || value.contains("/*") || value.contains("*/")) return false;
    Pattern item = Pattern.compile("(?i)^([A-Za-z_][A-Za-z0-9_]*)(?:\\s+(ASC|DESC))?$");
    for (String part : value.split(",")) {
      var matcher = item.matcher(part.trim());
      if (!matcher.matches() || !outputNames.contains(matcher.group(1).toLowerCase(Locale.ROOT)))
        return false;
    }
    return true;
  }

  private Model requireAds(long modelId) {
    Model model = models.findById(modelId).orElseThrow(() -> new IllegalArgumentException("当前项目没有该 ADS 模型"));
    if (!"ADS".equalsIgnoreCase(model.layerCode()) || model.processId() == null
        || model.status() == ModelStatus.DISABLED) throw new IllegalArgumentException("仅有效 ADS 模型可配置应用输出合同");
    return model;
  }

  private Model requireAdsForUpdate(long modelId) {
    Model model = models.findByIdForUpdate(modelId).orElseThrow(() -> new IllegalArgumentException("当前项目没有该 ADS 模型"));
    if (!"ADS".equalsIgnoreCase(model.layerCode()) || model.processId() == null
        || model.status() == ModelStatus.DISABLED) throw new IllegalArgumentException("仅有效 ADS 模型可配置应用输出合同");
    return model;
  }

  private Optional<Saved> current(long modelId) {
    return jdbc.query("SELECT model_id,revision,contract_sha256,contract_json,updated_by,updated_at "
        + "FROM yak_modeling_ads_application_design WHERE project_id=? AND model_id=?",
        (rs, row) -> decode(rs), projects.requireProjectId(), modelId).stream().findFirst();
  }

  private HistoryEntry decodeHistory(ResultSet rs) throws SQLException {
    Saved value = decode(rs);
    return new HistoryEntry(value.modelId(), value.revision(), value.contractSha256(),
        value.contractJson(), value.contract(), value.updatedBy(), value.updatedAt());
  }

  private Saved decode(ResultSet rs) throws SQLException {
    String json = rs.getString("contract_json");
    try {
      return new Saved(rs.getLong("model_id"), rs.getLong("revision"), rs.getString("contract_sha256"),
          json, mapper.readValue(json, Contract.class), rs.getString("updated_by"), rs.getString("updated_at"));
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("ADS 应用合同无法读取", error);
    }
  }

  private String encode(Contract value) {
    try { return mapper.writeValueAsString(value); }
    catch (JsonProcessingException error) { throw new IllegalStateException("ADS 应用合同无法序列化", error); }
  }
}
