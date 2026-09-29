package io.yak.ops.business.metadata.register;

import io.yak.ops.business.metadata.api.MetadataRegistrationApi;
import io.yak.ops.business.metadata.api.RegisterCommand;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link MetadataRegistrationApi} 的实现：<b>本类永不向源域抛异常</b>（ticket 130 必须 1）。
 *
 * <p>失败处理只有一个方向——落 outbox，由 worker 重放；连排队都失败（库不可达）只留 ERROR 日志，
 * 终兜底是 ticket 135 的对账副通道。这里刻意不向上抛："登记失败拖垮业务保存"是目录侧最难自证
 * 清白的事故形态（plan §9 T20）。
 *
 * <p>第二件事是归属取值：{@code projectId} 只从这里的服务端上下文解析，DTO 里没有这个字段（§0.9）。
 * 上下文缺失说明源域在请求作用域之外调了接口——命令连归属都定不了，outbox 行（project_id NOT NULL）
 * 同样进不去，这里留日志后丢弃，这是对账通道该管的形状。
 */
@Slf4j
@Component
public class RegistrationAppService implements MetadataRegistrationApi {

  private final MetadataRegistrationService registrationService;
  private final RegisterRetryStore retryStore;
  private final CurrentProject currentProject;

  public RegistrationAppService(
      MetadataRegistrationService registrationService,
      RegisterRetryStore retryStore,
      CurrentProject currentProject) {
    this.registrationService = registrationService;
    this.retryStore = retryStore;
    this.currentProject = currentProject;
  }

  @Override
  public void register(RegisterCommand command) {
    Long projectId = trustedProjectId();
    if (projectId == null) {
      log.error("register 丢弃：缺少项目上下文 key={}", command == null ? null : command.getAssetKey());
      return;
    }
    try {
      registrationService.register(projectId, command);
    } catch (Throwable primary) {
      enqueueRegister(projectId, command, primary);
    }
  }

  @Override
  public void unregister(String typeName, String sourceId) {
    Long projectId = trustedProjectId();
    if (projectId == null) {
      log.error("unregister 丢弃：缺少项目上下文 type={} source={}", typeName, sourceId);
      return;
    }
    // 批量撤销没有"那一次变更"的键，读不回键就排不进队（asset_key NOT NULL）——
    // 所以先按键逐个撤销，让每一条失败都各自可重放；键列表本身读不出来才落日志交对账。
    List<String> keys;
    try {
      keys = registrationService.registeredKeys(projectId, typeName, sourceId);
    } catch (Throwable failure) {
      log.error("unregister 无法读回登记键，交对账兜底 type={} source={}", typeName, sourceId, failure);
      return;
    }
    for (String key : keys) {
      unregister(typeName, sourceId, key);
    }
  }

  @Override
  public void unregister(String typeName, String sourceId, String assetKey) {
    if (assetKey == null || assetKey.isBlank()) {
      unregister(typeName, sourceId);
      return;
    }
    Long projectId = trustedProjectId();
    if (projectId == null) {
      log.error("unregister 丢弃：缺少项目上下文 key={}", assetKey);
      return;
    }
    try {
      registrationService.unregister(projectId, typeName, sourceId, assetKey);
    } catch (Throwable primary) {
      try {
        retryStore.enqueueUnregister(projectId, typeName, sourceId, assetKey, primary);
        log.warn("unregister 失败已排队重放 key={}: {}", assetKey, primary.getMessage());
      } catch (Throwable secondary) {
        log.error("unregister 失败且排队失败，交对账兜底 key={}", assetKey, secondary);
      }
    }
  }

  private void enqueueRegister(Long projectId, RegisterCommand command, Throwable primary) {
    try {
      retryStore.enqueueRegister(projectId, command, primary);
      log.warn(
          "register 失败已排队重放 key={}: {}",
          command == null ? null : command.getAssetKey(),
          primary.getMessage());
    } catch (Throwable secondary) {
      log.error(
          "register 失败且排队失败，交对账兜底 key={}",
          command == null ? null : command.getAssetKey(),
          secondary);
    }
  }

  private Long trustedProjectId() {
    return Optional.ofNullable(currentProject.current().orElse(null))
        .map(ProjectContext::projectId)
        .orElse(null);
  }
}
