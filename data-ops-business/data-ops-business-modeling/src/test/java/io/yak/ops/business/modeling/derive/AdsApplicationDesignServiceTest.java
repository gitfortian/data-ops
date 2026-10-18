package io.yak.ops.business.modeling.derive;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.repository.ModelVersionRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.version.ModelPublishedStructureReader;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AdsApplicationDesignServiceTest {
  private final ModelRepository models = mock(ModelRepository.class);
  private final ModelVersionRepository versions = mock(ModelVersionRepository.class);
  private final ModelStructureService structures = mock(ModelStructureService.class);
  private final ModelPublishedStructureReader published = mock(ModelPublishedStructureReader.class);
  private final CurrentProject project = mock(CurrentProject.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AdsApplicationDesignService service = new AdsApplicationDesignService(
      models, versions, structures, published, project, jdbc, new ObjectMapper());

  private final Model ads = new Model(
      10L, "ads_order_overview", "订单经营日报", ModelDialect.MYSQL, null,
      ModelStatus.DRAFT, "alice", null, null, "ADS", 50L, null, null, null, null);

  @Test
  void rejectsMissingApplicationIdentityBeforeReadingOrPersistingUpstream() {
    when(models.findByIdForUpdate(10L)).thenReturn(Optional.of(ads));
    var request = new AdsApplicationDesignService.Request(
        "", "订单经营日报", "alice", "订单运营看板", "REPORT", "DWS",
        20L, 30L, 2, "a".repeat(64),
        List.of(new AdsApplicationDesignService.OutputField("order_day", "order_day", null, "LOW")),
        List.of("order_day"), "", "", null, "DAILY");
    assertThatThrownBy(() -> service.save(10L, request, "alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("应用编码");
    verify(models, never()).findById(20L);
    verifyNoInteractions(versions, published, jdbc);
  }

  @Test
  void rejectsUnsupportedConsumerTypeBeforePersisting() {
    when(models.findByIdForUpdate(10L)).thenReturn(Optional.of(ads));
    var request = new AdsApplicationDesignService.Request(
        "order_overview", "订单经营日报", "alice", "订单运营看板", "UNKNOWN", "DWS",
        20L, 30L, 2, "a".repeat(64),
        List.of(new AdsApplicationDesignService.OutputField("order_day", "order_day", null, "LOW")),
        List.of("order_day", "ORDER_DAY"), "", "", null, "DAILY");
    assertThatThrownBy(() -> service.save(10L, request, "alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("消费类型");
    verify(models, never()).findById(20L);
    verifyNoInteractions(versions, published, jdbc);
  }
}
