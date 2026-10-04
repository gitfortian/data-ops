from pathlib import Path
p=Path('data-ops-business/data-ops-business-datasource/src/main/java/io/yak/ops/business/datasource/execution/DefaultSqlExecutionRuntime.java')
s=p.read_text(encoding='utf-8')
start=s.index('    void finishSucceeded()')
end=s.index('    void failBeforeStart(',start)
s=s[:start]+s[start:end].replace('      completeIfTerminal();\n','')+s[end:]
needle='      retainCompleted(execution.executionId());'
s=s.replace(needle,'      // Await includes resource cleanup, not just the aggregate state transition.\n      execution.completeIfTerminal();\n'+needle)
p.write_text(s,encoding='utf-8')
