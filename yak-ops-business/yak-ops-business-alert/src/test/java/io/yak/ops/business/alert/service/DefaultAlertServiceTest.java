package io.yak.ops.business.alert.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.alert.domain.AlertChannelDefinition;
import io.yak.ops.business.alert.exception.AlertException;
import io.yak.ops.business.alert.repository.AlertChannelRepository;
import io.yak.ops.business.alert.service.impl.DefaultAlertService;
import io.yak.ops.common.bean.dto.alert.AlertChannelSaveDTO;
import io.yak.ops.common.bean.dto.alert.AlertNotifyDTO;
import io.yak.ops.common.bean.dto.alert.AlertSendDTO;
import io.yak.ops.common.bean.vo.alert.AlertChannelVO;
import io.yak.ops.common.enums.alert.AlertChannelStatus;
import io.yak.ops.core.plugin.alert.AlertPluginRegistry;
import io.yak.ops.plugin.alert.api.AlertLevel;
import io.yak.ops.plugin.alert.api.AlertMessage;
import io.yak.ops.plugin.alert.api.AlertPlugin;
import io.yak.ops.plugin.alert.api.AlertPluginDescriptor;
import io.yak.ops.plugin.alert.api.AlertResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DefaultAlertService 单元测试。 */
class DefaultAlertServiceTest {

