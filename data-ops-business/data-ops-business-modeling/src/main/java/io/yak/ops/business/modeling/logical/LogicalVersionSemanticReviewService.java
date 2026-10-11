package io.yak.ops.business.modeling.logical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.exception.ModelingException;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human-readable review of an exact DRAFT logical snapshot against today's
 * Modeling draft and authorized Semantic facts. Never approves a relation,
 * publishes a model or persists a logical/physical mapping.
 */
@Service
@RequiredArgsConstructor
public class LogicalVersionSemanticReviewService {
  private static final int MAX_ITEMS = 1000;
  private final LogicalDraftService logical;
  private final ProcessApi semantic;
  private final ObjectMapper json;

  public record Change(String objectType, Long objectId, String label,
                       String status, String frozenValue, String currentValue) {}
  public record AttributeReview(Long entityId, String entityName, Long attributeId,
                                String attributeName, Long stdFieldId,
                                String standardName, String standardRole,
                                String standardStatus, String explanation) {}
  public record RelationReview(Long relationId, String sourceEntity, String targetEntity,
                               String cardinality, String status, String explanation) {}
  public record Report(Long logicalModelId, int versionNo, String frozenStatus,
                       String draftStatus, String frozenSnapshotSha256,
                       String frozenDesignSha256, String currentDesignSha256,
                       int entityCount, int attributeCount, int relationCount,
                       String designComparison, String reviewStatus,
                       List<Change> changes, List<AttributeReview> attributes,
                       List<RelationReview> relations, List<String> blockers,
                       List<String> limitations) {}

  private record Root(Long id, Long processId, String code, String name,
                      String description) {}
  private record Entity(Long id, String code, String name, String businessName,
                        String description) {}
  private record Attribute(Long id, Long entityId, String code, String name,
                           Long stdFieldId, String logicalType, Boolean primaryFlag,
                           Boolean nullable, String description) {}
  private record Relation(Long id, Long sourceEntityId, Long targetEntityId,
                          String relationType, String cardinality, String description) {}
  private record Design(Root root, List<Entity> entities, List<Attribute> attributes,
                        List<Relation> relations) {}

