package io.yak.ops.business.alert.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.alert.domain.AlertChannelDefinition;
import io.yak.ops.business.alert.exception.AlertException;
import io.yak.ops.business.alert.repository.AlertChannelRepository;
import io.yak.ops.business.alert.service.AlertService;
import io.yak.ops.common.bean.dto.alert.AlertChannelSaveDTO;
import io.yak.ops.common.bean.dto.alert.AlertNotifyDTO;
import io.yak.ops.common.bean.dto.alert.AlertSendDTO;
import io.yak.ops.common.bean.vo.alert.AlertChannelVO;
import io.yak.ops.common.enums.alert.AlertChannelStatus;
import io.yak.ops.common.enums.alert.AlertErrorCode;
import io.yak.ops.core.plugin.alert.AlertPluginRegistry;
import io.yak.ops.plugin.alert.api.AlertLevel;
import io.yak.ops.plugin.alert.api.AlertMessage;
import io.yak.ops.plugin.alert.api.AlertPlugin;
import io.yak.ops.plugin.alert.api.AlertResult;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 告警服务默认实现，通过 AlertPluginRegistry 路由到具体的告警插件。 */
@Slf4j
@Service
public class DefaultAlertService implements AlertService {

  private final AlertPluginRegistry registry;
  private final AlertChannelRepository repository;
  private final ObjectMapper objectMapper;

  @Autowired
  public DefaultAlertService(AlertPluginRegistry registry, AlertChannelRepository repository) {
    this(registry, repository, new ObjectMapper());
  }

  /** Constructor for testing with a custom ObjectMapper. */
  public DefaultAlertService(AlertPluginRegistry registry, AlertChannelRepository repository, ObjectMapper objectMapper) {
    this.registry = registry;
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @PostConstruct
  void logRegisteredPlugins() {
    registry.descriptors().forEach(
        desc -> log.info("Registered alert plugin: type={}, name={}", desc.type(), desc.name()));
  }

  @Override
  public AlertResult send(AlertSendDTO dto) {
    if (dto == null) {
      return AlertResult.fail("Alert request must not be null");
    }

    AlertPlugin plugin = resolvePlugin(dto.getChannelType());

    AlertLevel level = parseLevel(dto.getLevel());
    AlertMessage message =
        AlertMessage.of(dto.getTitle(), dto.getContent(), level, dto.getConfigJson());

    AlertResult result = plugin.send(message);
    if (result.success()) {
      log.info("Alert sent successfully: channel={}, title={}", dto.getChannelType(), dto.getTitle());
    } else {
      log.warn(
          "Alert send failed: channel={}, title={}, error={}",
          dto.getChannelType(),
          dto.getTitle(),
          result.errorMessage());
    }
    return result;
  }

  @Override
  public boolean testConnection(String channelType, String configJson) {
    AlertPlugin plugin = resolvePlugin(channelType);
    // configJson 为空时使用数据库已保存的配置
    String effectiveConfig = configJson;
    if (effectiveConfig == null || effectiveConfig.isBlank()) {
      AlertChannelDefinition persisted = repository.findByChannelType(channelType).orElse(null);
      if (persisted != null && persisted.getConfigJson() != null) {
        effectiveConfig = persisted.getConfigJson();
      } else {
        effectiveConfig = "{}";
      }
    }
    try {
      boolean success = plugin.testConnection(effectiveConfig);
      // 更新持久化连通状态
      if (repository.findByChannelType(channelType).isPresent()) {
        repository.updateConnStatus(
            channelType, success ? AlertChannelStatus.CONNECTED : AlertChannelStatus.DISCONNECTED);
      }
      return success;
    } catch (Exception e) {
      log.warn("Alert connection test failed: channel={}, error={}", channelType, e.getMessage());
      if (repository.findByChannelType(channelType).isPresent()) {
        repository.updateConnStatus(channelType, AlertChannelStatus.DISCONNECTED);
      }
      return false;
    }
  }

  @Override
  public List<AlertChannelVO> listChannels() {
    // SPI 插件描述 → 基础 VO 列表
    List<AlertChannelVO> channels = registry.descriptors().stream()
        .map(AlertChannelVO::from)
        .toList();

    // 数据库持久化配置 → 合并安全的列表字段；敏感 configJson 仅由详情接口返回。
    Map<String, AlertChannelDefinition> persisted = repository.findAll().stream()
        .collect(Collectors.toMap(AlertChannelDefinition::getChannelType, Function.identity()));

    for (AlertChannelVO vo : channels) {
      AlertChannelDefinition def = persisted.get(vo.getType());
      if (def != null) {
        vo.setId(def.getId());
        vo.setEnabled(def.getEnabled());
        vo.setConnStatus(def.getConnStatus() != null ? def.getConnStatus().name() : "UNKNOWN");
      }
    }
    return channels;
  }

  @Override
  public AlertChannelVO getChannel(String channelType) {
    // 确保 SPI 插件存在
    resolvePlugin(channelType);

    AlertChannelVO vo = registry.descriptors().stream()
        .filter(d -> d.type().equals(channelType))
        .map(AlertChannelVO::from)
        .findFirst()
        .orElseThrow(() -> new AlertException(
            AlertErrorCode.CHANNEL_NOT_FOUND, "未找到告警渠道：" + channelType));

    // 合并持久化配置
    AlertChannelDefinition def = repository.findByChannelType(channelType).orElse(null);
    if (def != null) {
      vo.setId(def.getId());
      vo.setEnabled(def.getEnabled());
      vo.setConnStatus(def.getConnStatus() != null ? def.getConnStatus().name() : "UNKNOWN");
      vo.setConfigJson(def.getConfigJson());
    }
    return vo;
  }

  @Override
  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      rollbackFor = Exception.class)
  public boolean saveChannel(AlertChannelSaveDTO dto) {
    if (dto == null || dto.getChannelType() == null || dto.getChannelType().isBlank()) {
      throw new AlertException(AlertErrorCode.INVALID_CHANNEL_TYPE, "渠道类型不能为空");
    }
    if (dto.getConfigJson() == null || dto.getConfigJson().isBlank()) {
      throw new AlertException(AlertErrorCode.INVALID_CONFIG, "渠道配置不能为空");
    }

    // 验证 SPI 插件存在
    resolvePlugin(dto.getChannelType());

    AlertChannelDefinition existing = repository.findByChannelType(dto.getChannelType()).orElse(null);
    if (existing != null) {
      existing.setConfigJson(dto.getConfigJson());
      if (dto.getEnabled() != null) {
        existing.setEnabled(dto.getEnabled());
      }
      existing.setConnStatus(AlertChannelStatus.UNKNOWN);
      return repository.update(existing);
    } else {
      AlertChannelDefinition definition = new AlertChannelDefinition();
      definition.setChannelType(dto.getChannelType());
      definition.setConfigJson(dto.getConfigJson());
      definition.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
      definition.setConnStatus(AlertChannelStatus.UNKNOWN);
      return repository.insert(definition);
    }
  }

