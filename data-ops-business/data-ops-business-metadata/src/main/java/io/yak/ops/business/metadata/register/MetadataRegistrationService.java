package io.yak.ops.business.metadata.register;

import io.yak.ops.business.metadata.api.RegisterCommand;
import io.yak.ops.business.metadata.dao.model.CatalogAssetRow;
import io.yak.ops.business.metadata.dao.model.CatalogAssetState;
import io.yak.ops.business.metadata.dao.model.CatalogPresenceRow;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.BatchCommand;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.GoneCommand;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.GoneRow;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.PresenceScan;
import io.yak.ops.business.metadata.harvest.MetadataHarvestService;
import io.yak.ops.business.metadata.harvest.MetadataAttributeCodec;
import io.yak.ops.business.metadata.metamodel.MetadataKeyCodec;
import io.yak.ops.business.metadata.metamodel.MetadataKeyCodec.KeyProblem;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry;
import io.yak.ops.business.metadata.metamodel.MetadataTypeRegistry.TypeDefinition;
import io.yak.ops.common.enums.metadata.MetadataEntityStatus;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import io.yak.ops.common.enums.metadata.MetadataEnums.TypeStatus;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 登记主通道的本体（ticket 130，plan §3.2b/§3.2c 之"保序"与"同一 upsert"）。
 *
 * <p>本类<b>会抛异常</b>——它是 {@code RegistrationAppService} 与重试 worker 的被调方，
 * 失败必须让调用方看见才能落 outbox。"API 永不拖垮业务保存"由外层门面兑现，不在这里稀释。
 *
 * <p>两条通道在共表上的分岔全部复用采集已有的一套：upsert、指纹分岔、软删、变更流水都出自
 * {@link AssetUpsertRepository}。本类若自己写一条 INSERT，"同一实体两个真相"只是时间问题。
 *
 * <p>项目上下文不在本类里取：{@code projectId} 一律是显式入参——HTTP 挂钩取自 {@code CurrentProject}，
 * worker 取自 outbox 行（T10），两条来源在测试里可各自钉死。
 */
@Component
public class MetadataRegistrationService {

  /** unregister 候选池上限：行数由源侧决定，必须给上限（与在场扫描同一形状）。 */
  static final int UNREGISTER_SCAN_LIMIT = 2_000;

  /** changed_by 是 NOT NULL：源域没交出用户名时按机器写落 system（与采集侧同一口径）。 */
  static final String OPERATOR_FALLBACK = "system";

  // 截断上限与 MetadataHarvestService 同源：同一列被两条通道写，宽度不一致就是偶发 1406。
  private static final int NAME_MAX = 200;
  private static final int DISPLAY_NAME_MAX = 256;
  private static final int SUMMARY_MAX = 2048;
  private static final int FQN_MAX = 768;
  private static final int OWNER_MAX = 64;
  private static final int DOMAIN_IDS_MAX = 512;
  private static final int LAYER_MAX = 32;
  private static final int SOURCE_ID_MAX = 200;

  private final AssetUpsertRepository upsertRepository;
  private final MetadataTypeRegistry typeRegistry;
  private final MetadataAttributeCodec attributeCodec;

  public MetadataRegistrationService(
      AssetUpsertRepository upsertRepository,
      MetadataTypeRegistry typeRegistry,
      MetadataAttributeCodec attributeCodec) {
    this.upsertRepository = upsertRepository;
    this.typeRegistry = typeRegistry;
    this.attributeCodec = attributeCodec;
  }

  /** 一次登记落了内容，还是因"更旧"只刷了在场。 */
  public enum RegistrationResult {
    APPLIED,
    STALE_PRESENT
  }

  /**
   * 登记一个投影实体：校验 → 保序 → 与采集同一个 upsert。
   *
   * <p>失败一律抛 {@link MetadataException}（由门面落 outbox），错误码见各校验点。
   */
  public RegistrationResult register(Long projectId, RegisterCommand command) {
    if (command == null) {
      throw new MetadataException(MetadataErrorCode.REGISTER_COMMAND_INVALID, "command 为 null");
    }
    TypeDefinition type = requireProjectableType(command.getTypeName());
    requireValidKey(type, command.getAssetKey());
    require(command.getSourceId(), "sourceId");
    require(command.getSourceHash(), "sourceHash");
    if (command.getSourceUpdatedAt() == null) {
      // 保序令牌与 outbox 去重键都吃这一列，缺了它整条命令在库上无处安放（T17）。
      throw new MetadataException(
          MetadataErrorCode.REGISTER_COMMAND_INVALID, "sourceUpdatedAt 必填（保序与去重都靠它）");
    }

    LocalDateTime now = LocalDateTime.now();
    CatalogAssetState existing =
        upsertRepository.statesOf(projectId, List.of(command.getAssetKey())).get(command.getAssetKey());
    if (isStale(existing, command)) {
      // 晚到的旧快照不能覆盖新内容（保序），但"实体此刻仍在源侧"与快照新旧无关，必须留下。
      upsertRepository.touchPresence(projectId, List.of(command.getAssetKey()), now);
      return RegistrationResult.STALE_PRESENT;
    }
    CatalogAssetRow row = buildRow(projectId, type, command, now);
    upsertRepository.write(
        new BatchCommand(
            projectId,
            type.typeName(),
            ProviderType.REGISTERED,
            null,
            orDefault(command.getOperator(), OPERATOR_FALLBACK),
            now,
            false,
            List.of(row)));
    return RegistrationResult.APPLIED;
  }

