package io.yak.ops.business.consumption.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.relationship.ConsumerRef;
import io.yak.ops.business.consumption.relationship.ConsumerType;
import io.yak.ops.business.consumption.relationship.ConsumptionMode;
import io.yak.ops.business.consumption.relationship.Subscription;
import io.yak.ops.business.consumption.relationship.SubscriptionRepository;
import io.yak.ops.business.consumption.relationship.SubscriptionStatus;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class MybatisSubscriptionRepository implements SubscriptionRepository {

  private final SubscriptionMapper mapper;

  @Override
  public Optional<Subscription> find(
      Long projectId,
      ProductKey productKey,
      ConsumerRef consumerRef,
      ConsumptionMode consumptionMode) {
    LambdaQueryWrapper<SubscriptionPO> query = new LambdaQueryWrapper<SubscriptionPO>()
        .eq(SubscriptionPO::getProjectId, projectId)
        .eq(SubscriptionPO::getProductKey, productKey.value())
        .eq(SubscriptionPO::getConsumerType, consumerRef.consumerType().name())
        .eq(SubscriptionPO::getSourceDomain, consumerRef.sourceDomain())
        .eq(SubscriptionPO::getSourceIdentity, consumerRef.sourceIdentity())
        .eq(SubscriptionPO::getConsumptionMode, consumptionMode.name())
        .last("LIMIT 1");
    return Optional.ofNullable(mapper.selectOne(query)).map(this::toDomain);
  }

  @Override
  public Optional<Subscription> findById(Long projectId, Long subscriptionId) {
    LambdaQueryWrapper<SubscriptionPO> query = new LambdaQueryWrapper<SubscriptionPO>()
        .eq(SubscriptionPO::getProjectId, projectId)
        .eq(SubscriptionPO::getId, subscriptionId)
        .last("LIMIT 1");
    return Optional.ofNullable(mapper.selectOne(query)).map(this::toDomain);
  }

  @Override
  public Subscription save(Subscription subscription) {
    SubscriptionPO po = toPo(subscription);
    if (po.getId() == null) {
      mapper.insert(po);
    } else {
      mapper.updateById(po);
    }
    return toDomain(po);
  }

  @Override
  public List<Subscription> list(Long projectId, ProductKey productKey, ConsumerRef consumerRef) {
    LambdaQueryWrapper<SubscriptionPO> query = new LambdaQueryWrapper<SubscriptionPO>()
        .eq(SubscriptionPO::getProjectId, projectId)
        .orderByDesc(SubscriptionPO::getUpdatedAt)
        .orderByDesc(SubscriptionPO::getId);
    if (productKey != null) {
      query.eq(SubscriptionPO::getProductKey, productKey.value());
    }
    if (consumerRef != null) {
      query.eq(SubscriptionPO::getConsumerType, consumerRef.consumerType().name())
          .eq(SubscriptionPO::getSourceDomain, consumerRef.sourceDomain())
          .eq(SubscriptionPO::getSourceIdentity, consumerRef.sourceIdentity());
    }
    return mapper.selectList(query).stream().map(this::toDomain).toList();
  }

  @Override
  public List<Subscription> listRecentActive(Long projectId, ProductKey productKey, int limit) {
    if (projectId == null || projectId <= 0 || productKey == null) {
      throw new IllegalArgumentException("Subscription evidence requires a project and product");
    }
    LambdaQueryWrapper<SubscriptionPO> query = new LambdaQueryWrapper<SubscriptionPO>()
        .eq(SubscriptionPO::getProjectId, projectId)
        .eq(SubscriptionPO::getProductKey, productKey.value())
        .eq(SubscriptionPO::getStatus, SubscriptionStatus.ACTIVE.name())
        .orderByDesc(SubscriptionPO::getUpdatedAt)
        .orderByDesc(SubscriptionPO::getId)
        .last("LIMIT " + Math.max(1, Math.min(200, limit)));
    return mapper.selectList(query).stream().map(this::toDomain).toList();
  }

  private Subscription toDomain(SubscriptionPO po) {
    return new Subscription(
        po.getId(),
        po.getProjectId(),
        ProductKey.parse(po.getProductKey()),
        new ConsumerRef(
            ConsumerType.valueOf(po.getConsumerType()),
            po.getSourceDomain(),
            po.getSourceIdentity(),
            po.getDisplayHint()),
        ConsumptionMode.valueOf(po.getConsumptionMode()),
        SubscriptionStatus.valueOf(po.getStatus()),
        po.getCreatedBy(),
        po.getCreatedAt(),
        po.getUpdatedBy(),
        po.getUpdatedAt());
  }

  private SubscriptionPO toPo(Subscription subscription) {
    SubscriptionPO po = new SubscriptionPO();
    po.setId(subscription.id());
    po.setProjectId(subscription.projectId());
    po.setProductKey(subscription.productKey().value());
    po.setConsumerType(subscription.consumerRef().consumerType().name());
    po.setSourceDomain(subscription.consumerRef().sourceDomain());
    po.setSourceIdentity(subscription.consumerRef().sourceIdentity());
    po.setDisplayHint(subscription.consumerRef().displayHint());
    po.setConsumptionMode(subscription.consumptionMode().name());
    po.setStatus(subscription.status().name());
    po.setCreatedBy(subscription.createdBy());
    po.setCreatedAt(subscription.createdAt());
    po.setUpdatedBy(subscription.updatedBy());
    po.setUpdatedAt(subscription.updatedAt());
    return po;
  }
}
