package io.yak.ops.business.dataservice.access;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class DataServiceForbiddenException extends RuntimeException {
  public DataServiceForbiddenException(String message) {
    super(message);
  }
}