  /**
   * 撤销登记行：按软删候选逐条走采集同一个 {@code markGone}。
   *
   * @param assetKey 为空则撤销该类型在该源下登记的全部行
   * @return 实际软删的行数；0 不是错误（幂等重放/从未登记都走这里）
   */
  public int unregister(Long projectId, String typeName, String sourceId, String assetKey) {
    TypeDefinition type = typeRegistry.require(typeName);
    require(sourceId, "sourceId");
    // 只圈本模块写的行：source_type/provider_type 两道谓词在 SQL 里（工单 130 的越界红线——
    // 遗留认领行归 lineage 的撤销链路，物理采集行归在场性判定）。
    PresenceScan scan =
        upsertRepository.registeredScan(projectId, type.type().getId(), sourceId, UNREGISTER_SCAN_LIMIT);
    if (scan.truncated()) {
      // 触顶说明候选池没读全，撤销会漏行；宁可抛出让门面落 outbox，不假装删干净。
      throw new MetadataException(
          MetadataErrorCode.REGISTER_COMMAND_INVALID,
          "unregister 候选触顶 " + UNREGISTER_SCAN_LIMIT + "，source=" + sourceId);
    }
    List<GoneRow> candidates =
        scan.rows().stream()
            .filter(row -> assetKey == null || assetKey.isBlank() || assetKey.equals(row.getAssetKey()))
            .<GoneRow>map(row -> new GoneRow(row, type.typeName()))
            .toList();
    if (candidates.isEmpty()) {
      return 0;
    }
    return upsertRepository.markGone(
        new GoneCommand(
            projectId,
            null,
            OPERATOR_FALLBACK,
            LocalDateTime.now(),
            false,
            ProviderType.REGISTERED,
            candidates));
  }

  /**
   * 该源该类型下当前在场登记的键（两参批量撤销在排队前先读键用）。
   *
   * <p>与 {@link #unregister} 共用同一候选查询，"撤销谁"与"数谁"因此不可能给出两个答案。
   */
  public List<String> registeredKeys(Long projectId, String typeName, String sourceId) {
    TypeDefinition type = typeRegistry.require(typeName);
    PresenceScan scan =
        upsertRepository.registeredScan(projectId, type.type().getId(), sourceId, UNREGISTER_SCAN_LIMIT);
    if (scan.truncated()) {
      // 与 unregister 同一口径：读不全的键列表会让批量撤销静默漏行，宁可交出去重放。
      throw new MetadataException(
          MetadataErrorCode.REGISTER_COMMAND_INVALID,
          "unregister 候选触顶 " + UNREGISTER_SCAN_LIMIT + "，source=" + sourceId);
    }
    return scan.rows().stream().map(CatalogPresenceRow::getAssetKey).toList();
  }

  /**
   * 登记资格校验：存在的 ENTITY + ACTIVE + <b>非采集</b>类型，且 lineage 类型可用。
   *
   * <p>非采集这一条是通道归属的分界：collectible 类型的在场由物理采集负责，走登记通道会出现
   * "同一张表两把真相的指纹"，且采集的缺席判定会把它当物理实体数进分母。
   */
  private TypeDefinition requireProjectableType(String typeName) {
    TypeDefinition type = typeRegistry.require(typeName); // 49002
    if (!type.isEntity()) {
      throw new MetadataException(
          MetadataErrorCode.REGISTER_COMMAND_INVALID, "type=" + typeName + " 不是实体类型");
    }
    if (!TypeStatus.ACTIVE.name().equals(type.type().getStatus())) {
      throw new MetadataException(MetadataErrorCode.TYPE_DEPRECATED, "type=" + typeName);
    }
    if (type.isCollectible()) {
      throw new MetadataException(
          MetadataErrorCode.REGISTER_COMMAND_INVALID,
          "type=" + typeName + " 由物理采集通道维护，不接受登记");
    }
    if (type.type().getLineageAssetType() == null || type.type().getLineageAssetType().isBlank()) {
      // 写出 null 的 asset_type 会让 lineage 读行 valueOf 抛异常——炸的是别人的查询（§9 T1）。
      throw new MetadataException(
          MetadataErrorCode.LINEAGE_ASSET_TYPE_INVALID, "type=" + typeName + " 尚未映射 lineage 类型");
    }
    return type;
  }

