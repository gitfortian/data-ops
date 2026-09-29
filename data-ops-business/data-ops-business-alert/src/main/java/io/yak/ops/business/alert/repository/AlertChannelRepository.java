package io.yak.ops.business.alert.repository;

import io.yak.ops.business.alert.domain.AlertChannelDefinition;
import io.yak.ops.common.enums.alert.AlertChannelStatus;
import java.util.List;
import java.util.Optional;

/** 告警渠道领域仓储。 */
public interface AlertChannelRepository {
  Optional<AlertChannelDefinition> findById(long id);

  Optional<AlertChannelDefinition> findByChannelType(String channelType);

  List<AlertChannelDefinition> findAll();

  boolean insert(AlertChannelDefinition definition);

  boolean update(AlertChannelDefinition definition);

  boolean updateEnabled(String channelType, boolean enabled);

  boolean updateConnStatus(String channelType, AlertChannelStatus status);
}
