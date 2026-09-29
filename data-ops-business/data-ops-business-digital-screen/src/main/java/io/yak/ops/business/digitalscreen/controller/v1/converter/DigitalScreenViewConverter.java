package io.yak.ops.business.digitalscreen.controller.v1.converter;

import io.yak.ops.business.digitalscreen.controller.v1.vo.DigitalScreenViews.DigitalScreenVO;
import io.yak.ops.business.digitalscreen.controller.v1.vo.DigitalScreenViews.DigitalScreenVersionSummaryVO;
import io.yak.ops.business.digitalscreen.controller.v1.vo.DigitalScreenViews.DigitalScreenVersionVO;
import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenVersion;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DigitalScreenViewConverter {

  public DigitalScreenVO screen(DigitalScreen source) {
    return new DigitalScreenVO(
        Long.toString(source.id()),
        source.name(),
        source.description(),
        source.templateId(),
        source.templateVersion(),
        source.status().name().toLowerCase(Locale.ROOT),
        source.bindings(),
        source.revision(),
        source.publishedRevision(),
        source.publishedVersionNo() > 0 ? source.publishedVersionNo() : null,
        source.hasUnpublishedChanges(),
        source.publishedTime(),
        source.createTime(),
        source.updateTime());
  }

  public DigitalScreenVersionSummaryVO versionSummary(
      DigitalScreenVersion source,
      int currentVersionNo) {
    return new DigitalScreenVersionSummaryVO(
        Long.toString(source.id()),
        source.versionNo(),
        source.sourceRevision(),
        source.name(),
        source.publishedTime(),
        source.versionNo() == currentVersionNo);
  }

  public DigitalScreenVersionVO version(DigitalScreenVersion source) {
    return new DigitalScreenVersionVO(
        Long.toString(source.id()),
        Long.toString(source.screenId()),
        source.versionNo(),
        source.sourceRevision(),
        source.name(),
        source.description(),
        source.templateId(),
        source.templateVersion(),
        source.bindings(),
        source.publishedTime(),
        source.createTime());
  }
}