  @Transactional(transactionManager = "yakBusinessTransactionManager", readOnly = true)
  public Report review(Long id, int versionNo) {
    if (id == null || id <= 0 || versionNo <= 0) throw invalid("需要明确的逻辑模型与冻结版本");
    // Both accesses validate the canonical project-scoped logical-model root.
    String original = logical.versionSnapshot(id, versionNo);
    LogicalDraftService.DraftView current = logical.get(id);
    JsonNode frozenNode;
    JsonNode currentNode;
    try {
      frozenNode = json.readTree(original);
      currentNode = json.valueToTree(current);
    } catch (Exception ex) {
      throw invalid("逻辑版本或当前草稿无法形成可审阅的设计证据");
    }
    Design frozen = parse(frozenNode, id);
    Design live = parse(currentNode, id);
    if (!Objects.equals(frozen.root().processId(), live.root().processId())) {
      // This is a drift finding, not permission to resolve the old process by ID.
      // Don't call Semantic with the untrusted historic process ID.
    }
    String snapshotHash = sha(original);
    String frozenHash = hash(frozen);
    String liveHash = hash(live);

    List<Change> changes = new ArrayList<>();
    compare("MODEL", Map.of(frozen.root().id(), frozen.root()),
        Map.of(live.root().id(), live.root()), changes,
        value -> ((Root) value).name());
    compare("ENTITY", keyed(frozen.entities(), Entity::id), keyed(live.entities(), Entity::id),
        changes, value -> ((Entity) value).name());
    compare("ATTRIBUTE", keyed(frozen.attributes(), Attribute::id),
        keyed(live.attributes(), Attribute::id), changes,
        value -> ((Attribute) value).name());
    compare("RELATION", keyed(frozen.relations(), Relation::id),
        keyed(live.relations(), Relation::id), changes,
        value -> "关系 #" + ((Relation) value).id());

    List<String> blockers = new ArrayList<>();
    if (frozen.entities().isEmpty() || frozen.attributes().isEmpty()) {
      blockers.add("冻结版本缺少业务实体或属性，不能认定逻辑设计可审阅");
    }
    if (!frozenHash.equals(liveHash)) {
      blockers.add("当前逻辑草稿与所选冻结版本不同；请先核实实体、属性和关系差异");
    }
    if (!Objects.equals(frozen.root().processId(), current.process().id())) {
      blockers.add("冻结版本所指业务过程与当前项目可访问的业务过程不同，需人工核对历史过程");
    }
    if (current.process().grain() == null || current.process().grain().isBlank()) {
      blockers.add("当前业务过程缺少明确粒度；不可自动从字段名推断订单与明细基数");
    }

    Map<Long, StandardField> standards = new HashMap<>();
    List<AttributeReview> attributeReviews = new ArrayList<>();
    Map<Long, Entity> entityById = keyed(frozen.entities(), Entity::id);
    for (Attribute attr : frozen.attributes()) {
      Entity owner = entityById.get(attr.entityId());
      if (attr.stdFieldId() == null) {
        attributeReviews.add(new AttributeReview(attr.entityId(), owner.name(), attr.id(),
            attr.name(), null, null, null, "PENDING_STANDARD",
            "冻结逻辑属性没有正式标准字段引用，仍为待治理设计项"));
        blockers.add("属性待治理：" + owner.name() + "." + attr.name());
        continue;
      }
      StandardField ref = standards.computeIfAbsent(attr.stdFieldId(), semantic::getField);
      if (ref == null || !ref.isEnabled()) {
        attributeReviews.add(new AttributeReview(attr.entityId(), owner.name(), attr.id(),
            attr.name(), attr.stdFieldId(), null, null, "INVALID_STANDARD",
            "当前标准字段不存在、不可用或停用；冻结 ID 不得静默改为同名标准"));
        blockers.add("标准字段不可用：" + owner.name() + "." + attr.name());
      } else {
        attributeReviews.add(new AttributeReview(attr.entityId(), owner.name(), attr.id(),
            attr.name(), attr.stdFieldId(), ref.name(), ref.role(), "ENABLED_NOW",
            "当前授权标准字段可读且启用；不等于冻结时的标准版本已锁定，也不推断聚合角色"));
      }
    }

    List<RelationReview> relationReviews = new ArrayList<>();
    for (Relation relation : frozen.relations()) {
      Entity from = entityById.get(relation.sourceEntityId());
      Entity to = entityById.get(relation.targetEntityId());
      String state = "UNKNOWN".equals(relation.cardinality())
          ? "CARDINALITY_PENDING" : "DECLARED_NOT_CONFIRMED";
      if ("CARDINALITY_PENDING".equals(state)) {
        blockers.add("关系基数待确认：#" + relation.id());
      }
      relationReviews.add(new RelationReview(relation.id(), from.name(), to.name(),
          relation.cardinality(), state,
          "关系来自逻辑草稿的人工声明；没有独立业务证据、确认人或执行 JOIN 的许可"));
    }

    List<String> limitations = List.of(
        "逻辑版本为冻结的 DRAFT 设计快照，不是已审批业务语义或正式发布版本",
        "标准字段显示当前 Semantic 可访问及启用状态，未冻结该字段发布当时的内容与标准版本",
        "关系基数来自作者声明；同名列或外键不构成真实业务基数证据",
        "本报告不会创建或更新逻辑→物理映射、物理模型、DataDev 任务或执行结果");
    return new Report(id, versionNo, frozenNode.path("model").path("status").asText("UNKNOWN"),
        current.model().getStatus(), snapshotHash, frozenHash, liveHash,
        frozen.entities().size(), frozen.attributes().size(), frozen.relations().size(),
        frozenHash.equals(liveHash) ? "MATCHES_CURRENT" : "DIFFERS_FROM_CURRENT",
        blockers.isEmpty() ? "REVIEWABLE_ONLY" : "NEEDS_REVIEW",
        List.copyOf(changes), List.copyOf(attributeReviews),
        List.copyOf(relationReviews), List.copyOf(blockers), limitations);
  }