  private void requireValidKey(TypeDefinition type, String assetKey) {
    KeyProblem problem =
        MetadataKeyCodec.keyProblem(type.type().getKeyPrefix(), assetKey);
    if (problem != KeyProblem.NONE) {
      throw new MetadataException(
          MetadataErrorCode.ASSET_KEY_INVALID, "type=" + type.typeName() + " " + problem);
    }
  }

  private static boolean isStale(CatalogAssetState existing, RegisterCommand command) {
    return existing != null
        && existing.getSourceUpdatedAt() != null
        && command.getSourceUpdatedAt().isBefore(existing.getSourceUpdatedAt());
  }

  private CatalogAssetRow buildRow(
      Long projectId, TypeDefinition type, RegisterCommand command, LocalDateTime now) {
    CatalogAssetRow row = new CatalogAssetRow();
    row.setProjectId(projectId);
    row.setAssetKey(command.getAssetKey());
    row.setAssetType(type.type().getLineageAssetType());
    row.setTypeId(type.type().getId());
    row.setName(truncate(orDefault(command.getName(), command.getAssetKey()), NAME_MAX));
    row.setDisplayName(truncate(orDefault(command.getDisplayName(), command.getName()), DISPLAY_NAME_MAX));
    row.setSummary(truncate(command.getSummary(), SUMMARY_MAX));
    row.setSourceType(MetadataHarvestService.SOURCE_TYPE);
    row.setSourceId(truncate(command.getSourceId(), SOURCE_ID_MAX));
    row.setParentAssetId(resolveParentId(projectId, command.getParentAssetKey()));
    row.setOwnerUser(truncate(command.getOwnerUser(), OWNER_MAX));
    row.setDomainIds(truncate(command.getDomainIds(), DOMAIN_IDS_MAX));
    row.setLayerCode(truncate(command.getLayerCode(), LAYER_MAX));
    row.setFullyQualifiedName(truncate(renderFqn(type, command), FQN_MAX));
    row.setFqnHash(MetadataKeyCodec.fqnHash(command.getAssetKey()));
    row.setProviderType(ProviderType.REGISTERED.name());
    row.setEntityStatus(MetadataEntityStatus.UNPROCESSED.value());
    // content_hash 恒 NULL：登记通道看不见物理结构，伪造一份就是给采集的缺席判定递假证据。
    row.setSourceHash(command.getSourceHash());
    row.setSourceUpdatedAt(command.getSourceUpdatedAt());
    row.setFirstSeenAt(now);
    row.setLastCollectAt(now);
    row.setUpdatedBy(orDefault(command.getOperator(), OPERATOR_FALLBACK));
    // 属性袋唯一写入处（架构守卫按文件名放行 MetadataAttributeCodec）；未登记字段 49012 拒。
    attributeCodec.applyTo(row, type, command.getAttributes());
    return row;
  }

  /** 父级对不齐就留 NULL：登记的顺序由源域决定，缺父不是丢数据，对账通道会补齐。 */
  private Long resolveParentId(Long projectId, String parentAssetKey) {
    if (parentAssetKey == null || parentAssetKey.isBlank()) {
      return null;
    }
    Map<String, Long> ids = upsertRepository.idsOf(projectId, List.of(parentAssetKey));
    return ids.get(parentAssetKey);
  }

  /**
   * 展示用 FQN：占位符上下文 = name + 属性袋里的字符串值（type_def 的投影类模式如
   * {@code {modelCode}} 用的正是属性字段名）。渲染不出来的段被 {@code renderFqn} 丢弃，
   * 整段为空则 FQN 留 NULL——它是展示列，不是身份列。
   */
  private static String renderFqn(TypeDefinition type, RegisterCommand command) {
    Map<String, String> context = new LinkedHashMap<>();
    context.put("name", orDefault(command.getName(), ""));
    context.put("displayName", orDefault(command.getDisplayName(), ""));
    if (command.getAttributes() != null) {
      command.getAttributes().forEach((key, value) -> context.put(key, value == null ? "" : String.valueOf(value)));
    }
    return MetadataKeyCodec.renderFqn(
        type.type().getFqnPattern(), type.type().getKeySeparator(), context);
  }

  private static void require(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new MetadataException(MetadataErrorCode.REGISTER_COMMAND_INVALID, field + " 必填");
    }
  }

  private static String orDefault(String raw, String fallback) {
    String value = raw == null || raw.isBlank() ? null : raw.trim();
    return value == null ? fallback : value;
  }

  private static String truncate(String raw, int max) {
    String value = orDefault(raw, null);
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
