package io.yak.ops.business.digitalscreen.application;

import java.util.Map;

public record UpdateDigitalScreenCommand(
    String name,
    String description,
    Map<String, Object> bindings) {
}
