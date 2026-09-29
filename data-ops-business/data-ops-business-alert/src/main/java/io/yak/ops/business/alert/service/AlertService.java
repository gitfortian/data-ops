package io.yak.ops.business.alert.service;

import io.yak.ops.common.bean.dto.alert.AlertChannelSaveDTO;
import io.yak.ops.common.bean.dto.alert.AlertNotifyDTO;
import io.yak.ops.common.bean.dto.alert.AlertSendDTO;
import io.yak.ops.common.bean.vo.alert.AlertChannelVO;
import io.yak.ops.plugin.alert.api.AlertResult;
import java.util.List;

/** 告警管理服务。 */
public interface AlertService {

  /**
   * 发送告警消息。
   *
   * @param dto 告警请求参数
   * @return 发送结果
   */
  AlertResult send(AlertSendDTO dto);

  /**
   * 测试告警渠道连通性。
   *
   * @param channelType 渠道类型
   * @param configJson 渠道配置 JSON
   * @return 连通是否成功
   */
  boolean testConnection(String channelType, String configJson);

  /**
   * 列出所有已注册的告警渠道（合并 SPI 插件描述 + 数据库持久化配置）。
   *
   * @return 渠道信息列表
   */
  List<AlertChannelVO> listChannels();

  /**
   * 获取指定渠道的详细配置。
   *
   * @param channelType 渠道类型
   * @return 渠道详细信息（含 configJson），不存在返回 null
   */
  AlertChannelVO getChannel(String channelType);

  /**
   * 保存告警渠道配置（UPSERT）。
   *
   * @param dto 保存请求参数
   * @return 是否保存成功
   */
  boolean saveChannel(AlertChannelSaveDTO dto);

  /**
   * 发送告警通知（供其他模块内部调用）。
   *
   * <p>与 {@link #send(AlertSendDTO)} 不同，本方法自动从数据库读取持久化的渠道配置，
   * 并与 {@code dto.paramsJson} 中的非地址参数合并后发送，调用方无需关心 webhook 地址等敏感配置。
   *
   * @param dto 告警通知请求参数（channelType + paramsJson + 告警信息）
   * @return 发送结果
   */
  AlertResult notify(AlertNotifyDTO dto);

  /**
   * 切换告警渠道启用状态。
   *
   * @param channelType 渠道类型
   * @param enabled 是否启用
   * @return 是否操作成功
   */
  boolean toggleEnabled(String channelType, boolean enabled);
}
