package io.yak.ops.business.alert.repository;

import io.yak.ops.business.alert.dao.AlertChannelDao;
import io.yak.ops.business.alert.domain.AlertChannelDefinition;
import io.yak.ops.common.bean.po.alert.AlertChannelPO;
import io.yak.ops.common.enums.alert.AlertChannelStatus;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

/** MyBatis 持久化模型与告警渠道领域模型之间的适配器。 */
@Repository
@RequiredArgsConstructor
public class AlertChannelRepositoryAdapter implements AlertChannelRepository {

  private final AlertChannelDao dao;

  @Override
  public Optional<AlertChannelDefinition> findById(long id) {
    return Optional.ofNullable(toDomain(dao.selectById(id)));
  }

  @Override
  public Optional<AlertChannelDefinition> findByChannelType(String channelType) {
    return Optional.ofNullable(toDomain(dao.selectByChannelType(channelType)));
  }

  @Override
  public List<AlertChannelDefinition> findAll() {
    return dao.selectAll().stream().map(this::toDomain).toList();
  }

  @Override
  public boolean insert(AlertChannelDefinition definition) {
    return dao.insert(toPO(definition)) > 0;
  }

  @Override
  public boolean update(AlertChannelDefinition definition) {
    return dao.update(toPO(definition)) > 0;
  }

  @Override
  public boolean updateEnabled(String channelType, boolean enabled) {
    return dao.updateEnabled(channelType, enabled);
  }

  @Override
  public boolean updateConnStatus(String channelType, AlertChannelStatus status) {
    return dao.updateConnStatus(channelType, status);
  }

  private AlertChannelDefinition toDomain(AlertChannelPO po) {
    if (po == null) return null;
    AlertChannelDefinition definition = new AlertChannelDefinition();
    BeanUtils.copyProperties(po, definition);
    return definition;
  }

  private AlertChannelPO toPO(AlertChannelDefinition definition) {
    AlertChannelPO po = new AlertChannelPO();
    BeanUtils.copyProperties(definition, po);
    return po;
  }
}
