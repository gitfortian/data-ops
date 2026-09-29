package io.yak.ops.business.dataservice.runtime;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class DataServiceCircuitOpenException extends RuntimeException {
  public DataServiceCircuitOpenException(String message) { super(message); }
}
