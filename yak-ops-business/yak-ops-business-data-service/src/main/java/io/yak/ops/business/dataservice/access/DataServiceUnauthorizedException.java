package io.yak.ops.business.dataservice.access;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class DataServiceUnauthorizedException extends RuntimeException {
  public DataServiceUnauthorizedException(String message) { super(message); }
}