  private DefaultAlertService alertService;
  private StubAlertChannelRepository channelRepository;
  private static final String DINGTALK = "DINGTALK";
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    AlertPlugin stubPlugin = new StubAlertPlugin(DINGTALK, "钉钉告警", "测试钉钉插件", "1.0.0");
    AlertPluginRegistry registry = AlertPluginRegistry.from(List.of(stubPlugin));
    channelRepository = new StubAlertChannelRepository();
    alertService = new DefaultAlertService(registry, channelRepository, objectMapper);
  }

  @Test
  void send_success() {
    AlertSendDTO dto = new AlertSendDTO();
    dto.setChannelType(DINGTALK);
    dto.setTitle("测试告警");
    dto.setContent("告警内容");
    dto.setLevel("WARN");
    dto.setConfigJson("{}");

    AlertResult result = alertService.send(dto);
    assertTrue(result.success());
  }

  @Test
  void send_nullDto_returnsFail() {
    AlertResult result = alertService.send(null);
    assertFalse(result.success());
  }

  @Test
  void send_blankChannelType_throwsAlertException() {
    AlertSendDTO dto = new AlertSendDTO();
    dto.setChannelType("");
    dto.setTitle("测试");
    dto.setContent("内容");

    assertThrows(AlertException.class, () -> alertService.send(dto));
  }

  @Test
  void send_unknownChannelType_throwsAlertException() {
    AlertSendDTO dto = new AlertSendDTO();
    dto.setChannelType("UNKNOWN");
    dto.setTitle("测试");
    dto.setContent("内容");

    assertThrows(AlertException.class, () -> alertService.send(dto));
  }

  @Test
  void testConnection_success() {
    boolean result = alertService.testConnection(DINGTALK, "{}");
    assertTrue(result);
  }

  @Test
  void testConnection_unknownChannel_throwsAlertException() {
    assertThrows(AlertException.class, () -> alertService.testConnection("UNKNOWN", "{}"));
  }

  @Test
  void listChannels_returnsRegisteredPlugins() {
    List<AlertChannelVO> channels = alertService.listChannels();
    assertEquals(1, channels.size());
    AlertChannelVO vo = channels.get(0);
    assertEquals(DINGTALK, vo.getType());
    assertEquals("钉钉告警", vo.getName());
    assertEquals("1.0.0", vo.getVersion());
    assertFalse(vo.getEnabled()); // 未配置时默认 disabled
    assertEquals("UNKNOWN", vo.getConnStatus());
    assertNull(vo.getId());
    assertNull(vo.getConfigJson());
  }

  @Test
  void listChannels_exposesStableIdButNotSensitiveConfig() {
    AlertChannelSaveDTO dto = new AlertChannelSaveDTO();
    dto.setChannelType(DINGTALK);
    dto.setConfigJson("{\"webhookUrl\":\"https://example.com\",\"secret\":\"SEC123\"}");
    dto.setEnabled(true);
    assertTrue(alertService.saveChannel(dto));

    AlertChannelVO listItem = alertService.listChannels().get(0);
    assertEquals(41L, listItem.getId());
    assertTrue(listItem.getEnabled());
    assertNull(listItem.getConfigJson());

    AlertChannelVO detail = alertService.getChannel(DINGTALK);
    assertEquals(41L, detail.getId());
    assertEquals(dto.getConfigJson(), detail.getConfigJson());
  }

  @Test
  void saveChannel_insertsNew() {
    AlertChannelSaveDTO dto = new AlertChannelSaveDTO();
    dto.setChannelType(DINGTALK);
    dto.setConfigJson("{\"webhookUrl\":\"https://example.com\"}");
    dto.setEnabled(true);

    assertTrue(alertService.saveChannel(dto));
    AlertChannelVO vo = alertService.getChannel(DINGTALK);
    assertTrue(vo.getEnabled());
    assertEquals("{\"webhookUrl\":\"https://example.com\"}", vo.getConfigJson());
  }

  @Test
  void saveChannel_updatesExisting() {
    AlertChannelSaveDTO dto = new AlertChannelSaveDTO();
    dto.setChannelType(DINGTALK);
    dto.setConfigJson("{\"webhookUrl\":\"https://first.com\"}");
    dto.setEnabled(true);
    alertService.saveChannel(dto);

    AlertChannelSaveDTO update = new AlertChannelSaveDTO();
    update.setChannelType(DINGTALK);
    update.setConfigJson("{\"webhookUrl\":\"https://second.com\"}");
    assertTrue(alertService.saveChannel(update));

    AlertChannelVO vo = alertService.getChannel(DINGTALK);
    assertEquals("{\"webhookUrl\":\"https://second.com\"}", vo.getConfigJson());
  }

  @Test
  void toggleEnabled_success() {
    AlertChannelSaveDTO dto = new AlertChannelSaveDTO();
    dto.setChannelType(DINGTALK);
    dto.setConfigJson("{}");
    dto.setEnabled(true);
    alertService.saveChannel(dto);

    assertTrue(alertService.toggleEnabled(DINGTALK, false));
    AlertChannelVO vo = alertService.getChannel(DINGTALK);
    assertFalse(vo.getEnabled());
  }

  @Test
  void toggleEnabled_notSaved_autoCreates() {
    assertTrue(alertService.toggleEnabled(DINGTALK, true));
    AlertChannelVO vo = alertService.getChannel(DINGTALK);
    assertTrue(vo.getEnabled());
  }

  // ---- notify 方法测试 ----

  @Test
  void notify_success() {
    // 先保存渠道配置并启用
    AlertChannelSaveDTO saveDto = new AlertChannelSaveDTO();
    saveDto.setChannelType(DINGTALK);
    saveDto.setConfigJson("{\"webhookUrl\":\"https://example.com\",\"secret\":\"SEC123\"}");
    saveDto.setEnabled(true);
    alertService.saveChannel(saveDto);

    AlertNotifyDTO dto = new AlertNotifyDTO();
    dto.setChannelType(DINGTALK);
    dto.setTitle("任务失败告警");
    dto.setContent("任务 T-001 执行失败");
    dto.setLevel("ERROR");

    AlertResult result = alertService.notify(dto);
    assertTrue(result.success());
  }

  @Test
  void notify_withParamsOverride() {
    // 先保存渠道配置并启用
    AlertChannelSaveDTO saveDto = new AlertChannelSaveDTO();
    saveDto.setChannelType(DINGTALK);
    saveDto.setConfigJson("{\"webhookUrl\":\"https://example.com\",\"msgType\":\"text\"}");
    saveDto.setEnabled(true);
    alertService.saveChannel(saveDto);

    AlertNotifyDTO dto = new AlertNotifyDTO();
    dto.setChannelType(DINGTALK);
    dto.setTitle("告警");
    dto.setContent("内容");
    dto.setParamsJson("{\"msgType\":\"markdown\",\"isAtAll\":true}");

    AlertResult result = alertService.notify(dto);
    assertTrue(result.success());
  }

  @Test
  void notify_nullDto_returnsFail() {
    AlertResult result = alertService.notify(null);
    assertFalse(result.success());
  }

  @Test
  void notify_channelNotConfigured_returnsFail() {
    AlertNotifyDTO dto = new AlertNotifyDTO();
    dto.setChannelType(DINGTALK);
    dto.setTitle("告警");
    dto.setContent("内容");

    AlertResult result = alertService.notify(dto);
    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("未启用"));
  }

  @Test
  void notify_channelDisabled_returnsFail() {
    AlertChannelSaveDTO saveDto = new AlertChannelSaveDTO();
    saveDto.setChannelType(DINGTALK);
    saveDto.setConfigJson("{}");
    saveDto.setEnabled(false);
    alertService.saveChannel(saveDto);

    AlertNotifyDTO dto = new AlertNotifyDTO();
    dto.setChannelType(DINGTALK);
    dto.setTitle("告警");
    dto.setContent("内容");

    AlertResult result = alertService.notify(dto);
    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("未启用"));
  }

  @Test
  void notify_unknownChannelType_throwsAlertException() {
    AlertNotifyDTO dto = new AlertNotifyDTO();
    dto.setChannelType("UNKNOWN");
    dto.setTitle("告警");
    dto.setContent("内容");

    assertThrows(AlertException.class, () -> alertService.notify(dto));
  }

  // ---- mergeConfig 方法测试 ----

  @Test
  void mergeConfig_overrideFields() throws Exception {
    String base = "{\"webhookUrl\":\"https://example.com\",\"secret\":\"SEC123\",\"msgType\":\"text\"}";
    String override = "{\"msgType\":\"markdown\",\"isAtAll\":true}";

    String merged = alertService.mergeConfig(base, override);
    JsonNode node = objectMapper.readTree(merged);

    assertEquals("https://example.com", node.get("webhookUrl").asText());
    assertEquals("SEC123", node.get("secret").asText());
    assertEquals("markdown", node.get("msgType").asText()); // 被覆盖
    assertTrue(node.get("isAtAll").asBoolean()); // 新增字段
  }

  @Test
  void mergeConfig_noOverride_returnsBase() throws Exception {
    String base = "{\"webhookUrl\":\"https://example.com\",\"secret\":\"SEC123\"}";

    String merged = alertService.mergeConfig(base, null);
    JsonNode node = objectMapper.readTree(merged);

    assertEquals("https://example.com", node.get("webhookUrl").asText());
    assertEquals("SEC123", node.get("secret").asText());
  }

  @Test
  void mergeConfig_emptyOverride_returnsBase() throws Exception {
    String base = "{\"webhookUrl\":\"https://example.com\"}";

    String merged = alertService.mergeConfig(base, "");
    JsonNode node = objectMapper.readTree(merged);
    assertEquals("https://example.com", node.get("webhookUrl").asText());
  }

  @Test
  void mergeConfig_invalidOverride_fallbackToBase() {
    String base = "{\"webhookUrl\":\"https://example.com\"}";

    String merged = alertService.mergeConfig(base, "not-valid-json");
    assertEquals(base, merged);
  }

  /** 测试用 Stub 插件。 */
  private static class StubAlertPlugin implements AlertPlugin {

    private final AlertPluginDescriptor descriptor;

    StubAlertPlugin(String type, String name, String description, String version) {
      this.descriptor = new AlertPluginDescriptor(type, name, description, version);
    }

    @Override
    public AlertPluginDescriptor descriptor() {
      return descriptor;
    }

    @Override
    public AlertResult send(AlertMessage message) {
      return AlertResult.ok();
    }

    @Override
    public boolean testConnection(String configJson) {
      return true;
    }
  }

  /** 测试用 Stub 仓储。 */
  private static class StubAlertChannelRepository implements AlertChannelRepository {

    private AlertChannelDefinition stored;

    @Override
    public Optional<AlertChannelDefinition> findById(long id) {
      return stored != null && stored.getId() != null && stored.getId() == id
          ? Optional.of(clone(stored))
          : Optional.empty();
    }

    @Override
    public Optional<AlertChannelDefinition> findByChannelType(String channelType) {
      return stored != null && stored.getChannelType().equals(channelType)
          ? Optional.of(clone(stored))
          : Optional.empty();
    }

    @Override
    public List<AlertChannelDefinition> findAll() {
      return stored != null ? List.of(clone(stored)) : List.of();
    }

    @Override
    public boolean insert(AlertChannelDefinition definition) {
      stored = clone(definition);
      if (stored.getId() == null) {
        stored.setId(41L);
      }
      return true;
    }

    @Override
    public boolean update(AlertChannelDefinition definition) {
      stored = clone(definition);
      return true;
    }

    @Override
    public boolean updateEnabled(String channelType, boolean enabled) {
      if (stored != null && stored.getChannelType().equals(channelType)) {
        stored.setEnabled(enabled);
        return true;
      }
      return false;
    }

    @Override
    public boolean updateConnStatus(String channelType, AlertChannelStatus status) {
      if (stored != null && stored.getChannelType().equals(channelType)) {
        stored.setConnStatus(status);
        return true;
      }
      return false;
    }

    private static AlertChannelDefinition clone(AlertChannelDefinition def) {
      AlertChannelDefinition copy = new AlertChannelDefinition();
      copy.setId(def.getId());
      copy.setChannelType(def.getChannelType());
      copy.setConfigJson(def.getConfigJson());
      copy.setEnabled(def.getEnabled());
      copy.setConnStatus(def.getConnStatus());
      copy.setCreateTime(def.getCreateTime());
      copy.setUpdateTime(def.getUpdateTime());
      return copy;
    }
  }
}
