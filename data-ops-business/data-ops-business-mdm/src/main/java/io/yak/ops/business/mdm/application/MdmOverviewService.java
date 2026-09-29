package io.yak.ops.business.mdm.application;

import io.yak.ops.business.mdm.dao.MdmOverviewCardRow;
import io.yak.ops.business.mdm.dao.MdmOverviewTotalsRow;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.infrastructure.repository.MdmChangeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmDistributionRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmOverviewRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSubscriptionRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 主数据总览(ticket 62 → R7 收口):六卡 + 管线节点 + 实体卡片。
 *
 * <p>计数全部下推 SQL：一次聚合查询出六卡与管线节点，一次 LIMIT 查询出实体卡片。此前遍历
 * findAll() 再逐实体四次 count 的写法属无界 list + N+1，违反 docs/home-overview-contract.md。
 * 每张卡片/节点独立容错：查不到 = -1（展示为「-」），绝不把失败伪装成 0。
 */
@Service
public class MdmOverviewService {

  /** 实体卡片列表的硬上限(首页只需最近若干条)。 */
  public static final int MAX_ENTITY_CARDS = 50;

  private static final int DEFAULT_ENTITY_CARDS = 10;

  private final MdmOverviewRepository overviewRepository;
  private final MdmEntityService entityService;
  private final MdmRecordRepository recordRepository;
  private final MdmDistributionRepository distributionRepository;
  private final MdmSubscriptionRepository subscriptionRepository;
  private final MdmChangeRepository changeRepository;

  public MdmOverviewService(
      MdmOverviewRepository overviewRepository,
      MdmEntityService entityService,
      MdmRecordRepository recordRepository,
      MdmDistributionRepository distributionRepository,
      MdmSubscriptionRepository subscriptionRepository,
      MdmChangeRepository changeRepository) {
    this.overviewRepository = overviewRepository;
    this.entityService = entityService;
    this.recordRepository = recordRepository;
    this.distributionRepository = distributionRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.changeRepository = changeRepository;
  }

  /** 全量总览：六卡计数 + 管线五节点 + 最近实体卡片（limit 有界）。 */
  public OverviewResult getOverview(Integer requestedLimit) {
    int limit = clampLimit(requestedLimit);
    MdmOverviewTotalsRow row = safeTotals();
    OverviewTotals totals =
        new OverviewTotals(
            row.entities(),
            row.activeRecords(),
            row.pendingChanges(),
            row.cleanRules(),
            row.distributionTargets(),
            row.subscribers());
    List<PipelineNode> pipeline =
        List.of(
            new PipelineNode("COLLECT", "采集", row.collectLinks(), 0L),
            // 加工任务活在数据开发域,MDM 侧无真相表:按契约不伪造,展示「-」。
            new PipelineNode("PROCESSING", "加工", -1L, 0L),
            new PipelineNode("CLEANSE", "清洗", row.cleanRules(), row.mergeLogs()),
            new PipelineNode("APPROVE", "审批", row.pendingChanges(), row.pendingChanges()),
            new PipelineNode(
                "DISTRIBUTE", "分发", row.distributionTargets(), row.failingDistributions()));
    List<EntityCard> cards =
        overviewRepository.recentCards(limit).stream().map(MdmOverviewService::toCard).toList();
    return new OverviewResult(totals, pipeline, cards);
  }

  /** 单实体概览卡片。 */
  public EntityCard getEntityCard(Long entityId) {
    MdmEntity entity = entityService.get(entityId);
    return new EntityCard(
        entityId,
        entity.code(),
        entity.name(),
        safeCount(() -> recordRepository.countActiveByEntity(entityId)),
        safeCount(() -> distributionRepository.countActiveByEntity(entityId)),
        safeCount(() -> subscriptionRepository.countActiveByEntity(entityId)),
        safeCount(() -> changeRepository.countPending(entityId)));
  }

  private static int clampLimit(Integer requested) {
    if (requested == null || requested <= 0) {
      return DEFAULT_ENTITY_CARDS;
    }
    return Math.min(requested, MAX_ENTITY_CARDS);
  }

  private MdmOverviewTotalsRow safeTotals() {
    try {
      return overviewRepository.totals();
    } catch (RuntimeException exception) {
      return new MdmOverviewTotalsRow(-1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L);
    }
  }

  private static EntityCard toCard(MdmOverviewCardRow row) {
    return new EntityCard(
        row.entityId(),
        row.entityCode(),
        row.entityName(),
        row.activeRecords(),
        row.distributionTargets(),
        row.subscribers(),
        row.pendingChanges());
  }

  private static long safeCount(java.util.function.LongSupplier supplier) {
    try {
      return supplier.getAsLong();
    } catch (RuntimeException ignored) {
      return -1L;
    }
  }

  /** 总览结果。 */
  public record OverviewResult(
      OverviewTotals totals, List<PipelineNode> pipeline, List<EntityCard> entities) {}

  /** 六卡计数（-1 表示不可得，前端展示「-」而非 0）。 */
  public record OverviewTotals(
      long entities,
      long activeRecords,
      long pendingChanges,
      long cleanRules,
      long distributionTargets,
      long subscribers) {}

  /** 管线节点：count=事实数，attention=需要红点数。 */
  public record PipelineNode(String key, String label, long count, long attention) {}

  /** 单实体卡片（每张卡片独立容错：-1 表示查不到）。 */
  public record EntityCard(
      Long entityId,
      String entityCode,
      String entityName,
      long activeRecords,
      long distributionTargets,
      long subscribers,
      long pendingChanges) {}
}
