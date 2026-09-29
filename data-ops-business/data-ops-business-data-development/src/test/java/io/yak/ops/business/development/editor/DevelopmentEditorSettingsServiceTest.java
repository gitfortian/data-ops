package io.yak.ops.business.development.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.repository.DevelopmentEditorSettingRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DevelopmentEditorSettingsServiceTest {

  @Test
  void returnsDefaultsWhenNothingWasPersisted() {
    DevelopmentEditorSettingRepository repository = mock(DevelopmentEditorSettingRepository.class);
    when(repository.findJson("bruce")).thenReturn(Optional.empty());
    DevelopmentEditorSettingsService service =
        new DevelopmentEditorSettingsService(repository, new ObjectMapper());

    Map<String, Object> settings = service.get("bruce");

    assertThat(settings.get("theme")).isEqualTo("Yak-Light");
    assertThat(settings.get("fontSize")).isEqualTo(14);
    assertThat(settings.get("wordWrap")).isEqualTo(true);
  }

  @Test
  void normalizesAndPersistsThroughRepositoryContract() throws Exception {
    DevelopmentEditorSettingRepository repository = mock(DevelopmentEditorSettingRepository.class);
    ObjectMapper objectMapper = new ObjectMapper();
    DevelopmentEditorSettingsService service =
        new DevelopmentEditorSettingsService(repository, objectMapper);

    Map<String, Object> saved = service.save(
        " bruce ",
        Map.of("theme", "", "fontSize", 99, "lineHeight", 0.2));

    assertThat(saved.get("theme")).isEqualTo("Yak-Light");
    assertThat(saved.get("fontSize")).isEqualTo(32);
    assertThat(saved.get("lineHeight")).isEqualTo(1.0);

    ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    verify(repository).upsertJson(eq("bruce"), json.capture());
    Map<?, ?> persisted = objectMapper.readValue(json.getValue(), Map.class);
    assertThat(persisted.get("fontSize")).isEqualTo(32);
  }
}
