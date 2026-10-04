from pathlib import Path
p=Path('data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/conversation/AgentTurnExecutor.java')
s=p.read_text(encoding='utf-8')
s=s.replace('  public void execute(AgentTurnRecord record) {','''  public void execute(AgentTurnRecord record) {
    startExecution(record, new CountDownLatch(1));
  }

  /** Dispatcher-only admission lease: keep a worker until inference settles or shutdown interrupts. */
  void executeAndAwait(AgentTurnRecord record) {
    CountDownLatch completed = new CountDownLatch(1);
    startExecution(record, completed);
    try {
      completed.await();
    } catch (InterruptedException shutdown) {
      Thread.currentThread().interrupt();
      // Leave durable RUNNING truth for the existing orphan -> INTERRUPTED recovery policy.
      turnRegistry.detach(record.turnId());
    }
  }

  private void startExecution(AgentTurnRecord record, CountDownLatch completed) {''')
s=s.replace('      appendQuietly(record.turnId(), ChatTurnEvent.error("会话未绑定项目空间，无法执行推理"));\n      return;', '      appendQuietly(record.turnId(), ChatTurnEvent.error("会话未绑定项目空间，无法执行推理"));\n      completed.countDown();\n      return;')
s=s.replace('() -> doExecute(record));','() -> doExecute(record, completed));')
s=s.replace('private void doExecute(AgentTurnRecord record)', 'private void doExecute(AgentTurnRecord record, CountDownLatch completed)')
s=s.replace('log.debug("turn skipped (not claimable): turnId={}, status={}", turnId, record.status());\n      return;', 'log.debug("turn skipped (not claimable): turnId={}, status={}", turnId, record.status());\n      completed.countDown();\n      return;')
s=s.replace('appendQuietly(turnId, ChatTurnEvent.error("轮次输入无效，已终止执行"));\n      return;', 'appendQuietly(turnId, ChatTurnEvent.error("轮次输入无效，已终止执行"));\n      completed.countDown();\n      return;')
s=s.replace('TurnState state = new TurnState();','TurnState state = new TurnState(completed);')
start=s.index('    try {\n      // The worker budget covers')
end=s.index('\n  }',start)
s=s[:start]+s[end:]
s=s.replace('    final CountDownLatch completed = new CountDownLatch(1);','''    final CountDownLatch completed;
    TurnState(CountDownLatch completed) {
      this.completed = completed;
    }''')
p.write_text(s,encoding='utf-8')
p=p.with_name('AgentTurnDispatcher.java')
p.write_text(p.read_text(encoding='utf-8').replace('executor.execute(record);','executor.executeAndAwait(record);'),encoding='utf-8')
p=Path('data-ops-business/data-ops-business-agent/src/test/java/io/yak/ops/business/agent/conversation/AgentTurnDispatcherTest.java')
p.write_text(p.read_text(encoding='utf-8').replace('.execute(','.executeAndAwait('),encoding='utf-8')
