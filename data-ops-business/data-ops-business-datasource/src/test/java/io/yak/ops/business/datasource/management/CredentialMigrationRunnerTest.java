package io.yak.ops.business.datasource.management;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.config.CredentialCipher;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 存量补加密只在密钥可用时执行，并且逐项目恢复上下文(Ticket 05)。 */
@ExtendWith(MockitoExtension.class)
class CredentialMigrationRunnerTest {

  @Mock private DataSourceRepository repository;
  @Mock private CredentialCipher cipher;
  @Mock private ProjectContextScope projectScope;

  @Test
  void doesNothingWithoutConfiguredKey() {
    when(cipher.isEnabled()).thenReturn(false);

    runner(true).migratePlainCredentials();

    verifyNoInteractions(repository, projectScope);
  }

  @Test
  void doesNothingWhenSwitchIsOff() {
    runner(false).migratePlainCredentials();

    verifyNoInteractions(repository, projectScope, cipher);
  }

  @Test
  void encryptsEachProjectInsideItsOwnContext() {
    when(cipher.isEnabled()).thenReturn(true);
    when(repository.distinctProjectIds()).thenReturn(List.of(7L, 8L));
    when(repository.encryptStoredCredentials()).thenReturn(2);
    when(projectScope.call(any(), any())).thenAnswer(call -> runSupplier(call.getArgument(1)));

    runner(true).migratePlainCredentials();

    verify(projectScope).call(eq(new ProjectContext(7L, null)), any());
    verify(projectScope).call(eq(new ProjectContext(8L, null)), any());
    verify(repository, times(2)).encryptStoredCredentials();
  }

  @Test
  void oneBrokenProjectDoesNotAbortTheBackfill() {
    when(cipher.isEnabled()).thenReturn(true);
    when(repository.distinctProjectIds()).thenReturn(List.of(7L, 8L));
    when(projectScope.call(eq(new ProjectContext(7L, null)), any()))
        .thenThrow(new IllegalStateException("项目 7 已删除"));
    when(projectScope.call(eq(new ProjectContext(8L, null)), any()))
        .thenAnswer(call -> runSupplier(call.getArgument(1)));

    CredentialMigrationRunner runner = runner(true);

    assertThatCode(runner::migratePlainCredentials).doesNotThrowAnyException();
    verify(projectScope).call(eq(new ProjectContext(8L, null)), any());
  }

  private CredentialMigrationRunner runner(boolean migrateOnStartup) {
    DataSourceProperties properties = new DataSourceProperties();
    properties.getCredential().setMigrateOnStartup(migrateOnStartup);
    return new CredentialMigrationRunner(repository, cipher, properties, projectScope);
  }

  private static Object runSupplier(Supplier<?> action) {
    return action.get();
  }
}
