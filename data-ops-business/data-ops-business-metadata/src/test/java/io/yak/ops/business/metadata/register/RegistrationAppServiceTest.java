package io.yak.ops.business.metadata.register;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metadata.api.RegisterCommand;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.register.RegisterRetryStore.EnqueueResult;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 门面的唯一使命（ticket 130 必须 1）：<b>源域的保存永远成功</b>。
 * 每个测试都从两条路径之一证明这一点——异常被吞去排队，或吞不下时只留日志。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationAppServiceTest {

  private static final long PROJECT = 7L;

  private final MetadataRegistrationService registrationService =
      mock(MetadataRegistrationService.class);
  private final RegisterRetryStore retryStore = mock(RegisterRetryStore.class);
  private final CurrentProject currentProject = mock(CurrentProject.class);
  private final RegistrationAppService appService =
      new RegistrationAppService(registrationService, retryStore, currentProject);

  @BeforeEach
  void useContext() {
    when(currentProject.current()).thenReturn(Optional.of(new ProjectContext(PROJECT, null)));
    when(retryStore.enqueueRegister(anyLong(), any(), any())).thenReturn(EnqueueResult.QUEUED);
    when(retryStore.enqueueUnregister(anyLong(), any(), any(), any(), any()))
        .thenReturn(EnqueueResult.QUEUED);
  }

  @Test
  void aFailureInsideRegistrationBecomesAnOutboxRowNotAnException() {
    doThrow(new MetadataException(MetadataErrorCode.PERSISTENCE_CONFLICT, "库锁等待超时"))
        .when(registrationService)
        .register(eq(PROJECT), any());
    RegisterCommand command = command();

    assertThatCode(() -> appService.register(command)).doesNotThrowAnyException();

    // 整份命令入队（重放不回查源域）；projectId 是服务端解析的那一个，不是命令自带的。
    verify(retryStore).enqueueRegister(eq(PROJECT), eq(command), any(MetadataException.class));
  }

  @Test
  void evenAFailedEnqueueMustNotReachTheSourceDomain() {
    doThrow(new MetadataException(MetadataErrorCode.PERSISTENCE_CONFLICT)).when(registrationService)
        .register(eq(PROJECT), any());
    when(retryStore.enqueueRegister(anyLong(), any(), any()))
        .thenThrow(new IllegalStateException("outbox 库也不可达"));

    // 双失败只剩日志：终兜底是对账（135），不是把异常还给业务保存。
    assertThatCode(() -> appService.register(command())).doesNotThrowAnyException();
    verify(retryStore).enqueueRegister(eq(PROJECT), any(), any());
  }

  @Test
  void successTouchesTheQueueNotAtAll() {
    appService.register(command());

    verify(registrationService).register(eq(PROJECT), any());
    verify(retryStore, never()).enqueueRegister(anyLong(), any(), any());
  }

  @Test
  void withoutServerTrustedContextNothingIsWrittenAndNothingIsQueued() {
    // 归属列 NOT NULL：没有 CurrentProject 就连 outbox 行都填不出来——丢日志交对账，绝不猜一个项目。
    when(currentProject.current()).thenReturn(Optional.empty());

    assertThatCode(() -> appService.register(command())).doesNotThrowAnyException();
    verifyNoInteractions(registrationService, retryStore);
  }

  @Test
  void bulkUnregisterTurnsIntoPerKeyRevokesSoEachFailureStaysReplayable() {
    when(registrationService.registeredKeys(PROJECT, "dataModel", "42"))
        .thenReturn(List.of("modeling:model:1", "modeling:model:2"));
    doThrow(new MetadataException(MetadataErrorCode.PERSISTENCE_CONFLICT))
        .when(registrationService)
        .unregister(PROJECT, "dataModel", "42", "modeling:model:2");

    assertThatCode(() -> appService.unregister("dataModel", "42")).doesNotThrowAnyException();

    verify(registrationService).unregister(PROJECT, "dataModel", "42", "modeling:model:1");
    // 只有失败的那一条排队——键在 outbox 行上，重放才有的放矢。
    verify(retryStore).enqueueUnregister(eq(PROJECT), eq("dataModel"), eq("42"), eq("modeling:model:2"), any());
  }

  @Test
  void aBulkRevokeThatCannotEvenReadItsKeyListFallsToTheReconcileChannelAlone() {
    when(registrationService.registeredKeys(anyLong(), anyString(), anyString()))
        .thenThrow(new MetadataException(MetadataErrorCode.PERSISTENCE_CONFLICT));

    // asset_key NOT NULL：连"撤销谁"都不知道，队列行无从落下——这是唯一允许只留日志的撤销形状。
    assertThatCode(() -> appService.unregister("dataModel", "42")).doesNotThrowAnyException();
    verifyNoInteractions(retryStore);
  }

  @Test
  void aBlankKeyOnTheThreeArgOverloadDegradesToBulkSemantics() {
    when(registrationService.registeredKeys(PROJECT, "dataModel", "42")).thenReturn(List.of());

    assertThatCode(() -> appService.unregister("dataModel", "42", " ")).doesNotThrowAnyException();

    verify(registrationService).registeredKeys(PROJECT, "dataModel", "42");
    verify(registrationService, never()).unregister(anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  void singleRevokeFailuresGoToTheQueueWithTheirKey() {
    doThrow(new MetadataException(MetadataErrorCode.PERSISTENCE_CONFLICT))
        .when(registrationService)
        .unregister(PROJECT, "dataModel", "42", "modeling:model:9");

    assertThatCode(() -> appService.unregister("dataModel", "42", "modeling:model:9"))
        .doesNotThrowAnyException();

    verify(retryStore)
        .enqueueUnregister(eq(PROJECT), eq("dataModel"), eq("42"), eq("modeling:model:9"), any());
    verify(retryStore, never()).enqueueRegister(anyLong(), any(), any());
  }

  private static RegisterCommand command() {
    RegisterCommand command = new RegisterCommand();
    command.setTypeName("dataModel");
    command.setSourceId("42");
    command.setAssetKey("modeling:model:9");
    command.setSourceHash("sha-demo");
    command.setSourceUpdatedAt(LocalDateTime.now());
    return command;
  }
}
