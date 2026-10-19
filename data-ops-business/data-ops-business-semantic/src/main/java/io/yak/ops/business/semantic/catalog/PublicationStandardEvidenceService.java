package io.yak.ops.business.semantic.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.repository.SemanticFieldRepository;
import io.yak.ops.business.semantic.repository.SemanticPublicationRevisionService;
import io.yak.ops.core.project.CurrentProject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * R14-B2 T1 development: real, project-scoped current Semantic batch evidence.
 *
 * <p>Reads every requested physical column and its actual StandardField / linked
 * standard facts under the same project's persisted revision-row lock. A VALID
 * result describes only the current Semantic catalog at this exact transaction,
 * NOT Security authorization, immutable ModelVersion approval, or permission to
 * publish. No publish path is wired to this component.
 *
 * <p>Security/masking references remain UNKNOWN until T3 supplies an authenticated,
 * non-disclosing authorization scope + digest. Never manufacture a valid result
 * from missing projects, incomplete CODE members or read failures.
 */
@Component
public class PublicationStandardEvidenceService implements PublicationStandardEvidenceApi {
  public static final String ALGORITHM = "B2-SEMANTIC-CURRENT-V1";
  private static final int MAX_COLUMNS = 1000;
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  private static final Set<String> LAYERS = Set.of("DWD", "DIM", "DWS", "ADS");

  private final CurrentProject project;
  private final SemanticPublicationRevisionService revision;
  private final SemanticFieldRepository fields;
  private final StandardQueryApi standards;

