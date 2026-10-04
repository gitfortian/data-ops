from pathlib import Path
p=Path('data-ops-business/data-ops-business-agent/src/test/java/io/yak/ops/business/agent/conversation/AgentTurnExecutorTest.java');s=p.read_text(encoding='utf-8');i=s.index('  @Test\n  void admissionRemains')
s=s[:i]+'''  @Test
  void cancellationDuringRuntimeConstructionIsRememberedAndDisposesLateHandle() throws Exception {
    CountDownLatch constructing = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(turnRepository.cancelRunning("t1")).thenReturn(true);
    when(agentRuntime.stream(anyLong(), anyString(), anyString(), anyString(), anyLong(), any(), any(), any()))
        .thenAnswer(inv -> {
          onComplete = inv.getArgument(6);
          constructing.countDown();
          assertTrue(release.await(2, TimeUnit.SECONDS));
          return (io.yak.ops.business.agent.runtime.TurnSubscription) () -> disposed.countDown();
        });
    var worker = Executors.newSingleThreadExecutor();
    try {
      var execution = worker.submit(() -> executor().executeAndAwait(startRecord()));
      assertTrue(constructing.await(2, TimeUnit.SECONDS));
      registry.cancelBySession("s1");
      verify(turnRepository).cancelRunning("t1");
      release.countDown();
      execution.get(2, TimeUnit.SECONDS);
      assertTrue(disposed.await(2, TimeUnit.SECONDS));
      onComplete.run();
      verify(turnRepository, never()).complete(anyString());
      assertTrue(registry.runningTurnId("s1").isEmpty());
    } finally {
      release.countDown();
      worker.shutdownNow();
    }
  }

'''+s[i:];p.write_text(s,encoding='utf-8')