  private Design parse(JsonNode root, Long expectedModelId) {
    if (root == null || !root.isObject() || !root.path("model").isObject()
        || root.path("model").path("id").asLong(-1) != expectedModelId
        || !root.path("entities").isArray() || !root.path("relations").isArray()) {
      throw invalid("冻结逻辑版本的身份或集合格式不可信");
    }
    JsonNode model = root.path("model");
    Long process = positive(model.path("processId"));
    if (process == null) throw invalid("逻辑模型缺少正式业务过程身份");
    Root rootFact = new Root(expectedModelId, process, str(model, "code"),
        str(model, "name"), str(model, "description"));
    List<Entity> entities = new ArrayList<>();
    List<Attribute> attributes = new ArrayList<>();
    Set<Long> ids = new HashSet<>();
    Set<Long> attrIds = new HashSet<>();
    for (JsonNode row : root.path("entities")) {
      JsonNode e = row.path("entity");
      Long eid = positive(e.path("id"));
      if (eid == null || !ids.add(eid) || !row.path("attributes").isArray()
          || entities.size() >= MAX_ITEMS) throw invalid("逻辑实体缺少唯一身份或超出核验上限");
      entities.add(new Entity(eid, str(e, "code"), str(e, "name"),
          str(e, "businessName"), str(e, "description")));
      for (JsonNode value : row.path("attributes")) {
        Long aid = positive(value.path("id"));
        if (aid == null || !attrIds.add(aid)
            || !Objects.equals(positive(value.path("entityId")), eid)
            || attributes.size() >= MAX_ITEMS) {
          throw invalid("逻辑属性缺少稳定 ID、跨实体或超过核验上限");
        }
        attributes.add(new Attribute(aid, eid, str(value, "code"), str(value, "name"),
            positive(value.path("stdFieldId")), str(value, "logicalType"),
            bool(value, "primaryFlag"), bool(value, "nullable"), str(value, "description")));
      }
    }
    List<Relation> relations = new ArrayList<>();
    Set<Long> relationIds = new HashSet<>();
    for (JsonNode relation : root.path("relations")) {
      Long rid = positive(relation.path("id"));
      Long from = positive(relation.path("sourceEntityId"));
      Long to = positive(relation.path("targetEntityId"));
      if (rid == null || !relationIds.add(rid) || from == null || to == null
          || !ids.contains(from) || !ids.contains(to)
          || relations.size() >= MAX_ITEMS) {
        throw invalid("逻辑关系身份重复、引用外部实体或超过核验上限");
      }
      String cardinality = str(relation, "cardinality");
      if (!Set.of("ONE_TO_ONE", "ONE_TO_MANY", "MANY_TO_ONE", "MANY_TO_MANY", "UNKNOWN")
          .contains(cardinality)) throw invalid("无法解释的逻辑关系基数");
      relations.add(new Relation(rid, from, to, str(relation, "relationType"),
          cardinality, str(relation, "description")));
    }
    entities.sort(Comparator.comparing(Entity::id));
    attributes.sort(Comparator.comparing(Attribute::id));
    relations.sort(Comparator.comparing(Relation::id));
    return new Design(rootFact, List.copyOf(entities), List.copyOf(attributes),
        List.copyOf(relations));
  }

  private static <T> Map<Long, T> keyed(List<T> rows, java.util.function.Function<T, Long> id) {
    Map<Long, T> result = new HashMap<>();
    for (T row : rows) result.put(id.apply(row), row);
    return result;
  }
  private static <T> void compare(String objectType, Map<Long, T> frozen,
      Map<Long, T> live, List<Change> into, java.util.function.Function<T, String> label) {
    Set<Long> all = new java.util.TreeSet<>(frozen.keySet());
    all.addAll(live.keySet());
    for (Long id : all) {
      T old = frozen.get(id), now = live.get(id);
      if (Objects.equals(old, now)) continue;
      String status = old == null ? "ADDED_NOW" : now == null ? "REMOVED_NOW" : "CHANGED_NOW";
      String name = label.apply(old == null ? now : old);
      into.add(new Change(objectType, id, name, status,
          old == null ? null : String.valueOf(old),
          now == null ? null : String.valueOf(now)));
    }
  }
  private String hash(Design design) {
    try { return sha(json.writeValueAsString(design)); }
    catch (JsonProcessingException ex) { throw invalid("无法形成稳定逻辑设计指纹"); }
  }
  private static String sha(String source) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(source.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
  }
  private static String str(JsonNode object, String key) {
    return object.path(key).isNull() || object.path(key).isMissingNode()
        ? null : object.path(key).asText();
  }
  private static Boolean bool(JsonNode object, String key) {
    return object.path(key).isBoolean() ? object.path(key).asBoolean() : null;
  }
  private static Long positive(JsonNode node) { return node.canConvertToLong() && node.asLong() > 0
      ? node.asLong() : null; }
  private static ModelingException invalid(String reason) {
    return new ModelingException(ModelingErrorCode.INVALID_COLUMN, reason);
  }
}