  public PublicationStandardEvidenceService(
      CurrentProject project,
      SemanticPublicationRevisionService revision,
      SemanticFieldRepository fields,
      StandardQueryApi standards) {
    this.project = project;
    this.revision = revision;
    this.fields = fields;
    this.standards = standards;
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public BatchResult evaluate(BatchRequest request) {
    if (request == null) {
      return unknown(null, "INVALID_REQUEST");
    }
    // Reject a cross-project query before any Semantic detail lookup. A caller-
    // supplied projectId is evidence identity, never its own authorization.
    try {
      Long actual = project.requireProjectId();
      if (actual == null || actual <= 0 || actual.longValue() != request.projectId()) {
        return unknown(request, "SCOPE_UNAVAILABLE");
      }
      if (!validRequest(request)) return unknown(request, "INVALID_REQUEST");
      long currentRevision = revision.lockExistingForReview();
      List<ColumnEvidence> evidence = new ArrayList<>();
      Set<String> reasons = new java.util.LinkedHashSet<>();
      boolean unknown = false;
      for (ColumnRequest column : request.columns()) {
        ColumnEvidence inspected = inspect(column);
        evidence.add(inspected);
        if (inspected.safeReasonCode() != null) reasons.add(inspected.safeReasonCode());
        if ("UNKNOWN".equals(inspected.status())) unknown = true;
      }
      evidence.sort(Comparator.comparing(ColumnEvidence::columnName,
          String.CASE_INSENSITIVE_ORDER));
      if (unknown) {
        return new BatchResult(Verdict.UNKNOWN, request.projectId(), request.modelId(),
            request.layerCode(), request.physicalStructureSha256(), currentRevision,
            null, null, evidence, List.copyOf(reasons));
      }
      Verdict verdict = reasons.isEmpty() ? Verdict.VALID : Verdict.BLOCKED;
      String evidenceSha = digest(List.of(ALGORITHM, request.projectId(), request.modelId(),
          request.layerCode(), request.physicalStructureSha256(), currentRevision, evidence));
      return new BatchResult(verdict, request.projectId(), request.modelId(),
          request.layerCode(), request.physicalStructureSha256(), currentRevision,
          ALGORITHM, evidenceSha, evidence, List.copyOf(reasons));
    } catch (RuntimeException failure) {
      // DB errors, unavailable revision rows, missing project permissions,
      // malformed provider output: fail closed, never return an empty VALID.
      return unknown(request, "PROVIDER_UNAVAILABLE");
    }
  }

  private ColumnEvidence inspect(ColumnRequest column) {
    Long fieldId = column.standardFieldId();
    if (fieldId == null || fieldId <= 0) return rejected(column, "MISSING_STANDARD");
    StandardField field = fields.findById(fieldId).orElse(null);
    if (field == null || !Objects.equals(field.id(), fieldId) || !field.isEnabled()
        || !StandardField.isValidRole(field.role())) {
      return rejected(column, "INVALID_STANDARD");
    }
    if (field.dataType() == null || !field.dataType().trim()
        .equalsIgnoreCase(column.physicalType().trim())) {
      return rejected(column, "PHYSICAL_TYPE_MISMATCH");
    }

    var references = new ArrayList<StandardReference>();
    String issue = addReference(references, field.stdTypeId(), StandardKind.TYPE, true);
    if (issue == null && StandardField.ROLE_METRIC.equals(field.role())) {
      issue = addReference(references, field.stdUnitId(), StandardKind.UNIT, true);
      if (issue == null) issue = addReference(references,
          field.stdCaliberId(), StandardKind.CALIBER, false);
    }
    CodeSetReference codeSet = null;
    if (issue == null && StandardField.ROLE_DIMENSION.equals(field.role())) {
      if (field.stdCodeSetCode() != null && !field.stdCodeSetCode().isBlank()) {
        var code = standards.codeSetReference(field.stdCodeSetCode());
        if (code == null || !Objects.equals(code.codeSetCode(), field.stdCodeSetCode())
            || code.itemCount() <= 0 || code.enabledItemCount() != code.itemCount()
            || !"ENABLED".equals(code.status())
            || !isSha(code.contentSha256())) {
          issue = "INVALID_CODE_SET";
        } else {
          codeSet = new CodeSetReference(code.codeSetCode(), code.status(),
              code.itemCount(), code.enabledItemCount(), "CODE-ALL-MEMBERS-V1",
              code.contentSha256());
        }
      }
      if (field.stdSecurityId() != null && field.stdSecurityId() > 0) {
        // A low-entropy masking rule digest cannot stand in for Security's
        // trusted policy/subject/epoch proof; never expose or hash it here.
        var security = standards.get(field.stdSecurityId());
        issue = security == null || security.kind() != StandardKind.SECURITY
            || security.status() != StandardStatus.ENABLED
            ? "INVALID_SECURITY_STANDARD" : "SECURITY_EPOCH_UNAVAILABLE";
      }
    }
    StandardReference identity = new StandardReference("STANDARD_FIELD", field.id(),
        field.version(), field.status(), digest(List.of(field.id(), field.version(),
            field.role(), field.status(), field.dataType(),
            String.valueOf(field.stdTypeId()), String.valueOf(field.stdUnitId()),
            String.valueOf(field.stdCaliberId()),
            String.valueOf(field.stdCodeSetCode()), String.valueOf(field.stdSecurityId()))));
    String status = "SECURITY_EPOCH_UNAVAILABLE".equals(issue) ? "UNKNOWN"
        : issue == null ? "VALID" : "BLOCKED";
    return new ColumnEvidence(column.columnName(), column.physicalType(), field.id(),
        field.role(), status, identity, references, codeSet, issue);
  }

  private String addReference(List<StandardReference> into, Long id,
                              StandardKind kind, boolean required) {
    if (id == null || id <= 0) return required ? "MISSING_REQUIRED_REFERENCE" : null;
    Standard fact = standards.get(id);
    if (fact == null || !Objects.equals(fact.id(), id)
        || fact.kind() != kind || fact.status() != StandardStatus.ENABLED
        || fact.fields() == null || !hasDefinition(fact)) {
      return "INVALID_STANDARD_REFERENCE";
    }
    String definition = switch (kind) {
      case TYPE -> fact.fields().typeCode() + "|" + fact.fields().stdType();
      case UNIT -> fact.fields().unitCode();
      case CALIBER -> fact.fields().calRule();
      default -> throw new IllegalArgumentException("Unsupported publication standard kind");
    };
    into.add(new StandardReference(kind.name(), fact.id(), fact.version(),
        fact.status().name(), digest(List.of(fact.id(), fact.version(),
            fact.status().name(), kind.name(), definition))));
    return null;
  }

  private static boolean hasDefinition(Standard fact) {
    return switch (fact.kind()) {
      case TYPE -> hasText(fact.fields().typeCode()) && hasText(fact.fields().stdType());
      case UNIT -> hasText(fact.fields().unitCode());
      case CALIBER -> hasText(fact.fields().calRule());
      default -> false;
    };
  }

  private static ColumnEvidence rejected(ColumnRequest column, String reason) {
    return new ColumnEvidence(column.columnName(), column.physicalType(),
        column.standardFieldId(), null, "BLOCKED", null, List.of(), null, reason);
  }

  private static BatchResult unknown(BatchRequest req, String code) {
    return new BatchResult(Verdict.UNKNOWN,
        req == null ? 0 : req.projectId(), req == null ? 0 : req.modelId(),
        req == null ? null : req.layerCode(),
        req == null ? null : req.physicalStructureSha256(), null, null, null,
        List.of(), List.of(code));
  }

  private static boolean validRequest(BatchRequest req) {
    if (req.projectId() <= 0 || req.modelId() <= 0 || !LAYERS.contains(req.layerCode())
        || !isSha(req.physicalStructureSha256())
        || req.columns().isEmpty() || req.columns().size() > MAX_COLUMNS) return false;
    Set<String> names = new HashSet<>();
    for (ColumnRequest col : req.columns()) {
      if (col == null || !hasText(col.columnName()) || !hasText(col.physicalType())
          || !names.add(col.columnName().trim().toLowerCase(Locale.ROOT))) return false;
    }
    return true;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static boolean isSha(String value) {
    return value != null && value.matches("[a-f0-9]{64}");
  }

  private static String digest(Object payload) {
    try {
      byte[] bytes = JSON.writeValueAsBytes(payload);
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception error) {
      throw new IllegalStateException("Semantic standard publication digest failed", error);
    }
  }
}
