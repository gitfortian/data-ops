package io.yak.ops.business.alert.dao;

import io.yak.ops.common.bean.po.alert.AlertChannelPO;
import io.yak.ops.common.enums.alert.AlertChannelStatus;
import java.util.List;

/** 告警渠道数据访问接口，只暴露持久化模型。 */
public interface AlertChannelDao {

  int insert(AlertChannelPO po);

  int update(AlertChannelPO po);

  AlertChannelPO selectById(long id);

  AlertChannelPO selectByChannelType(String channelType);

  List<AlertChannelPO> selectAll();

  boolean updateEnabled(String channelType, boolean enabled);

  boolean updateConnStatus(String channelType, AlertChannelStatus status);
}
