package io.yak.ops.business.digitalscreen.application;

import java.util.Map;

public record CreateDigitalScreenCommand(
    String name,
    String description,
    String templateId,
    Map<String, Object> bindings) {
}
