package io.yak.ops.business.sync.realtime.execution;

/** Internal execution command identity used by start and version replacement flows. */
enum RealtimeExecutionIntent {
  START("START_REQUESTED", "启动"),
  RESTART_EXECUTION("RESTART_EXECUTION_REQUESTED", "重启当前运行版本"),
  APPLY_PUBLISHED_VERSION("APPLY_PUBLISHED_VERSION_REQUESTED", "应用已发布版本");

  private final String eventType;
  private final String messagePrefix;

  RealtimeExecutionIntent(String eventType, String messagePrefix) {
    this.eventType = eventType;
    this.messagePrefix = messagePrefix;
  }

  String eventType() {
    return eventType;
  }

  String messagePrefix() {
    return messagePrefix;
  }
}