  @Override
  @Transactional(
      transactionManager = "yakBusinessTransactionManager",
      rollbackFor = Exception.class)
  public boolean toggleEnabled(String channelType, boolean enabled) {
    if (channelType == null || channelType.isBlank()) {
      throw new AlertException(AlertErrorCode.INVALID_CHANNEL_TYPE, "渠道类型不能为空");
    }
    // 验证 SPI 插件存在
    resolvePlugin(channelType);

    if (!repository.findByChannelType(channelType).isPresent()) {
      // 数据库无记录，自动创建
      AlertChannelDefinition definition = new AlertChannelDefinition();
      definition.setChannelType(channelType);
      definition.setConfigJson("{}");
      definition.setEnabled(enabled);
      definition.setConnStatus(AlertChannelStatus.UNKNOWN);
      return repository.insert(definition);
    }
    return repository.updateEnabled(channelType, enabled);
  }

  private AlertPlugin resolvePlugin(String channelType) {
    if (channelType == null || channelType.isBlank()) {
      throw new AlertException(
          AlertErrorCode.INVALID_CHANNEL_TYPE, "告警渠道类型不能为空");
    }
    return registry
        .find(channelType)
        .orElseThrow(
            () ->
                new AlertException(
                    AlertErrorCode.PLUGIN_NOT_FOUND,
                    "未找到告警插件：" + channelType));
  }

  @Override
  public AlertResult notify(AlertNotifyDTO dto) {
    if (dto == null) {
      return AlertResult.fail("Alert notify request must not be null");
    }

    AlertPlugin plugin = resolvePlugin(dto.getChannelType());

    // 检查渠道是否已启用
    AlertChannelDefinition persisted = repository.findByChannelType(dto.getChannelType()).orElse(null);
    if (persisted == null || !Boolean.TRUE.equals(persisted.getEnabled())) {
      return AlertResult.fail("告警渠道未启用：" + dto.getChannelType());
    }

    // 合并持久化配置与传入的非地址参数
    String mergedConfig = mergeConfig(persisted.getConfigJson(), dto.getParamsJson());

    AlertLevel level = parseLevel(dto.getLevel());
    AlertMessage message = AlertMessage.of(dto.getTitle(), dto.getContent(), level, mergedConfig);

    AlertResult result = plugin.send(message);
    if (result.success()) {
      log.info("Alert notify sent: channel={}, title={}", dto.getChannelType(), dto.getTitle());
    } else {
      log.warn(
          "Alert notify failed: channel={}, title={}, error={}",
          dto.getChannelType(),
          dto.getTitle(),
          result.errorMessage());
    }
    return result;
  }

  /**
   * 合并渠道配置：以持久化配置为基础，用传入参数覆盖同名字段。
   *
   * <p>传入参数优先级更高，用于覆盖非地址类参数（如@人设置、消息类型等）。
   */
  public String mergeConfig(String baseConfigJson, String overrideParamsJson) {
    try {
      ObjectNode baseNode = parseToObjectNode(baseConfigJson);
      if (overrideParamsJson == null || overrideParamsJson.isBlank()) {
        return objectMapper.writeValueAsString(baseNode);
      }
      ObjectNode overrideNode = parseToObjectNode(overrideParamsJson);
      // 传入参数覆盖基础配置的同名字段
      baseNode.setAll(overrideNode);
      return objectMapper.writeValueAsString(baseNode);
    } catch (Exception e) {
      log.warn("Failed to merge alert config: base={}, override={}, error={}",
          baseConfigJson, overrideParamsJson, e.getMessage());
      // 合并失败时回退到基础配置
      return baseConfigJson;
    }
  }

  private ObjectNode parseToObjectNode(String json) throws Exception {
    if (json == null || json.isBlank()) {
      return objectMapper.createObjectNode();
    }
    JsonNode node = objectMapper.readTree(json);
    if (node.isObject()) {
      return (ObjectNode) node;
    }
    return objectMapper.createObjectNode();
  }

  private AlertLevel parseLevel(String level) {
    if (level == null || level.isBlank()) {
      return AlertLevel.INFO;
    }
    try {
      return AlertLevel.valueOf(level.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return AlertLevel.INFO;
    }
  }
}
