package io.yak.ops.business.alert.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.alert.dao.AlertChannelDao;
import io.yak.ops.business.alert.dao.mapper.AlertChannelMapper;
import io.yak.ops.common.bean.po.alert.AlertChannelPO;
import io.yak.ops.common.enums.alert.AlertChannelStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 基于 MyBatis-Plus 的告警渠道数据访问实现。 */
@Repository
@RequiredArgsConstructor
public class AlertChannelDaoImpl implements AlertChannelDao {

  private final AlertChannelMapper alertChannelMapper;

  @Override
  public int insert(AlertChannelPO po) {
    return alertChannelMapper.insert(po);
  }

  @Override
  public int update(AlertChannelPO po) {
    return alertChannelMapper.updateById(po);
  }

  @Override
  public AlertChannelPO selectById(long id) {
    return alertChannelMapper.selectById(id);
  }

  @Override
  public AlertChannelPO selectByChannelType(String channelType) {
    return alertChannelMapper.selectOne(
        Wrappers.<AlertChannelPO>lambdaQuery()
            .eq(AlertChannelPO::getChannelType, channelType));
  }

  @Override
  public List<AlertChannelPO> selectAll() {
    return alertChannelMapper.selectList(
        Wrappers.<AlertChannelPO>lambdaQuery()
            .orderByAsc(AlertChannelPO::getChannelType));
  }

  @Override
  public boolean updateEnabled(String channelType, boolean enabled) {
    return alertChannelMapper.update(
            null,
            Wrappers.<AlertChannelPO>lambdaUpdate()
                .set(AlertChannelPO::getEnabled, enabled)
                .eq(AlertChannelPO::getChannelType, channelType))
        > 0;
  }

  @Override
  public boolean updateConnStatus(String channelType, AlertChannelStatus status) {
    return alertChannelMapper.update(
            null,
            Wrappers.<AlertChannelPO>lambdaUpdate()
                .set(AlertChannelPO::getConnStatus, status)
                .eq(AlertChannelPO::getChannelType, channelType))
        > 0;
  }
}
