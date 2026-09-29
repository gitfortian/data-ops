package io.yak.ops.business.sync.offline.repository;

import static io.yak.ops.business.sync.offline.OfflineProjectTestContext.currentProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.dao.OfflineJobExecutionDao;
import org.junit.jupiter.api.Test;

class OfflineJobExecutionRepositoryAdapterTest {

  @Test
  void delegatesDurableRetryReservationToDao() {
    OfflineJobExecutionDao dao = mock(OfflineJobExecutionDao.class);
    when(dao.reserveRetry(org.mockito.ArgumentMatchers.eq(9L), any())).thenReturn(true);
    OfflineJobExecutionRepositoryAdapter repository =
        new OfflineJobExecutionRepositoryAdapter(dao, currentProject());

    assertThat(repository.reserveRetry(9L)).isTrue();
    verify(dao).reserveRetry(org.mockito.ArgumentMatchers.eq(9L), any());
  }

  @Test
  void rejectsInvalidRetryReservationIdentityBeforeDao() {
    OfflineJobExecutionDao dao = mock(OfflineJobExecutionDao.class);
    OfflineJobExecutionRepositoryAdapter repository =
        new OfflineJobExecutionRepositoryAdapter(dao, currentProject());

    assertThatThrownBy(() -> repository.reserveRetry(0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ExecutionId");
  }
}
