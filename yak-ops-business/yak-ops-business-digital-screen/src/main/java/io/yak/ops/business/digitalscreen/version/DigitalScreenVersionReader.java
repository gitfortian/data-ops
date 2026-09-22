package io.yak.ops.business.digitalscreen.version;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenVersion;
import io.yak.ops.business.digitalscreen.repository.DigitalScreenRepository;
import io.yak.ops.business.digitalscreen.repository.DigitalScreenVersionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Read role for immutable Digital Screen publication history. */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DigitalScreenVersionReader {

  private final DigitalScreenRepository screens;
  private final DigitalScreenVersionRepository versions;

  public List<DigitalScreenVersion> versions(long screenId) {
    requireScreen(screenId);
    return versions.list(screenId);
  }

  public DigitalScreenVersion version(long screenId, int versionNo) {
    requireScreen(screenId);
    return versions.findByVersionNo(screenId, versionNo)
        .orElseThrow(() -> versionNotFound(screenId, versionNo));
  }

  public DigitalScreenVersion published(long screenId) {
    DigitalScreen screen = requireScreen(screenId);
    if (screen.publishedVersionId() == null) {
      throw new IllegalArgumentException("数字化大屏尚未发布：" + screenId);
    }
    DigitalScreenVersion version = versions.findById(screen.publishedVersionId())
        .orElseThrow(() -> new IllegalStateException(
            "数字化大屏当前发布版本不存在：" + screenId));
    if (version.screenId() != screenId) {
      throw new IllegalStateException("数字化大屏发布版本指针异常：" + screenId);
    }
    return version;
  }

  private DigitalScreen requireScreen(long screenId) {
    return screens.findById(screenId)
        .orElseThrow(() -> new IllegalArgumentException(
            "数字化大屏不存在或已被删除：" + screenId));
  }

  private IllegalArgumentException versionNotFound(long screenId, int versionNo) {
    return new IllegalArgumentException(
        "数字化大屏版本不存在：" + screenId + " / V" + versionNo);
  }
}
