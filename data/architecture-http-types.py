from pathlib import Path
import re
p=Path('data-ops-ui/src/services/workflow/instances.ts');s=p.read_text(encoding='utf-8');s="import type { AttemptVO, NodeInstanceVO, WorkflowInstanceVO, WorkflowRunDTO, NodeDTO, EdgeDTO } from './httpContracts.generated';\n"+s
for name,generated,overrides in [('WorkflowNodePayload','NodeDTO',{'triggerRule':'WorkflowTriggerRule','failurePolicy':'WorkflowNodeFailurePolicy'}),('WorkflowEdgePayload','EdgeDTO',{}),('WorkflowRunPayload','WorkflowRunDTO',{'nodes':'WorkflowNodePayload[]','edges':'WorkflowEdgePayload[]','failureStrategy':'WorkflowFailureStrategy'}),('WorkflowAttempt','AttemptVO',{}),('WorkflowNodeInstance','NodeInstanceVO',{'attempts':'WorkflowAttempt[]','triggerRule':'WorkflowTriggerRule','failurePolicy':'WorkflowNodeFailurePolicy'}),('WorkflowInstance','WorkflowInstanceVO',{'nodes':'WorkflowNodeInstance[]','failureStrategy':'WorkflowFailureStrategy'})]:
 m=re.search(r'export interface '+name+r' \{\n(.*?)\n\}',s,re.S);assert m,name
 fields=re.findall(r'^  (\w+)(\?)?: (.*?);',m[1],re.M)
 required=[f for f,opt,t in fields if not opt and f not in overrides]
 omit=[f for f in overrides]
 parts=[]
 if required:parts.append('Required<Pick<'+generated+', '+ ' | '.join(repr(x) for x in required)+'>>')
 parts.append('Omit<'+generated+', '+ ' | '.join(repr(x) for x in required+omit)+'>') if required+omit else parts.append(generated)
 if overrides:
  props=['  '+f+('?' if opt else '')+': '+overrides[f]+';' for f,opt,t in fields if f in overrides]
  parts.append('{\n'+'\n'.join(props)+'\n}')
 s=s[:m.start()]+'export type '+name+' = '+' &\n  '.join(parts)+';'+s[m.end():]
p.write_text(s,encoding='utf-8')
