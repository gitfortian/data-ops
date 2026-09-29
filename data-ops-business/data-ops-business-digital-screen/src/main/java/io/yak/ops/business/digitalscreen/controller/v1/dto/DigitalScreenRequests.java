package io.yak.ops.business.digitalscreen.controller.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public final class DigitalScreenRequests {

  private DigitalScreenRequests() {
  }

  public record CreateDigitalScreenRequest(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 2000) String description,
      @NotBlank @Size(max = 128) String templateId,
      Map<String, Object> bindings) {
  }

  public record UpdateDigitalScreenRequest(
      @Size(max = 200) String name,
      @Size(max = 2000) String description,
      Map<String, Object> bindings) {
  }
}
