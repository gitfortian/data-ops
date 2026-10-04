from pathlib import Path
base=Path('data-ops-business/data-ops-business-agent/src/main/java/io/yak/ops/business/agent/conversation')
p=base/'AgentTurnRegistry.java';s=p.read_text(encoding='utf-8');s=s.replace('import java.util.Optional;','import java.util.Optional;\nimport java.util.function.BooleanSupplier;')
pos=s.index('  /** 正常终态清理')
s=s[:pos]+'''  /** Publish the cancellation handle atomically with QUEUED -> RUNNING. */
  synchronized boolean claimAndRegister(
      String sessionId, String turnId, TurnSubscription subscription,
      Runnable cancelFinalizer, BooleanSupplier claim) {
    if (!claim.getAsBoolean()) return false;
    register(sessionId, turnId, subscription, cancelFinalizer);
    return true;
  }

'''+s[pos:]
s=s.replace('    String turnId = sessionIndex.remove(sessionId);','    cancelBySession(sessionId, () -> {});\n  }\n\n  void cancelBySession(String sessionId, Runnable cancelQueued) {\n    Handle handle;\n    synchronized (this) {\n      String turnId = sessionIndex.remove(sessionId);\n      if (turnId == null) {\n        cancelQueued.run();\n        return;\n      }\n      handle = running.remove(turnId);\n    }')
s=s.replace('    if (turnId == null) {\n      return;\n    }\n    Handle handle = running.remove(turnId);\n','')
p.write_text(s,encoding='utf-8')
p=base/'AgentChatService.java';s=p.read_text(encoding='utf-8');a=s.index('    if (turnRegistry.runningTurnId(sessionId).isPresent())',s.index('  public void cancel('));b=s.index('\n  }',a)
s=s[:a]+'''    turnRegistry.cancelBySession(sessionId, () -> {
      int cancelled = turnRepository.cancelQueuedBySession(sessionId);
      log.info("queued turns cancelled: sessionId={}, count={}", sessionId, cancelled);
    });'''+s[b:];p.write_text(s,encoding='utf-8')
p=base/'AgentTurnExecutor.java';s=p.read_text(encoding='utf-8');a=s.index('    if (!turnRepository.claimForExecution(turnId))');b=s.index('    TurnInput input;',a);s=s[:a]+s[b:];s=s.replace('    } catch (RuntimeException e) {\n      turnRepository.fail', '    } catch (RuntimeException e) {\n      if (!turnRepository.claimForExecution(turnId)) {\n        completed.countDown();\n        return;\n      }\n      turnRepository.fail',1)
a=s.index('    if (record.kind() ==');b=s.index('    try {\n      TurnSubscription subscription',a)
block=s[a:b];block=block[:block.index('    // 单飞真相')]
s=s[:a]+'''    TurnState state = new TurnState(completed);
    state.startMillis = System.currentTimeMillis();
    DeferredSubscription deferred = new DeferredSubscription();
    if (!turnRegistry.claimAndRegister(sessionId, turnId, deferred,
        () -> finishCancelled(record, input, state),
        () -> turnRepository.claimForExecution(turnId))) {
      completed.countDown();
      return;
    }
    if (state.completed.getCount() == 0) return;
'''+block+s[b:]
s=s.replace('      turnRegistry.register(sessionId, turnId, subscription, () -> finishCancelled(record, input, state));','      deferred.attach(subscription);')
s=s.replace('    try {\n      if (state.clarified.get()) {','    try {\n      if (state.completed.getCount() == 0) return;\n      if (state.clarified.get()) {',1)
pos=s.index('  /** 一轮执行的运行时可变状态')
s=s[:pos]+'''  /** Cancellation can arrive while runtime.stream is still constructing its handle. */
  private static final class DeferredSubscription implements TurnSubscription {
    private TurnSubscription subscription;
    private boolean disposed;

    synchronized void attach(TurnSubscription subscription) {
      if (disposed) subscription.dispose();
      else this.subscription = subscription;
    }

    @Override
    public synchronized void dispose() {
      disposed = true;
      if (subscription != null) {
        subscription.dispose();
        subscription = null;
      }
    }
  }

'''+s[pos:];p.write_text(s,encoding='utf-8')
