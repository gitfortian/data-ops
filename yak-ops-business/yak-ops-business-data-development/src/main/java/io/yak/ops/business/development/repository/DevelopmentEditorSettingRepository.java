package io.yak.ops.business.development.repository;

import java.util.Optional;

/** Persistence contract for per-user Data Development editor settings. */
public interface DevelopmentEditorSettingRepository {

  Optional<String> findJson(String userKey);

  void upsertJson(String userKey, String settingJson);
}
