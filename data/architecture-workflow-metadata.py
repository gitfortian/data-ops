from pathlib import Path
import re
p=Path('data-ops-business/data-ops-business-workflow/src/main/java/io/yak/ops/business/workflow/runtime/WorkflowRuntime.java');s=p.read_text(encoding='utf-8')
a=s.index('  record WorkflowExecutionMetadata(');b=s.index('  private final class RuntimeNodeExecutor',a);segment=s[a:b]
for name in ['WorkflowExecutionMetadata','NodeMetadata']:
 match=re.search(r'  record '+name+r'\([\s\S]*?\n  \}',segment);assert match
 imports='import java.util.Map;\n'+('import io.yak.ops.business.job.task.TaskVersionSnapshot;\n' if name=='NodeMetadata' else '')
 body='package io.yak.ops.business.workflow.runtime;\n\n'+imports+'\n'+match[0].replace('  record','record',1)+'\n'
 p.with_name(name+'.java').write_text(body,encoding='utf-8')
s=s[:a]+s[b:];p.write_text(s,encoding='utf-8')
p=p.with_name('WorkflowExecutionProjection.java');s=p.read_text(encoding='utf-8');s=re.sub(r'import io\.yak\.ops\.business\.workflow\.runtime\.WorkflowRuntime\.(WorkflowExecutionMetadata|NodeMetadata);\n','',s);p.write_text(s,encoding='utf-8')
