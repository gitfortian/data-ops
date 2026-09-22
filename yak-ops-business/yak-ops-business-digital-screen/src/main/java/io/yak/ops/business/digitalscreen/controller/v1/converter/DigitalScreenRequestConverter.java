package io.yak.ops.business.digitalscreen.controller.v1.converter;

import io.yak.ops.business.digitalscreen.application.CreateDigitalScreenCommand;
import io.yak.ops.business.digitalscreen.application.UpdateDigitalScreenCommand;
import io.yak.ops.business.digitalscreen.controller.v1.dto.DigitalScreenRequests.CreateDigitalScreenRequest;
import io.yak.ops.business.digitalscreen.controller.v1.dto.DigitalScreenRequests.UpdateDigitalScreenRequest;
import org.springframework.stereotype.Component;

@Component
public class DigitalScreenRequestConverter {

  public CreateDigitalScreenCommand create(CreateDigitalScreenRequest request) {
    return new CreateDigitalScreenCommand(
        request.name(),
        request.description(),
        request.templateId(),
        request.bindings());
  }

  public UpdateDigitalScreenCommand update(UpdateDigitalScreenRequest request) {
    return new UpdateDigitalScreenCommand(
        request.name(),
        request.description(),
        request.bindings());
  }
}
