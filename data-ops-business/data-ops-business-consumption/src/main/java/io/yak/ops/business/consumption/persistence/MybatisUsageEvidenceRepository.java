package io.yak.ops.business.consumption.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.business.consumption.relationship.ConsumerRef;
import io.yak.ops.business.consumption.relationship.ConsumerType;
import io.yak.ops.business.consumption.relationship.ConsumptionMode;
import io.yak.ops.business.consumption.relationship.UsageEvidence;
import io.yak.ops.business.consumption.relationship.UsageEvidenceRepository;
import io.yak.ops.business.consumption.relationship.UsageOutcome;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class MybatisUsageEvidenceRepository implements UsageEvidenceRepository {

  private final UsageEvidenceMapper mapper;

  @Override
  public Optional<UsageEvidence> findByDeduplicationId(Long projectId, String deduplicationId) {
    LambdaQueryWrapper<UsageEvidencePO> query = new LambdaQueryWrapper<UsageEvidencePO>()
        .eq(UsageEvidencePO::getProjectId, projectId)
        .eq(UsageEvidencePO::getDeduplicationId, deduplicationId)
        .last("LIMIT 1");
    return Optional.ofNullable(mapper.selectOne(query)).map(this::toDomain);
  }

  @Override
  public UsageEvidence save(UsageEvidence evidence) {
    UsageEvidencePO po = toPo(evidence);
    if (po.getId() == null) mapper.insert(po);
    else mapper.updateById(po);
    return toDomain(po);
  }

  @Override
  public List<UsageEvidence> list(
      Long projectId, ProductKey productKey, ConsumerRef consumerRef, int limit) {
    LambdaQueryWrapper<UsageEvidencePO> query = new LambdaQueryWrapper<UsageEvidencePO>()
        .eq(UsageEvidencePO::getProjectId, projectId)
        .orderByDesc(UsageEvidencePO::getObservedAt)
        .orderByDesc(UsageEvidencePO::getId)
        .last("LIMIT " + Math.max(1, Math.min(200, limit)));
    if (productKey != null) query.eq(UsageEvidencePO::getProductKey, productKey.value());
    if (consumerRef != null) {
      query.eq(UsageEvidencePO::getConsumerType, consumerRef.consumerType().name())
          .eq(UsageEvidencePO::getSourceDomain, consumerRef.sourceDomain())
          .eq(UsageEvidencePO::getSourceIdentity, consumerRef.sourceIdentity());
    }
    return mapper.selectList(query).stream().map(this::toDomain).toList();
  }

  @Override
  public List<UsageEvidence> listByVersion(
      Long projectId, ProductKey productKey, String sourceVersionIdentity, int limit) {
    LambdaQueryWrapper<UsageEvidencePO> query = new LambdaQueryWrapper<UsageEvidencePO>()
        .eq(UsageEvidencePO::getProjectId, projectId)
        .eq(UsageEvidencePO::getProductKey, productKey.value())
        .eq(UsageEvidencePO::getSourceVersionIdentity, sourceVersionIdentity)
        .orderByDesc(UsageEvidencePO::getObservedAt)
        .orderByDesc(UsageEvidencePO::getId)
        .last("LIMIT " + Math.max(1, Math.min(200, limit)));
    return mapper.selectList(query).stream().map(this::toDomain).toList();
  }

  private UsageEvidence toDomain(UsageEvidencePO po) {
    return new UsageEvidence(
        po.getId(),
        po.getProjectId(),
        ProductKey.parse(po.getProductKey()),
        new SourceVersionRef(po.getSourceVersionIdentity(), po.getSourceDisplayVersion()),
        new ConsumerRef(
            ConsumerType.valueOf(po.getConsumerType()),
            po.getSourceDomain(),
            po.getSourceIdentity(),
            po.getDisplayHint()),
        po.getObservedAt(),
        ConsumptionMode.valueOf(po.getConsumptionMode()),
        UsageOutcome.valueOf(po.getOutcome()),
        po.getProvider(),
        po.getProviderEvidenceRef(),
        po.getDeduplicationId(),
        po.getNormalizedAt());
  }

  private UsageEvidencePO toPo(UsageEvidence evidence) {
    UsageEvidencePO po = new UsageEvidencePO();
    po.setId(evidence.id());
    po.setProjectId(evidence.projectId());
    po.setProductKey(evidence.productKey().value());
    po.setSourceVersionIdentity(evidence.sourceVersion().identity());
    po.setSourceDisplayVersion(evidence.sourceVersion().displayVersion());
    po.setConsumerType(evidence.consumerRef().consumerType().name());
    po.setSourceDomain(evidence.consumerRef().sourceDomain());
    po.setSourceIdentity(evidence.consumerRef().sourceIdentity());
    po.setDisplayHint(evidence.consumerRef().displayHint());
    po.setObservedAt(evidence.observedAt());
    po.setConsumptionMode(evidence.consumptionMode().name());
    po.setOutcome(evidence.outcome().name());
    po.setProvider(evidence.provider());
    po.setProviderEvidenceRef(evidence.providerEvidenceRef());
    po.setDeduplicationId(evidence.deduplicationId());
    po.setNormalizedAt(evidence.normalizedAt());
    return po;
  }
}
