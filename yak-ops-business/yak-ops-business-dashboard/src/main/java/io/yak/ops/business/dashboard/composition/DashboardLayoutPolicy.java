package io.yak.ops.business.dashboard.composition;

import io.yak.ops.business.dashboard.domain.WidgetSpec;
import org.springframework.stereotype.Component;

/** Owns the 24-column Dashboard widget layout contract. */
@Component
public class DashboardLayoutPolicy {

  public void validate(WidgetSpec value, String widgetKey) {
    if (value.x() < 0 || value.x() >= 24) {
      throw new IllegalArgumentException("Widget x 必须在 0~23：" + widgetKey);
    }
    if (value.y() < 0) {
      throw new IllegalArgumentException("Widget y 不能小于 0：" + widgetKey);
    }
    if (value.w() <= 0 || value.w() > 24) {
      throw new IllegalArgumentException("Widget w 必须在 1~24：" + widgetKey);
    }
    if (value.h() <= 0 || value.h() > 60) {
      throw new IllegalArgumentException("Widget h 必须在 1~60：" + widgetKey);
    }
    if (value.x() + value.w() > 24) {
      throw new IllegalArgumentException("Widget 超出 24 栅格：" + widgetKey);
    }
    if (value.minW() != null && (value.minW() <= 0 || value.minW() > value.w())) {
      throw new IllegalArgumentException("Widget minW 必须大于 0 且不能超过 w：" + widgetKey);
    }
    if (value.minH() != null && (value.minH() <= 0 || value.minH() > value.h())) {
      throw new IllegalArgumentException("Widget minH 必须大于 0 且不能超过 h：" + widgetKey);
    }
  }
}
