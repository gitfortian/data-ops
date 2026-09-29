package io.yak.ops.business.agent.controller.v1;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Agent 接口参数/状态类错误的统一出口。
 * 直接手写 JSON 到 response：chat/stream 的 Accept 为 text/event-stream，
 * 走返回值+消息转换器会因内容协商失败（No acceptable representation）二次报错。
 */
@ConditionalOnAgentEnabled
@RestControllerAdvice(assignableTypes = {AgentController.class, AgentSkillController.class})
public class AgentExceptionHandler {

  private static final String BODY_TEMPLATE =
      "{\"code\":%d,\"message\":\"%s\",\"data\":null}";

  @ExceptionHandler(io.yak.ops.business.agent.conversation.TurnConflictException.class)
  public void handleConflict(
      io.yak.ops.business.agent.conversation.TurnConflictException exception,
      HttpServletResponse response)
      throws Exception {
    write(exception, response, HttpStatus.CONFLICT.value());
  }

  @ExceptionHandler(io.yak.ops.business.agent.conversation.AgentSkillConflictException.class)
  public void handleSkillConflict(
      io.yak.ops.business.agent.conversation.AgentSkillConflictException exception,
      HttpServletResponse response)
      throws Exception {
    write(exception, response, HttpStatus.CONFLICT.value());
  }

  @ExceptionHandler(io.yak.ops.business.agent.conversation.AgentSkillNotFoundException.class)
  public void handleSkillNotFound(
      io.yak.ops.business.agent.conversation.AgentSkillNotFoundException exception,
      HttpServletResponse response)
      throws Exception {
    write(exception, response, HttpStatus.NOT_FOUND.value());
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  public void handleBadRequest(RuntimeException exception, HttpServletResponse response)
      throws Exception {
    write(exception, response, HttpStatus.BAD_REQUEST.value());
  }

  private void write(RuntimeException exception, HttpServletResponse response, int status)
      throws Exception {
    String message =
        exception.getMessage() == null || exception.getMessage().isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
    response.getWriter().write(String.format(BODY_TEMPLATE, status, escaped));
  }
}
