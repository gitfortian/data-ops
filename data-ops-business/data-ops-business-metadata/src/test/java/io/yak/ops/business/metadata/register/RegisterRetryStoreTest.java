package io.yak.ops.business.metadata.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.yak.ops.business.metadata.api.RegisterCommand;
import io.yak.ops.business.metadata.dao.mapper.MdRegisterRetryMapper;
import io.yak.ops.business.metadata.dao.model.MdRegisterRetryPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.RetryOperation;
import io.yak.ops.common.enums.metadata.MetadataEnums.RetryStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

/**
 * outbox 的排队/退避/终态（ticket 130 必须 2，plan §3.2c）。
 * due/claim 的 SQL 谓词形状在 {@code MdRegisterRetryMapper.xml} 里；本测试盯 Java 侧算术：
 * 退避公式、DEAD 上界、"撞键即已排队"。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegisterRetryStoreTest {

  private static final long PROJECT = 7L;

  private final MdRegisterRetryMapper mapper = mock(MdRegisterRetryMapper.class);
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final RegisterRetryStore store = new RegisterRetryStore(mapper, objectMapper);

  @Test
  void aRegisterFailureIsQueuedAsTheWholeCommandWithUsableQueueSemantics() throws Exception {
    RegisterCommand command = command();

    assertThat(store.enqueueRegister(PROJECT, command, new IllegalStateException("db down")))
        .isEqualTo(RegisterRetryStore.EnqueueResult.QUEUED);

    ArgumentCaptor<MdRegisterRetryPO> captor = ArgumentCaptor.forClass(MdRegisterRetryPO.class);
    verify(mapper).insert(captor.capture());
    MdRegisterRetryPO row = captor.getValue();
    assertThat(row.getStatus()).isEqualTo(RetryStatus.PENDING.name());
    assertThat(row.getAttempts()).isZero();
    assertThat(row.getOperation()).isEqualTo(RetryOperation.REGISTER.name());
    assertThat(row.getProjectId()).isEqualTo(PROJECT);
    // uk 的"变更"令牌就在行上，不在 payload 里——去重由数据库兑现。
    assertThat(row.getSourceUpdatedAt()).isEqualTo(command.getSourceUpdatedAt());
    // payload = 整份命令：重放不回查源域，避免"排队时实体又被改了"的时序依赖。
    RegisterCommand replayed = objectMapper.readValue(row.getPayload(), RegisterCommand.class);
    assertThat(replayed).isEqualTo(command);
  }

  @Test
  void aUniqueKeyCollisionMeansAlreadyQueuedWhichIsSuccess() {
    when(mapper.insert(any(MdRegisterRetryPO.class)))
        .thenThrow(new DuplicateKeyException("uk_yak_md_retry_change"));

    // 键在"变更"上：并发两次登记同一次变更，第二次的撞键就是去重在工作，不是失败。
    assertThatCode(() -> store.enqueueRegister(PROJECT, command(), new RuntimeException("x")))
        .doesNotThrowAnyException();
    assertThat(store.enqueueRegister(PROJECT, command(), new RuntimeException("x")))
        .isEqualTo(RegisterRetryStore.EnqueueResult.ALREADY_QUEUED);
  }

  @Test
  void commandsMissingQueueNotNullColumnsRefuseToEnterRatherThanVanish() {
    RegisterCommand noKey = command();
    noKey.setAssetKey(" ");
    assertThatThrownBy(() -> store.enqueueRegister(PROJECT, noKey, new RuntimeException("x")))
        .isInstanceOf(IllegalArgumentException.class);

    RegisterCommand noTimestamp = command();
    noTimestamp.setSourceUpdatedAt(null);
    assertThatThrownBy(() -> store.enqueueRegister(PROJECT, noTimestamp, new RuntimeException("x")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unregisterRowsCarryTheirKeyOnColumnsAndSkipThePayload() {
    store.enqueueUnregister(PROJECT, "dataModel", "42", "modeling:model:9", new RuntimeException("x"));

    ArgumentCaptor<MdRegisterRetryPO> captor = ArgumentCaptor.forClass(MdRegisterRetryPO.class);
    verify(mapper).insert(captor.capture());
    MdRegisterRetryPO row = captor.getValue();
    assertThat(row.getOperation()).isEqualTo(RetryOperation.UNREGISTER.name());
    assertThat(row.getAssetKey()).isEqualTo("modeling:model:9");
    assertThat(row.getPayload()).isNull();
  }

  @Test
  void backoffDoublesUpToOneHourAndTheTwelfthFailureIsDead() {
    // claim 已在库里 attempts+1，所以"这次是第几次失败" = 读值 + 1。
    assertThat(statusAfterFail(0)).isEqualTo(RetryStatus.PENDING.name());
    assertThat(statusAfterFail(10)).isEqualTo(RetryStatus.PENDING.name());
    assertThat(statusAfterFail(11)).isEqualTo(RetryStatus.DEAD.name());
    assertThat(statusAfterFail(15)).isEqualTo(RetryStatus.DEAD.name());

    assertThat(delayAfterFail(0)).isEqualTo(1L);
    assertThat(delayAfterFail(5)).isEqualTo(32L);
    assertThat(delayAfterFail(10)).isEqualTo(1_024L);
    assertThat(delayAfterFail(11)).isEqualTo(2_048L);
    // 退避封顶一小时：再密也追不上人，再稀也没到一天。
    assertThat(delayAfterFail(20)).isEqualTo(3_600L);
  }

  @Test
  void failureMessagesAreBoundedAndNeverTheReasonTheWorkerDies() {
    reset(mapper);
    String huge = "x".repeat(3_000);
    store.fail(task(1), new RuntimeException(huge));

    ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
    verify(mapper).fail(eq("t-1"), error.capture(), anyLong(), anyString());
    assertThat(error.getValue()).hasSize(2_000);

    // message 为 null 的异常（NPE）也要有可读的 last_error，否则 DEAD 之后无从复盘。
    reset(mapper);
    store.fail(task(1), new NullPointerException());
    verify(mapper).fail(eq("t-1"), eq("NullPointerException"), anyLong(), anyString());
  }

  @Test
  void claimIsExactlyTheAtomicUpdateResult() {
    when(mapper.claim("t-1")).thenReturn(1, 0);
    assertThat(store.claim(task(1))).isTrue();
    assertThat(store.claim(task(1))).isFalse();
  }

  @Test
  void dueRowsComeBackAsTasksAndPayloadRoundTripsThroughDeserialize() {
    MdRegisterRetryPO row = new MdRegisterRetryPO();
    row.setTaskId("t-1");
    row.setProjectId(PROJECT);
    row.setTypeName("dataModel");
    row.setSourceId("42");
    row.setAssetKey("modeling:model:9");
    row.setOperation(RetryOperation.REGISTER.name());
    row.setAttempts(3);
    row.setPayload(
        "{\"typeName\":\"dataModel\",\"sourceId\":\"42\",\"assetKey\":\"modeling:model:9\","
            + "\"sourceHash\":\"sha-demo\",\"sourceUpdatedAt\":[2026,9,20,10,0],"
            + "\"attributes\":{},\"operator\":\"alice\"}");
    when(mapper.selectDue(20)).thenReturn(List.of(row));

    List<RegisterRetryStore.Task> tasks = store.due(20);
    assertThat(tasks).hasSize(1);
    assertThat(tasks.get(0).operation()).isEqualTo(RetryOperation.REGISTER);
    assertThat(tasks.get(0).attempts()).isEqualTo(3);
    assertThat(store.deserialize(tasks.get(0).payload()).getAssetKey())
        .isEqualTo("modeling:model:9");

    // 队列行允许 payload 为 NULL（UNREGISTER），但 REGISTER 任务缺了它无法重放——fail 会把它送向 DEAD。
    assertThatThrownBy(() -> store.deserialize(null)).isInstanceOf(IllegalStateException.class);
  }

  private String statusAfterFail(int attemptsBeforeClaim) {
    reset(mapper);
    store.fail(task(attemptsBeforeClaim), new RuntimeException("boom"));
    ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
    verify(mapper).fail(anyString(), anyString(), anyLong(), status.capture());
    return status.getValue();
  }

  private long delayAfterFail(int attemptsBeforeClaim) {
    reset(mapper);
    store.fail(task(attemptsBeforeClaim), new RuntimeException("boom"));
    ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);
    verify(mapper).fail(anyString(), anyString(), delay.capture(), anyString());
    return delay.getValue();
  }

  private static RegisterRetryStore.Task task(int attempts) {
    return new RegisterRetryStore.Task(
        "t-1", PROJECT, "dataModel", "42", "modeling:model:9", RetryOperation.REGISTER, attempts, null);
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
